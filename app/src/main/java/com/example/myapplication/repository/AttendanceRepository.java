package com.example.myapplication.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.myapplication.data.Attendance;
import com.example.myapplication.data.AppDatabase;
import com.example.myapplication.data.AttendanceDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceRepository {

    private static final String TAG = "AttendanceRepository";

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
        insert(attendance, onDone, null);
    }

    /**
     * Inserts an attendance row, retrying once if the first attempt throws (e.g. a transient
     * disk/SQLite error). If the retry also fails, {@code onFailed} is invoked instead of the
     * failure being swallowed silently - previously an exception here just died on the background
     * executor with no way for the UI to know the record was never saved.
     */
    public void insert(Attendance attendance, Runnable onDone, @Nullable Runnable onFailed) {
        executor.execute(() -> {
            try {
                attendanceDao.insert(attendance);
            } catch (Exception firstError) {
                Log.w(TAG, "Attendance insert failed, retrying once", firstError);
                try {
                    attendanceDao.insert(attendance);
                } catch (Exception secondError) {
                    Log.e(TAG, "Attendance insert failed again, giving up", secondError);
                    if (onFailed != null) onFailed.run();
                    return;
                }
            }
            if (onDone != null) onDone.run();
        });
    }
}
