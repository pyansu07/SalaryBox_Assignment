package com.example.myapplication.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "staff")
public class Staff {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;

    private String employeeId;

    /** 192-d MobileFaceNet embedding stored as comma-separated floats, null until enrolled. */
    @Nullable
    private String faceEmbedding;

    public Staff(String name, String employeeId) {
        this.name = name;
        this.employeeId = employeeId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    @Nullable
    public String getFaceEmbedding() {
        return faceEmbedding;
    }

    public void setFaceEmbedding(@Nullable String faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
    }

    public boolean isEnrolled() {
        return faceEmbedding != null && !faceEmbedding.isEmpty();
    }
}
