package com.example.myapplication.ml;

/**
 * Drives one guided-capture "round": live quality gating (blur/brightness/face-size), an
 * optional head-pose requirement (for multi-angle enrolment), and an open -> closed -> open
 * blink-liveness challenge. Feed it every live {@link FrameAssessment}; it reports human-readable
 * guidance text and signals when the round is satisfied and it's safe to fire the actual still
 * capture.
 *
 * One controller instance is reused across multiple rounds (e.g. the 3 shots of multi-sample
 * enrolment) via repeated {@link #startRound}/{@link #stop} calls.
 */
public class GuidedCaptureController implements GuidedCaptureAnalyzer.Listener {

    /** Which head pose a round requires before it will proceed to the blink challenge. */
    public enum PoseRequirement {
        ANY,
        STRAIGHT,
        TURN_LEFT,
        TURN_RIGHT
    }

    private static final float STRAIGHT_MAX_ABS_YAW = 15f;
    private static final float TURN_MIN_ABS_YAW = 20f;

    /**
     * ML Kit's {@code Face.getHeadEulerAngleY()} is documented as: "Positive euler y is when the
     * face turns toward the right side of the image that is being processed" - i.e. the sign is
     * relative to the raw (unmirrored) frame ML Kit sees, not what the user sees in the mirrored
     * front-camera preview, and not necessarily the subject's own left/right.
     *
     * NOT YET VERIFIED ON A PHYSICAL DEVICE. Based on that documented convention with an
     * unmirrored front-camera InputImage, this assumes turning the user's head to their own LEFT
     * produces a NEGATIVE yaw and turning to their own RIGHT produces a POSITIVE yaw. Confirm this
     * on-device (log a.headEulerAngleY while turning your head a known direction) and flip this
     * single constant to -1 if the "turn left"/"turn right" prompts are accepting the wrong way.
     */
    private static final int YAW_SIGN_FOR_USERS_RIGHT = +1;

    public interface Callback {
        void onGuidance(String message);

        void onReadyToCapture();
    }

    private final BlinkLivenessDetector blinkDetector = new BlinkLivenessDetector();
    private Callback callback;
    private PoseRequirement poseRequirement = PoseRequirement.ANY;
    private boolean active;

    public void startRound(Callback callback) {
        startRound(PoseRequirement.ANY, callback);
    }

    public void startRound(PoseRequirement poseRequirement, Callback callback) {
        this.poseRequirement = poseRequirement;
        this.callback = callback;
        blinkDetector.reset();
        active = true;
    }

    public void stop() {
        active = false;
    }

    @Override
    public void onAssessment(FrameAssessment a) {
        if (!active || callback == null) return;

        if (!a.hasFace) {
            blinkDetector.reset();
            callback.onGuidance("Position your face in the frame");
            return;
        }
        if (a.brightness == FrameAssessment.Brightness.TOO_DARK) {
            blinkDetector.reset();
            callback.onGuidance("Too dark - find better lighting");
            return;
        }
        if (a.brightness == FrameAssessment.Brightness.TOO_BRIGHT) {
            blinkDetector.reset();
            callback.onGuidance("Too bright - avoid strong backlight");
            return;
        }
        if (a.blurry) {
            blinkDetector.reset();
            callback.onGuidance("Hold steady");
            return;
        }
        if (a.faceSizeRatio < GuidedCaptureAnalyzer.MIN_FACE_SIZE_RATIO) {
            blinkDetector.reset();
            callback.onGuidance("Move closer");
            return;
        }

        String poseIssue = poseGuidance(a.headEulerAngleY, poseRequirement);
        if (poseIssue != null) {
            blinkDetector.reset();
            callback.onGuidance(poseIssue);
            return;
        }

        BlinkLivenessDetector.State state =
                blinkDetector.update(a.leftEyeOpenProbability, a.rightEyeOpenProbability, System.currentTimeMillis());
        switch (state) {
            case WAITING_FOR_OPEN:
            case WAITING_FOR_CLOSE:
                callback.onGuidance("Blink to continue");
                break;
            case WAITING_FOR_REOPEN:
                callback.onGuidance("Blink detected - open your eyes");
                break;
            case CONFIRMED:
                active = false;
                callback.onGuidance("Great, hold still...");
                callback.onReadyToCapture();
                break;
            case TIMED_OUT:
                blinkDetector.reset();
                callback.onGuidance("No blink detected - blink to continue");
                break;
        }
    }

    /** Returns null when the pose requirement is satisfied, or guidance text when it isn't. */
    private static String poseGuidance(float rawYaw, PoseRequirement requirement) {
        // From here on, positive = the user's own right, negative = the user's own left.
        float yaw = rawYaw * YAW_SIGN_FOR_USERS_RIGHT;

        switch (requirement) {
            case STRAIGHT:
                return Math.abs(yaw) > STRAIGHT_MAX_ABS_YAW ? "Turn back to face forward" : null;
            case TURN_LEFT:
                if (yaw > -TURN_MIN_ABS_YAW) {
                    return "Turn a bit more to your left";
                }
                return null;
            case TURN_RIGHT:
                if (yaw < TURN_MIN_ABS_YAW) {
                    return "Turn a bit more to your right";
                }
                return null;
            case ANY:
            default:
                return null;
        }
    }
}
