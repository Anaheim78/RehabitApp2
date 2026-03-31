package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.rehabilitationapp.data.model.TrainingHistory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.google.firebase.storage.FirebaseStorage;
import android.net.Uri;
import java.util.concurrent.CountDownLatch;

public class CsvUploadWorker extends Worker {
    //ToDo..檢查整枝CODE有沒有沒抓到FIREBASE LOG的ERROR
    private static final String TAG = "CsvUploadWorker";

    public CsvUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String trainingID = getInputData().getString("trainingID");
        String csvFileName = getInputData().getString("csvFileName");


        Log.d(TAG, "🔄 WorkManager 開始上傳: " + trainingID);
        AppLogger.log("CsvUploadWorker.doWork ", "CsvWorkManager 開始上傳 : " + trainingID);


        if (trainingID == null || csvFileName == null || csvFileName.isEmpty()) {
            // TODO .. : 改為雲端LOG紀錄，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.e(TAG, "❌ 參數錯誤，跳過");
            AppLogger.log("CsvUploadWorker.doWork_檢查，參數錯誤_不會再嘗試重傳處理。 ", "trainingID == null || csvFileName == null || csvFileName.isEmpty()");
            AppLogger.logError("CsvUploadWorker.doWork_檢查，參數錯誤_不會再嘗試重傳處理。", "trainingID == null || csvFileName == null || csvFileName.isEmpty()");
            return Result.failure();
        }

        // ★★★ 防衝突：先檢查是否已上傳 ★★★
        TrainingHistory record = AppDatabase.getInstance(getApplicationContext())
                .trainingHistoryDao().getById(trainingID);

        if (record == null) {
            // TODO .. : 改為雲端LOG紀錄，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.d(TAG, "⚠️ 紀錄不存在，跳過: " + trainingID);
            AppLogger.log("CsvUploadWorker.doWork_檢查，trainingID 的DB 紀錄不存在_不會再嘗試重傳處理。", trainingID+"TrainingHistory record = null");
            AppLogger.logError("CsvUploadWorker.doWork_檢查，trainingID 的DB 紀錄不存在_不會再嘗試重傳處理。", trainingID+"TrainingHistory record = null");

            return Result.failure();
        }

        if (record.csvUploaded == 1) {
            // TODO .. : 改為雲端LOG紀錄，並告知"檢查不通過"永久不會再嘗試重傳處理。
            Log.d(TAG, "✅ 已上傳完成，跳過: " + trainingID);
            AppLogger.log("CsvUploadWorker.doWork_檢查，record.csvUploaded == 1。", "已上傳完成，跳過: " + trainingID);
            return Result.success();
        }

        // 執行上傳
        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            // TODO .. : 想想取道error怎麼解決。
            String userId = prefs.getString("current_user_id", "no_login");

            if ("no_login".equals(userId)) {
                Log.e(TAG, "❌ 找不到 userId");
                AppLogger.log("CsvUploadWorker找不到 userId，會再嘗試重傳處理", trainingID);
                AppLogger.logError("CsvUploadWorker找不到 userId，會再嘗試重傳處理", trainingID);
                return Result.retry();
            }

            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File csvFile = new File(dir, csvFileName);

            if (!csvFile.exists()) {
                Log.e(TAG, "❌ 檔案不存在: " + csvFileName);
                AppLogger.log("CsvUploadWorker", "檔案不存在: " + csvFileName + " trainingID: " + trainingID);
                AppLogger.logError("CsvUploadWorker", "檔案不存在: " + csvFileName + " trainingID: " + trainingID);

                return Result.failure();

            }

            //ToDo..開始裝入Byte，但都是舊方法Subsapce
            String trainingType = extractTrainingType(csvFileName);
            String storagePath = "CSV_RehabAPP/" + userId + "/" + trainingType + "/" + csvFileName;

            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            FirebaseStorage.getInstance().getReference()
                    .child(storagePath)
                    .putFile(Uri.fromFile(csvFile))
                    .addOnSuccessListener(taskSnapshot -> {
                        new Thread(() -> {
                            try {
                                AppDatabase.getInstance(context).trainingHistoryDao().markCsvUploaded(trainingID);
                                AppLogger.log("CsvUploadWorker", "已標記 csvUploaded=1: " + trainingID);
                            } catch (Exception e) {
                                AppLogger.logError("CsvUploadWorker", "markCsvUploaded 失敗: " + trainingID + " " + e.getMessage());
                            }
                        }).start();

                        Log.d(TAG, "✅ WorkManager 上傳成功: " + trainingID);
                        AppLogger.log("CsvUploadWorker.addOnSuccessListener", "✅ 上傳成功 trainingID: " + trainingID);
                        AppLogger.logCsvUpload(trainingID, true, null);
                        success[0] = true;
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ 上傳失敗: " + e.getMessage());
                        AppLogger.logCsvUpload(trainingID, false, e.getMessage());
                        AppLogger.log("CsvUploadWorker.addOnFailureListener", "❌ 上傳失敗 檔名: " + csvFileName + " trainingID: " + trainingID);
                        AppLogger.logError("CsvUploadWorker.addOnFailureListener", "❌ 上傳失敗 檔名: " + csvFileName + " error: " + e.getMessage());

                        success[0] = false;
                        latch.countDown();
                    });

            try {
                latch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ 等待超時: " + trainingID);
                AppLogger.logError("CsvUploadWorker.latch.await", "60秒等待超時: " + trainingID);
                return Result.retry();
            }

            return success[0] ? Result.success() : Result.retry();


        } catch (Exception e) {
            Log.e(TAG, "❌ 上傳異常: " + e.getMessage());
            AppLogger.logCsvUpload(trainingID, false, e.getMessage());
            AppLogger.logError("CsvUploadWorker", "上傳異常: " + trainingID + " error: " + e.getMessage());
            AppLogger.log("CsvUploadWorker", "上傳異常: " + trainingID + " error: " + e.getMessage());

            return Result.retry();
        }
    }

    private String extractTrainingType(String fileName) {
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