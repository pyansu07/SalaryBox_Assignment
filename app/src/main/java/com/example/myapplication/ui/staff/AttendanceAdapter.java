package com.example.myapplication.ui.staff;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.Attendance;
import com.example.myapplication.util.GeocoderHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final GeocoderHelper geocoderHelper;

    /** Resolved-address cache keyed by Attendance.id, so scrolling doesn't re-geocode the same row. */
    private final Map<Integer, String> resolvedAddresses = new HashMap<>();
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

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView timestampText;
        final TextView locationText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            timestampText = itemView.findViewById(R.id.timestampText);
            locationText = itemView.findViewById(R.id.locationText);
        }
    }
}
