package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.rehabilitationapp.data.model.TrainingHistory;
import com.example.rehabilitationapp.data.model.TrainingItem;

import java.util.List;
@Dao
public interface TrainingHistoryDao {
    @Insert
    void insert(TrainingHistory history);

    @Query("SELECT * FROM TrainingHistory ORDER BY createAt DESC")
    List<TrainingHistory> getAllHistory();

    @Query("SELECT * FROM TrainingHistory WHERE DATE(createAt/1000, 'unixepoch') = DATE('now') ORDER BY createAt DESC")
    List<TrainingHistory> getTodayHistory();

    // 在 TrainingHistoryDao.java 中添加
    @Query("SELECT * FROM TrainingHistory WHERE date(createAt/1000,'unixepoch','localtime') = date('now','localtime') ORDER BY createAt DESC")
    List<TrainingHistory> getTodayRecords();
}