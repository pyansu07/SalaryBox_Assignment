package com.example.myapplication.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts between a list of raw face-embedding float arrays (one per enrolled sample) and the
 * single string stored in Room: samples are separated by '|', and each sample's floats by ','.
 */
public final class EmbeddingCodec {

    private static final String SAMPLE_DELIMITER = "|";
    private static final Pattern SAMPLE_SPLIT_PATTERN = Pattern.compile(Pattern.quote(SAMPLE_DELIMITER));
    private static final String VALUE_DELIMITER = ",";

    private EmbeddingCodec() {
    }

    public static String encodeSamples(List<float[]> embeddings) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embeddings.size(); i++) {
            if (i > 0) sb.append(SAMPLE_DELIMITER);
            sb.append(encodeOne(embeddings.get(i)));
        }
        return sb.toString();
    }

    public static List<float[]> decodeSamples(String encoded) {
        List<float[]> result = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return result;
        for (String sample : SAMPLE_SPLIT_PATTERN.split(encoded)) {
            if (!sample.isEmpty()) {
                result.add(decodeOne(sample));
            }
        }
        return result;
    }

    private static String encodeOne(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(VALUE_DELIMITER);
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    private static float[] decodeOne(String encoded) {
        String[] parts = encoded.split(VALUE_DELIMITER);
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}
