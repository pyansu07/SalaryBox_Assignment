package com.example.myapplication.ui.staff;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.Attendance;
import com.example.myapplication.util.CryptoUtils;
import com.example.myapplication.util.GeocoderHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final GeocoderHelper geocoderHelper;
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Resolved-address cache keyed by Attendance.id, so scrolling doesn't re-geocode the same row. */
    private final Map<Integer, String> resolvedAddresses = new HashMap<>();
    /** Decrypted-thumbnail cache keyed by Attendance.id, so scrolling doesn't re-decrypt the same selfie. */
    private final Map<Integer, Bitmap> thumbnailCache = new HashMap<>();
    private List<Attendance> records = new ArrayList<>();

    public AttendanceAdapter(GeocoderHelper geocoderHelper) {
        this.geocoderHelper = geocoderHelper;
    }

    public void submitList(List<Attendance> newList) {
        this.records = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendance attendance = records.get(position);
        holder.timestampText.setText(dateFormat.format(new Date(attendance.getTimestamp())));
        bindLocation(holder, attendance);
        bindThumbnail(holder, attendance);
    }

    private void bindLocation(ViewHolder holder, Attendance attendance) {
        String coordinates = String.format(Locale.getDefault(),
                "%.5f, %.5f", attendance.getLatitude(), attendance.getLongitude());

        String cached = resolvedAddresses.get(attendance.getId());
        if (cached != null) {
            holder.locationText.setText(cached);
            return;
        }

        holder.locationText.setText(coordinates);
        int attendanceId = attendance.getId();
        geocoderHelper.getAddress(attendance.getLatitude(), attendance.getLongitude(), address -> {
            String display = address != null ? address : coordinates;
            resolvedAddresses.put(attendanceId, display);
            notifyDataSetChanged();
        });
    }

    private void bindThumbnail(ViewHolder holder, Attendance attendance) {
        Bitmap cached = thumbnailCache.get(attendance.getId());
        if (cached != null) {
            holder.selfieThumbnail.setImageBitmap(cached);
            return;
        }

        holder.selfieThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        int attendanceId = attendance.getId();
        String path = attendance.getSelfiePath();
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = decryptThumbnail(path);
            if (bitmap != null) {
                mainHandler.post(() -> {
                    thumbnailCache.put(attendanceId, bitmap);
                    notifyDataSetChanged();
                });
            }
        });
    }

    /** Decrypts an attendance selfie file (see ImageUtils.encryptSelfieFile) and downsamples it for a small list thumbnail. */
    private static Bitmap decryptThumbnail(String path) {
        if (path == null) return null;
        File file = new File(path);
        if (!file.exists()) return null;
        try {
            byte[] encryptedBytes;
            try (FileInputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                encryptedBytes = buffer.toByteArray();
            }
            byte[] plainBytes = CryptoUtils.decrypt(encryptedBytes);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.length, bounds);

            int targetSize = 160; // a little larger than the on-screen thumbnail, for crisp scaling
            int sampleSize = 1;
            while (bounds.outWidth / (sampleSize * 2) >= targetSize && bounds.outHeight / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            return BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.length, decodeOptions);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    /** Call from the hosting Activity's onDestroy() to stop the thumbnail-decoding thread. */
    public void shutdown() {
        thumbnailExecutor.shutdown();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView timestampText;
        final TextView locationText;
        final ImageView selfieThumbnail;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            timestampText = itemView.findViewById(R.id.timestampText);
            locationText = itemView.findViewById(R.id.locationText);
            selfieThumbnail = itemView.findViewById(R.id.selfieThumbnail);
        }
    }
}
