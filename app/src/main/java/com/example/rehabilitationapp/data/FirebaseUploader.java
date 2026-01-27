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
import java.util.concurrent.TimeUnit;

public class FirebaseUploader {

    private static final String TAG = "FirebaseUploader";

    public interface UploadCallback {
        default void onProgress(int current, int total) {
            // 預設空實作，不強制要覆寫，不然facecircle用Lamda呼叫會錯，Lamda寫法只能配合只有一個覆寫方法的介面。
        }
        void onComplete(int successCount, int failCount);
    }

    public static void uploadTodayUnsynced(Context context, UploadCallback callback) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<TrainingHistory> list = db.trainingHistoryDao().getUnsyncedWithLimit();

            if (list.isEmpty()) {
                Log.d(TAG, "沒有未同步的紀錄");
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "找到 " + list.size() + " 筆未同步紀錄");

            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", null);

            if (userId == null) {
                Log.e(TAG, "找不到 userId");
                if (callback != null) callback.onComplete(0, list.size());
                return;
            }

            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            final int[] successCount = {0};
            final int[] failCount = {0};
            final int[] doneCount = {0};
            final int total = list.size();

            // ★ 先回報總數
            if (callback != null) callback.onProgress(0, total);

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
                            new Thread(() -> {
                                AppDatabase.getInstance(context.getApplicationContext())
                                        .trainingHistoryDao().markSynced(item.trainingID);
                            }).start();

                            successCount[0]++;
                            doneCount[0]++;
                            Log.d(TAG, "上傳成功: " + item.trainingID);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        })
                        .addOnFailureListener(e -> {
                            failCount[0]++;
                            doneCount[0]++;
                            Log.e(TAG, "上傳失敗: " + item.trainingID, e);
                            scheduleFirebaseUpload(context, item.trainingID);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        });
            }
        }).start();
    }

    //結果頁面上傳按鈕呼叫
//    public static void uploadTodayUnsynced(Context context, UploadCallback callback) {
//        new Thread(() -> {
//            // 1. 取得今天的時間範圍
//            Calendar cal = Calendar.getInstance();
//            cal.set(Calendar.HOUR_OF_DAY, 0);
//            cal.set(Calendar.MINUTE, 0);
//            cal.set(Calendar.SECOND, 0);
//            cal.set(Calendar.MILLISECOND, 0);
//            long startOfDay = cal.getTimeInMillis();
//            long endOfDay = startOfDay + 24 * 60 * 60 * 1000;
//
//            // 2. 查詢未同步的紀錄
//            AppDatabase db = AppDatabase.getInstance(context);
////            List<TrainingHistory> list = db.trainingHistoryDao().getUnsyncedToday(startOfDay, endOfDay);
//
//            // 先將每日改為最多20筆
//            List<TrainingHistory> list = db.trainingHistoryDao().getUnsyncedWithLimit();
//            if (list.isEmpty()) {
//                Log.d(TAG, "今天沒有未同步的紀錄");
//                if (callback != null) callback.onComplete(0, 0);
//                return;
//            }
//
//            Log.d(TAG, "找到 " + list.size() + " 筆未同步紀錄");
//
//            // 3. 取得 userId
//            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
//            String userId = prefs.getString("current_user_id", null);
//
//            if (userId == null) {
//                Log.e(TAG, "找不到 userId");
//                if (callback != null) callback.onComplete(0, list.size());
//                return;
//            }
//
//            // 4. 上傳到 Firebase
//            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
//            final int[] successCount = {0};
//            final int[] failCount = {0};
//            final int total = list.size();
//
//            for (TrainingHistory item : list) {
//                Map<String, Object> data = new HashMap<>();
//                data.put("trainingLabel", item.trainingLabel);
//                data.put("createAt", item.createAt);
//                data.put("finishAt", item.finishAt);
//                data.put("targetTimes", item.targetTimes);
//                data.put("achievedTimes", item.achievedTimes);
//                data.put("durationTime", item.durationTime);
//                data.put("curveJson", item.curveJson);
//
//                firestore.collection("Users")
//                        .document(userId)
//                        .collection("trainingHistory")
//                        .document(item.trainingID)
//                        .set(data)
//                        .addOnSuccessListener(aVoid -> {
//                            // 上傳成功，標記已同步
//                            new Thread(() -> {
//                                AppDatabase.getInstance(context.getApplicationContext()).trainingHistoryDao().markSynced(item.trainingID);
//                            }).start();
//
//                            successCount[0]++;
//                            Log.d(TAG, "上傳成功: " + item.trainingID);
//
//                            if (successCount[0] + failCount[0] == total) {
//                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
//                            }
//                        })
//                        .addOnFailureListener(e -> {
//                            failCount[0]++;
//                            Log.e(TAG, "上傳失敗: " + item.trainingID, e);
//                            // ⭐⭐⭐ 新增：失敗時排程 WorkManager 重試 ⭐⭐⭐
//                            scheduleFirebaseUpload(context, item.trainingID);
//
//                            if (successCount[0] + failCount[0] == total) {
//                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
//                            }
//                        });
//            }
//        }).start();
//    }

    // ★★★ 排程 WorkManager 背景上傳 Firebase ★★★
    public static void scheduleFirebaseUpload(Context context, String trainingID) {
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.Data inputData = new androidx.work.Data.Builder()
                .putString("trainingID", trainingID)
                .build();

        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(FirebaseUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("firebase_upload_" + trainingID)
                .build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("firebase_" + trainingID, androidx.work.ExistingWorkPolicy.KEEP, request);

        Log.d(TAG, "📅 已排程 Firebase WorkManager: " + trainingID);
    }
}
