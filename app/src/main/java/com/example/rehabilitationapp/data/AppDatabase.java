package com.example.rehabilitationapp.data;

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

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
        version = 2
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_DEBUG_TAG = "DB_DEBUG_TAG";
    private static volatile AppDatabase INSTANCE;

    public abstract TrainingItemDao trainingItemDao();
    public abstract TrainingPlanDao trainingPlanDao();
    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        Log.d(DB_DEBUG_TAG, "=== Into getInstance ===");
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Log.d(DB_DEBUG_TAG, "=== Creating new database instance ===");
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "rehab_db_2")
                            .fallbackToDestructiveMigration()
                            .build();

                    // 插入預設資料
                    new Thread(() -> {
                        try {
                            TrainingItemDao itemDao = INSTANCE.trainingItemDao();
                            TrainingPlanDao planDao = INSTANCE.trainingPlanDao();

                            int count = itemDao.count();
                            Log.d(DB_DEBUG_TAG, "=== Current item count: " + count + " ===");

                            if (count == 0) {
                                Log.d(DB_DEBUG_TAG, "=== Inserting default data ===");

                                // 插入訓練動作
                                itemDao.insertAll(Preload.getDefaultItems());

                                // 插入訓練計畫
                                for (TrainingPlan plan : Preload.getDefaultPlans()) {
                                    planDao.insertPlan(plan);
                                }

                                // 插入中介表關聯
                                for (PlanItemCrossRef ref : Preload.getDefaultPlanItemLinks()) {
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
