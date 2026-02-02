package com.example.rehabilitationapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AppLogger {

    private static final String TAG = "AppLogger";
    private static FirebaseAnalytics analytics;
    private static String currentUserId = "unknown";
    private static String currentUserName = "unknown";

    // ===== 初始化 =====
    public static void init(Context context) {
        analytics = FirebaseAnalytics.getInstance(context);
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(true);

        // 記錄裝置資訊
        crashlytics.setCustomKey("device_model", Build.MODEL);
        crashlytics.setCustomKey("device_brand", Build.BRAND);
        crashlytics.setCustomKey("android_version", Build.VERSION.RELEASE);

        // 嘗試讀取已儲存的用戶 ID
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = prefs.getString("current_user_id", "unknown");

        Log.d(TAG, "✅ AppLogger 初始化完成，userId: " + currentUserId);
    }

    // ===== 設定用戶（登入時呼叫）=====
    public static void setUser(String userId, String userName) {
        currentUserId = userId != null ? userId : "unknown";
        currentUserName = userName != null ? userName : "unknown";

        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setUserId(currentUserId);
        crashlytics.setCustomKey("user_id", currentUserId);
        crashlytics.setCustomKey("user_name", currentUserName);

        if (analytics != null) {
            analytics.setUserId(currentUserId);
        }
    }

    // ===== 取得目前用戶 ID =====
    public static String getCurrentUserId() {
        return currentUserId;
    }

    // ===== 打開 APP =====
    public static void logAppOpen(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = prefs.getString("current_user_id", "未登入");

        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("device_model", Build.MODEL);
        bundle.putString("device_brand", Build.BRAND);
        bundle.putString("android_version", Build.VERSION.RELEASE);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("app_open", bundle);
    }

    // ===== 登入 =====
    public static void logLogin(String userId, String userName) {
        setUser(userId, userName);

        Bundle bundle = new Bundle();
        bundle.putString("user_id", userId);
        bundle.putString("user_name", userName);
        bundle.putString("device_model", Build.MODEL);
        bundle.putString("device_brand", Build.BRAND);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("user_login", bundle);
    }

    // ===== 登出 =====
    public static void logLogout() {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("user_logout", bundle);
    }

    // ===== 開始訓練 =====
    public static void logTrainingStart(String trainingLabel) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("training_label", trainingLabel);
        bundle.putString("device_model", Build.MODEL);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("training_start", bundle);
    }

    // ===== 完成訓練 =====
    public static void logTrainingComplete(String trainingLabel) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("training_label", trainingLabel);
        bundle.putString("device_model", Build.MODEL);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("training_complete", bundle);
    }

    // ===== Firebase 紀錄上傳 =====
    public static void logFirebaseUpload(String trainingId, boolean success, String error) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("training_id", trainingId);
        bundle.putBoolean("success", success);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("firebase_upload", bundle);

        if (!success && error != null) {
            logError("FIREBASE", trainingId + " 上傳失敗: " + error);
        }
    }

    // ===== CSV 上傳 =====
    public static void logCsvUpload(String trainingId, boolean success, String error) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("training_id", trainingId);
        bundle.putBoolean("success", success);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("csv_upload", bundle);

        if (!success && error != null) {
            logError("CSV", trainingId + " 上傳失敗: " + error);
        }
    }

    // ===== 影片上傳 =====
    public static void logVideoUpload(String trainingId, String fileName, boolean success, String error) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("training_id", trainingId);
        bundle.putString("file_name", fileName);
        bundle.putBoolean("success", success);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("video_upload", bundle);

        if (!success && error != null) {
            logError("VIDEO", fileName + " 上傳失敗: " + error);
        }
    }

    // ===== 同步完成（批次）=====
    public static void logSyncComplete(String syncType, int successCount, int failCount) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", currentUserId);
        bundle.putString("sync_type", syncType);
        bundle.putInt("success_count", successCount);
        bundle.putInt("fail_count", failCount);
        bundle.putLong("timestamp", System.currentTimeMillis());
        logEvent("sync_complete", bundle);
    }

    // ===== 通用事件（核心方法）=====
    public static void logEvent(String eventName, Bundle params) {
        // ★★★ 1. Logcat 即時顯示 ★★★
        Log.d(TAG, "📌 Event: " + eventName + " | user: " + currentUserId);

        // ★★★ 2. Firebase Analytics ★★★
        if (analytics != null) {
            analytics.logEvent(eventName, params);
        }

        // ★★★ 3. Crashlytics Log ★★★
        FirebaseCrashlytics.getInstance().log("Event: " + eventName + " | user: " + currentUserId);

        // ★★★ 4. Firestore 即時 Log（遠端馬上看）★★★
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", eventName);
            logData.put("user_id", currentUserId);
            logData.put("device_model", Build.MODEL);
            logData.put("device_brand", Build.BRAND);
            logData.put("timestamp", System.currentTimeMillis());

            // 把 Bundle 的資料也加進去
            if (params != null) {
                for (String key : params.keySet()) {
                    Object value = params.get(key);
                    if (value != null) {
                        logData.put(key, value);
                    }
                }
            }

            FirebaseFirestore.getInstance()
                    .collection("app_logs")
                    .add(logData)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore Log 失敗: " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Firestore Log 異常: " + e.getMessage());
        }
    }

    // ===== 記錄錯誤 =====
    public static void logError(String tag, String message) {
        Log.e(TAG, "❌ [" + currentUserId + "] " + tag + ": " + message);
        FirebaseCrashlytics.getInstance().log("[" + currentUserId + "] " + tag + ": " + message);
    }

    // ===== 記錄 Exception =====
    public static void logException(Throwable e) {
        Log.e(TAG, "💥 Exception: " + e.getMessage(), e);
        FirebaseCrashlytics.getInstance().recordException(e);
    }
}