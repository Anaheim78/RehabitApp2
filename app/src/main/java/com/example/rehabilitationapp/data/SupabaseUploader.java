package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseUploader {

    private static final String TAG = "SupabaseUploader";

    // 🔥 你的 Supabase 設定
    private static final String SUPABASE_URL = "https://xexprgwyxrxegpdxbvno.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhleHByZ3d5eHJ4ZWdwZHhidm5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc3MzUyNTksImV4cCI6MjA4MzMxMTI1OX0.b2MUA2LIWZJaS7Mg_DKWrWCDrKuRwmtmNqbVNL8tL0U";
    private static final String BUCKET_NAME = "CSV_RehabAPP";

    public interface UploadCallback {
        void onSuccess(String publicUrl);
        void onFailure(String error);
    }

    /**
     * 上傳 CSV 到 Supabase Storage
     * @param context Context
     * @param fileName CSV 檔名（例如 testuser01_FaceTraining_POUT_LIPS_20260106_204032.csv）
     * @param callback 回調
     */
    public static void uploadCsv(Context context, String fileName, UploadCallback callback) {
        new Thread(() -> {
            try {
                // 1. 取得 userId
                SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                String userId = prefs.getString("current_user_id", "guest");

                // 2. 找到 CSV 檔案
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File csvFile = new File(downloadsDir, fileName);

                if (!csvFile.exists()) {
                    Log.e(TAG, "❌ CSV 檔案不存在: " + fileName);
                    if (callback != null) {
                        callback.onFailure("檔案不存在: " + fileName);
                    }
                    return;
                }

                // 3. 讀取檔案內容
                byte[] fileBytes = java.nio.file.Files.readAllBytes(csvFile.toPath());

                // 4. 建立上傳路徑：userId/trainingType/fileName
                String trainingType = extractTrainingType(fileName);
                String storagePath = userId + "/" + trainingType + "/" + fileName;

                // 5. 上傳到 Supabase
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
                    String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + storagePath;
                    Log.d(TAG, "✅ 上傳成功: " + publicUrl);
                    if (callback != null) {
                        callback.onSuccess(publicUrl);
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "❌ 上傳失敗: " + response.code() + " - " + errorBody);
                    if (callback != null) {
                        callback.onFailure("上傳失敗: " + response.code());
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "❌ 上傳異常", e);
                if (callback != null) {
                    callback.onFailure("上傳異常: " + e.getMessage());
                }
            }
        }).start();
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