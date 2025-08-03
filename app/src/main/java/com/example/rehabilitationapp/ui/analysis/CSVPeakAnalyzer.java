package com.example.rehabilitationapp.ui.analysis;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV檔案峰值分析器
 * 讀取CSV檔案並進行峰值檢測與重分配分析
 */
public class CSVPeakAnalyzer {
    private static final String TAG = "CSVPeakAnalyzer";

    /**
     * 分析結果類
     */
    public static class AnalysisResult {
        public String fileName;
        public String trainingLabel;
        public int totalDataPoints;
        public int calibratingPeaks;
        public int maintainingPeaks;
        public int totalPeaks;
        public double averageValue;
        public String targetColumn;
        public boolean success;
        public String errorMessage;

        public AnalysisResult() {
            this.success = false;
        }
    }

    /**
     * 從CSV檔案分析峰值
     */
    public static AnalysisResult analyzePeaksFromFile(Context context, String fileName) {
        Log.d(TAG, "🔍 開始分析CSV檔案: " + fileName);

        AnalysisResult result = new AnalysisResult();
        result.fileName = fileName;

        try {
            // 1. 讀取CSV檔案
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File csvFile = new File(downloadsDir, fileName);

            if (!csvFile.exists()) {
                result.errorMessage = "檔案不存在: " + fileName;
                Log.e(TAG, result.errorMessage);
                return result;
            }

            // 2. 解析CSV內容
            List<String[]> csvData = readCSV(csvFile);
            if (csvData.isEmpty()) {
                result.errorMessage = "CSV檔案為空或讀取失敗";
                Log.e(TAG, result.errorMessage);
                return result;
            }

            // 3. 分析標題行，確定目標欄位
            String[] headers = csvData.get(0);
            int targetColumnIndex = determineTargetColumn(headers, fileName);

            if (targetColumnIndex == -1) {
                result.errorMessage = "無法找到適合的分析欄位";
                Log.e(TAG, result.errorMessage);
                return result;
            }

            result.targetColumn = headers[targetColumnIndex];
            result.trainingLabel = extractTrainingLabel(fileName);

            Log.d(TAG, "目標分析欄位: " + result.targetColumn);
            Log.d(TAG, "訓練標籤: " + result.trainingLabel);

            // 4. 提取數據
            DataExtractionResult extractResult = extractColumnData(csvData, targetColumnIndex);
            if (!extractResult.success) {
                result.errorMessage = extractResult.errorMessage;
                return result;
            }

            result.totalDataPoints = extractResult.allData.size();
            result.averageValue = PeakRedistributionProcessor.calculateMean(
                    extractResult.allData.stream().mapToDouble(Double::doubleValue).toArray()
            );

            Log.d(TAG, String.format("數據統計 - 總筆數: %d, 平均值: %.6f",
                    result.totalDataPoints, result.averageValue));

            // 5. 分別分析不同狀態的峰值
            if (!extractResult.calibratingData.isEmpty()) {
                double[] calibratingArray = extractResult.calibratingData.stream()
                        .mapToDouble(Double::doubleValue).toArray();
                result.calibratingPeaks = PeakRedistributionProcessor.countPeaks(calibratingArray);
                Log.d(TAG, "校正階段峰值數: " + result.calibratingPeaks);
            }

            if (!extractResult.maintainingData.isEmpty()) {
                double[] maintainingArray = extractResult.maintainingData.stream()
                        .mapToDouble(Double::doubleValue).toArray();
                result.maintainingPeaks = PeakRedistributionProcessor.countPeaks(maintainingArray);
                Log.d(TAG, "維持階段峰值數: " + result.maintainingPeaks);
            }

            result.totalPeaks = result.calibratingPeaks + result.maintainingPeaks;
            result.success = true;

            Log.d(TAG, "✅ CSV分析完成!");
            Log.d(TAG, String.format("   - 校正階段峰值: %d", result.calibratingPeaks));
            Log.d(TAG, String.format("   - 維持階段峰值: %d", result.maintainingPeaks));
            Log.d(TAG, String.format("   - 總峰值數: %d", result.totalPeaks));

        } catch (Exception e) {
            result.errorMessage = "分析過程發生錯誤: " + e.getMessage();
            Log.e(TAG, result.errorMessage, e);
        }

        return result;
    }

    /**
     * 數據提取結果
     */
    private static class DataExtractionResult {
        List<Double> allData = new ArrayList<>();
        List<Double> calibratingData = new ArrayList<>();
        List<Double> maintainingData = new ArrayList<>();
        boolean success = false;
        String errorMessage = "";
    }

    /**
     * 提取目標欄位數據
     */
    private static DataExtractionResult extractColumnData(List<String[]> csvData, int targetColumnIndex) {
        DataExtractionResult result = new DataExtractionResult();

        try {
            // 跳過標題行，從第二行開始
            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);

                if (row.length <= targetColumnIndex) {
                    continue; // 跳過欄位不足的行
                }

                // 解析目標欄位的數值
                try {
                    double value = Double.parseDouble(row[targetColumnIndex].trim());
                    result.allData.add(value);

                    // 根據狀態欄位分類（假設state欄位在第2欄，索引為1）
                    if (row.length > 1) {
                        String state = row[1].trim().toUpperCase();
                        if ("CALIBRATING".equals(state)) {
                            result.calibratingData.add(value);
                        } else if ("MAINTAINING".equals(state)) {
                            result.maintainingData.add(value);
                        }
                    }

                } catch (NumberFormatException e) {
                    Log.w(TAG, "無法解析數值: " + row[targetColumnIndex] + " (行 " + (i+1) + ")");
                }
            }

            if (result.allData.isEmpty()) {
                result.errorMessage = "未找到有效的數值資料";
            } else {
                result.success = true;
            }

        } catch (Exception e) {
            result.errorMessage = "數據提取錯誤: " + e.getMessage();
            Log.e(TAG, result.errorMessage, e);
        }

        return result;
    }

    /**
     * 確定目標分析欄位
     */
    private static int determineTargetColumn(String[] headers, String fileName) {
        // 根據檔案名稱中的訓練標籤決定分析哪個欄位
        String fileNameLower = fileName.toLowerCase();

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();

            if (fileNameLower.contains("抿嘴")) {
                // 抿嘴訓練：分析面積比值
                if (header.contains("area_ratio") || header.contains("ratio")) {
                    return i;
                }
            } else if (fileNameLower.contains("嘟嘴")) {
                // 嘟嘴訓練：分析高寬比值
                if (header.contains("height_width_ratio") || header.contains("ratio")) {
                    return i;
                }
            }

            // 通用：尋找包含 "ratio" 或 "value" 的欄位
            if (header.contains("ratio") || header.contains("value")) {
                return i;
            }
        }

        // 如果沒找到特定欄位，使用最後一個數值欄位
        for (int i = headers.length - 1; i >= 0; i--) {
            String header = headers[i].toLowerCase().trim();
            if (!header.equals("time_seconds") && !header.equals("state")) {
                return i;
            }
        }

        return -1; // 沒找到適合的欄位
    }

    /**
     * 從檔案名稱提取訓練標籤
     */
    private static String extractTrainingLabel(String fileName) {
        if (fileName.contains("抿嘴")) {
            return "抿嘴";
        } else if (fileName.contains("嘟嘴")) {
            return "嘟嘴";
        } else {
            return "未知";
        }
    }

    /**
     * 讀取CSV檔案
     */
    private static List<String[]> readCSV(File csvFile) {
        List<String[]> data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 處理CSV的逗號分隔
                String[] values = line.split(",");

                // 清理每個值的空白字符
                for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].trim();
                }

                data.add(values);
            }

            Log.d(TAG, "成功讀取CSV - 總行數: " + data.size());

        } catch (IOException e) {
            Log.e(TAG, "讀取CSV檔案失敗", e);
        }

        return data;
    }

    /**
     * 格式化分析結果為顯示文字
     */
    public static String formatResultForDisplay(AnalysisResult result) {
        if (!result.success) {
            return "❌ 分析失敗: " + result.errorMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 峰值分析結果\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("🏷️ 訓練類型: %s\n", result.trainingLabel));
        sb.append(String.format("📈 分析欄位: %s\n", result.targetColumn));
        sb.append(String.format("📝 總資料點: %d 筆\n", result.totalDataPoints));
        sb.append(String.format("📊 平均值: %.4f\n", result.averageValue));
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("🟡 校正階段峰值: %d 個\n", result.calibratingPeaks));
        sb.append(String.format("🟢 維持階段峰值: %d 個\n", result.maintainingPeaks));
        sb.append(String.format("🔵 總峰值數量: %d 個", result.totalPeaks));

        return sb.toString();
    }
}