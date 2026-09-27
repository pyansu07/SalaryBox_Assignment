package com.example.myapplication.ml;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

/**
 * Wraps ML Kit's on-device face detector for the final, high-accuracy pass run on a captured
 * still photo (landmarks + classification enabled, for alignment and eye-open state). Assumes a
 * single, mostly-frontal face per photo - when several faces are detected the largest bounding
 * box (closest to camera) is used.
 */
public class FaceDetectorHelper {

    public interface Callback {
        void onFaceDetected(Bitmap sourceBitmap, Face face);

        void onNoFaceDetected();

        void onError(Exception e);
    }

    private final FaceDetector detector;

    public FaceDetectorHelper() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);
    }

    public void detectLargestFace(@NonNull Bitmap sourceBitmap, @NonNull Callback callback) {
        InputImage image = InputImage.fromBitmap(sourceBitmap, 0);
        detector.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        if (faces == null || faces.isEmpty()) {
                            callback.onNoFaceDetected();
                            return;
                        }
                        Face largest = faces.get(0);
                        int largestArea = area(largest.getBoundingBox());
                        for (Face f : faces) {
                            int a = area(f.getBoundingBox());
                            if (a > largestArea) {
                                largest = f;
                                largestArea = a;
                            }
                        }
                        callback.onFaceDetected(sourceBitmap, largest);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        callback.onError(e);
                    }
                });
    }

    private static int area(Rect r) {
        return Math.max(0, r.width()) * Math.max(0, r.height());
    }

    public void close() {
        detector.close();
    }
}
