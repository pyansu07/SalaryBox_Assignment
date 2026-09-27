package com.example.myapplication.ml;

/** Per-frame quality/liveness signal produced by {@link GuidedCaptureAnalyzer}. */
public class FrameAssessment {

    public enum Brightness { OK, TOO_DARK, TOO_BRIGHT }

    public final boolean hasFace;
    public final float faceSizeRatio;
    public final boolean blurry;
    public final Brightness brightness;
    /** -1 when eye-open classification wasn't available for this frame. */
    public final float leftEyeOpenProbability;
    public final float rightEyeOpenProbability;
    /**
     * Head yaw in degrees from ML Kit's {@code Face.getHeadEulerAngleY()} - only meaningful when
     * {@link #hasFace} is true. Raw ML Kit sign convention (positive = face turned toward the
     * right side of the processed image); see {@link GuidedCaptureController} for how this is
     * mapped to "the user's own left/right" for a front camera.
     */
    public final float headEulerAngleY;

    public FrameAssessment(boolean hasFace, float faceSizeRatio, boolean blurry, Brightness brightness,
                            float leftEyeOpenProbability, float rightEyeOpenProbability, float headEulerAngleY) {
        this.hasFace = hasFace;
        this.faceSizeRatio = faceSizeRatio;
        this.blurry = blurry;
        this.brightness = brightness;
        this.leftEyeOpenProbability = leftEyeOpenProbability;
        this.rightEyeOpenProbability = rightEyeOpenProbability;
        this.headEulerAngleY = headEulerAngleY;
    }

    public boolean passesQualityGate() {
        return hasFace
                && !blurry
                && brightness == Brightness.OK
                && faceSizeRatio >= GuidedCaptureAnalyzer.MIN_FACE_SIZE_RATIO;
    }
}
