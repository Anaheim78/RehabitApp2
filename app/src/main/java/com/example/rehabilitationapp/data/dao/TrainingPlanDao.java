package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.List;

@Dao
public interface TrainingPlanDao {
    @Insert
    void insert(TrainingPlan plan);

    @Delete
    void delete(TrainingPlan plan);

    @Query("SELECT * FROM training_plans")
    List<TrainingPlan> getAll();

    @Query("SELECT * FROM training_plans WHERE id = :id")
    TrainingPlan getById(int id);
}