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
    static String TAG_TEST_3 = "NoDatTest";


    //Lambda只能覆寫一個，所以會走onProgress(default)，onComplete(需覆寫)
    //匿名類別（Anonymous Class）可覆寫多個方法，所以可以覆寫onProgress、onComplete等..
    public interface UploadCallback {
        default void onProgress(int current, int total) {
            // 預設空實作，不強制要覆寫，不然facecircle用Lamda呼叫會錯，Lamda寫法只能配合只有一個覆寫方法的介面。
        }
        void onComplete(int successCount, int failCount);
    }

    public static void uploadTodayUnsynced(Context context, UploadCallback callback) {
        //uploadTodayUnsynced，同步更新的範圍條件 : 時間由舊到新，至多100筆


        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<TrainingHistory> list = db.trainingHistoryDao().getUnsyncedWithLimit();

            if (list.isEmpty()) {
                Log.d(TAG, "沒有未同步的紀錄");
                Log.d(TAG_TEST_3, "沒有未同步的紀錄");
                //TODO .. 改LOG到FIREBASE雲端，需紀錄UserID來識別
                //沒有找到尚未傳至FIREBASE的紀錄時，會回傳(0,0)。 且LOG紀錄:自動上傳結果：成功 0 筆，失敗 0 筆
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "找到 " + list.size() + " 筆未同步紀錄");

            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", null);
            // TODO .. : LOG紀錄 :部分userID為unknown的問題
            Log.d(TAG_TEST_3, "userId==>"+userId);

            if (userId == null) {
                Log.e(TAG, "找不到 userId");
                Log.d(TAG_TEST_3, "找不到 userId");
                //TODO .. 改LOG到FIREBASE雲端，需紀錄UserID來識別
                //SharedPreferences抓不到userId，會回傳(0,N)。 且LOG紀錄:自動上傳結果：成功 0 筆，失敗 N 筆
                if (callback != null) callback.onComplete(0, list.size());
                return;
            }

            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            final int[] successCount = {0};
            final int[] failCount = {0};
            final int[] doneCount = {0}; //不論進入成功失敗都會++
            final int total = list.size();

            // ★ 先回報總數
            //TODO: onProgress沒寫實作， 乾脆不要onProgress?不行，雖然部分呼叫端用不到但對於有覆寫的呼叫端，這邊拿掉會讓人家永遠拿不到值

            //可以額外多加一段用LOG到FIREBASE跟本地端，內容  "userID : 📤 uploadTodayUnsynced開始上傳 " + total + " 筆")
            if (callback != null) callback.onProgress(0, total);
            Log.d(TAG_TEST_3, userId +  "uploadTodayUnsynced開始上傳 " + total + " 筆");

            for (TrainingHistory item : list) {
                //TODO ..紀錄上傳結果，每筆trainingID識別
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
                        //兩個Listener一定會觸發嗎?
                        .addOnSuccessListener(aVoid -> {
                            //TODO .. AppLogger先註記item.trainingID已經進入addOnSuccessListener區塊
                            Log.d(TAG_TEST_3, "AppLogger先註記item.trainingID已經進入addOnSuccessListener區塊");
                            //Q : 目的要去回壓DB為何要開異步?不用同步?
                            //以下可能會導致哪一些結果
                            //結果 : 1 完成 : ，2 中斷 :
                            new Thread(() -> {
                                AppDatabase.getInstance(context.getApplicationContext())
                                        .trainingHistoryDao().markSynced(item.trainingID);
                            }).start();



                            successCount[0]++;
                            doneCount[0]++;
                            //TODO .. FirebaseUpload方法內，遠端LOG追蹤完成度
                            Log.d(TAG_TEST_3, item.trainingID + "FIREBASE紀錄"+item.trainingID+"上傳成功，進入addOnSuccessListener");
                            Log.d(TAG, "上傳成功: " + item.trainingID);
                            AppLogger.logFirebaseUpload(item.trainingID, true, null);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        })
                        .addOnFailureListener(e -> {
                            //TODO .. AppLogger先註記item.trainingID已經進入addOnFailureListener區塊
                            Log.d(TAG_TEST_3, item.trainingID + "FIREBASE紀錄"+item.trainingID+"上傳失敗，進入addOnFailureListener");
                            Log.d(TAG_TEST_3, item.trainingID+ "上傳失敗，exception="+e);

                            //OnError也要上傳
                            Log.e(TAG, "上傳失敗: " + item.trainingID, e);

                            failCount[0]++;
                            doneCount[0]++;

                            AppLogger.logFirebaseUpload(item.trainingID, false, e.getMessage()+"，後續交給排程");


                            scheduleFirebaseUpload(context, item.trainingID);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                            }
                        });
            }

        }).start();
    }
    //Q: 這支會不會有甚麼Activity或process或thread被砍掉的問題

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
        Log.d(TAG_TEST_3, trainingID+ "已進入scheduleFirebaseUpload");
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

        //
        Log.d(TAG, "📅 已排程 Firebase WorkManager: " + trainingID);
        Log.d(TAG_TEST_3, trainingID+ "scheudle排成設定完成");

    }
}
