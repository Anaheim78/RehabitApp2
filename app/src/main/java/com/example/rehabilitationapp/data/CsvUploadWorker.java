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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CsvUploadWorker extends Worker {

    private static final String TAG = "CsvUploadWorker";
    private static final String SUPABASE_URL = "https://xexprgwyxrxegpdxbvno.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhleHByZ3d5eHJ4ZWdwZHhidm5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc3MzUyNTksImV4cCI6MjA4MzMxMTI1OX0.b2MUA2LIWZJaS7Mg_DKWrWCDrKuRwmtmNqbVNL8tL0U";
    private static final String BUCKET_NAME = "CSV_RehabAPP";

    public CsvUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String trainingID = getInputData().getString("trainingID");
        String csvFileName = getInputData().getString("csvFileName");

        Log.d(TAG, "🔄 WorkManager 開始上傳: " + trainingID);

        if (trainingID == null || csvFileName == null || csvFileName.isEmpty()) {
            Log.e(TAG, "❌ 參數錯誤，跳過");
            return Result.failure();
        }

        // ★★★ 防衝突：先檢查是否已上傳 ★★★
        TrainingHistory record = AppDatabase.getInstance(getApplicationContext())
                .trainingHistoryDao().getById(trainingID);

        if (record == null) {
            Log.d(TAG, "⚠️ 紀錄不存在，跳過: " + trainingID);
            return Result.failure();
        }

        if (record.csvUploaded == 1) {
            Log.d(TAG, "✅ 已由 A 版上傳完成，跳過: " + trainingID);
            return Result.success();
        }

        // 執行上傳
        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", "guest");

            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File csvFile = new File(dir, csvFileName);

            if (!csvFile.exists()) {
                Log.e(TAG, "❌ 檔案不存在: " + csvFileName);
                return Result.failure();
            }

            byte[] fileBytes = java.nio.file.Files.readAllBytes(csvFile.toPath());
            String trainingType = extractTrainingType(csvFileName);
            String storagePath = userId + "/" + trainingType + "/" + csvFileName;
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

            if (response.isSuccessful()) {
                // 上傳成功，標記 DB
                AppDatabase.getInstance(context).trainingHistoryDao().markCsvUploaded(trainingID);
                Log.d(TAG, "✅ WorkManager 上傳成功: " + trainingID);
                return Result.success();
            } else {
                Log.e(TAG, "❌ 上傳失敗: " + response.code());
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 上傳異常: " + e.getMessage());
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