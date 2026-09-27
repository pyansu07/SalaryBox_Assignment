package com.example.myapplication.ml;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Rotates and crops a detected face so both eyes sit level and the face is centered, using ML
 * Kit's eye landmark coordinates - improves embedding consistency across head-tilt variation for
 * both enrolment and verification. Falls back to a plain bounding-box crop if eye landmarks
 * aren't available (e.g. a very oblique pose, or landmarks disabled).
 */
public final class FaceAligner {

    /** Crop side length as a multiple of the raw face bounding box's larger dimension. */
    private static final float MARGIN_FACTOR = 1.6f;

    private FaceAligner() {
    }

    public static Bitmap align(Bitmap source, Face face) {
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

        if (leftEye == null || rightEye == null) {
            return cropToBounds(source, face.getBoundingBox());
        }

        PointF leftPos = leftEye.getPosition();
        PointF rightPos = rightEye.getPosition();

        double angleRad = Math.atan2(rightPos.y - leftPos.y, rightPos.x - leftPos.x);
        float angleDeg = (float) Math.toDegrees(angleRad);

        float eyeCenterX = (leftPos.x + rightPos.x) / 2f;
        float eyeCenterY = (leftPos.y + rightPos.y) / 2f;

        Matrix rotation = new Matrix();
        rotation.postRotate(-angleDeg, eyeCenterX, eyeCenterY);

        Bitmap rotated;
        try {
            rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), rotation, true);
        } catch (Exception e) {
            return cropToBounds(source, face.getBoundingBox());
        }

        Rect box = face.getBoundingBox();
        int side = (int) (Math.max(box.width(), box.height()) * MARGIN_FACTOR);
        if (side <= 0) {
            return cropToBounds(source, face.getBoundingBox());
        }

        // Eyes sit a little above the vertical center of a typical face crop.
        int left = Math.round(eyeCenterX - side / 2f);
        int top = Math.round(eyeCenterY - side / 2.6f);

        left = clamp(left, 0, Math.max(0, rotated.getWidth() - side));
        top = clamp(top, 0, Math.max(0, rotated.getHeight() - side));
        int width = Math.min(side, rotated.getWidth() - left);
        int height = Math.min(side, rotated.getHeight() - top);

        if (width <= 0 || height <= 0) {
            return cropToBounds(source, face.getBoundingBox());
        }
        return Bitmap.createBitmap(rotated, left, top, width, height);
    }

    private static Bitmap cropToBounds(Bitmap source, Rect bounds) {
        int left = Math.max(0, bounds.left);
        int top = Math.max(0, bounds.top);
        int right = Math.min(source.getWidth(), bounds.right);
        int bottom = Math.min(source.getHeight(), bounds.bottom);
        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);
        return Bitmap.createBitmap(source, left, top, width, height);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
