package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.rehabilitationapp.data.model.PlanItemCrossRef;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import com.example.rehabilitationapp.data.model.User;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);

    @Query("SELECT * FROM users WHERE user_id = :id LIMIT 1")
    User getUserById(String id);
}
