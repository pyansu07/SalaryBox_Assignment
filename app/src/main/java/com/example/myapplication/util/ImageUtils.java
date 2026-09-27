package com.example.myapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ImageUtils {

    private ImageUtils() {
    }

    public static File createSelfieFile(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "selfies");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create selfies directory");
        }
        String name = "selfie_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        return new File(dir, name);
    }

    /** Loads a bitmap from disk, correcting orientation using EXIF metadata written by CameraX. */
    public static Bitmap loadCorrectlyOrientedBitmap(File file) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            throw new IOException("Could not decode bitmap from " + file.getAbsolutePath());
        }
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        float rotationDegrees = 0f;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotationDegrees = 90f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotationDegrees = 180f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotationDegrees = 270f;
                break;
        }
        if (rotationDegrees == 0f) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Encrypts a plaintext selfie JPEG at rest: reads it, AES-GCM encrypts the bytes via
     * {@link CryptoUtils}, writes them to a sibling ".jpg.enc" file, and deletes the plaintext
     * original. Returns the encrypted file, whose path is what gets persisted in the DB.
     */
    public static File encryptSelfieFile(File plainFile) throws IOException {
        byte[] plainBytes;
        try (FileInputStream in = new FileInputStream(plainFile)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            plainBytes = buffer.toByteArray();
        }

        byte[] encryptedBytes = CryptoUtils.encrypt(plainBytes);
        File encryptedFile = new File(plainFile.getParentFile(), plainFile.getName() + ".enc");
        try (FileOutputStream out = new FileOutputStream(encryptedFile)) {
            out.write(encryptedBytes);
        }
        plainFile.delete();
        return encryptedFile;
    }
}
