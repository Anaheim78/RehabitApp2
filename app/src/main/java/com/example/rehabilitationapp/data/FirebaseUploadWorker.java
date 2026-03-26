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
    //ToDo..檢查整枝CODE有沒有沒抓到FIREBASE LOG的ERROR
    private static final String TAG = "FirebaseUploadWorker";

    public FirebaseUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String trainingID = getInputData().getString("trainingID");

        //TODO.. 改記到雲端
        Log.d(TAG, "🔄 WorkManager 開始上傳 Firebase: " + trainingID);

        if (trainingID == null || trainingID.isEmpty()) {
            //TODO.. 改記到雲端，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.e(TAG, "❌ 參數錯誤，跳過");
            return Result.failure();
        }

        Context context = getApplicationContext();

        // 1. 檢查是否已上傳
        TrainingHistory record = AppDatabase.getInstance(context)
                .trainingHistoryDao().getById(trainingID);

        if (record == null) {
            //TODO.. 改記到雲端，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.d(TAG, "⚠️ 紀錄不存在，跳過: " + trainingID);
            return Result.failure();
        }

        if (record.synced == 1) {
            //TODO.. 改記到雲端，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.d(TAG, "✅ 已上傳過，跳過: " + trainingID);
            return Result.success();
        }

        // 2. 取得 userId
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("current_user_id", null);

        if (userId == null) {
            //TODO.. 改記到雲端，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.e(TAG, "❌ 找不到 userId");
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
                        AppDatabase.getInstance(context).trainingHistoryDao().markSynced(trainingID);
                    }).start();

                    //Todo 表明trainingID ，並且是在Worker的addOnSuccessListener內
                    Log.d(TAG, "✅ WorkManager 上傳 Firebase 成功: " + trainingID);
                    AppLogger.logFirebaseUpload(trainingID, true, null);

                    success[0] = true;
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    //Todo 表明trainingID，並且是在Worker的addOnFailureListener內
                    Log.e(TAG, "❌ 上傳失敗: " + e.getMessage());
                    AppLogger.logFirebaseUpload(trainingID, false, e.getMessage());
                    success[0] = false;
                    latch.countDown();
                });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            //Todo 表明trainingID，並且是在Worker的latch.await超時
            Log.e(TAG, "❌ 等待超時");
            return Result.retry();
        }

        //Todo 這裡要說明Worker末段，success[0]=?，成功:失敗
        return success[0] ? Result.success() : Result.retry();
    }
}