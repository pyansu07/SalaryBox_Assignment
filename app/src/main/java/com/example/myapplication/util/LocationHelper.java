package com.example.myapplication.util;

import android.annotation.SuppressLint;
import android.content.Context;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/** Wraps FusedLocationProviderClient to fetch a single current location fix. */
public class LocationHelper {

    public interface LocationCallback {
        void onLocation(double latitude, double longitude);

        void onFailure(Exception e);
    }

    private final FusedLocationProviderClient client;

    public LocationHelper(Context context) {
        client = LocationServices.getFusedLocationProviderClient(context);
    }

    @SuppressLint("MissingPermission")
    public void getCurrentLocation(LocationCallback callback) {
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build();

        client.getCurrentLocation(request, new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocation(location.getLatitude(), location.getLongitude());
                    } else {
                        callback.onFailure(new IllegalStateException("Location unavailable. Enable GPS and try again."));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }
}
