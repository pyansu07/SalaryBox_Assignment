package com.example.myapplication.ui.admin;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.data.EmbeddingCodec;
import com.example.myapplication.data.Staff;
import com.example.myapplication.databinding.ActivityCameraCaptureBinding;
import com.example.myapplication.ml.FaceAligner;
import com.example.myapplication.ml.FaceDetectorHelper;
import com.example.myapplication.ml.FaceEmbedder;
import com.example.myapplication.ml.GuidedCaptureAnalyzer;
import com.example.myapplication.ml.GuidedCaptureController;
import com.example.myapplication.util.CameraXHelper;
import com.example.myapplication.util.CryptoUtils;
import com.example.myapplication.util.ImageUtils;
import com.example.myapplication.util.PermissionUtils;
import com.google.mlkit.vision.face.Face;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Guides the admin through capturing {@link #SHOT_PROMPTS}.length shots (straight-on plus two
 * mild turns) so verification later has some pose variety to compare against, not just one
 * frozen angle. Each shot must independently pass the live quality gate and blink-liveness
 * challenge before it's captured and embedded.
 */
public class EnrollFaceActivity extends AppCompatActivity {

    public static final String EXTRA_STAFF_ID = "extra_staff_id";

    private static final String[] SHOT_PROMPTS = {
            "Look straight ahead",
            "Turn your head slightly left",
            "Turn your head slightly right"
    };

    private static final GuidedCaptureController.PoseRequirement[] SHOT_POSES = {
            GuidedCaptureController.PoseRequirement.STRAIGHT,
            GuidedCaptureController.PoseRequirement.TURN_LEFT,
            GuidedCaptureController.PoseRequirement.TURN_RIGHT
    };

    private ActivityCameraCaptureBinding binding;
    private StaffViewModel viewModel;
    private CameraXHelper cameraXHelper;
    private FaceDetectorHelper faceDetectorHelper;
    private FaceEmbedder faceEmbedder;
    private GuidedCaptureAnalyzer guidedCaptureAnalyzer;
    private GuidedCaptureController guidedCaptureController;
    private Staff currentStaff;
    private boolean isProcessing = false;
    private boolean roundStarted = false;
    private int currentShotIndex = 0;
    private final List<float[]> collectedEmbeddings = new ArrayList<>();

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to enrol a face", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setTitle("Enrol Face");
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.shotLabelText.setVisibility(View.VISIBLE);

        int staffId = getIntent().getIntExtra(EXTRA_STAFF_ID, -1);
        viewModel = new ViewModelProvider(this).get(StaffViewModel.class);
        viewModel.getStaffById(staffId).observe(this, staff -> {
            currentStaff = staff;
            if (currentStaff != null && !roundStarted) {
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

        binding.retryButton.setOnClickListener(v -> beginRound());

        if (PermissionUtils.hasCameraPermission(this)) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        cameraXHelper.start(binding.previewView, true, guidedCaptureAnalyzer, e ->
                showStatus("Could not start camera: " + e.getMessage()));
    }

    /** Starts (or restarts) one guided round for the current shot: quality gate, then blink challenge. */
    private void beginRound() {
        binding.retryButton.setVisibility(View.GONE);
        setProcessing(false);
        binding.shotLabelText.setText(
                "Shot " + (currentShotIndex + 1) + " of " + SHOT_PROMPTS.length + ": " + SHOT_PROMPTS[currentShotIndex]);
        binding.guidanceText.setText("Position your face in the frame");
        guidedCaptureController.startRound(SHOT_POSES[currentShotIndex], new GuidedCaptureController.Callback() {
            @Override
            public void onGuidance(String message) {
                runOnUiThread(() -> {
                    if (!isProcessing) binding.guidanceText.setText(message);
                });
            }

            @Override
            public void onReadyToCapture() {
                runOnUiThread(EnrollFaceActivity.this::captureSelfie);
            }
        });
    }

    private void captureSelfie() {
        if (isProcessing || currentStaff == null) return;
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
                    float[] embedding = faceEmbedder.getEmbedding(aligned);
                    file.delete(); // not persisted for enrolment - only the embedding is stored
                    onShotCaptured(embedding);
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

    private void onShotCaptured(float[] embedding) {
        collectedEmbeddings.add(embedding);
        currentShotIndex++;
        runOnUiThread(() -> {
            if (currentShotIndex < SHOT_PROMPTS.length) {
                setProcessing(false);
                showStatus("Shot " + currentShotIndex + " of " + SHOT_PROMPTS.length + " captured");
                binding.guidanceText.setText("Great! Next: " + SHOT_PROMPTS[currentShotIndex]);
                binding.guidanceText.postDelayed(this::beginRound, 1500);
            } else {
                finishEnrollment();
            }
        });
    }

    private void finishEnrollment() {
        String plainSamples = EmbeddingCodec.encodeSamples(collectedEmbeddings);
        currentStaff.setFaceEmbedding(CryptoUtils.encryptToString(plainSamples));
        viewModel.updateStaff(currentStaff, () -> runOnUiThread(() -> {
            setProcessing(false);
            Toast.makeText(this, "Face enrolled for " + currentStaff.getName()
                    + " (" + collectedEmbeddings.size() + " samples)", Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    /** The current shot failed after capture (no face / detection error / io error) - let the user retry it. */
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
