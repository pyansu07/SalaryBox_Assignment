package com.example.myapplication.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM encryption backed by an Android Keystore key. The key is generated once, marked
 * non-exportable, and never leaves the keystore in plaintext - encrypted data can only be
 * decrypted on this device, by this app, for as long as the app is installed.
 *
 * Chosen over SQLCipher (whole-database encryption) as the cleaner fit for the time available:
 * it needs no change to Room's SQLite driver or query layer, and lets us encrypt exactly the two
 * things that matter (the embedding field, selfie files) without re-plumbing the data layer.
 */
public final class CryptoUtils {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "attendance_face_data_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private CryptoUtils() {
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }

    /** Encrypts UTF-8 text for storage as a String column: returns Base64(iv || ciphertext). */
    public static String encryptToString(String plainText) {
        byte[] cipherBytes = encrypt(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP);
    }

    public static String decryptFromString(String base64CipherText) {
        byte[] cipherBytes = Base64.decode(base64CipherText, Base64.NO_WRAP);
        return new String(decrypt(cipherBytes), StandardCharsets.UTF_8);
    }

    public static byte[] encrypt(byte[] plainBytes) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plainBytes);
            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt", e);
        }
    }

    public static byte[] decrypt(byte[] ivAndCipherText) {
        try {
            SecretKey key = getOrCreateKey();
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            int cipherLen = ivAndCipherText.length - GCM_IV_LENGTH_BYTES;
            byte[] cipherText = new byte[cipherLen];
            System.arraycopy(ivAndCipherText, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherLen);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt", e);
        }
    }
}
