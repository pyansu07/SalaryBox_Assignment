package com.example.myapplication.ui.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.Staff;
import com.example.myapplication.databinding.ActivityStaffProfileBinding;

import java.util.Locale;

public class StaffProfileActivity extends AppCompatActivity {

    public static final String EXTRA_STAFF_ID = "extra_staff_id";

    private ActivityStaffProfileBinding binding;
    private StaffViewModel viewModel;
    private int staffId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStaffProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        staffId = getIntent().getIntExtra(EXTRA_STAFF_ID, -1);
        viewModel = new ViewModelProvider(this).get(StaffViewModel.class);

        viewModel.getStaffById(staffId).observe(this, this::bindStaff);

        binding.enrollFaceButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EnrollFaceActivity.class);
            intent.putExtra(EnrollFaceActivity.EXTRA_STAFF_ID, staffId);
            startActivity(intent);
        });
    }

    private void bindStaff(Staff staff) {
        if (staff == null) return;
        binding.nameText.setText(staff.getName());
        binding.employeeIdText.setText("Employee ID: " + staff.getEmployeeId());
        binding.avatarText.setText(initialOf(staff.getName()));

        if (staff.isEnrolled()) {
            binding.enrollmentStatusText.setText("Face enrolled ✓");
            binding.enrollmentStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_success));
            binding.enrollmentStatusText.setBackgroundResource(R.drawable.bg_badge_success);
            binding.enrollFaceButton.setText("Re-enrol Face");
        } else {
            binding.enrollmentStatusText.setText("Face not enrolled yet");
            binding.enrollmentStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_error));
            binding.enrollmentStatusText.setBackgroundResource(R.drawable.bg_badge_neutral);
            binding.enrollFaceButton.setText("Enrol Face");
        }
    }

    private static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }
}
