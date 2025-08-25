// TrainingPlan.java (Entity)
package com.example.rehabilitationapp.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int localId;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "created_at_formatted")
    public String createdAtFormatted;
}
