package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 🔐 SFTP 影片上傳工具
 * 自動依 userId 建立子資料夾
 */
public class SftpUploader {

    private static final String TAG = "SftpUploader";

    // ============================================
    // 【⚙️ 設定區】
    // ============================================

    private static final String SFTP_HOST = "163.25.101.37";
    private static final int SFTP_PORT = 22222;
    private static final String SFTP_USER = "hao_lab01";
    private static final String SFTP_PASSWORD = "123456";  // ← 改這裡
    private static final String REMOTE_BASE_DIR = "/Rh_Videos";  // 基礎資料夾

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int SESSION_TIMEOUT = 120000;
    private static final int MAX_BATCH_SIZE = 5;

    // ============================================
    // 【回呼介面】
    // ============================================

    public interface UploadCallback {
        void onProgress(int percent);
        void onSuccess(String remoteFilePath);
        void onFailure(String errorMessage);
    }

    public interface BatchUploadCallback {
        void onFileStart(int index, int total, String fileName);
        void onFileProgress(int index, int total, int percent);
        void onFileSuccess(int index, int total, String fileName);
        void onFileFailure(int index, int total, String fileName, String error);
        void onAllComplete(int successCount, int failCount, List<String> failedFiles);
    }

    // ============================================
    // 【單檔上傳】自動依 userId 分資料夾
    // ============================================

    public static void uploadVideoAsync(Context context, File videoFile, UploadCallback callback) {
        new Thread(() -> uploadVideo(context, videoFile, callback)).start();
    }

    public static boolean uploadVideo(Context context, File videoFile, UploadCallback callback) {
        if (videoFile == null || !videoFile.exists()) {
            String error = "檔案不存在";
            Log.e(TAG, error);
            if (callback != null) callback.onFailure(error);
            return false;
        }

        // 🔑 取得 userId
        String userId = getUserId(context);

        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            Log.d(TAG, "📤 開始上傳: " + videoFile.getName());
            Log.d(TAG, "👤 UserId: " + userId);

            JSch jsch = new JSch();
            session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
            session.setPassword(SFTP_PASSWORD);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.setServerAliveInterval(5000);
            session.connect(CONNECT_TIMEOUT);
            Log.d(TAG, "✅ SSH 連線成功");

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(SESSION_TIMEOUT);
            Log.d(TAG, "✅ SFTP 通道開啟");

            // 🔑 建立資料夾結構：/Rh_Videos/userId/
            String userDir = REMOTE_BASE_DIR + "/" + userId;
            ensureDirectoryExists(channelSftp, REMOTE_BASE_DIR);
            ensureDirectoryExists(channelSftp, userDir);
            channelSftp.cd(userDir);
            Log.d(TAG, "📁 目標資料夾: " + userDir);

            // 上傳
            FileInputStream fis = new FileInputStream(videoFile);
            final long fileSize = videoFile.length();

            SftpProgressMonitor monitor = new SftpProgressMonitor() {
                private long transferred = 0;
                private int lastPercent = -1;

                @Override
                public void init(int op, String src, String dest, long max) {
                    Log.d(TAG, "開始傳輸...");
                }

                @Override
                public boolean count(long count) {
                    transferred += count;
                    int percent = (int) ((transferred * 100) / fileSize);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        if (callback != null) callback.onProgress(percent);
                    }
                    return true;
                }

                @Override
                public void end() {
                    Log.d(TAG, "傳輸完成");
                }
            };

            channelSftp.put(fis, videoFile.getName(), monitor, ChannelSftp.OVERWRITE);
            fis.close();

            String remotePath = userDir + "/" + videoFile.getName();
            Log.d(TAG, "✅ 上傳成功: " + remotePath);
            if (callback != null) callback.onSuccess(remotePath);
            return true;

        } catch (Exception e) {
            String error = "上傳失敗: " + e.getMessage();
            Log.e(TAG, error, e);
            if (callback != null) callback.onFailure(error);
            return false;

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    // ============================================
    // 【批次上傳】自動依 userId 分資料夾
    // ============================================

    public static void uploadMultipleAsync(Context context, List<File> videoFiles, BatchUploadCallback callback) {
        new Thread(() -> uploadMultiple(context, videoFiles, callback)).start();
    }

    public static void uploadMultiple(Context context, List<File> videoFiles, BatchUploadCallback callback) {
        if (videoFiles == null || videoFiles.isEmpty()) {
            Log.w(TAG, "沒有檔案要上傳");
            if (callback != null) callback.onAllComplete(0, 0, new ArrayList<>());
            return;
        }

        // 限制最多 5 部
        List<File> filesToUpload;
        if (videoFiles.size() > MAX_BATCH_SIZE) {
            Log.w(TAG, "⚠️ 超過上限，只上傳前 " + MAX_BATCH_SIZE + " 部");
            filesToUpload = videoFiles.subList(0, MAX_BATCH_SIZE);
        } else {
            filesToUpload = videoFiles;
        }

        // 🔑 取得 userId
        String userId = getUserId(context);

        int total = filesToUpload.size();
        int successCount = 0;
        int failCount = 0;
        List<String> failedFiles = new ArrayList<>();

        Log.d(TAG, "📦 批次上傳開始，共 " + total + " 個檔案");
        Log.d(TAG, "👤 UserId: " + userId);

        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
            session.setPassword(SFTP_PASSWORD);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.setServerAliveInterval(5000);
            session.connect(CONNECT_TIMEOUT);
            Log.d(TAG, "✅ SSH 連線成功");

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(SESSION_TIMEOUT);
            Log.d(TAG, "✅ SFTP 通道開啟");

            // 🔑 建立資料夾結構
            String userDir = REMOTE_BASE_DIR + "/" + userId;
            ensureDirectoryExists(channelSftp, REMOTE_BASE_DIR);
            ensureDirectoryExists(channelSftp, userDir);
            channelSftp.cd(userDir);
            Log.d(TAG, "📁 目標資料夾: " + userDir);

            // 逐一上傳
            for (int i = 0; i < total; i++) {
                File videoFile = filesToUpload.get(i);
                final int index = i;
                String fileName = videoFile.getName();

                Log.d(TAG, "📤 [" + (i + 1) + "/" + total + "] " + fileName);

                if (callback != null) callback.onFileStart(i, total, fileName);

                if (!videoFile.exists()) {
                    Log.e(TAG, "❌ 檔案不存在: " + fileName);
                    failCount++;
                    failedFiles.add(fileName);
                    if (callback != null) callback.onFileFailure(i, total, fileName, "檔案不存在");
                    continue;
                }

                try {
                    FileInputStream fis = new FileInputStream(videoFile);
                    final long fileSize = videoFile.length();

                    SftpProgressMonitor monitor = new SftpProgressMonitor() {
                        private long transferred = 0;
                        private int lastPercent = -1;

                        @Override
                        public void init(int op, String src, String dest, long max) {}

                        @Override
                        public boolean count(long count) {
                            transferred += count;
                            int percent = (int) ((transferred * 100) / fileSize);
                            if (percent != lastPercent && percent % 10 == 0) {
                                lastPercent = percent;
                                if (callback != null) callback.onFileProgress(index, total, percent);
                            }
                            return true;
                        }

                        @Override
                        public void end() {}
                    };

                    channelSftp.put(fis, fileName, monitor, ChannelSftp.OVERWRITE);
                    fis.close();

                    Log.d(TAG, "✅ [" + (i + 1) + "/" + total + "] 成功");
                    successCount++;
                    if (callback != null) callback.onFileSuccess(i, total, fileName);

                } catch (Exception e) {
                    Log.e(TAG, "❌ [" + (i + 1) + "/" + total + "] 失敗: " + e.getMessage());
                    failCount++;
                    failedFiles.add(fileName);
                    if (callback != null) callback.onFileFailure(i, total, fileName, e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 連線失敗: " + e.getMessage(), e);
            failCount = total;
            for (File f : filesToUpload) failedFiles.add(f.getName());

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
            Log.d(TAG, "🔌 連線已關閉");
        }

        Log.d(TAG, "📦 完成: 成功 " + successCount + " / 失敗 " + failCount);
        if (callback != null) callback.onAllComplete(successCount, failCount, failedFiles);
    }

    // ============================================
    // 【工具方法】
    // ============================================

    /**
     * 取得 userId
     */
    private static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("current_user_id", null);

        if (userId == null || userId.isEmpty()) {
            userId = "guest";
        }

        return userId;
    }

    /**
     * 確保資料夾存在，不存在就建立
     */
    private static void ensureDirectoryExists(ChannelSftp channelSftp, String path) {
        try {
            channelSftp.stat(path);  // 檢查是否存在
            Log.d(TAG, "📁 資料夾已存在: " + path);
        } catch (SftpException e) {
            // 不存在，建立它
            try {
                channelSftp.mkdir(path);
                Log.d(TAG, "📁 已建立資料夾: " + path);
            } catch (SftpException e2) {
                Log.w(TAG, "⚠️ 建立資料夾失敗（可能已存在）: " + path);
            }
        }
    }


    // ============================================
    // 【Worker 排程】
    // ============================================

    /**
     * 排程影片上傳 Worker
     * @param delayMinutes 延遲幾分鐘後執行（0 = 立即）
     */
    public static void scheduleVideoUpload(Context context, String trainingID, String videoFileName, int delayMinutes) {
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.Data inputData = new androidx.work.Data.Builder()
                .putString("trainingID", trainingID)
                .putString("videoFileName", videoFileName)
                .build();

        androidx.work.OneTimeWorkRequest.Builder builder = new androidx.work.OneTimeWorkRequest.Builder(VideoUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .addTag("video_upload_" + trainingID);

        if (delayMinutes > 0) {
            builder.setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES);
        }

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("video_" + trainingID, androidx.work.ExistingWorkPolicy.KEEP, builder.build());

        Log.d(TAG, "📅 已排程 Video Worker: " + trainingID + " (延遲 " + delayMinutes + " 分鐘)");
    }

    /**
     * 取消影片上傳 Worker
     */
    public static void cancelVideoUpload(Context context, String trainingID) {
        androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork("video_" + trainingID);

        Log.d(TAG, "🚫 已取消 Video Worker: " + trainingID);
    }

    /**
     * 測試連線
     */
    public static boolean testConnection() {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            Log.d(TAG, "🧪 測試連線: " + SFTP_HOST + ":" + SFTP_PORT);

            JSch jsch = new JSch();
            session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
            session.setPassword(SFTP_PASSWORD);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(CONNECT_TIMEOUT);
            Log.d(TAG, "✅ SSH 連線成功");

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(SESSION_TIMEOUT);
            Log.d(TAG, "✅ SFTP OK");

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ 連線失敗: " + e.getMessage(), e);
            return false;

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}