package com.example.myapplication.ui.admin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.Staff;

import java.util.ArrayList;
import java.util.List;

public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

    public interface OnStaffClickListener {
        void onStaffClick(Staff staff);
    }

    private List<Staff> staffList = new ArrayList<>();
    private final OnStaffClickListener listener;

    public StaffAdapter(OnStaffClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Staff> newList) {
        this.staffList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StaffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_staff, parent, false);
        return new StaffViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
        Staff staff = staffList.get(position);
        holder.nameText.setText(staff.getName());
        holder.employeeIdText.setText("ID: " + staff.getEmployeeId());
        if (staff.isEnrolled()) {
            holder.enrollmentStatusText.setText("Face enrolled");
            holder.enrollmentStatusText.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            holder.enrollmentStatusText.setText("Face not enrolled");
            holder.enrollmentStatusText.setTextColor(Color.parseColor("#C62828"));
        }
        holder.itemView.setOnClickListener(v -> listener.onStaffClick(staff));
    }

    @Override
    public int getItemCount() {
        return staffList.size();
    }

    static class StaffViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView employeeIdText;
        final TextView enrollmentStatusText;

        StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            employeeIdText = itemView.findViewById(R.id.employeeIdText);
            enrollmentStatusText = itemView.findViewById(R.id.enrollmentStatusText);
        }
    }
}
