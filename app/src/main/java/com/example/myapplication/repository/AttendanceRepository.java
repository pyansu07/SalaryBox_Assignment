package com.example.myapplication.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.myapplication.data.Attendance;
import com.example.myapplication.data.AppDatabase;
import com.example.myapplication.data.AttendanceDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceRepository {

    private final AttendanceDao attendanceDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AttendanceRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        attendanceDao = db.attendanceDao();
    }

    public LiveData<List<Attendance>> getAttendanceForStaff(int staffId) {
        return attendanceDao.getAttendanceForStaff(staffId);
    }

    public LiveData<List<Attendance>> getAllAttendance() {
        return attendanceDao.getAllAttendance();
    }

    public void insert(Attendance attendance, Runnable onDone) {
        executor.execute(() -> {
            attendanceDao.insert(attendance);
            if (onDone != null) onDone.run();
        });
    }
}
