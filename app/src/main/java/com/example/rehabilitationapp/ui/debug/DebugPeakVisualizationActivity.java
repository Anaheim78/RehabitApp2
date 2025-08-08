package com.example.rehabilitationapp.ui.debug;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.ui.analysis.CSVPeakAnalyzer;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * 🆕 NEW: 基於 BASELINE 的峰值視覺化頁面
 * 每個 CALIBRATING 段落建立獨立 BASELINE，對應的 MAINTAINING 段落使用該 BASELINE 計算峰值
 * ✨ 閔嘴唇時數據自動乘以 -1，視覺化呈現正數峰值
 */
public class DebugPeakVisualizationActivity extends AppCompatActivity {
    private static final String TAG = "NEWPeakViz";

    // UI 元件
    private LineChart newPeakChart;
    private TextView newInfoText;
    private Button newCloseButton;
    private Button newRefreshButton;
    private Button newExportButton;

    // 🎛️ 參數控制元件
    private SeekBar baselineMultiplierSlider;
    private SeekBar mergeDistanceSlider;
    private TextView baselineMultiplierValue;
    private TextView mergeDistanceValue;
    private Switch autoReanalyzeSwitch;

    // 數據變數
    private String csvFileName;
    private String trainingLabel;
    private int actualCount;
    private int targetCount;

    // 🎛️ 可調整的峰值檢測參數
    private double baselineMultiplier = 3.0;  // BASELINE 倍數係數（預設 3.0 倍標準差）
    private double mergeDistance = 2.0;       // 合併距離（預設 2.0 秒）

    // 原始數據（不會改變）
    private List<Double> allDataValues = new ArrayList<>();
    private List<Double> allTimePoints = new ArrayList<>();
    private List<String> allPhases = new ArrayList<>();
    private String targetColumn;

    // ✨ 數據轉換標記
    private boolean isLipClosingData = false;  // 是否為閔嘴唇數據

    // 🎯 BASELINE 相關數據
    private List<BaselineSegment> baselineSegments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug_peak_visualization_activity);

        Log.d(TAG, "🆕 NEW: 基於 BASELINE 的峰值視覺化頁面啟動");

        // 初始化 UI
        initViews();

        // 取得傳入數據
        getIntentData();

        // 設置滑桿監聽器
        setupSliders();

        // 設置按鈕事件
        setupButtons();

        // 載入原始數據
        loadOriginalData();
    }

    private void initViews() {
        newPeakChart = findViewById(R.id.new_peak_chart);
        newInfoText = findViewById(R.id.new_info_text);
        newCloseButton = findViewById(R.id.new_close_button);
        newRefreshButton = findViewById(R.id.new_refresh_button);
        newExportButton = findViewById(R.id.new_export_button);

        // 🎛️ 參數控制元件
        baselineMultiplierSlider = findViewById(R.id.baseline_multiplier_slider);
        mergeDistanceSlider = findViewById(R.id.merge_distance_slider);
        baselineMultiplierValue = findViewById(R.id.baseline_multiplier_value);
        mergeDistanceValue = findViewById(R.id.merge_distance_value);
        autoReanalyzeSwitch = findViewById(R.id.auto_reanalyze_switch);

        // 設置圖表基本樣式
        setupChart();

        Log.d(TAG, "🆕 NEW: UI 元件初始化完成");
    }

    private void getIntentData() {
        csvFileName = getIntent().getStringExtra("csv_file_name");
        trainingLabel = getIntent().getStringExtra("training_label");
        actualCount = getIntent().getIntExtra("actual_count", 0);
        targetCount = getIntent().getIntExtra("target_count", 4);

        // ✨ 判斷是否為閔嘴唇訓練
        if (trainingLabel != null && trainingLabel.contains("抿嘴")) {
            isLipClosingData = true;
            Log.d(TAG, "✨ 檢測到閔嘴唇訓練，將自動轉換數據為正數");
        }

        Log.d(TAG, String.format("🆕 NEW: 接收數據 - CSV: %s, 標籤: %s, 實際: %d, 目標: %d, 閔嘴唇: %b",
                csvFileName, trainingLabel, actualCount, targetCount, isLipClosingData));
    }

    private void setupSliders() {
        // 🎛️ BASELINE 倍數滑桿 (1.0 - 5.0)
        baselineMultiplierSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    baselineMultiplier = 1.0 + (progress / 100.0) * 4.0; // 1.0 to 5.0
                    baselineMultiplierValue.setText(String.format("%.1f", baselineMultiplier));
                    Log.d(TAG, "🎛️ BASELINE 倍數調整為: " + baselineMultiplier);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (autoReanalyzeSwitch.isChecked()) {
                    reanalyzeWithCurrentParams();
                }
            }
        });

        // 🎛️ 合併距離滑桿 (0.5 - 5.0 秒)
        mergeDistanceSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mergeDistance = 0.5 + (progress / 100.0) * 4.5; // 0.5 to 5.0
                    mergeDistanceValue.setText(String.format("%.1f", mergeDistance));
                    Log.d(TAG, "🎛️ 合併距離調整為: " + mergeDistance);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (autoReanalyzeSwitch.isChecked()) {
                    reanalyzeWithCurrentParams();
                }
            }
        });

        // 設置初始值
        updateSliderValues();
    }

    private void updateSliderValues() {
        int baselineProgress = (int) ((baselineMultiplier - 1.0) / 4.0 * 100);
        int mergeProgress = (int) ((mergeDistance - 0.5) / 4.5 * 100);

        baselineMultiplierSlider.setProgress(baselineProgress);
        mergeDistanceSlider.setProgress(mergeProgress);

        baselineMultiplierValue.setText(String.format("%.1f", baselineMultiplier));
        mergeDistanceValue.setText(String.format("%.1f", mergeDistance));
    }

    private void setupButtons() {
        // 關閉按鈕
        newCloseButton.setOnClickListener(v -> {
            Log.d(TAG, "🆕 NEW: 關閉視覺化頁面");
            finish();
        });

        // 重新分析按鈕
        newRefreshButton.setOnClickListener(v -> {
            Log.d(TAG, "🆕 NEW: 手動重新分析數據");
            reanalyzeWithCurrentParams();
        });

        // 匯出按鈕
        newExportButton.setOnClickListener(v -> {
            Log.d(TAG, "🆕 NEW: 匯出數據");
            exportAnalysisData();
        });
    }

    private void setupChart() {
        // 基本設置
        newPeakChart.getDescription().setEnabled(false);
        newPeakChart.setTouchEnabled(true);
        newPeakChart.setDragEnabled(true);
        newPeakChart.setScaleEnabled(true);
        newPeakChart.setPinchZoom(true);

        // X軸設置
        XAxis xAxis = newPeakChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.GRAY);

        // Y軸設置
        YAxis leftAxis = newPeakChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setTextColor(Color.GRAY);

        YAxis rightAxis = newPeakChart.getAxisRight();
        rightAxis.setEnabled(false);

        Log.d(TAG, "🆕 NEW: 圖表設置完成");
    }

    private void loadOriginalData() {
        if (csvFileName == null || csvFileName.isEmpty()) {
            showError("CSV 檔案名稱為空");
            return;
        }

        newInfoText.setText("🔄 正在載入原始數據...");

        new Thread(() -> {
            try {
                // 🔥 讀取原始數據
                CSVPeakAnalyzer.DEBUGEnhancedAnalysisResult result =
                        CSVPeakAnalyzer.DEBUGPeakAnalyzeWithDetailedInfo(this, csvFileName);

                if (result.success) {
                    // ✨ 轉換數據（如果是閔嘴唇訓練）
                    allDataValues = new ArrayList<>();
                    for (Double value : result.allDataValues) {
                        if (isLipClosingData) {
                            allDataValues.add(value * -1.0);  // 閔嘴唇數據乘以 -1
                        } else {
                            allDataValues.add(value);
                        }
                    }

                    allTimePoints = new ArrayList<>(result.allTimePoints);
                    allPhases = new ArrayList<>(result.allPhases);
                    targetColumn = result.targetColumn;

                    Log.d(TAG, String.format("🆕 NEW: 原始數據載入成功，數據點: %d, 閔嘴唇轉換: %b",
                            allDataValues.size(), isLipClosingData));

                    // 🎯 分析 BASELINE 段落
                    analyzeBaselineSegments();

                    runOnUiThread(() -> {
                        updateSliderValues();
                        reanalyzeWithCurrentParams();
                    });
                } else {
                    Log.e(TAG, "🆕 NEW: 載入失敗 - " + result.errorMessage);
                    runOnUiThread(() -> showError("載入失敗: " + result.errorMessage));
                }

            } catch (Exception e) {
                Log.e(TAG, "🆕 NEW: 載入原始數據時發生錯誤", e);
                runOnUiThread(() -> showError("載入錯誤: " + e.getMessage()));
            }
        }).start();
    }

    // 🎯 分析 BASELINE 段落
    private void analyzeBaselineSegments() {
        baselineSegments.clear();

        int currentCalibStart = -1;
        int currentMaintainStart = -1;
        BaselineSegment currentSegment = null;

        for (int i = 0; i < allPhases.size(); i++) {
            String phase = allPhases.get(i);

            if ("CALIBRATING".equals(phase)) {
                // 進入校正階段
                if (currentCalibStart == -1) {
                    currentCalibStart = i;
                    if (currentSegment != null && currentMaintainStart != -1) {
                        // 結束前一個 maintain 段落
                        currentSegment.maintainEndIndex = i - 1;
                        baselineSegments.add(currentSegment);
                        currentSegment = null;
                        currentMaintainStart = -1;
                    }
                }
            } else if ("MAINTAINING".equals(phase)) {
                // 進入維持階段
                if (currentCalibStart != -1) {
                    // 結束校正階段，開始新的段落
                    currentSegment = new BaselineSegment();
                    currentSegment.calibStartIndex = currentCalibStart;
                    currentSegment.calibEndIndex = i - 1;
                    currentSegment.maintainStartIndex = i;
                    currentMaintainStart = i;
                    currentCalibStart = -1;

                    // 計算 BASELINE 統計
                    calculateBaselineStats(currentSegment);
                }
            }
        }

        // 處理最後一個段落
        if (currentSegment != null && currentMaintainStart != -1) {
            currentSegment.maintainEndIndex = allPhases.size() - 1;
            baselineSegments.add(currentSegment);
        }

        Log.d(TAG, "🎯 找到 " + baselineSegments.size() + " 個 BASELINE 段落");
    }

    // 🎯 計算 BASELINE 統計數據（已經是轉換後的數據）
    private void calculateBaselineStats(BaselineSegment segment) {
        List<Double> calibData = new ArrayList<>();
        for (int i = segment.calibStartIndex; i <= segment.calibEndIndex; i++) {
            calibData.add(allDataValues.get(i));  // 這裡已經是轉換後的數據
        }

        if (!calibData.isEmpty()) {
            // 計算平均值
            segment.average = calibData.stream().mapToDouble(d -> d).average().orElse(0.0);

            // 計算標準差
            double variance = calibData.stream()
                    .mapToDouble(v -> Math.pow(v - segment.average, 2))
                    .average().orElse(0.0);
            segment.standardDeviation = Math.sqrt(variance);

            Log.d(TAG, String.format("🎯 BASELINE: 平均=%.3f, 標準差=%.3f (閔嘴唇轉換: %b)",
                    segment.average, segment.standardDeviation, isLipClosingData));
        }
    }

    private void reanalyzeWithCurrentParams() {
        if (allDataValues.isEmpty() || baselineSegments.isEmpty()) {
            showError("沒有數據或 BASELINE 段落");
            return;
        }

        newInfoText.setText("🔄 正在重新分析 (基於 BASELINE)...");

        new Thread(() -> {
            try {
                List<PeakPoint> allPeaks = new ArrayList<>();

                // 🎯 對每個 BASELINE 段落進行獨立分析
                for (BaselineSegment segment : baselineSegments) {
                    if (segment.maintainStartIndex <= segment.maintainEndIndex) {
                        double threshold = segment.average + baselineMultiplier * segment.standardDeviation;

                        List<PeakPoint> segmentPeaks = detectPeaksInSegment(segment, threshold);
                        allPeaks.addAll(segmentPeaks);

                        Log.d(TAG, String.format("🎯 段落峰值: 閾值=%.3f, 峰值數=%d",
                                threshold, segmentPeaks.size()));
                    }
                }

                // 重分布峰值（在各自段落內）
                List<PeakPoint> redistributedPeaks = redistributePeaks(allPeaks);

                Log.d(TAG, String.format("🆕 重新分析完成 - 總峰值: %d, 重分布峰值: %d",
                        allPeaks.size(), redistributedPeaks.size()));

                runOnUiThread(() -> {
                    updateInfoDisplay(allPeaks, redistributedPeaks);
                    updateChart(allPeaks, redistributedPeaks);
                });

            } catch (Exception e) {
                Log.e(TAG, "🆕 NEW: 重新分析時發生錯誤", e);
                runOnUiThread(() -> showError("重新分析錯誤: " + e.getMessage()));
            }
        }).start();
    }

    // 🎯 在單一段落內檢測峰值（數據已經轉換）
    private List<PeakPoint> detectPeaksInSegment(BaselineSegment segment, double threshold) {
        List<PeakPoint> peaks = new ArrayList<>();

        for (int i = segment.maintainStartIndex + 1; i < segment.maintainEndIndex; i++) {
            if (i - 1 < 0 || i + 1 >= allDataValues.size()) continue;

            double prev = allDataValues.get(i - 1);
            double current = allDataValues.get(i);
            double next = allDataValues.get(i + 1);

            // 檢查是否為局部最大值且超過閾值
            if (current > prev && current > next && current > threshold) {
                PeakPoint peak = new PeakPoint(
                        allTimePoints.get(i),
                        current,
                        allPhases.get(i),
                        i,
                        segment
                );
                peaks.add(peak);
            }
        }

        return peaks;
    }

    private List<PeakPoint> redistributePeaks(List<PeakPoint> originalPeaks) {
        List<PeakPoint> result = new ArrayList<>();
        List<PeakPoint> remaining = new ArrayList<>(originalPeaks);

        while (!remaining.isEmpty()) {
            PeakPoint currentPeak = remaining.remove(0);
            List<PeakPoint> closePeaks = new ArrayList<>();
            closePeaks.add(currentPeak);

            // 找出時間相近且在同一段落的峰值
            remaining.removeIf(peak -> {
                if (peak.baselineSegment == currentPeak.baselineSegment &&
                        Math.abs(peak.time - currentPeak.time) <= mergeDistance) {
                    closePeaks.add(peak);
                    return true;
                }
                return false;
            });

            // 選擇數值最高的作為代表
            PeakPoint representativePeak = closePeaks.stream()
                    .max((p1, p2) -> Double.compare(p1.value, p2.value))
                    .orElse(currentPeak);

            result.add(representativePeak);
        }

        return result;
    }

    private void updateInfoDisplay(List<PeakPoint> originalPeaks, List<PeakPoint> redistributedPeaks) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("📁 檔案: %s\n", csvFileName));
        info.append(String.format("🏷️ 訓練: %s%s\n", trainingLabel, isLipClosingData ? " (正數轉換)" : ""));
        info.append(String.format("📊 數據點: %d 個\n", allDataValues.size()));
        info.append("━━━━━━━━━━━━━━━━━━━━\n");

        info.append(String.format("🎯 BASELINE 段落: %d 個\n", baselineSegments.size()));
        info.append(String.format("🎛️ BASELINE 倍數: %.1f 倍標準差\n", baselineMultiplier));
        info.append(String.format("🔄 合併距離: %.1f 秒\n", mergeDistance));
        if (isLipClosingData) {
            info.append("✨ 閔嘴唇數據已轉換為正數顯示\n");
        }
        info.append("━━━━━━━━━━━━━━━━━━━━\n");

        // 詳細段落資訊
        for (int i = 0; i < baselineSegments.size(); i++) {
            BaselineSegment segment = baselineSegments.get(i);
            double threshold = segment.average + baselineMultiplier * segment.standardDeviation;

            long segmentPeaks = redistributedPeaks.stream()
                    .filter(p -> p.baselineSegment == segment)
                    .count();

            info.append(String.format("📊 段落 %d:\n", i + 1));
            info.append(String.format("  🟡 校正: %.1f~%.1f 秒\n",
                    allTimePoints.get(segment.calibStartIndex),
                    allTimePoints.get(segment.calibEndIndex)));
            info.append(String.format("  🟢 維持: %.1f~%.1f 秒\n",
                    allTimePoints.get(segment.maintainStartIndex),
                    allTimePoints.get(segment.maintainEndIndex)));
            info.append(String.format("  📈 平均: %.3f, 標準差: %.3f\n",
                    segment.average, segment.standardDeviation));
            info.append(String.format("  🎯 閾值: %.3f, 峰值: %d 個\n", threshold, segmentPeaks));
            info.append("\n");
        }

        info.append("━━━━━━━━━━━━━━━━━━━━\n");
        info.append(String.format("🔍 總峰值: %d 個\n", originalPeaks.size()));
        info.append(String.format("🎯 重分布峰值: %d 個\n", redistributedPeaks.size()));

        newInfoText.setText(info.toString());
    }

    private void updateChart(List<PeakPoint> originalPeaks, List<PeakPoint> redistributedPeaks) {
        // 準備數據集
        List<Entry> dataEntries = new ArrayList<>();
        List<Entry> originalPeakEntries = new ArrayList<>();
        List<Entry> redistributedPeakEntries = new ArrayList<>();

        // 原始數據（已轉換）
        for (int i = 0; i < allDataValues.size(); i++) {
            dataEntries.add(new Entry(allTimePoints.get(i).floatValue(), allDataValues.get(i).floatValue()));
        }

        // 峰值點
        for (PeakPoint peak : originalPeaks) {
            originalPeakEntries.add(new Entry((float)peak.time, (float)peak.value));
        }
        for (PeakPoint peak : redistributedPeaks) {
            redistributedPeakEntries.add(new Entry((float)peak.time, (float)peak.value));
        }

        // 🎨 創建數據集
        LineDataSet dataSet = new LineDataSet(dataEntries, isLipClosingData ? "原始數據 (正數轉換)" : "原始數據");
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);

        LineDataSet originalPeakSet = new LineDataSet(originalPeakEntries, "原始峰值");
        originalPeakSet.setColor(Color.TRANSPARENT);
        originalPeakSet.setCircleColor(Color.rgb(255, 165, 0));
        originalPeakSet.setCircleRadius(6f);
        originalPeakSet.setDrawCircles(true);
        originalPeakSet.setDrawValues(false);

        LineDataSet redistributedPeakSet = new LineDataSet(redistributedPeakEntries, "最終峰值");
        redistributedPeakSet.setColor(Color.TRANSPARENT);
        redistributedPeakSet.setCircleColor(Color.RED);
        redistributedPeakSet.setCircleRadius(8f);
        redistributedPeakSet.setDrawCircles(true);
        redistributedPeakSet.setDrawValues(true);
        redistributedPeakSet.setValueTextColor(Color.RED);
        redistributedPeakSet.setValueTextSize(10f);

        // 組合數據
        LineData lineData = new LineData();
        lineData.addDataSet(dataSet);
        if (!originalPeakEntries.isEmpty()) {
            lineData.addDataSet(originalPeakSet);
        }
        if (!redistributedPeakEntries.isEmpty()) {
            lineData.addDataSet(redistributedPeakSet);
        }

        // 🎨 添加 BASELINE 基準線（已轉換數據的基準線）
        YAxis leftAxis = newPeakChart.getAxisLeft();
        leftAxis.removeAllLimitLines();

        for (int i = 0; i < baselineSegments.size(); i++) {
            BaselineSegment segment = baselineSegments.get(i);

            // 基準線（平均值）
            LimitLine baselineLine = new LimitLine((float)segment.average, "BASELINE " + (i + 1));
            baselineLine.setLineColor(Color.GREEN);
            baselineLine.setLineWidth(2f);
            baselineLine.enableDashedLine(10f, 5f, 0f);
            leftAxis.addLimitLine(baselineLine);

            // 閾值線
            double threshold = segment.average + baselineMultiplier * segment.standardDeviation;
            LimitLine thresholdLine = new LimitLine((float)threshold, "閾值 " + (i + 1));
            thresholdLine.setLineColor(Color.RED);
            thresholdLine.setLineWidth(1.5f);
            thresholdLine.enableDashedLine(5f, 3f, 0f);
            leftAxis.addLimitLine(thresholdLine);
        }

        // 更新圖表
        newPeakChart.setData(lineData);
        newPeakChart.invalidate();

        String toastMessage = isLipClosingData ?
                "📈 圖表已更新（基於 BASELINE，閔嘴唇正數轉換）！" :
                "📈 圖表已更新（基於 BASELINE）！";
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
    }

    private void exportAnalysisData() {
        StringBuilder exportData = new StringBuilder();
        exportData.append("=== NEW 基於 BASELINE 的峰值分析報告 ===\n");
        exportData.append(String.format("BASELINE 倍數: %.1f 倍標準差\n", baselineMultiplier));
        exportData.append(String.format("合併距離: %.1f 秒\n", mergeDistance));
        if (isLipClosingData) {
            exportData.append("✨ 閔嘴唇數據已轉換為正數進行分析\n");
        }
        exportData.append("━━━━━━━━━━━━━━━━━━━━\n");
        exportData.append(newInfoText.getText());

        Log.d(TAG, "🆕 NEW: 匯出數據:\n" + exportData.toString());
        Toast.makeText(this, "📤 詳細數據已輸出到 Logcat", Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        newInfoText.setText("❌ 錯誤: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "🆕 NEW: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🆕 NEW: 基於 BASELINE 的峰值視覺化頁面銷毀");
    }

    // 🎯 BASELINE 段落類別
    private static class BaselineSegment {
        public int calibStartIndex;
        public int calibEndIndex;
        public int maintainStartIndex;
        public int maintainEndIndex;
        public double average;
        public double standardDeviation;
    }

    // 🎯 峰值點類別
    private static class PeakPoint {
        public double time;
        public double value;
        public String phase;
        public int originalIndex;
        public BaselineSegment baselineSegment;

        public PeakPoint(double time, double value, String phase, int originalIndex, BaselineSegment baselineSegment) {
            this.time = time;
            this.value = value;
            this.phase = phase;
            this.originalIndex = originalIndex;
            this.baselineSegment = baselineSegment;
        }
    }
}