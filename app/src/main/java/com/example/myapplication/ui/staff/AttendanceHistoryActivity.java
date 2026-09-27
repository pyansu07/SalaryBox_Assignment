package com.example.myapplication.ui.staff;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.data.Attendance;
import com.example.myapplication.databinding.ActivityAttendanceHistoryBinding;
import com.example.myapplication.repository.AttendanceRepository;
import com.example.myapplication.util.GeocoderHelper;

import java.util.List;

public class AttendanceHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_STAFF_ID = "extra_staff_id";

    private ActivityAttendanceHistoryBinding binding;
    private AttendanceAdapter adapter;
    private GeocoderHelper geocoderHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int staffId = getIntent().getIntExtra(EXTRA_STAFF_ID, -1);

        geocoderHelper = new GeocoderHelper(this);
        adapter = new AttendanceAdapter(geocoderHelper);
        binding.attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attendanceRecyclerView.setAdapter(adapter);

        AttendanceRepository repository = new AttendanceRepository(getApplication());
        repository.getAttendanceForStaff(staffId).observe(this, this::onListChanged);
    }

    private void onListChanged(List<Attendance> records) {
        adapter.submitList(records);
        boolean empty = records == null || records.isEmpty();
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.attendanceRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geocoderHelper != null) geocoderHelper.shutdown();
        if (adapter != null) adapter.shutdown();
    }
}
