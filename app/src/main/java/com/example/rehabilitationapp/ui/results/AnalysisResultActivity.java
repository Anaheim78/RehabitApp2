package com.example.rehabilitationapp.ui.results;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;

public class AnalysisResultActivity extends AppCompatActivity {
    private static final String TAG = "AnalysisResult";

    // UI 元件
    private TextView trainingTypeText;
    private TextView actualCountText;
    private TextView targetCountText;
    private TextView durationText;
    private TextView completionRateText;
    private TextView feedbackText;
    private ProgressBar completionProgress;
    private Button saveResultButton;
    private Button retryButton;
    private Button shareLineButton;
    private Button debugPeakButton; // 🔧 DEBUG: 峰值分析按鈕

    // 數據變數
    private String trainingLabel;
    private int actualCount;
    private int targetCount = 4; // 預設目標次數
    private int trainingDuration;
    private String csvFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        Log.d(TAG, "📊 結果頁面啟動");

        // 初始化 UI
        initViews();

        // 取得傳入的數據
        getIntentData();

        // 顯示結果
        displayResults();

        // 設定按鈕事件
        setupButtons();
    }

    private void initViews() {
        trainingTypeText = findViewById(R.id.training_type_text);
        actualCountText = findViewById(R.id.actual_count_text);
        targetCountText = findViewById(R.id.target_count_text);
        durationText = findViewById(R.id.duration_text);
        completionRateText = findViewById(R.id.completion_rate_text);
        feedbackText = findViewById(R.id.feedback_text);
        completionProgress = findViewById(R.id.completion_progress);
        saveResultButton = findViewById(R.id.save_result_button);
        retryButton = findViewById(R.id.retry_button);
        shareLineButton = findViewById(R.id.share_line_button);

        // 🔧 DEBUG: 初始化峰值分析按鈕
        debugPeakButton = findViewById(R.id.debug_peak_button);

        Log.d(TAG, "✅ UI 元件初始化完成");
    }

    private void getIntentData() {
        Intent intent = getIntent();

        trainingLabel = intent.getStringExtra("training_label");
        actualCount = intent.getIntExtra("actual_count", 0);
        targetCount = intent.getIntExtra("target_count", 4);
        trainingDuration = intent.getIntExtra("training_duration", 0);
        csvFileName = intent.getStringExtra("csv_file_name");

        Log.d(TAG, String.format("📋 接收數據 - 類型: %s, 實際: %d, 目標: %d, 時間: %d秒",
                trainingLabel, actualCount, targetCount, trainingDuration));
    }

    private void displayResults() {
        // 設定訓練類型
        if (trainingTypeText != null) {
            trainingTypeText.setText(trainingLabel != null ? trainingLabel : "訓練");
        }

        // 設定實際次數
        if (actualCountText != null) {
            actualCountText.setText("實際次數：" + actualCount);
        }

        // 設定目標次數
        if (targetCountText != null) {
            targetCountText.setText("目標次數：" + targetCount);
        }

        // 設定持續時間
        if (durationText != null) {
            durationText.setText("持續時間：" + trainingDuration + " 秒");
        }

        // 計算完成率
        int completionRate = targetCount > 0 ? (actualCount * 100 / targetCount) : 0;
        completionRate = Math.min(completionRate, 100); // 限制最大100%

        // 設定完成率文字
        if (completionRateText != null) {
            completionRateText.setText(completionRate + "%");
        }

        // 設定進度條
        if (completionProgress != null) {
            completionProgress.setProgress(completionRate);
        }

        // 設定反饋文字
        displayFeedback(completionRate);

        Log.d(TAG, String.format("📊 顯示結果 - 完成率: %d%%", completionRate));
    }

    private void displayFeedback(int completionRate) {
        String feedback;

        if (completionRate >= 90) {
            feedback = "🎉 表現優秀！\n您已經完全掌握了這個動作。";
        } else if (completionRate >= 75) {
            feedback = "😊 您已完成過半次數！\n建議可進行更多練習以提升效果。";
        } else if (completionRate >= 50) {
            feedback = "💪 不錯的開始！\n繼續努力，您會越來越進步。";
        } else {
            feedback = "🌟 每一次練習都是進步！\n建議多加練習以達到更好效果。";
        }

        if (feedbackText != null) {
            feedbackText.setText(feedback);
        }
    }

    private void setupButtons() {
        // 儲存結果按鈕
        if (saveResultButton != null) {
            saveResultButton.setOnClickListener(v -> {
                Log.d(TAG, "💾 儲存結果");
                saveResults();
            });
        }

        // 重新測量按鈕
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                Log.d(TAG, "🔄 重新測量");
                retryTraining();
            });
        }

        // 分享至 LINE 按鈕
        if (shareLineButton != null) {
            shareLineButton.setOnClickListener(v -> {
                Log.d(TAG, "📤 分享至 LINE");
                shareToLine();
            });
        }

        // 🔧 DEBUG: 峰值分析按鈕
        setupDEBUGPeakButtons();
    }

    /**
     * 🔧 DEBUG: 設置峰值分析按鈕
     */
    private void setupDEBUGPeakButtons() {
        if (debugPeakButton != null) {
            debugPeakButton.setOnClickListener(v -> {
                Log.d(TAG, "🔧 DEBUG: 點擊峰值分析按鈕");
                openDEBUGPeakVisualization();
            });
        }
    }

    /**
     * 🔧 DEBUG: 開啟峰值視覺化頁面
     */
    private void openDEBUGPeakVisualization() {
        if (csvFileName == null || csvFileName.isEmpty()) {
            Toast.makeText(this, "❌ 無法找到 CSV 檔案", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "🔧 DEBUG: CSV 檔案名稱為空");
            return;
        }

        try {
            Intent intent = new Intent(this, com.example.rehabilitationapp.ui.debug.DebugPeakVisualizationActivity.class);
            intent.putExtra("csv_file_name", csvFileName);
            intent.putExtra("training_label", trainingLabel);
            intent.putExtra("actual_count", actualCount);
            intent.putExtra("target_count", targetCount);

            Log.d(TAG, "🔧 DEBUG: 準備跳轉峰值視覺化頁面");
            Log.d(TAG, "🔧 DEBUG: CSV檔案 = " + csvFileName);
            Log.d(TAG, "🔧 DEBUG: 訓練標籤 = " + trainingLabel);

            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "🔧 DEBUG: 開啟峰值視覺化失敗", e);
            Toast.makeText(this, "❌ 開啟分析頁面失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveResults() {
        // 使用 ReportGenerator 生成報告
        ReportGenerator reportGenerator = new ReportGenerator();

        ReportGenerator.TrainingReport report = new ReportGenerator.TrainingReport();
        report.trainingType = trainingLabel;
        report.actualCount = actualCount;
        report.targetCount = targetCount;
        report.duration = trainingDuration;
        report.completionRate = targetCount > 0 ? (actualCount * 100 / targetCount) : 0;
        report.csvFileName = csvFileName;

        boolean success = reportGenerator.saveReport(this, report);

        if (success) {
            Log.d(TAG, "✅ 報告儲存成功");
            // 可以顯示 Toast 或 Snackbar
        } else {
            Log.e(TAG, "❌ 報告儲存失敗");
        }
    }

    private void retryTraining() {
        // 返回訓練頁面
        finish(); // 關閉結果頁面，返回上一頁
    }

    private void shareToLine() {
        String shareText = generateShareText();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.setPackage("jp.naver.line.android"); // LINE 的 package name

        try {
            startActivity(shareIntent);
            Log.d(TAG, "📤 啟動 LINE 分享");
        } catch (Exception e) {
            Log.e(TAG, "❌ LINE 分享失敗，使用一般分享", e);

            // 如果 LINE 不存在，使用一般分享
            Intent generalShareIntent = new Intent(Intent.ACTION_SEND);
            generalShareIntent.setType("text/plain");
            generalShareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(generalShareIntent, "分享訓練結果"));
        }
    }

    private String generateShareText() {
        int completionRate = targetCount > 0 ? (actualCount * 100 / targetCount) : 0;

        return String.format(
                "🎯 復健訓練結果 🎯\n" +
                        "訓練項目：%s\n" +
                        "完成次數：%d/%d\n" +
                        "完成率：%d%%\n" +
                        "訓練時間：%d 秒\n" +
                        "\n#復健 #訓練成果",
                trainingLabel, actualCount, targetCount, completionRate, trainingDuration
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🔚 結果頁面銷毀");
    }
}