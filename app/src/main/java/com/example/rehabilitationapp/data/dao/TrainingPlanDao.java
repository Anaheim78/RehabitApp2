package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.rehabilitationapp.data.model.PlanItemCrossRef;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.List;

@Dao
public interface TrainingPlanDao {

    // 單純插入一個計畫
    @Insert
    long insertPlan(TrainingPlan plan);

    // 批次插入（給 preload 用）
    @Insert
    void insertPlanList(List<TrainingPlan> plans);

    // 插入 plan-item 關聯
    @Insert
    void insertCrossRef(PlanItemCrossRef ref);

    // 或加便利用：
    @Transaction
    default void insertCrossRef(long planId, int itemId) {
        insertCrossRef(new PlanItemCrossRef(planId, itemId));
    }

    // 全部查詢（含 item）
    @Transaction
    @Query("SELECT * FROM training_plans")
    List<PlanWithItems> getAllPlansWithItems();

    // 查單筆（含 item）
    @Transaction
    @Query("SELECT * FROM training_plans WHERE id = :id")
    PlanWithItems getPlanWithItemsById(int id);

    // 查單筆（不含 item）
    @Query("SELECT * FROM training_plans WHERE id = :id")
    TrainingPlan getById(int id);

    // 查全部（不含 item）
    @Query("SELECT * FROM training_plans")
    List<TrainingPlan> getAll();

    // 刪除整個 plan（不會自動 cascade 刪除 crossref）
    @Delete
    void delete(TrainingPlan plan);
}
