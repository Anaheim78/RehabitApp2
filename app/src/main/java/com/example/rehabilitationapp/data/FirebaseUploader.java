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
    //ToDo..檢查整枝CODE有沒有沒抓到FIREBASE LOG的ERROR
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
                AppLogger.log("FirebaseUploader.trainingHistoryDao", "沒有未同步的紀錄");
                //沒有找到尚未傳至FIREBASE的紀錄時，會回傳(0,0)。 且LOG紀錄:自動上傳結果：成功 0 筆，失敗 0 筆
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "找到 " + list.size() + " 筆未同步紀錄");
            AppLogger.log("FirebaseUploader.trainingHistoryDao", "找到 " + list.size() + " 筆未同步紀錄");

            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", null);
            // TODO .. : LOG紀錄 :部分userID為unknown的問題
            Log.d(TAG_TEST_3, "userId==>"+userId);
            AppLogger.log("SharedPreferences查詢current_user_id", "userId==>"+userId);

            if (userId == null) {
                Log.e(TAG, "找不到 userId");
                Log.d(TAG_TEST_3, "找不到 userId");

                //TODO .. 改LOG到FIREBASE雲端，需紀錄UserID來識別
                AppLogger.logError("SharedPreferences查詢current_user_id", "找不到 userId");
                AppLogger.log("SharedPreferences查詢current_user_id", "找不到 userId");
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
            AppLogger.log("FirebaseUploader準備上傳未同步資料 ", "uploadTodayUnsynced共 " + total + " 筆");

            for (TrainingHistory item : list) {
                //TODO ..紀錄上傳結果，每筆trainingID識別
                AppLogger.log("上傳TrainingHistory中", "紀錄"+item.trainingID);

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
                            Log.d(TAG_TEST_3, "AppLogger先註記item.trainingID已經進入addOnSuccessListener區塊");
                            AppLogger.log("FirebaseUploader單筆上傳成功", item.trainingID + "已經進入addOnSuccessListener區塊");

                            new Thread(() -> {
                                try {
                                    AppDatabase.getInstance(context.getApplicationContext())
                                            .trainingHistoryDao().markSynced(item.trainingID);
                                    AppLogger.log("FirebaseUploader", "已標記 synced=1: " + item.trainingID);
                                } catch (Exception e) {
                                    AppLogger.logError("FirebaseUploader", "markSynced 失敗: " + item.trainingID + " " + e.getMessage());
                                }
                            }).start();


                            // ... 後面不變



                            successCount[0]++;
                            doneCount[0]++;
                            //TODO .. FirebaseUpload方法內，遠端LOG追蹤完成度
                            Log.d(TAG_TEST_3, item.trainingID + "FIREBASE紀錄"+item.trainingID+"上傳成功，進入addOnSuccessListener");
                            Log.d(TAG, "上傳成功: " + item.trainingID);
                            AppLogger.logFirebaseUpload(item.trainingID, true, null);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                                AppLogger.log("FirebaseUploader上傳成果", "成功 :"+successCount[0]+"，失敗:" +failCount[0]);
                            }


                        })
                        .addOnFailureListener(e -> {
                            //TODO .. AppLogger先註記item.trainingID已經進入addOnFailureListener區塊
                            Log.d(TAG_TEST_3, item.trainingID + "FIREBASE紀錄"+item.trainingID+"上傳失敗，進入addOnFailureListener");
                            Log.d(TAG_TEST_3, item.trainingID+ "上傳失敗，exception="+e);

                            AppLogger.log("FirebaseUploader單筆上傳失敗", item.trainingID+"已經進入addOnFailureListener區塊");
                            AppLogger.logError("FirebaseUploader單筆上傳失敗", item.trainingID+"已經進入addOnFailureListener區塊");

                            //OnError也要上傳
                            Log.e(TAG, "上傳失敗: " + item.trainingID, e);



                            failCount[0]++;
                            doneCount[0]++;

                            AppLogger.logFirebaseUpload(item.trainingID, false, e.getMessage()+"，後續交給排程");


                            scheduleFirebaseUpload(context, item.trainingID);

                            if (callback != null) callback.onProgress(doneCount[0], total);

                            if (doneCount[0] == total) {
                                if (callback != null) callback.onComplete(successCount[0], failCount[0]);
                                AppLogger.log("FirebaseUploader上傳成果", "成功 :"+successCount[0]+"，失敗:" +failCount[0]);

                            }
                        });
            }

        }).start();
    }
    //Q: 這支會不會有甚麼Activity或process或thread被砍掉的問題



    // ★★★ 排程 WorkManager 背景上傳 Firebase ★★★
    public static void scheduleFirebaseUpload(Context context, String trainingID) {
        try {
            Log.d(TAG_TEST_3, trainingID + "已進入scheduleFirebaseUpload");
            AppLogger.log("排程 WorkManager 背景上傳 Firebase", "已進入scheduleFirebaseUpload: " + trainingID);

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
            AppLogger.log("已排程 Firebase WorkManager", "排程已設定完成: " + trainingID);

        } catch (Exception e) {
            Log.e(TAG, "❌ WorkManager 排程失敗: " + trainingID, e);
            AppLogger.logError("scheduleFirebaseUpload", "排程失敗: " + trainingID + " " + e.getMessage());
        }
    }
}
