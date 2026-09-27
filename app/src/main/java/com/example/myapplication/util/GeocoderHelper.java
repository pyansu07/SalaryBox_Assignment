package com.example.myapplication.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reverse-geocodes a lat/lng pair into a short human-readable locality string, for display only
 * - the DB always keeps the raw coordinates regardless of whether this succeeds. Requires network
 * access on most devices (the platform Geocoder is backed by a network service); callers should
 * treat a null result as "couldn't resolve, show the coordinates instead".
 */
public class GeocoderHelper {

    public interface Callback {
        void onAddress(@Nullable String humanReadableAddress);
    }

    private final Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeocoderHelper(Context context) {
        geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());
    }

    public void getAddress(double latitude, double longitude, Callback callback) {
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(latitude, longitude, 1,
                    addresses -> mainHandler.post(() -> callback.onAddress(format(addresses))));
        } else {
            executor.execute(() -> {
                String result;
                try {
                    @SuppressWarnings("deprecation")
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    result = format(addresses);
                } catch (IOException | RuntimeException e) {
                    result = null;
                }
                String finalResult = result;
                mainHandler.post(() -> callback.onAddress(finalResult));
            });
        }
    }

    @Nullable
    private static String format(@Nullable List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) return null;
        Address address = addresses.get(0);

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, address.getLocality() != null ? address.getLocality() : address.getSubAdminArea());
        appendIfPresent(sb, address.getAdminArea());
        appendIfPresent(sb, address.getCountryName());

        if (sb.length() > 0) return sb.toString();
        return address.getAddressLine(0); // fall back to the full line if we couldn't build a short one
    }

    private static void appendIfPresent(StringBuilder sb, @Nullable String part) {
        if (part == null || part.isEmpty()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(part);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
