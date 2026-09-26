package com.example.myapplication.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AttendanceDao {

    @Insert
    long insert(Attendance attendance);

    @Query("SELECT * FROM attendance WHERE staffId = :staffId ORDER BY timestamp DESC")
    LiveData<List<Attendance>> getAttendanceForStaff(int staffId);

    @Query("SELECT * FROM attendance ORDER BY timestamp DESC")
    LiveData<List<Attendance>> getAllAttendance();
}
