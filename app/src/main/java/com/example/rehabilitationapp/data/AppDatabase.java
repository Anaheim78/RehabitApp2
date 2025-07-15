package com.example.rehabilitationapp.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import android.util.Log;
import com.example.rehabilitationapp.data.dao.TrainingPlanDao;
import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.Preload;

@Database(entities = {TrainingPlan.class, TrainingItem.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_DEBUG_TAG = "DB_DEBUG_TAG";
    private static volatile AppDatabase INSTANCE;

    public abstract TrainingPlanDao trainingPlanDao();
    public abstract TrainingItemDao trainingItemDao();

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

                    // 立即檢查並插入預設資料
                    new Thread(() -> {
                        try {
                            TrainingItemDao dao = INSTANCE.trainingItemDao();
                            int count = dao.count();
                            Log.d(DB_DEBUG_TAG, "=== Current count: " + count + " ===");

                            if (count == 0) {
                                Log.d(DB_DEBUG_TAG, "=== Inserting default data ===");
                                dao.insertAll(Preload.getDefaults());
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
