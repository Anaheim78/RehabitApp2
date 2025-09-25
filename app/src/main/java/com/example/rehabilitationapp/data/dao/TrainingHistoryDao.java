package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.rehabilitationapp.data.model.TrainingHistory;
import com.example.rehabilitationapp.data.model.TrainingHistoryWithTitle;
import com.example.rehabilitationapp.data.model.TrainingItem;

import java.util.List;
@Dao
public interface TrainingHistoryDao {
    @Insert
    void insert(TrainingHistory history);

    @Insert
    long insertSync(TrainingHistory entity); // 注意：不要在 Main Thread 呼叫


    @Query("SELECT * FROM TrainingHistory ORDER BY createAt DESC")
    List<TrainingHistory> getAllHistory();

    @Query("SELECT * FROM TrainingHistory WHERE DATE(createAt/1000, 'unixepoch') = DATE('now') ORDER BY createAt DESC")
    List<TrainingHistory> getTodayHistory();

    // 在 TrainingHistoryDao.java 中添加
    @Query("SELECT * FROM TrainingHistory WHERE date(createAt/1000,'unixepoch','localtime') = date('now','localtime') ORDER BY createAt DESC")
    List<TrainingHistory> getTodayRecords();


    //給歷史紀錄底下的圖卡
    @Query(" SELECT h.createAt    AS createAt, i.title AS title , h.trainingLabel AS trainingLabel FROM   trainingHistory h LEFT JOIN training_items i ON UPPER(i.analysisType) = UPPER(h.trainingLabel) WHERE  h.createAt BETWEEN :startMs AND :endMs ORDER BY h.createAt ASC")
    List<TrainingHistoryWithTitle> getHistoryWithTitleForDay(long startMs, long endMs);
}