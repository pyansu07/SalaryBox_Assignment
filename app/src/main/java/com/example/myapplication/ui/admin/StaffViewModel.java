package com.example.myapplication.ui.admin;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.myapplication.data.Staff;
import com.example.myapplication.repository.StaffRepository;

import java.util.List;

public class StaffViewModel extends AndroidViewModel {

    private final StaffRepository repository;
    private final LiveData<List<Staff>> allStaff;

    public StaffViewModel(@NonNull Application application) {
        super(application);
        repository = new StaffRepository(application);
        allStaff = repository.getAllStaff();
    }

    public LiveData<List<Staff>> getAllStaff() {
        return allStaff;
    }

    public LiveData<Staff> getStaffById(int id) {
        return repository.getStaffById(id);
    }

    public void addStaff(String name, String employeeId, StaffRepository.InsertCallback callback) {
        repository.insert(new Staff(name, employeeId), callback);
    }

    public void updateStaff(Staff staff, Runnable onDone) {
        repository.update(staff, onDone);
    }
}
