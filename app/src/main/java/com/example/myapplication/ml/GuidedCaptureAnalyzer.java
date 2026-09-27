package com.example.myapplication.ml;

import android.graphics.Rect;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs on every live camera-preview frame to assess capture quality (blur, brightness, face
 * size) and eye-open state for the blink-liveness challenge.
 *
 * Uses a fast, landmark-free ML Kit config since it must keep up with the live frame rate; the
 * final captured photo is re-detected at higher accuracy (with landmarks) by
 * {@link FaceDetectorHelper} for alignment. Thresholds below are reasonable starting points, not
 * tuned against real hardware - see README for tuning notes.
 */
public class GuidedCaptureAnalyzer implements ImageAnalysis.Analyzer {

    /** Minimum face-bounding-box area as a fraction of the frame area ("move closer" gate). */
    public static final float MIN_FACE_SIZE_RATIO = 0.20f;

    private static final int MIN_MEAN_LUMINANCE = 50;
    private static final int MAX_MEAN_LUMINANCE = 220;
    private static final double BLUR_VARIANCE_THRESHOLD = 25.0;
    private static final int SAMPLE_STEP = 6;

    public interface Listener {
        void onAssessment(FrameAssessment assessment);
    }

    private final FaceDetector detector;
    private final Listener listener;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public GuidedCaptureAnalyzer(Listener listener) {
        this.listener = listener;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build();
        detector = FaceDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!busy.compareAndSet(false, true)) {
            // A previous frame is still being processed; drop this one to avoid falling behind.
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            busy.set(false);
            imageProxy.close();
            return;
        }

        LumaStats luma = analyzeLumaPlane(imageProxy);
        int frameArea = imageProxy.getWidth() * imageProxy.getHeight();

        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        detector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    deliverResult(faces, luma, frameArea);
                    imageProxy.close();
                    busy.set(false);
                })
                .addOnFailureListener(e -> {
                    imageProxy.close();
                    busy.set(false);
                });
    }

    private void deliverResult(List<Face> faces, LumaStats luma, int frameArea) {
        FrameAssessment.Brightness brightness;
        if (luma.mean < MIN_MEAN_LUMINANCE) {
            brightness = FrameAssessment.Brightness.TOO_DARK;
        } else if (luma.mean > MAX_MEAN_LUMINANCE) {
            brightness = FrameAssessment.Brightness.TOO_BRIGHT;
        } else {
            brightness = FrameAssessment.Brightness.OK;
        }
        boolean blurry = luma.laplacianVariance < BLUR_VARIANCE_THRESHOLD;

        if (faces == null || faces.isEmpty()) {
            listener.onAssessment(new FrameAssessment(false, 0f, blurry, brightness, -1f, -1f, 0f));
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

        float sizeRatio = frameArea > 0 ? (float) largestArea / frameArea : 0f;
        Float leftOpen = largest.getLeftEyeOpenProbability();
        Float rightOpen = largest.getRightEyeOpenProbability();

        listener.onAssessment(new FrameAssessment(
                true,
                sizeRatio,
                blurry,
                brightness,
                leftOpen != null ? leftOpen : -1f,
                rightOpen != null ? rightOpen : -1f,
                largest.getHeadEulerAngleY()));
    }

    private static int area(Rect r) {
        return Math.max(0, r.width()) * Math.max(0, r.height());
    }

    private static class LumaStats {
        double mean;
        double laplacianVariance;
    }

    /**
     * Samples the Y (luma) plane on a coarse grid to cheaply estimate mean brightness and a
     * blur proxy (variance of a discrete Laplacian). This is a heuristic, not a textbook
     * per-pixel Laplacian-of-Gaussian - it's fast enough to run on every analyzed frame and
     * good enough to distinguish "in focus" from "clearly moving/out of focus".
     */
    private static LumaStats analyzeLumaPlane(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
        ByteBuffer buffer = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();

        buffer.rewind();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        long sum = 0;
        long lapSum = 0;
        long lapSumSq = 0;
        int count = 0;

        int margin = SAMPLE_STEP * 2;
        for (int y = margin; y < height - margin; y += SAMPLE_STEP) {
            for (int x = margin; x < width - margin; x += SAMPLE_STEP) {
                int center = luma(data, rowStride, pixelStride, x, y);
                sum += center;

                int up = luma(data, rowStride, pixelStride, x, y - SAMPLE_STEP);
                int down = luma(data, rowStride, pixelStride, x, y + SAMPLE_STEP);
                int left = luma(data, rowStride, pixelStride, x - SAMPLE_STEP, y);
                int right = luma(data, rowStride, pixelStride, x + SAMPLE_STEP, y);
                int lap = 4 * center - up - down - left - right;
                lapSum += lap;
                lapSumSq += (long) lap * lap;
                count++;
            }
        }

        LumaStats result = new LumaStats();
        if (count == 0) {
            result.mean = 128;
            result.laplacianVariance = BLUR_VARIANCE_THRESHOLD * 100; // couldn't sample; don't block on blur
            return result;
        }
        result.mean = (double) sum / count;
        double lapMean = (double) lapSum / count;
        result.laplacianVariance = (double) lapSumSq / count - lapMean * lapMean;
        return result;
    }

    private static int luma(byte[] data, int rowStride, int pixelStride, int x, int y) {
        int index = y * rowStride + x * pixelStride;
        if (index < 0 || index >= data.length) return 0;
        return data[index] & 0xFF;
    }

    public void close() {
        detector.close();
    }
}
