package com.example.rehabilitationapp.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.rehabilitationapp.data.model.TrainingHistory;

import java.io.File;

public class VideoUploadWorker extends Worker {

    private static final String TAG = "VideoUploadWorker";

    public VideoUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String trainingID = getInputData().getString("trainingID");
        String videoFileName = getInputData().getString("videoFileName");

        Log.d(TAG, "🔄 Worker 開始: " + trainingID);

        if (trainingID == null || videoFileName == null || videoFileName.isEmpty()) {
            Log.e(TAG, "❌ 參數錯誤，跳過");
            return Result.failure();
        }

        Context context = getApplicationContext();

        // 1. 檢查是否已上傳（防止重複傳）
        TrainingHistory record = AppDatabase.getInstance(context)
                .trainingHistoryDao().getById(trainingID);

        if (record == null) {
            Log.d(TAG, "⚠️ 紀錄不存在，跳過: " + trainingID);
            return Result.failure();
        }

        if (record.videoUploaded == 1) {
            Log.d(TAG, "✅ 已上傳過，跳過: " + trainingID);
            return Result.success();
        }

        // 2. 找到檔案
        File videoFile = new File(context.getExternalFilesDir(null), videoFileName);
        if (!videoFile.exists()) {
            Log.e(TAG, "❌ 檔案不存在: " + videoFileName);
            return Result.failure();
        }

        // 3. 同步上傳
        boolean success = SftpUploader.uploadVideo(context, videoFile, null);

        if (success) {
            AppDatabase.getInstance(context).trainingHistoryDao().markVideoUploaded(trainingID);
            Log.d(TAG, "✅ Worker 上傳成功: " + trainingID);
            AppLogger.logVideoUpload(trainingID, videoFileName, true, null);
            return Result.success();
        } else {
            Log.d(TAG, "⚠️ 上傳失敗，稍後重試: " + trainingID);
            AppLogger.logVideoUpload(trainingID, videoFileName, false, "上傳失敗");
            return Result.retry();
        }
    }
}