package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//根據特定動作類型
public class FaceDataRecorder {
    private static final String TAG = "FaceDataRecorder";

    private Context context;
    private String trainingLabel;
    private int trainingType;
    private List<String> dataLines;
    private String fileName;

    // 🔥 新增：記錄開始時間，用於計算相對時間
    private long startTime = 0;

    // MediaPipe 臉部關鍵點索引
    private static final int[] UPPER_LIP_INDICES = {61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318};
    private static final int[] LOWER_LIP_INDICES = {78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415};

    public FaceDataRecorder(Context context, String trainingLabel, int trainingType) {
        this.context = context;
        this.trainingLabel = trainingLabel;
        this.trainingType = trainingType;
        this.dataLines = new ArrayList<>();

        // 🔥 記錄開始時間
        this.startTime = System.currentTimeMillis();

        // 建立檔案名稱
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        this.fileName = String.format("FaceTraining_%s_%s.csv", trainingLabel, timestamp);

        // 寫入 CSV 標題
        initializeCSV();

        Log.d(TAG, "初始化記錄器 - 檔案: " + fileName + ", 開始時間: " + startTime);
    }

    private void initializeCSV() {
        String header = "";

        if ("抿嘴".equals(trainingLabel)) {
            header = "time_seconds,state,upper_lip_area,lower_lip_area,area_ratio";
        } else if ("嘟嘴".equals(trainingLabel)) {
            header = "time_seconds,state,mouth_height,mouth_width,height_width_ratio";
        } else {
            header = "time_seconds,state,metric_value"; // 預設格式
        }

        dataLines.add(header);
        Log.d(TAG, "CSV 標題: " + header);
    }

    public void recordLandmarkData(String state, float[][] landmarks) {
        try {
            // 🔥 改用相對時間，從0開始，以秒為單位
            long currentTime = System.currentTimeMillis();
            double relativeTimeSeconds = (currentTime - startTime) / 1000.0;

            String dataLine = "";

            if ("抿嘴".equals(trainingLabel)) {
                // 🔥 改用掃描線方法計算上下嘴唇面積
                float upperLipArea = calculateLipAreaByScanline(landmarks, UPPER_LIP_INDICES);
                float lowerLipArea = calculateLipAreaByScanline(landmarks, LOWER_LIP_INDICES);
                float areaRatio = lowerLipArea > 0 ? upperLipArea / lowerLipArea : 0;

                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f,%.3f",
                        relativeTimeSeconds, state, upperLipArea, lowerLipArea, areaRatio);

                Log.d(TAG, String.format("抿嘴數據 [%.3fs] - 上唇面積: %.3f, 下唇面積: %.3f, 比值: %.3f",
                        relativeTimeSeconds, upperLipArea, lowerLipArea, areaRatio));

            } else if ("嘟嘴".equals(trainingLabel)) {
                // 🔥 改用外緣點計算嘴巴高度和寬度
                float[] mouthDimensions = calculateMouthDimensionsImproved(landmarks);
                float height = mouthDimensions[0];
                float width = mouthDimensions[1];
                float heightWidthRatio = width > 0 ? height / width : 0;

                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f,%.3f",
                        relativeTimeSeconds, state, height, width, heightWidthRatio);

                Log.d(TAG, String.format("嘟嘴數據 [%.3fs] - 高度: %.3f, 寬度: %.3f, 比值: %.3f",
                        relativeTimeSeconds, height, width, heightWidthRatio));
            }

            if (!dataLine.isEmpty()) {
                dataLines.add(dataLine);
            }

        } catch (Exception e) {
            Log.e(TAG, "記錄數據時發生錯誤", e);
        }
    }

    // 🔥 新方法：用掃描線計算嘴唇面積
    private float calculateLipAreaByScanline(float[][] landmarks, int[] lipIndices) {
        try {
            List<float[]> lipPoints = new ArrayList<>();

            // 收集嘴唇關鍵點
            for (int index : lipIndices) {
                if (index < landmarks.length) {
                    lipPoints.add(new float[]{landmarks[index][0], landmarks[index][1]});
                }
            }

            if (lipPoints.size() < 3) {
                return 0; // 不足以形成多邊形
            }

            // 找出Y軸的範圍
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            for (float[] point : lipPoints) {
                minY = Math.min(minY, point[1]);
                maxY = Math.max(maxY, point[1]);
            }

            // 🔥 掃描線方法：每隔0.5像素掃描一條橫線
            float totalArea = 0;
            float scanStep = 0.5f; // 掃描精度

            for (float y = minY; y <= maxY; y += scanStep) {
                List<Float> intersections = new ArrayList<>();

                // 找出這條水平線與多邊形邊界的交點
                for (int i = 0; i < lipPoints.size(); i++) {
                    int j = (i + 1) % lipPoints.size();
                    float[] p1 = lipPoints.get(i);
                    float[] p2 = lipPoints.get(j);

                    // 檢查線段是否與水平掃描線相交
                    if ((p1[1] <= y && y < p2[1]) || (p2[1] <= y && y < p1[1])) {
                        // 計算交點的X坐標
                        float x = p1[0] + (y - p1[1]) * (p2[0] - p1[0]) / (p2[1] - p1[1]);
                        intersections.add(x);
                    }
                }

                // 排序交點
                Collections.sort(intersections);

                // 計算這條掃描線上的面積（成對的交點之間）
                for (int i = 0; i < intersections.size() - 1; i += 2) {
                    if (i + 1 < intersections.size()) {
                        float lineWidth = Math.abs(intersections.get(i + 1) - intersections.get(i));
                        totalArea += lineWidth * scanStep;
                    }
                }
            }

            Log.d(TAG, String.format("掃描線面積計算完成 - 總面積: %.3f, 掃描範圍: %.1f to %.1f",
                    totalArea, minY, maxY));

            return totalArea;

        } catch (Exception e) {
            Log.e(TAG, "掃描線計算嘴唇面積時發生錯誤", e);
            return 0;
        }
    }

    // 🔥 改良版：用外緣點計算嘴巴高度和寬度
    private float[] calculateMouthDimensionsImproved(float[][] landmarks) {
        try {
            // 🔥 更準確的嘴角點 (61: 左嘴角, 291: 右嘴角)
            float leftCornerX = landmarks[61][0];
            float rightCornerX = landmarks[291][0];
            float mouthWidth = Math.abs(rightCornerX - leftCornerX);

            // 🔥 找出嘴唇外緣的最高和最低點
            // 上唇外緣關鍵點
            int[] upperOuterIndices = {61, 62, 63, 64, 65, 66, 67, 291, 292, 293, 294, 295, 296, 297};
            // 下唇外緣關鍵點
            int[] lowerOuterIndices = {61, 84, 17, 314, 405, 320, 307, 291, 375, 321, 308, 324, 318};

            float highestY = Float.MAX_VALUE; // Y軸越小越高
            float lowestY = Float.MIN_VALUE;  // Y軸越大越低

            // 找上唇最高點
            for (int index : upperOuterIndices) {
                if (index < landmarks.length) {
                    highestY = Math.min(highestY, landmarks[index][1]);
                }
            }

            // 找下唇最低點
            for (int index : lowerOuterIndices) {
                if (index < landmarks.length) {
                    lowestY = Math.max(lowestY, landmarks[index][1]);
                }
            }

            float mouthHeight = Math.abs(lowestY - highestY);

            Log.d(TAG, String.format("嘴巴尺寸 - 寬度: %.3f (左%.1f → 右%.1f), 高度: %.3f (上%.1f → 下%.1f)",
                    mouthWidth, leftCornerX, rightCornerX, mouthHeight, highestY, lowestY));

            return new float[]{mouthHeight, mouthWidth};

        } catch (Exception e) {
            Log.e(TAG, "計算嘴巴尺寸時發生錯誤", e);
            return new float[]{0, 0};
        }
    }

    // 🔥 保留原來的 Shoelace 方法作為備用
    private float calculateLipArea(float[][] landmarks, int[] lipIndices) {
        try {
            List<float[]> lipPoints = new ArrayList<>();

            // 收集嘴唇關鍵點
            for (int index : lipIndices) {
                if (index < landmarks.length) {
                    lipPoints.add(new float[]{landmarks[index][0], landmarks[index][1]});
                }
            }

            if (lipPoints.size() < 3) {
                return 0; // 不足以形成多邊形
            }

            // Shoelace 公式計算多邊形面積
            float area = 0;
            int n = lipPoints.size();

            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                area += lipPoints.get(i)[0] * lipPoints.get(j)[1];
                area -= lipPoints.get(j)[0] * lipPoints.get(i)[1];
            }

            return Math.abs(area) / 2.0f;

        } catch (Exception e) {
            Log.e(TAG, "計算嘴唇面積時發生錯誤", e);
            return 0;
        }
    }

    // 🔥 新增：峰值分析方法
    public void saveToFile() {
        try {
            // 儲存到 Downloads 資料夾，使用者容易找到
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);

            FileWriter writer = new FileWriter(file);
            for (String line : dataLines) {
                writer.write(line + "\n");
            }
            writer.close();

            Log.d(TAG, "✅ 檔案儲存成功: " + file.getAbsolutePath());
            Log.d(TAG, "📊 總共記錄了 " + (dataLines.size() - 1) + " 筆數據");

            // 🔥 新增：檔案儲存完成後進行峰值分析
            performPeakAnalysis();

            // 使用 Handler 切換到主線程顯示 Toast
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "檔案已儲存至下載資料夾", Toast.LENGTH_LONG).show()
            );

        } catch (IOException e) {
            Log.e(TAG, "❌ 儲存檔案失敗", e);

            // 🔥 修復：在主線程中顯示錯誤 Toast
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "儲存失敗: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    // 🔥 新增：峰值分析方法
    private void performPeakAnalysis() {
        Log.d(TAG, "🎯 開始進行峰值分析...");

        // 在背景線程執行峰值分析
        new Thread(() -> {
            try {
                // 調用 CSV 峰值分析器
                CSVPeakAnalyzer.AnalysisResult result = CSVPeakAnalyzer.analyzePeaksFromFile(context, fileName);

                if (result.success) {
                    Log.d(TAG, "✅ 峰值分析完成!");
                    Log.d(TAG, String.format("📊 峰值統計 - 校正: %d, 維持: %d, 總計: %d",
                            result.calibratingPeaks, result.maintainingPeaks, result.totalPeaks));

                    // 格式化結果並在主線程顯示
                    String displayText = CSVPeakAnalyzer.formatResultForDisplay(result);

                    // 切換到主線程顯示結果
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        // 顯示詳細的峰值分析結果
                        Toast.makeText(context, displayText, Toast.LENGTH_LONG).show();

                        // 如果需要，也可以簡化版本的 Toast
                        // String simpleMessage = String.format("🎯 峰值分析完成!\n總峰值數: %d 個", result.totalPeaks);
                        // Toast.makeText(context, simpleMessage, Toast.LENGTH_SHORT).show();
                    });

                } else {
                    Log.e(TAG, "❌ 峰值分析失敗: " + result.errorMessage);

                    // 在主線程顯示錯誤訊息
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, "峰值分析失敗: " + result.errorMessage, Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "峰值分析過程發生錯誤", e);

                // 在主線程顯示錯誤訊息
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "峰值分析錯誤: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

        }).start();
    }

    public void clearData() {
        dataLines.clear();
        initializeCSV();
        Log.d(TAG, "清空數據");
    }

    public int getDataCount() {
        return Math.max(0, dataLines.size() - 1); // 扣除標題行
    }

    // 🔥 新增：獲取檔案名稱（供外部使用）
    public String getFileName() {
        return fileName;
    }
}