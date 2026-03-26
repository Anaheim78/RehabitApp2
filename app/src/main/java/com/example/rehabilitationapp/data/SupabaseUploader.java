package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.work.Constraints;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.example.rehabilitationapp.data.model.TrainingHistory;
import java.util.List;

public class SupabaseUploader {
    //ToDo..檢查整枝CODE有沒有沒抓到FIREBASE LOG的ERROR
    private static final String TAG = "SupabaseUploader";

    // 🔥 你的 Supabase 設定
    private static final String SUPABASE_URL = "https://xexprgwyxrxegpdxbvno.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhleHByZ3d5eHJ4ZWdwZHhidm5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc3MzUyNTksImV4cCI6MjA4MzMxMTI1OX0.b2MUA2LIWZJaS7Mg_DKWrWCDrKuRwmtmNqbVNL8tL0U";
    private static final String BUCKET_NAME = "CSV_RehabAPP";

    static String TAG_TEST_3 = "NoDatTest";

    // 上傳後，新增帶 trainingID 的 callback
    public interface UploadCallbackWithId {
        void onSuccess(String publicUrl, String trainingID);
        void onFailure(String error, String trainingID);
    }


    /**
     * ★★★ 新增：上傳 CSV 並在成功後標記資料庫 ★★★
     * FaceCheck復健時會呼叫，以及Retry也是用這邊
     */
    public static void uploadCsvWithMark(Context context, String fileName, String trainingID, UploadCallbackWithId callback) {
        new Thread(() -> {
            try {
                // TODO .. : LOG紀錄 :部分userID為unknown的問題
                SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                String userId = prefs.getString("current_user_id", "null");


                //context.getExternalFilesDir(...) ==> /Android/data/你的app/files/XXX/，App私有空間
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                File csvFile = new File(dir, fileName);

                if (!csvFile.exists()) {
                    //ToDo..寫到FIREBASE_LOG
                    Log.e(TAG, "❌ CSV 檔案不存在: " + fileName);
                    Log.e(TAG_TEST_3,"❌ CSV 檔案不存在: " + fileName);
                    if (callback != null) callback.onFailure("檔案不存在: " + fileName, trainingID);
                    return;
                }

                //ToDo..開始裝入Byte，但都是舊方法Subsapce，要改為FIREBASE
                byte[] fileBytes = java.nio.file.Files.readAllBytes(csvFile.toPath());
                String trainingType = extractTrainingType(fileName);
                String storagePath = userId + "/" + trainingType + "/" + fileName;
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + storagePath;

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                RequestBody body = RequestBody.create(fileBytes, MediaType.parse("text/csv"));
                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Content-Type", "text/csv")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";  // ⭐ 加這行


                // ⭐ 200 成功 或 檔案已存在 都算成功
                if (response.isSuccessful() ||
                        responseBody.contains("Duplicate") ||
                        responseBody.contains("already exists")) {
                    String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + storagePath;
                    Log.d(TAG, "✅ 上傳成功: " + publicUrl);
                    AppLogger.logCsvUpload(trainingID, true, null);

                    // ★★★ 上傳成功，標記資料庫 ★★★
                    if (trainingID != null && !trainingID.isEmpty()) {
                        try {
                            AppDatabase.getInstance(context.getApplicationContext()).trainingHistoryDao().markCsvUploaded(trainingID);
                            Log.d(TAG, "✅ 已標記 csvUploaded=1: " + trainingID);
                        } catch (Exception e) {
                            Log.e(TAG, "⚠️ 標記 csvUploaded 失敗: " + e.getMessage());
                        }
                    }
                    if (callback != null) callback.onSuccess(publicUrl, trainingID);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "❌ 上傳失敗: " + response.code() + " - " + errorBody);
                    AppLogger.logCsvUpload(trainingID, false, "HTTP " + response.code());


                    if (callback != null) callback.onFailure("上傳失敗: " + response.code(), trainingID);
                    scheduleCsvUpload(context, trainingID, fileName);

                }

            } catch (IOException e) {
                Log.e(TAG, "❌ 上傳異常", e);
                if (callback != null) callback.onFailure("上傳異常: " + e.getMessage(), trainingID);
                scheduleCsvUpload(context, trainingID, fileName);
            }
        }).start();
    }


    // ★★★ 排程 WorkManager 背景上傳 ★★★
    //本物件SupbaseUploader內自用
    public static void scheduleCsvUpload(Context context, String trainingID, String csvFileName) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.Data inputData = new androidx.work.Data.Builder()
                .putString("trainingID", trainingID)
                .putString("csvFileName", csvFileName)
                .build();

        //CsvUploadWorker.class有繼承Worker類別，所以永遠直接呼叫doWork()
        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(CsvUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("csv_upload_" + trainingID)
                .build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("csv_" + trainingID, androidx.work.ExistingWorkPolicy.KEEP, request);

        Log.d(TAG, "📅 已排程 WorkManager: " + trainingID);
    }

    //重啟APP跟紀錄頁面，呼叫
    public static void retryUnsyncedCsv(Context context, RetryCallback callback) {
        new Thread(() -> {
            List<TrainingHistory> unsyncedList = AppDatabase.getInstance(context)
                    .trainingHistoryDao()
                    .getUnsyncedCsvRecords();

            if (unsyncedList == null || unsyncedList.isEmpty()) {
                Log.d(TAG, "沒有需要重傳的 CSV");
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "找到 " + unsyncedList.size() + " 筆未上傳的 CSV");

            final int[] successCount = {0};
            final int[] failCount = {0};
            final int[] doneCount = {0};
            final int total = unsyncedList.size();

            // ★ 先回報總數
            if (callback != null) callback.onProgress(0, total);

            for (TrainingHistory item : unsyncedList) {
                if (item.csvFileName == null || item.csvFileName.isEmpty()) {
                    failCount[0]++;
                    doneCount[0]++;
                    if (callback != null) callback.onProgress(doneCount[0], total);
                    if (doneCount[0] >= total && callback != null) {
                        callback.onComplete(successCount[0], failCount[0]);
                    }
                    continue;
                }

                uploadCsvWithMark(context, item.csvFileName, item.trainingID, new UploadCallbackWithId() {
                    @Override
                    public void onSuccess(String publicUrl, String trainingID) {
                        successCount[0]++;
                        doneCount[0]++;
                        Log.d(TAG, "✅ 重傳成功: " + trainingID);
                        if (callback != null) callback.onProgress(doneCount[0], total);
                        if (doneCount[0] >= total && callback != null) {
                            callback.onComplete(successCount[0], failCount[0]);
                        }
                    }

                    @Override
                    public void onFailure(String error, String trainingID) {
                        failCount[0]++;
                        doneCount[0]++;
                        Log.e(TAG, "❌ 重傳失敗: " + trainingID);
                        if (callback != null) callback.onProgress(doneCount[0], total);
                        if (doneCount[0] >= total && callback != null) {
                            callback.onComplete(successCount[0], failCount[0]);
                        }
                    }
                });
            }
        }).start();
    }

    //改成回報結果，結果頁面跟重開機都會用這裡看

//    // ★★★ A 版：App 啟動時重傳所有未上傳的 CSV ★★★
//    public static void retryUnsyncedCsv(Context context, RetryCallback callback) {
//        new Thread(() -> {
//            List<TrainingHistory> unsyncedList = AppDatabase.getInstance(context)
//                    .trainingHistoryDao()
//                    .getUnsyncedCsvRecords();
//
//            if (unsyncedList == null || unsyncedList.isEmpty()) {
//                Log.d(TAG, "沒有需要重傳的 CSV");
//                if (callback != null) callback.onComplete(0, 0);
//                return;
//            }
//
//            Log.d(TAG, "找到 " + unsyncedList.size() + " 筆未上傳的 CSV");
//
//            final int[] successCount = {0};
//            final int[] failCount = {0};
//            final int total = unsyncedList.size();
//
//            for (TrainingHistory item : unsyncedList) {
//                if (item.csvFileName == null || item.csvFileName.isEmpty()) {
//                    failCount[0]++;
//                    if (successCount[0] + failCount[0] >= total && callback != null) {
//                        callback.onComplete(successCount[0], failCount[0]);
//                    }
//                    continue;
//                }
//
//                uploadCsvWithMark(context, item.csvFileName, item.trainingID, new UploadCallbackWithId() {
//                    @Override
//                    public void onSuccess(String publicUrl, String trainingID) {
//                        successCount[0]++;
//                        Log.d(TAG, "✅ 重傳成功: " + trainingID);
//                        if (successCount[0] + failCount[0] >= total && callback != null) {
//                            callback.onComplete(successCount[0], failCount[0]);
//                        }
//                    }
//
//                    @Override
//                    public void onFailure(String error, String trainingID) {
//                        failCount[0]++;
//                        Log.e(TAG, "❌ 重傳失敗: " + trainingID);
//                        if (successCount[0] + failCount[0] >= total && callback != null) {
//                            callback.onComplete(successCount[0], failCount[0]);
//                        }
//                    }
//                });
//            }
//        }).start();
//    }

    //TrainingResultActivity，按下上傳會用到
    public interface RetryCallback {
        default void onProgress(int current, int total) {
            // 預設空實作，不強制要覆寫，不然facecircle用Lamda呼叫會錯，Lamda寫法只能配合只有一個覆寫方法的介面。
        }
        void onComplete(int successCount, int failCount);
    }

    /**
     * 從檔名提取訓練類型
     */
    private static String extractTrainingType(String fileName) {
        if (fileName.contains("POUT_LIPS")) return "POUT_LIPS";
        if (fileName.contains("SIP_LIPS")) return "SIP_LIPS";
        if (fileName.contains("PUFF_CHEEK")) return "PUFF_CHEEK";
        if (fileName.contains("REDUCE_CHEEK")) return "REDUCE_CHEEK";
        if (fileName.contains("TONGUE_LEFT")) return "TONGUE_LEFT";
        if (fileName.contains("TONGUE_RIGHT")) return "TONGUE_RIGHT";
        if (fileName.contains("TONGUE_UP")) return "TONGUE_UP";
        if (fileName.contains("TONGUE_DOWN")) return "TONGUE_DOWN";
        if (fileName.contains("TONGUE_FOWARD")) return "TONGUE_FOWARD";
        if (fileName.contains("TONGUE_BACK")) return "TONGUE_BACK";
        return "OTHER";
    }
}