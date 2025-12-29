// AppDatabase.java
package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.example.rehabilitationapp.data.dao.TrainingHistoryDao;
import com.example.rehabilitationapp.data.model.PlanItemCrossRef;
import com.example.rehabilitationapp.data.model.Preload;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.data.model.TrainingHistory;

@Database(
        entities = {
                TrainingItem.class,
                TrainingPlan.class,
                PlanItemCrossRef.class,
                User.class,
                TrainingHistory.class
        },
        //!!!!!!!!!!!!!!!!!!!!!!!每次更新資料庫這邊要改，比如5->6，這裡要寫6!!!!!!但不然會直接依打開就閃退掉!!!!可以不用清掉資料!!!!!!!!!!!!!!!!3=
        version = 7,              // ★ 版本 +1（原本是 2）
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_DEBUG_TAG = "DB_DEBUG_TAG";
    private static volatile AppDatabase INSTANCE;

    public abstract TrainingItemDao trainingItemDao();
    public abstract TrainingPlanDao trainingPlanDao();
    public abstract UserDao userDao();
    public abstract TrainingHistoryDao trainingHistoryDao();

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
    // 新增 Migration: 3 -> 4（加入 trainingHistroy 表）
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `trainingHistory` (" +
                    "`trainingID` TEXT NOT NULL, " +
                    "`trainingLabel` TEXT, " +
                    "`createAt` INTEGER NOT NULL, " +
                    "`finishAt` INTEGER NOT NULL, " +
                    "`targetTimes` INTEGER NOT NULL, " +
                    "`achievedTimes` INTEGER NOT NULL, " +
                    "`durationTime` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`trainingID`))");
        }
    };

    // ★ 新增 Migration: 4 -> 5（在 trainingHistory 加 curveJson）
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE trainingHistory ADD COLUMN curveJson TEXT");
        }
    };

    // 同步排程
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 在 users 表新增 need_sync 欄位
            db.execSQL("ALTER TABLE users ADD COLUMN need_sync INTEGER NOT NULL DEFAULT 0");
        }
    };

    // ★ 新增 Migration: 6 -> 7（在 trainingHistory 加 synced）
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE trainingHistory ADD COLUMN synced INTEGER NOT NULL DEFAULT 0");
        }
    };


    //20251123 多DB
    public static AppDatabase buildDatabase(Context context, String dbName) {

        AppDatabase db = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        dbName
                )
                .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                )
                .build();

        // ★ 你的預載資料這段要完整搬進來（照抄即可）
        new Thread(() -> {
            try {
                TrainingItemDao itemDao = db.trainingItemDao();
                TrainingPlanDao planDao = db.trainingPlanDao();

                int count = itemDao.count();

                if (count == 0) {
                    itemDao.insertAll(Preload.getDefaultItems());

                    for (TrainingPlan plan : Preload.getDefaultPlans()) {
                        planDao.insertPlan(plan);
                    }
                    for (var ref : Preload.getDefaultPlanItemLinks()) {
                        planDao.insertCrossRef(ref);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        return db;
    }

    public static AppDatabase getInstance(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        String userId = prefs.getString("current_user_id", "local");

        if (userId == null || userId.isEmpty()) userId = "local";

        return DatabaseProvider.getDatabase(context, userId);
    }



//    public static AppDatabase getInstance(Context context) {
//        Log.d(DB_DEBUG_TAG, "=== Into getInstance ===");
//        if (INSTANCE == null) {
//            synchronized (AppDatabase.class) {
//                if (INSTANCE == null) {
//                    Log.d(DB_DEBUG_TAG, "=== Creating new database instance ===");
//                    INSTANCE = Room.databaseBuilder(
//                                    context.getApplicationContext(),
//                                    AppDatabase.class,
//                                    "rehab_db_2"
//                            )
//                            // ★ 不要用 fallbackToDestructiveMigration()，會清庫
//                            .addMigrations(MIGRATION_2_3,MIGRATION_3_4, MIGRATION_4_5,MIGRATION_5_6) // ★ 加上 Migration
//                            .build();
//
//                    // ===== 下面是你原本的預載資料邏輯，維持不動 =====
//                    new Thread(() -> {
//                        try {
//                            TrainingItemDao itemDao = INSTANCE.trainingItemDao();
//                            TrainingPlanDao planDao = INSTANCE.trainingPlanDao();
//
//                            int count = itemDao.count();
//                            Log.d(DB_DEBUG_TAG, "=== Current item count: " + count + " ===");
//
//                            if (count == 0) {
//                                Log.d(DB_DEBUG_TAG, "=== Inserting default data ===");
//
//                                itemDao.insertAll(Preload.getDefaultItems());
//
//                                for (TrainingPlan plan : Preload.getDefaultPlans()) {
//                                    planDao.insertPlan(plan);
//                                }
//                                for (var ref : Preload.getDefaultPlanItemLinks()) {
//                                    planDao.insertCrossRef(ref);
//                                }
//                                Log.d(DB_DEBUG_TAG, "=== Default data inserted successfully ===");
//                            }
//                        } catch (Exception e) {
//                            Log.e(DB_DEBUG_TAG, "=== Error inserting data: " + e.getMessage() + " ===");
//                            e.printStackTrace();
//                        }
//                    }).start();
//                }
//            }
//        }
//        return INSTANCE;
//    }
}
