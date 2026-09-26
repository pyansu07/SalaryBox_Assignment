package com.example.myapplication.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.databinding.ActivityLoginBinding;
import com.example.myapplication.repository.StaffRepository;
import com.example.myapplication.ui.admin.AdminDashboardActivity;
import com.example.myapplication.ui.staff.StaffDashboardActivity;
import com.example.myapplication.util.SessionManager;

/** Simple hardcoded-credential login that routes to the Admin or Staff dashboard. */
public class LoginActivity extends AppCompatActivity {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String STAFF_USERNAME = "staff";
    private static final String STAFF_PASSWORD = "staff123";

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        // Make sure the demo staff record exists so the staff flow always has someone to enrol/mark.
        new StaffRepository(getApplication())
                .seedDemoStaffIfNeeded(SessionManager.DEMO_STAFF_EMPLOYEE_ID, SessionManager.DEMO_STAFF_NAME);

        binding.loginButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = String.valueOf(binding.usernameInput.getText()).trim();
        String password = String.valueOf(binding.passwordInput.getText()).trim();

        if (username.equals(ADMIN_USERNAME) && password.equals(ADMIN_PASSWORD)) {
            binding.errorText.setVisibility(android.view.View.GONE);
            sessionManager.loginAsAdmin();
            startActivity(new Intent(this, AdminDashboardActivity.class));
            finish();
        } else if (username.equals(STAFF_USERNAME) && password.equals(STAFF_PASSWORD)) {
            binding.errorText.setVisibility(android.view.View.GONE);
            sessionManager.loginAsStaff(SessionManager.DEMO_STAFF_EMPLOYEE_ID);
            startActivity(new Intent(this, StaffDashboardActivity.class));
            finish();
        } else {
            binding.errorText.setVisibility(android.view.View.VISIBLE);
        }
    }
}
