package com.example.myapplication.ui.admin;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.databinding.ActivityAddStaffBinding;

public class AddStaffActivity extends AppCompatActivity {

    private ActivityAddStaffBinding binding;
    private StaffViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(StaffViewModel.class);

        binding.saveButton.setOnClickListener(v -> saveStaff());
    }

    private void saveStaff() {
        String name = String.valueOf(binding.nameInput.getText()).trim();
        String employeeId = String.valueOf(binding.employeeIdInput.getText()).trim();

        if (name.isEmpty() || employeeId.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.saveButton.setEnabled(false);
        viewModel.addStaff(name, employeeId, newId -> runOnUiThread(() -> {
            Toast.makeText(this, "Staff added", Toast.LENGTH_SHORT).show();
            finish();
        }));
    }
}
