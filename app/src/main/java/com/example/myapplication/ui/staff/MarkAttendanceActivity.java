package com.example.myapplication.ui.staff;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.data.Attendance;
import com.example.myapplication.data.EmbeddingCodec;
import com.example.myapplication.data.Staff;
import com.example.myapplication.databinding.ActivityCameraCaptureBinding;
import com.example.myapplication.ml.FaceAligner;
import com.example.myapplication.ml.FaceDetectorHelper;
import com.example.myapplication.ml.FaceEmbedder;
import com.example.myapplication.ml.GuidedCaptureAnalyzer;
import com.example.myapplication.ml.GuidedCaptureController;
import com.example.myapplication.repository.AttendanceRepository;
import com.example.myapplication.repository.StaffRepository;
import com.example.myapplication.util.CameraXHelper;
import com.example.myapplication.util.CryptoUtils;
import com.example.myapplication.util.ImageUtils;
import com.example.myapplication.util.LocationHelper;
import com.example.myapplication.util.PermissionUtils;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.mlkit.vision.face.Face;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MarkAttendanceActivity extends AppCompatActivity {

    public static final String EXTRA_STAFF_ID = "extra_staff_id";

    /** Cosine similarity threshold for a match; start at 0.6 and tune after real-device testing. */
    private static final float MATCH_THRESHOLD = 0.6f;

    private ActivityCameraCaptureBinding binding;
    private StaffRepository staffRepository;
    private AttendanceRepository attendanceRepository;
    private CameraXHelper cameraXHelper;
    private FaceDetectorHelper faceDetectorHelper;
    private FaceEmbedder faceEmbedder;
    private GuidedCaptureAnalyzer guidedCaptureAnalyzer;
    private GuidedCaptureController guidedCaptureController;
    private LocationHelper locationHelper;
    private Staff staff;
    private boolean isProcessing = false;
    private boolean roundStarted = false;

    /** State held between "face matched" and "location fetched", since enabling location settings is async. */
    private File pendingEncryptedSelfieFile;
    private int pendingConfidencePercent;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to mark attendance", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Location permission is required to mark attendance", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    fetchLocationForPendingAttendance();
                } else {
                    cancelPendingAttendance("Location is off - turn it on to finish marking attendance.");
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setTitle("Mark Attendance");
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int staffId = getIntent().getIntExtra(EXTRA_STAFF_ID, -1);
        staffRepository = new StaffRepository(getApplication());
        attendanceRepository = new AttendanceRepository(getApplication());
        staffRepository.getStaffById(staffId).observe(this, s -> {
            staff = s;
            if (staff != null && !roundStarted) {
                roundStarted = true;
                beginRound();
            }
        });

        try {
            faceEmbedder = new FaceEmbedder(this);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load face recognition model", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        faceDetectorHelper = new FaceDetectorHelper();
        cameraXHelper = new CameraXHelper(this, this);
        guidedCaptureController = new GuidedCaptureController();
        guidedCaptureAnalyzer = new GuidedCaptureAnalyzer(assessment ->
                guidedCaptureController.onAssessment(assessment));
        locationHelper = new LocationHelper(this);

        binding.retryButton.setOnClickListener(v -> beginRound());

        if (PermissionUtils.hasCameraPermission(this)) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
        if (!PermissionUtils.hasLocationPermission(this)) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void startCamera() {
        cameraXHelper.start(binding.previewView, true, guidedCaptureAnalyzer, e ->
                showStatus("Could not start camera: " + e.getMessage()));
    }

    /** Starts (or restarts) one guided round: live quality gate, then blink-liveness challenge. */
    private void beginRound() {
        if (staff == null || !staff.isEnrolled()) {
            showStatus("Your face is not enrolled yet. Contact an admin.");
            return;
        }
        binding.retryButton.setVisibility(View.GONE);
        setProcessing(false);
        binding.guidanceText.setText("Position your face in the frame");
        guidedCaptureController.startRound(new GuidedCaptureController.Callback() {
            @Override
            public void onGuidance(String message) {
                runOnUiThread(() -> {
                    if (!isProcessing) binding.guidanceText.setText(message);
                });
            }

            @Override
            public void onReadyToCapture() {
                runOnUiThread(MarkAttendanceActivity.this::captureSelfie);
            }
        });
    }

    private void captureSelfie() {
        if (isProcessing) return;
        if (staff == null || !staff.isEnrolled()) {
            showStatus("Your face is not enrolled yet. Contact an admin.");
            return;
        }
        setProcessing(true);
        try {
            File outputFile = ImageUtils.createSelfieFile(this);
            cameraXHelper.takePicture(outputFile, new CameraXHelper.CaptureCallback() {
                @Override
                public void onCaptured(File file) {
                    processCapturedImage(file);
                }

                @Override
                public void onError(Exception e) {
                    onRoundFailed("Capture failed: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            onRoundFailed("Could not prepare storage: " + e.getMessage());
        }
    }

    private void processCapturedImage(File file) {
        try {
            Bitmap bitmap = ImageUtils.loadCorrectlyOrientedBitmap(file);
            faceDetectorHelper.detectLargestFace(bitmap, new FaceDetectorHelper.Callback() {
                @Override
                public void onFaceDetected(Bitmap sourceBitmap, Face face) {
                    Bitmap aligned = FaceAligner.align(sourceBitmap, face);
                    float[] liveEmbedding = faceEmbedder.getEmbedding(aligned);
                    String decryptedSamples = CryptoUtils.decryptFromString(staff.getFaceEmbedding());
                    List<float[]> enrolledEmbeddings = EmbeddingCodec.decodeSamples(decryptedSamples);
                    // 1:1 verification: staff already logged in and claimed this identity, so we
                    // only ever compare against their own enrolled samples, never search across
                    // all staff (see README for why 1:N identification isn't the right design here).
                    float similarity = FaceEmbedder.bestCosineSimilarity(liveEmbedding, enrolledEmbeddings);
                    int confidencePercent = toConfidencePercent(similarity);

                    if (similarity >= MATCH_THRESHOLD) {
                        onFaceMatched(file, confidencePercent);
                    } else {
                        file.delete();
                        onRoundFailed("Not matched - " + confidencePercent + "% confidence, try again.");
                    }
                }

                @Override
                public void onNoFaceDetected() {
                    file.delete();
                    onRoundFailed("No face detected. Please try again.");
                }

                @Override
                public void onError(Exception e) {
                    file.delete();
                    onRoundFailed("Face detection error: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            onRoundFailed("Could not read captured photo: " + e.getMessage());
        }
    }

    private void onFaceMatched(File selfieFile, int confidencePercent) {
        showStatus("Matched - " + confidencePercent + "% confidence. Checking location...");
        if (!PermissionUtils.hasLocationPermission(this)) {
            selfieFile.delete();
            onRoundFailed("Location permission required to complete attendance.");
            return;
        }

        File encryptedSelfieFile;
        try {
            // Encrypt the selfie at rest now that we know it's being kept; a non-match or
            // earlier failure just deletes the plaintext capture instead (see processCapturedImage).
            encryptedSelfieFile = ImageUtils.encryptSelfieFile(selfieFile);
        } catch (IOException e) {
            selfieFile.delete();
            onRoundFailed("Could not secure selfie: " + e.getMessage());
            return;
        }

        pendingEncryptedSelfieFile = encryptedSelfieFile;
        pendingConfidencePercent = confidencePercent;
        ensureLocationEnabledThenFetch();
    }

    /**
     * Runtime location permission being granted doesn't mean the device's Location toggle is
     * actually on - checkLocationSettings() catches that case and, via the ResolvableApiException
     * it throws, lets us show Android's native "turn on location" dialog instead of just failing
     * with a vague error once getCurrentLocation() times out.
     */
    private void ensureLocationEnabledThenFetch() {
        LocationRequest locationRequest =
                new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build();
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(response -> fetchLocationForPendingAttendance())
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            IntentSenderRequest intentSenderRequest =
                                    new IntentSenderRequest.Builder(resolvable.getResolution()).build();
                            locationSettingsLauncher.launch(intentSenderRequest);
                        } catch (Exception sendEx) {
                            cancelPendingAttendance("Could not prompt to enable location: " + sendEx.getMessage());
                        }
                    } else {
                        cancelPendingAttendance("Location services unavailable: " + e.getMessage());
                    }
                });
    }

    private void fetchLocationForPendingAttendance() {
        showStatus("Location on - fetching your position...");
        locationHelper.getCurrentLocation(new LocationHelper.LocationCallback() {
            @Override
            public void onLocation(double latitude, double longitude) {
                File selfieFile = pendingEncryptedSelfieFile;
                int confidencePercent = pendingConfidencePercent;
                pendingEncryptedSelfieFile = null;
                saveAttendance(selfieFile, latitude, longitude, confidencePercent);
            }

            @Override
            public void onFailure(Exception e) {
                cancelPendingAttendance("Could not fetch location: " + e.getMessage());
            }
        });
    }

    private void cancelPendingAttendance(String message) {
        if (pendingEncryptedSelfieFile != null) {
            pendingEncryptedSelfieFile.delete();
            pendingEncryptedSelfieFile = null;
        }
        onRoundFailed(message);
    }

    private void saveAttendance(File selfieFile, double latitude, double longitude, int confidencePercent) {
        Attendance attendance = new Attendance(
                staff.getId(),
                System.currentTimeMillis(),
                selfieFile.getAbsolutePath(),
                latitude,
                longitude);
        attendanceRepository.insert(attendance,
                () -> runOnUiThread(() -> {
                    setProcessing(false);
                    Toast.makeText(this, "Attendance marked - " + confidencePercent + "% confidence", Toast.LENGTH_LONG).show();
                    finish();
                }),
                () -> runOnUiThread(() -> {
                    selfieFile.delete();
                    onRoundFailed("Could not save attendance. Please try again.");
                }));
    }

    /** Cosine similarity is in [-1, 1]; clamp the negative half to 0% for a readable percentage. */
    private static int toConfidencePercent(float cosineSimilarity) {
        float clamped = Math.max(0f, Math.min(1f, cosineSimilarity));
        return Math.round(clamped * 100);
    }

    /** A round failed after capture (no match / no face / io error) - let the user retry. */
    private void onRoundFailed(String message) {
        runOnUiThread(() -> {
            setProcessing(false);
            showStatus(message);
            binding.guidanceText.setText("Tap Retry when ready");
            binding.retryButton.setVisibility(View.VISIBLE);
        });
    }

    private void setProcessing(boolean processing) {
        isProcessing = processing;
        binding.progressBar.setVisibility(processing ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message) {
        binding.statusText.setText(message);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.postDelayed(() -> binding.statusText.setVisibility(View.GONE), 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetectorHelper != null) faceDetectorHelper.close();
        if (faceEmbedder != null) faceEmbedder.close();
        if (guidedCaptureAnalyzer != null) guidedCaptureAnalyzer.close();
        if (cameraXHelper != null) cameraXHelper.shutdown();
    }
}
