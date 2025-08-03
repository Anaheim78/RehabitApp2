package com.example.rehabilitationapp.ui.results;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 訓練報告生成器
 * 負責生成和儲存訓練結果報告
 */
public class ReportGenerator {
    private static final String TAG = "ReportGenerator";

    /**
     * 訓練報告數據類
     */
    public static class TrainingReport {
        public String trainingType;      // 訓練類型
        public int actualCount;          // 實際完成次數
        public int targetCount;          // 目標次數
        public int duration;             // 訓練持續時間（秒）
        public int completionRate;       // 完成率（百分比）
        public String csvFileName;       // 原始CSV檔案名稱
        public String timestamp;         // 報告生成時間
        public String feedback;          // 反饋訊息

        public TrainingReport() {
            this.timestamp = getCurrentTimestamp();
        }
    }

    /**
     * 儲存訓練報告
     */
    public boolean saveReport(Context context, TrainingReport report) {
        Log.d(TAG, "📋 開始生成訓練報告");

        try {
            // 生成報告檔案名稱
            String fileName = generateReportFileName(report);

            // 取得儲存路徑
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File reportFile = new File(downloadsDir, fileName);

            // 生成報告內容
            String reportContent = generateReportContent(report);

            // 寫入檔案
            FileWriter writer = new FileWriter(reportFile);
            writer.write(reportContent);
            writer.close();

            Log.d(TAG, "✅ 報告儲存成功：" + reportFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "❌ 報告儲存失敗", e);
            return false;
        }
    }

    /**
     * 生成報告檔案名稱
     */
    private String generateReportFileName(TrainingReport report) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        return String.format("TrainingReport_%s_%s.txt",
                report.trainingType, timestamp);
    }

    /**
     * 生成報告內容
     */
    private String generateReportContent(TrainingReport report) {
        StringBuilder content = new StringBuilder();

        // 報告標題
        content.append("═══════════════════════════════════════\n");
        content.append("            🎯 復健訓練報告 🎯            \n");
        content.append("═══════════════════════════════════════\n\n");

        // 基本資訊
        content.append("📋 訓練資訊\n");
        content.append("─────────────────────────────────────\n");
        content.append(String.format("訓練類型：%s\n", report.trainingType));
        content.append(String.format("報告時間：%s\n", report.timestamp));
        content.append(String.format("原始檔案：%s\n\n", report.csvFileName));

        // 訓練結果
        content.append("📊 訓練結果\n");
        content.append("─────────────────────────────────────\n");
        content.append(String.format("目標次數：%d 次\n", report.targetCount));
        content.append(String.format("實際次數：%d 次\n", report.actualCount));
        content.append(String.format("完成率：%d%%\n", report.completionRate));
        content.append(String.format("訓練時間：%d 秒\n\n", report.duration));

        // 表現評估
        content.append("🎯 表現評估\n");
        content.append("─────────────────────────────────────\n");
        content.append(generatePerformanceAnalysis(report));
        content.append("\n\n");

        // 改善建議
        content.append("💡 改善建議\n");
        content.append("─────────────────────────────────────\n");
        content.append(generateImprovementSuggestions(report));
        content.append("\n\n");

        // 峰值分析摘要
        content.append("📈 峰值分析摘要\n");
        content.append("─────────────────────────────────────\n");
        content.append(generatePeakAnalysisSummary(report));
        content.append("\n\n");

        // 報告結尾
        content.append("═══════════════════════════════════════\n");
        content.append("報告生成時間：").append(getCurrentTimestamp()).append("\n");
        content.append("版本：復健 App v1.0\n");
        content.append("═══════════════════════════════════════\n");

        return content.toString();
    }

    /**
     * 生成表現分析
     */
    private String generatePerformanceAnalysis(TrainingReport report) {
        StringBuilder analysis = new StringBuilder();

        if (report.completionRate >= 90) {
            analysis.append("🌟 優秀表現！\n");
            analysis.append("您的表現非常出色，已經熟練掌握了這個訓練動作。\n");
            analysis.append("完成率達到 90% 以上，顯示您的動作準確性很高。");
        } else if (report.completionRate >= 75) {
            analysis.append("😊 良好表現！\n");
            analysis.append("您已經很好地掌握了這個訓練動作。\n");
            analysis.append("完成率在 75% 以上，是很不錯的成績。");
        } else if (report.completionRate >= 50) {
            analysis.append("💪 有進步空間！\n");
            analysis.append("您正在學習這個訓練動作，已經有了不錯的基礎。\n");
            analysis.append("完成率超過一半，繼續練習會有更好的效果。");
        } else {
            analysis.append("🌱 需要更多練習！\n");
            analysis.append("這個訓練動作對您來說還有挑戰性。\n");
            analysis.append("建議增加練習頻率，逐步提升動作準確性。");
        }

        return analysis.toString();
    }

    /**
     * 生成改善建議
     */
    private String generateImprovementSuggestions(TrainingReport report) {
        StringBuilder suggestions = new StringBuilder();

        // 根據完成率給出建議
        if (report.completionRate < 50) {
            suggestions.append("• 建議每天練習 2-3 次，每次 5-10 分鐘\n");
            suggestions.append("• 注意動作的準確性，寧慢勿快\n");
            suggestions.append("• 可以對著鏡子練習，觀察自己的動作\n");
        } else if (report.completionRate < 75) {
            suggestions.append("• 繼續保持練習頻率\n");
            suggestions.append("• 可以嘗試稍微增加訓練強度\n");
            suggestions.append("• 注意動作的持續性和穩定性\n");
        } else {
            suggestions.append("• 您的表現很好，可以嘗試更高難度的訓練\n");
            suggestions.append("• 保持目前的練習頻率\n");
            suggestions.append("• 可以幫助其他人學習這個動作\n");
        }

        // 根據訓練類型給出特定建議
        if ("抿嘴".equals(report.trainingType)) {
            suggestions.append("• 抿嘴動作要點：上下唇緊閉，保持 3-5 秒\n");
            suggestions.append("• 避免用力過度，自然閉合即可\n");
        } else if ("嘟嘴".equals(report.trainingType)) {
            suggestions.append("• 嘟嘴動作要點：嘴唇向前突出，形成圓形\n");
            suggestions.append("• 保持動作穩定，避免搖擺\n");
        }

        return suggestions.toString();
    }

    /**
     * 生成峰值分析摘要
     */
    private String generatePeakAnalysisSummary(TrainingReport report) {
        StringBuilder summary = new StringBuilder();

        summary.append(String.format("檢測到的有效動作次數：%d 次\n", report.actualCount));
        summary.append(String.format("動作識別準確率：基於峰值重分配算法\n"));
        summary.append(String.format("平均每次動作持續時間：約 %.1f 秒\n",
                report.duration > 0 ? (double) report.duration / Math.max(report.actualCount, 1) : 0));

        if (report.actualCount > report.targetCount) {
            summary.append("註：檢測到的動作次數超過目標，可能包含額外的練習。");
        } else if (report.actualCount < report.targetCount) {
            summary.append("註：部分動作可能未達到檢測標準，建議動作更加明顯。");
        }

        return summary.toString();
    }

    /**
     * 生成簡化版報告（用於分享）
     */
    public String generateShareReport(TrainingReport report) {
        return String.format(
                "🎯 復健訓練成果 🎯\n\n" +
                        "📋 訓練：%s\n" +
                        "🎯 完成：%d/%d 次 (%d%%)\n" +
                        "⏱️ 時間：%d 秒\n" +
                        "📅 日期：%s\n\n" +
                        "%s\n\n" +
                        "#復健訓練 #健康管理",
                report.trainingType,
                report.actualCount, report.targetCount, report.completionRate,
                report.duration,
                report.timestamp,
                getSimpleFeedback(report.completionRate)
        );
    }

    /**
     * 取得簡化反饋
     */
    private String getSimpleFeedback(int completionRate) {
        if (completionRate >= 90) {
            return "🌟 表現優秀！";
        } else if (completionRate >= 75) {
            return "😊 表現良好！";
        } else if (completionRate >= 50) {
            return "💪 繼續加油！";
        } else {
            return "🌱 持續練習！";
        }
    }

    /**
     * 取得當前時間戳
     */
    static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}