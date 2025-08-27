// AppDatabase.java
package com.example.rehabilitationapp.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.dao.TrainingPlanDao;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.PlanItemCrossRef;
import com.example.rehabilitationapp.data.model.Preload;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import com.example.rehabilitationapp.data.model.User;

@Database(
        entities = {
                TrainingItem.class,
                TrainingPlan.class,
                PlanItemCrossRef.class,
                User.class
        },
        version = 3,              // ★ 版本 +1（原本是 2）
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_DEBUG_TAG = "DB_DEBUG_TAG";
    private static volatile AppDatabase INSTANCE;

    public abstract TrainingItemDao trainingItemDao();
    public abstract TrainingPlanDao trainingPlanDao();
    public abstract UserDao userDao();

    // ★ Migration: 2 -> 3（新增 6 欄位）
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN login_status INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE users ADD COLUMN email TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN birthday TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN name TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN gender TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN ui_style TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        Log.d(DB_DEBUG_TAG, "=== Into getInstance ===");
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Log.d(DB_DEBUG_TAG, "=== Creating new database instance ===");
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "rehab_db_2"
                            )
                            // ★ 不要用 fallbackToDestructiveMigration()，會清庫
                            .addMigrations(MIGRATION_2_3) // ★ 加上 Migration
                            .build();

                    // ===== 下面是你原本的預載資料邏輯，維持不動 =====
                    new Thread(() -> {
                        try {
                            TrainingItemDao itemDao = INSTANCE.trainingItemDao();
                            TrainingPlanDao planDao = INSTANCE.trainingPlanDao();

                            int count = itemDao.count();
                            Log.d(DB_DEBUG_TAG, "=== Current item count: " + count + " ===");

                            if (count == 0) {
                                Log.d(DB_DEBUG_TAG, "=== Inserting default data ===");

                                itemDao.insertAll(Preload.getDefaultItems());

                                for (TrainingPlan plan : Preload.getDefaultPlans()) {
                                    planDao.insertPlan(plan);
                                }
                                for (var ref : Preload.getDefaultPlanItemLinks()) {
                                    planDao.insertCrossRef(ref);
                                }
                                Log.d(DB_DEBUG_TAG, "=== Default data inserted successfully ===");
                            }
                        } catch (Exception e) {
                            Log.e(DB_DEBUG_TAG, "=== Error inserting data: " + e.getMessage() + " ===");
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        }
        return INSTANCE;
    }
}
