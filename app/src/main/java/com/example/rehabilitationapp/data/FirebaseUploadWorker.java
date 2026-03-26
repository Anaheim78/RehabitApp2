package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.rehabilitationapp.data.model.TrainingHistory;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FirebaseUploadWorker extends Worker {

    private static final String TAG = "FirebaseUploadWorker";

    public FirebaseUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String trainingID = getInputData().getString("trainingID");

            Log.d(TAG, "🔄 WorkManager 開始上傳 Firebase: " + trainingID);
            AppLogger.log("Firebase WorkManage.doWork", "WorkManager 開始上傳 Firebase: " + trainingID);

            if (trainingID == null || trainingID.isEmpty()) {
                Log.e(TAG, "❌ 參數錯誤，跳過");
                AppLogger.log("參數trainingID檢查有誤，不再嘗試重傳處理", "trainingID == null || trainingID.isEmpty()");
                AppLogger.logError("參數trainingID檢查有誤，不再嘗試重傳處理", "trainingID == null || trainingID.isEmpty()");
                return Result.failure();
            }

            Context context = getApplicationContext();

            // 1. 檢查是否已上傳
            TrainingHistory record = AppDatabase.getInstance(context)
                    .trainingHistoryDao().getById(trainingID);

            if (record == null) {
                Log.d(TAG, "⚠️ 紀錄不存在，跳過: " + trainingID);
                AppLogger.log("本地AppDatabase查無本紀錄，不會再嘗試重傳處理", "trainingID == " + trainingID);
                return Result.failure();
            }

            if (record.synced == 1) {
                Log.d(TAG, "✅ 已上傳過，跳過: " + trainingID);
                AppLogger.log("已上傳過，跳過", trainingID);
                return Result.success();
            }

            // 2. 取得 userId
            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", null);

            if (userId == null) {
                Log.e(TAG, "❌ 找不到 userId");
                AppLogger.log("找不到 userId，會再嘗試重傳處理", trainingID);
                AppLogger.logError("找不到 userId，會再嘗試重傳處理", trainingID);
                return Result.retry();
            }

            // 3. 上傳到 Firebase
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

            Map<String, Object> data = new HashMap<>();
            data.put("trainingLabel", record.trainingLabel);
            data.put("createAt", record.createAt);
            data.put("finishAt", record.finishAt);
            data.put("targetTimes", record.targetTimes);
            data.put("achievedTimes", record.achievedTimes);
            data.put("durationTime", record.durationTime);
            data.put("curveJson", record.curveJson);

            // 用 CountDownLatch 等待 Firebase 完成
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            firestore.collection("Users")
                    .document(userId)
                    .collection("trainingHistory")
                    .document(trainingID)
                    .set(data)
                    .addOnSuccessListener(aVoid -> {
                        new Thread(() -> {
                            try {
                                AppDatabase.getInstance(context).trainingHistoryDao().markSynced(trainingID);
                                AppLogger.log("FirebaseUploadWorker", "已標記 synced=1: " + trainingID);
                            } catch (Exception e) {
                                AppLogger.logError("FirebaseUploadWorker", "markSynced 失敗: " + trainingID + " " + e.getMessage());
                            }
                        }).start();

                        Log.d(TAG, "✅ WorkManager 上傳 Firebase 成功: " + trainingID);
                        AppLogger.log("FirebaseUploadWorker.doWork->addOnSuccessListener", "✅ WorkManager 上傳 Firebase 成功: " + trainingID);
                        AppLogger.logFirebaseUpload(trainingID, true, null);

                        success[0] = true;
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ 上傳失敗: " + e.getMessage());
                        AppLogger.logFirebaseUpload(trainingID, false, e.getMessage());
                        AppLogger.log("FirebaseUploadWorker.doWork->addOnFailureListener", "❌ WorkManager 上傳 Firebase 失敗: " + trainingID);
                        AppLogger.logError("FirebaseUploadWorker.doWork->addOnFailureListener", "❌ WorkManager 上傳 Firebase 失敗: " + trainingID);

                        success[0] = false;
                        latch.countDown();
                    });

            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ 等待超時: " + trainingID);
                AppLogger.logError("latch.await", "30秒等待超時: " + trainingID);
                return Result.retry();
            }

            AppLogger.log("FirebaseUploadWorker結果", trainingID + " => " + (success[0] ? "成功" : "失敗，重新傳送"));
            return success[0] ? Result.success() : Result.retry();

        } catch (Exception e) {
            Log.e(TAG, "❌ Worker 執行異常: " + e.getMessage());
            AppLogger.logError("FirebaseUploadWorker", "doWork 異常: " + e.getMessage());
            return Result.retry();
        }
    }
}