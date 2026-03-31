package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import android.os.Environment;
public class BackupUploader {

    private static final String TAG = "BackupUploader";

    private static final String SFTP_HOST = "163.25.101.37";
    private static final int SFTP_PORT = 22222;
    private static final String SFTP_USER = "hao_lab01";
    private static final String SFTP_PASSWORD = "123456";
    private static final String REMOTE_BASE_DIR = "/Rh_Videos";

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int SESSION_TIMEOUT = 120000;

    public interface BackupCallback {
        void onProgress(String message);
        void onComplete(int totalFiles, int successCount, int failCount);
    }

    /**
     * 備份上傳：Room DB + CSV + MP4
     */
    public static void uploadBackup(Context context, BackupCallback callback) {
        new Thread(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());

            String userId = getUserId(context);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

            int totalFiles = 0;
            int successCount = 0;
            int failCount = 0;

            Session session = null;
            ChannelSftp channelSftp = null;

            try {
                // 連線
                mainHandler.post(() -> callback.onProgress("連線中..."));

                JSch jsch = new JSch();
                session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
                session.setPassword(SFTP_PASSWORD);

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setServerAliveInterval(5000);
                session.connect(CONNECT_TIMEOUT);

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect(SESSION_TIMEOUT);
                Log.d(TAG, "✅ SFTP 連線成功");

                // 建立備份資料夾
                String backupDir = REMOTE_BASE_DIR + "/" + userId + "/backup";
                ensureDirectoryExists(channelSftp, REMOTE_BASE_DIR);
                ensureDirectoryExists(channelSftp, REMOTE_BASE_DIR + "/" + userId);
                ensureDirectoryExists(channelSftp, backupDir);
                channelSftp.cd(backupDir);

// ===== 1. 上傳 Room DB =====
                mainHandler.post(() -> callback.onProgress("上傳資料庫..."));
                File dbFile = context.getDatabasePath("rehab_db_" + userId + ".db");
                if (dbFile.exists()) {
                    totalFiles++;
                    String remoteDbName = userId + "_backup_" + timestamp + ".db";
                    try {
                        FileInputStream fis = new FileInputStream(dbFile);
                        channelSftp.put(fis, remoteDbName);
                        fis.close();
                        successCount++;
                        Log.d(TAG, "✅ DB 上傳成功: " + remoteDbName);
                    } catch (Exception e) {
                        failCount++;
                        Log.e(TAG, "❌ DB 上傳失敗: " + e.getMessage());
                    }
                }

// ===== 2. 上傳 CSV 檔案 =====
                mainHandler.post(() -> callback.onProgress("上傳 CSV..."));
                File csvDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (csvDir != null && csvDir.exists()) {
                    File[] csvFiles = csvDir.listFiles((dir, name) -> name.endsWith(".csv"));
                    if (csvFiles != null) {
                        for (File csv : csvFiles) {
                            totalFiles++;
                            try {
                                FileInputStream fis = new FileInputStream(csv);
                                channelSftp.put(fis, csv.getName());
                                fis.close();
                                successCount++;
                                Log.d(TAG, "✅ CSV 上傳成功: " + csv.getName());
                            } catch (Exception e) {
                                failCount++;
                                Log.e(TAG, "❌ CSV 上傳失敗: " + csv.getName());
                            }
                        }
                    }
                }
                // ===== 3. 上傳 MP4 影片 =====
                mainHandler.post(() -> callback.onProgress("上傳影片..."));
                File videoDir = context.getExternalFilesDir(null);
                if (videoDir != null && videoDir.exists()) {
                    File[] mp4Files = videoDir.listFiles((dir, name) -> name.endsWith(".mp4"));
                    if (mp4Files != null) {
                        int videoIndex = 0;
                        for (File mp4 : mp4Files) {
                            videoIndex++;
                            int finalIndex = videoIndex;
                            int finalTotal = mp4Files.length;
                            mainHandler.post(() -> callback.onProgress("上傳影片 " + finalIndex + "/" + finalTotal));

                            totalFiles++;
                            try {
                                FileInputStream fis = new FileInputStream(mp4);
                                channelSftp.put(fis, mp4.getName());
                                fis.close();
                                successCount++;
                                Log.d(TAG, "✅ MP4 上傳成功: " + mp4.getName());
                            } catch (Exception e) {
                                failCount++;
                                Log.e(TAG, "❌ MP4 上傳失敗: " + mp4.getName());
                            }
                        }
                    }
                }

                Log.d(TAG, "📦 備份完成: 成功 " + successCount + " / 失敗 " + failCount);

            } catch (Exception e) {
                Log.e(TAG, "❌ 備份失敗: " + e.getMessage(), e);
                failCount = -1; // 表示連線失敗
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
                if (session != null && session.isConnected()) session.disconnect();
            }

            int finalTotal = totalFiles;
            int finalSuccess = successCount;
            int finalFail = failCount;
            mainHandler.post(() -> callback.onComplete(finalTotal, finalSuccess, finalFail));

        }).start();
    }

    private static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("current_user_id", "no_login");
        if (userId == null || userId.isEmpty()) {
            userId = "guest";
        }
        return userId;
    }

    private static void ensureDirectoryExists(ChannelSftp channelSftp, String path) {
        try {
            channelSftp.stat(path);
        } catch (Exception e) {
            try {
                channelSftp.mkdir(path);
                Log.d(TAG, "📁 已建立資料夾: " + path);
            } catch (Exception e2) {
                Log.w(TAG, "⚠️ 建立資料夾失敗: " + path);
            }
        }
    }
}