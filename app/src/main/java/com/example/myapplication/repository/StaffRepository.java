package com.example.myapplication.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.myapplication.data.AppDatabase;
import com.example.myapplication.data.Staff;
import com.example.myapplication.data.StaffDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StaffRepository {

    private final StaffDao staffDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public StaffRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        staffDao = db.staffDao();
    }

    public LiveData<List<Staff>> getAllStaff() {
        return staffDao.getAllStaff();
    }

    public LiveData<Staff> getStaffById(int id) {
        return staffDao.getStaffById(id);
    }

    public interface InsertCallback {
        void onResult(long newId);
    }

    public void insert(Staff staff, InsertCallback callback) {
        executor.execute(() -> {
            long id = staffDao.insert(staff);
            if (callback != null) callback.onResult(id);
        });
    }

    public void update(Staff staff, Runnable onDone) {
        executor.execute(() -> {
            staffDao.update(staff);
            if (onDone != null) onDone.run();
        });
    }

    public interface StaffCallback {
        void onResult(Staff staff);
    }

    public void getStaffByEmployeeIdSync(String employeeId, StaffCallback callback) {
        executor.execute(() -> {
            Staff staff = staffDao.getStaffByEmployeeId(employeeId);
            callback.onResult(staff);
        });
    }

    /** Ensures a demo staff record exists for the hardcoded staff login, so the staff flow has something to enrol/mark against. */
    public void seedDemoStaffIfNeeded(String employeeId, String name) {
        executor.execute(() -> {
            if (staffDao.countByEmployeeId(employeeId) == 0) {
                staffDao.insert(new Staff(name, employeeId));
            }
        });
    }
}
