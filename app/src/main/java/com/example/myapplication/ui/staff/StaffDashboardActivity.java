package com.example.myapplication.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.data.Staff;
import com.example.myapplication.databinding.ActivityStaffDashboardBinding;
import com.example.myapplication.repository.StaffRepository;
import com.example.myapplication.ui.LoginActivity;
import com.example.myapplication.util.SessionManager;

public class StaffDashboardActivity extends AppCompatActivity {

    private ActivityStaffDashboardBinding binding;
    private SessionManager sessionManager;
    private StaffRepository staffRepository;
    private Staff loggedInStaff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStaffDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        sessionManager = new SessionManager(this);
        staffRepository = new StaffRepository(getApplication());

        binding.markAttendanceButton.setOnClickListener(v -> {
            if (loggedInStaff == null) return;
            Intent intent = new Intent(this, MarkAttendanceActivity.class);
            intent.putExtra(MarkAttendanceActivity.EXTRA_STAFF_ID, loggedInStaff.getId());
            startActivity(intent);
        });

        binding.viewHistoryButton.setOnClickListener(v -> {
            if (loggedInStaff == null) return;
            Intent intent = new Intent(this, AttendanceHistoryActivity.class);
            intent.putExtra(AttendanceHistoryActivity.EXTRA_STAFF_ID, loggedInStaff.getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStaff();
    }

    private void loadStaff() {
        String employeeId = sessionManager.getLoggedInStaffEmployeeId();
        staffRepository.getStaffByEmployeeIdSync(employeeId, staff -> runOnUiThread(() -> {
            loggedInStaff = staff;
            if (staff == null) return;
            binding.welcomeText.setText("Welcome, " + staff.getName());
            binding.enrollmentStatusText.setText(staff.isEnrolled()
                    ? "Face enrolled ✓ - you can mark attendance"
                    : "Your face is not enrolled yet. Ask an admin to enrol you before marking attendance.");
            binding.markAttendanceButton.setEnabled(staff.isEnrolled());
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_logout, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            sessionManager.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
