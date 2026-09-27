package com.example.myapplication.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.R;
import com.example.myapplication.data.Staff;
import com.example.myapplication.databinding.ActivityAdminDashboardBinding;
import com.example.myapplication.ui.LoginActivity;
import com.example.myapplication.util.SessionManager;

import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private StaffViewModel viewModel;
    private StaffAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        viewModel = new ViewModelProvider(this).get(StaffViewModel.class);

        adapter = new StaffAdapter(staff -> {
            Intent intent = new Intent(this, StaffProfileActivity.class);
            intent.putExtra(StaffProfileActivity.EXTRA_STAFF_ID, staff.getId());
            startActivity(intent);
        });
        binding.staffRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.staffRecyclerView.setAdapter(adapter);

        viewModel.getAllStaff().observe(this, this::onStaffListChanged);

        binding.addStaffFab.setOnClickListener(v ->
                startActivity(new Intent(this, AddStaffActivity.class)));
    }

    private void onStaffListChanged(List<Staff> staffList) {
        adapter.submitList(staffList);
        boolean empty = staffList == null || staffList.isEmpty();
        binding.emptyText.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.staffRecyclerView.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_logout, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            new SessionManager(this).logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
