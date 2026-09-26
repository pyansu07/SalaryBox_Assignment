package com.example.myapplication.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StaffDao {

    @Insert
    long insert(Staff staff);

    @Update
    void update(Staff staff);

    @Query("SELECT * FROM staff ORDER BY name ASC")
    LiveData<List<Staff>> getAllStaff();

    @Query("SELECT * FROM staff WHERE id = :id")
    LiveData<Staff> getStaffById(int id);

    @Query("SELECT * FROM staff WHERE id = :id")
    Staff getStaffByIdSync(int id);

    @Query("SELECT * FROM staff WHERE employeeId = :employeeId LIMIT 1")
    Staff getStaffByEmployeeId(String employeeId);

    @Query("SELECT COUNT(*) FROM staff WHERE employeeId = :employeeId")
    int countByEmployeeId(String employeeId);
}
