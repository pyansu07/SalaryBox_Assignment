package com.example.myapplication.ml;

/**
 * Simple open -> closed -> open blink-challenge state machine driven by per-frame eye-open
 * probabilities from ML Kit's classification mode. Requires the full sequence within a fixed
 * time window as a lightweight liveness signal.
 *
 * This defends against a static printed photo (which can't blink on cue) but NOT against a
 * video replay of a real person blinking - see README for that limitation.
 */
public class BlinkLivenessDetector {

    public enum State {
        WAITING_FOR_OPEN,
        WAITING_FOR_CLOSE,
        WAITING_FOR_REOPEN,
        CONFIRMED,
        TIMED_OUT
    }

    private static final float OPEN_THRESHOLD = 0.6f;
    private static final float CLOSED_THRESHOLD = 0.3f;
    private static final long CHALLENGE_WINDOW_MS = 8000;

    private State state = State.WAITING_FOR_OPEN;
    private long challengeStartMs = -1;

    /** Feed the latest frame's eye-open probabilities (pass -1 for either eye if unknown this frame). */
    public State update(float leftOpenProb, float rightOpenProb, long nowMs) {
        if (state == State.CONFIRMED || state == State.TIMED_OUT) {
            return state;
        }
        if (challengeStartMs < 0) {
            challengeStartMs = nowMs;
        }
        if (nowMs - challengeStartMs > CHALLENGE_WINDOW_MS) {
            state = State.TIMED_OUT;
            return state;
        }
        if (leftOpenProb < 0 || rightOpenProb < 0) {
            // Eye-open state unknown for this frame (e.g. classification briefly unavailable);
            // don't penalize progress, just wait for a usable frame.
            return state;
        }

        float avgOpen = (leftOpenProb + rightOpenProb) / 2f;
        boolean eyesOpen = avgOpen >= OPEN_THRESHOLD;
        boolean eyesClosed = avgOpen <= CLOSED_THRESHOLD;

        switch (state) {
            case WAITING_FOR_OPEN:
                if (eyesOpen) state = State.WAITING_FOR_CLOSE;
                break;
            case WAITING_FOR_CLOSE:
                if (eyesClosed) state = State.WAITING_FOR_REOPEN;
                break;
            case WAITING_FOR_REOPEN:
                if (eyesOpen) state = State.CONFIRMED;
                break;
            default:
                break;
        }
        return state;
    }

    public void reset() {
        state = State.WAITING_FOR_OPEN;
        challengeStartMs = -1;
    }

    public State getState() {
        return state;
    }
}
