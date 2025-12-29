package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.rehabilitationapp.data.model.TrainingHistory;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseUploader {

    private static final String TAG = "FirebaseUploader";

    public interface UploadCallback {
        void onComplete(int successCount, int failCount);
    }

    public static void uploadTodayUnsynced(Context context, UploadCallback callback) {
        new Thread(() -> {
            // 1. 取得今天的時間範圍
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long endOfDay = startOfDay + 24 * 60 * 60 * 1000;

            // 2. 查詢未同步的紀錄
            AppDatabase db = AppDatabase.getInstance(context);
            List<TrainingHistory> list = db.trainingHistoryDao().getUnsyncedToday(startOfDay, endOfDay);

            if (list.isEmpty()) {
                Log.d(TAG, "今天沒有未同步的紀錄");
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "找到 " + list.size() + " 筆未同步紀錄");

            // 3. 取得 userId
            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", null);

            if (userId == null) {
                Log.e(TAG, "找不到 userId");
                if (callback != null) callback.onComplete(0, list.size());
                return;
            }

            // 4. 上傳到 Firebase
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            final int[] successCount = {0};
            final int[] failCount = {0};
            final int total = list.size();

            for (TrainingHistory item : list) {
                Map<String, Object> data = new HashMap<>();
                data.put("trainingLabel", item.trainingLabel);
                data.put("createAt", item.createAt);
                data.put("finishAt", item.finishAt);
                data.put("targetTimes", item.targetTimes);
                data.put("achievedTimes", item.achievedTimes);
                data.put("durationTime", item.durationTime);
                data.put("curveJson", item.curveJson);

                firestore.collection("Users")
                        .document(userId)
                        .collection("trainingHistory")
                        .document(item.trainingID)
                        .set(data)
                        .addOnSuccessListener(aVoid -> {
                            // 上傳成功，標記已同步
                            new Thread(() -> {
                                db.trainingHistoryDao().markSynced(item.trainingID);
                            }).start();

                            successCount[0]++;
                            Log.d(TAG, "上傳成功: " + item.trainingID);

                            if (successCount[0] + failCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        })
                        .addOnFailureListener(e -> {
                            failCount[0]++;
                            Log.e(TAG, "上傳失敗: " + item.trainingID, e);

                            if (successCount[0] + failCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        });
            }
        }).start();
    }
}
