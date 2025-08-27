package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.rehabilitationapp.data.model.TrainingItem;

import java.util.List;

@Dao
public interface TrainingItemDao {

    @Query("SELECT * FROM training_items")
    List<TrainingItem> getAll();

    @Insert
    void insert(TrainingItem item);

    @Insert
    void insertAll(List<TrainingItem> items);

    @Query("SELECT COUNT(*) FROM training_items")
    int count();

    @Query("SELECT * FROM training_items ORDER BY id")
    List<TrainingItem> getAllNow(); // 這裡用同步方法就好

}
