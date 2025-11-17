package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.rehabilitationapp.data.model.PlanItemCrossRef;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.List;

@Dao
public interface TrainingPlanDao {

    // ====== Plan 本體：新增 / 更新 / 刪除 / 查詢 ======

    // 單純插入一個計畫（建立新計畫）
    @Insert
    long insertPlan(TrainingPlan plan);

    // 批次插入（給 preload 用）
    @Insert
    void insertPlanList(List<TrainingPlan> plans);

    // 更新一個計畫（例如改名稱）
    @Update
    int updatePlan(TrainingPlan plan);

    // 查單筆（不含 item）
    @Query("SELECT * FROM training_plans WHERE id = :id")
    TrainingPlan getById(long id);

    // 查全部（不含 item）
    @Query("SELECT * FROM training_plans")
    List<TrainingPlan> getAll();

    // 刪除整個 plan（不會自動 cascade 刪除 crossref）
    @Delete
    void delete(TrainingPlan plan);


    // ====== Plan ↔ Item 關聯（中介表 PlanItemCrossRef）======

    // 插入一筆 plan-item 關聯
    @Insert
    void insertCrossRef(PlanItemCrossRef ref);

    // 方便用的 overloading：直接給 planId + itemId
    @Transaction
    default void insertCrossRef(long planId, int itemId) {
        insertCrossRef(new PlanItemCrossRef(planId, itemId));
    }

    // 讀取「某個 plan + 它底下所有 item」
    @Transaction
    @Query("SELECT * FROM training_plans")
    List<PlanWithItems> getAllPlansWithItems();

    @Transaction
    @Query("SELECT * FROM training_plans WHERE id = :id")
    PlanWithItems getPlanWithItemsById(long id);

    @Query("SELECT * FROM training_plans WHERE id = :id")
    TrainingPlan getPlanById(int id);
    // ====== 編輯用：先清空舊關聯，再塞新的 item ======

    // ⚠ 這裡的表名要跟 PlanItemCrossRef 的 @Entity(tableName=...) 一樣
    // 1）如果你的 Entity 沒指定 tableName，Room 預設用類名 → "PlanItemCrossRef"
    //    那下面這行就要改成：DELETE FROM PlanItemCrossRef WHERE planId = :planId
    // 2）如果你有 @Entity(tableName = "plan_item_crossref")，那就用現在這行。
    @Query("DELETE FROM PlanItemCrossRef WHERE planId = :planId")
    void deleteCrossRefsForPlan(long planId);

    // 統一做「重設某個 plan 的 item 集合」
    @Transaction
    default void replacePlanItems(long planId, List<Integer> itemIds) {
        // 先把這個 plan 原本所有 item 關聯清掉
        deleteCrossRefsForPlan(planId);

        // 再一個一個插入新的關聯
        if (itemIds != null) {
            for (int itemId : itemIds) {
                insertCrossRef(planId, itemId);
            }
        }
    }
}
