package com.example.myapplication.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * Generates a 192-d face embedding from a cropped face bitmap using a MobileFaceNet TFLite model.
 * Model expects a 112x112 RGB input normalized to [-1, 1] ((pixel - 127.5) / 128).
 */
public class FaceEmbedder {

    private static final String MODEL_FILE = "mobile_face_net.tflite";
    private static final int INPUT_SIZE = 112;

    private final Interpreter interpreter;

    public FaceEmbedder(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(context), options);
    }

    private static MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        try (FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    public float[] getEmbedding(Bitmap faceBitmap) {
        Bitmap scaled = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // This particular model export has a fixed (non-dynamic) batch dimension baked in
        // (its source demo always ran two images through it at once for comparison), so
        // feeding it a batch-of-1 array leaves its output tensor unallocated. Read the real
        // shapes off the model instead of assuming batch 1, and pad the batch by repeating
        // our single image so every batch slot has valid, non-zero input.
        int[] inputShape = interpreter.getInputTensor(0).shape();
        int batchSize = inputShape[0];
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        int embeddingSize = outputShape[1];

        float[][][][] input = new float[batchSize][INPUT_SIZE][INPUT_SIZE][3];

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[y * INPUT_SIZE + x];
                float r = ((pixel >> 16 & 0xFF) - 127.5f) / 128f;
                float g = ((pixel >> 8 & 0xFF) - 127.5f) / 128f;
                float b = ((pixel & 0xFF) - 127.5f) / 128f;
                for (int batch = 0; batch < batchSize; batch++) {
                    input[batch][y][x][0] = r;
                    input[batch][y][x][1] = g;
                    input[batch][y][x][2] = b;
                }
            }
        }

        float[][] output = new float[batchSize][embeddingSize];
        interpreter.run(input, output);
        return l2Normalize(output[0]);
    }

    private static float[] l2Normalize(float[] embedding) {
        double sumSquares = 0;
        for (float v : embedding) {
            sumSquares += v * v;
        }
        double norm = Math.sqrt(Math.max(sumSquares, 1e-10));
        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = (float) (embedding[i] / norm);
        }
        return normalized;
    }

    /** Cosine similarity between two embeddings, in [-1, 1]; 1 means identical direction. */
    public static float cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if (denom == 0) return 0f;
        return (float) (dot / denom);
    }

    /**
     * 1:1 verification against every embedding enrolled for one staff member (multi-sample
     * enrolment stores one embedding per captured angle), returning the best (highest) score.
     * This is a small linear scan over a handful of samples for a single already-claimed
     * identity, not a 1:N search across all staff - see README for that design note.
     */
    public static float bestCosineSimilarity(float[] liveEmbedding, List<float[]> enrolledEmbeddings) {
        float best = -1f;
        for (float[] enrolled : enrolledEmbeddings) {
            float similarity = cosineSimilarity(liveEmbedding, enrolled);
            if (similarity > best) best = similarity;
        }
        return best;
    }

    public void close() {
        interpreter.close();
    }
}
