package com.example.myapplication.util;

import android.content.Context;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small wrapper around CameraX preview + still-image capture + live frame analysis, shared by enrol and mark-attendance screens. */
public class CameraXHelper {

    private static final String TAG = "CameraXHelper";

    public interface InitCallback {
        void onError(Exception e);
    }

    public interface CaptureCallback {
        void onCaptured(File file);

        void onError(Exception e);
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private Camera camera;

    public CameraXHelper(Context context, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void start(PreviewView previewView, boolean useFrontCamera,
                       @Nullable ImageAnalysis.Analyzer analyzer, InitCallback callback) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                CameraSelector selector = useFrontCamera
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                provider.unbindAll();

                if (analyzer != null) {
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    imageAnalysis.setAnalyzer(analysisExecutor, analyzer);
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, imageAnalysis);
                } else {
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to start camera", e);
                callback.onError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void takePicture(File destination, CaptureCallback callback) {
        if (imageCapture == null) {
            callback.onError(new IllegalStateException("Camera not started"));
            return;
        }
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(destination).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        callback.onCaptured(destination);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        callback.onError(exception);
                    }
                });
    }

    public void shutdown() {
        analysisExecutor.shutdown();
    }
}
