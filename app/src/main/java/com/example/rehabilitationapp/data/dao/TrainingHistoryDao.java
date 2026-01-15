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


    //NAV_BAR，歷史紀錄下的圖卡使用
    @Query(" SELECT h.createAt    AS createAt, i.title AS title , h.trainingLabel AS trainingLabel,h.achievedTimes  AS achievedTimes    FROM   trainingHistory h LEFT JOIN training_items i ON UPPER(i.analysisType) = UPPER(h.trainingLabel) WHERE  h.createAt BETWEEN :startMs AND :endMs ORDER BY h.createAt ASC")
    List<TrainingHistoryWithTitle> getHistoryWithTitleForDay(long startMs, long endMs);



    //FireBase儲存歷史紀錄相關
    /** 查詢所有 Firebase 未同步的紀錄（最多 20 筆） */
    @Query("SELECT * FROM TrainingHistory WHERE synced = 0 ORDER BY createAt ASC LIMIT 20")
    List<TrainingHistory> getUnsyncedWithLimit();
    @Query("SELECT * FROM TrainingHistory WHERE synced = 0 AND createAt >= :startOfDay AND createAt < :endOfDay")
    List<TrainingHistory> getUnsyncedToday(long startOfDay, long endOfDay);

    @Query("UPDATE TrainingHistory SET synced = 1 WHERE trainingID = :id")
    void markSynced(String id);




    @Query("UPDATE TrainingHistory SET selfReportCount = :count WHERE trainingID = :id")
    void updateSelfReport(String id, int count);


    @Query("SELECT * FROM trainingHistory WHERE date(createAt/1000,'unixepoch','localtime') = date(:dateMs/1000,'unixepoch','localtime') ORDER BY createAt DESC")
    List<TrainingHistory> getRecordsByDate(long dateMs);

    // 2060105 CSV 上傳bucket紀錄相關方法
    /** 標記 CSV 已上傳成功 */
    @Query("UPDATE TrainingHistory SET csvUploaded = 1 WHERE trainingID = :id")
    void markCsvUploaded(String id);

    /** 查詢所有 CSV 未上傳的紀錄（用於重傳） */
    @Query("SELECT * FROM TrainingHistory WHERE csvUploaded = 0 AND csvFileName != '' ORDER BY createAt ASC")
    List<TrainingHistory> getUnsyncedCsvRecords();

    /** 查詢今天 CSV 未上傳的紀錄 */
    @Query("SELECT * FROM TrainingHistory WHERE csvUploaded = 0 AND csvFileName != '' AND createAt >= :startOfDay AND createAt < :endOfDay ORDER BY createAt ASC")
    List<TrainingHistory> getUnsyncedCsvToday(long startOfDay, long endOfDay);

    /** 更新 CSV 檔案名稱 ，幾乎沒用*/
    @Query("UPDATE TrainingHistory SET csvFileName = :fileName WHERE trainingID = :id")
    void updateCsvFileName(String id, String fileName);

    /** 根據 trainingID 查詢單筆紀錄 */
    @Query("SELECT * FROM TrainingHistory WHERE trainingID = :id LIMIT 1")
    TrainingHistory getById(String id);



    // 未使用
    @Query("UPDATE TrainingHistory SET saved = 1 WHERE trainingID = :id")
    void markSaved(String id);
    @Query("SELECT * FROM TrainingHistory WHERE saved = 1 AND date(createAt/1000,'unixepoch','localtime') = date('now','localtime') ORDER BY createAt DESC")
    List<TrainingHistory> getTodaySavedRecords();
}