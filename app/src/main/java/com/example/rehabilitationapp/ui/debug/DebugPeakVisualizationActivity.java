package com.example.rehabilitationapp.ui.debug;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * DebugPeakVisualizationActivity
 *
 * 舊流程（抿嘴/鼓臉…）：
 *   - 走你原本的 baseline / 峰值偵測與重分配，畫一條目標序列。
 *
 * 舌頭流程（自動偵測 CSV 含 x_norm,y_norm,tongue_detected）：
 *   - 讀取 time_seconds, state, tongue_detected, x_norm, y_norm
 *   - 忽略前 IGNORE_FIRST_SEC 秒
 *   - 缺值補植：EMA + leak（無偵測時往 0 衰減）
 *   - 可選基線抑制：fastEMA - slowEMA
 *   - 同圖畫兩條：紅= X(左右)，藍= Y(上下)；灰= raw 對照
 */
public class DebugPeakVisualizationActivity extends AppCompatActivity {
    private static final String TAG = "NEWPeakViz";

    // ====== 共用 UI ======
    private LineChart newPeakChart;
    private TextView newInfoText;
    private Button newCloseButton;
    private Button newRefreshButton;
    private Button newExportButton;
    private SeekBar baselineMultiplierSlider;
    private SeekBar mergeDistanceSlider;
    private TextView baselineMultiplierValue;
    private TextView mergeDistanceValue;
    private Switch autoReanalyzeSwitch;

    // 來自上一頁
    private String csvFileName;
    private String trainingLabel;
    private int actualCount;
    private int targetCount;

    // ====== 舊流程用（baseline/峰值） ======
    private double baselineMultiplier = 0.0;  // 閾值 = mean + k*std；允許負值
    private double mergeDistance = 3.5;       // 峰值合併秒數
    private List<Double> allDataValues = new ArrayList<>();
    private List<Double> allTimePoints = new ArrayList<>();
    private List<String> allPhases = new ArrayList<>();
    private String targetColumn;
    private boolean isLipClosingData = false; // 抿嘴 → 轉正
    private boolean isCheekPuff = false;      // 鼓臉/鼓頰
    private final List<BaselineSegment> baselineSegments = new ArrayList<>();

    // ====== 舌頭流程用 ======
    private boolean isTongueMode = false;
    private List<Double> tTongue = new ArrayList<>();
    private List<Integer> detTongue = new ArrayList<>();
    private List<Double> xRaw = new ArrayList<>();
    private List<Double> yRaw = new ArrayList<>();
    private List<Double> xFill = new ArrayList<>();
    private List<Double> yFill = new ArrayList<>();
    private List<Double> xFeat = new ArrayList<>();
    private List<Double> yFeat = new ArrayList<>();
    private double fsEst = 30.0;

    // 參數（可微調）
    private static final double IGNORE_FIRST_SEC = 5.0; // 舌頭與舊流程皆套用

    // 舌頭：缺口補值（EMA+leak）與雙 EMA 高通
    private static final boolean USE_HIGHPASS = true;
    private static final double SMOOTH_WIN_SEC = 0.15;   // EMA 等效視窗
    private static final double LEAK_PER_FRAME = 0.02;   // 缺值往 0 衰減比例

    private static final double FAST_WIN_SEC = 0.15;     // fast EMA 視窗
    private static final double SLOW_WIN_SEC = 1.20;     // slow EMA 視窗（~ fast 的 8 倍）

    // ====== 內部類別 ======
    private static class BaselineSegment {
        int calibStartIndex;
        int calibEndIndex;
        int maintainStartIndex;
        int maintainEndIndex;
        double average;
        double standardDeviation;
    }

    private static class PeakPoint {
        double time;
        double value;
        String phase;
        int originalIndex;
        BaselineSegment baselineSegment;

        PeakPoint(double time, double value, String phase, int originalIndex, BaselineSegment seg) {
            this.time = time; this.value = value; this.phase = phase; this.originalIndex = originalIndex; this.baselineSegment = seg;
        }
    }

    // ====== Activity 週期 ======
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug_peak_visualization_activity);

        initViews();
        getIntentData();
        setupSliders();
        setupButtons();

        loadDataAuto(); // 依 CSV 欄位自動選舌頭或舊流程
    }

    private void initViews() {
        newPeakChart = findViewById(R.id.new_peak_chart);
        newInfoText = findViewById(R.id.new_info_text);
        newCloseButton = findViewById(R.id.new_close_button);
        newRefreshButton = findViewById(R.id.new_refresh_button);
        newExportButton = findViewById(R.id.new_export_button);
        baselineMultiplierSlider = findViewById(R.id.baseline_multiplier_slider);
        mergeDistanceSlider = findViewById(R.id.merge_distance_slider);
        baselineMultiplierValue = findViewById(R.id.baseline_multiplier_value);
        mergeDistanceValue = findViewById(R.id.merge_distance_value);
        autoReanalyzeSwitch = findViewById(R.id.auto_reanalyze_switch);

        // Chart 基本
        newPeakChart.getDescription().setEnabled(false);
        newPeakChart.setTouchEnabled(true);
        newPeakChart.setDragEnabled(true);
        newPeakChart.setScaleEnabled(true);
        newPeakChart.setPinchZoom(true);

        XAxis xAxis = newPeakChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.GRAY);

        YAxis leftAxis = newPeakChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setTextColor(Color.GRAY);
        newPeakChart.getAxisRight().setEnabled(false);
    }

    private void getIntentData() {
        csvFileName = getIntent().getStringExtra("csv_file_name");
        trainingLabel = getIntent().getStringExtra("training_label");
        actualCount = getIntent().getIntExtra("actual_count", 0);
        targetCount = getIntent().getIntExtra("target_count", 4);

        isLipClosingData = trainingLabel != null && trainingLabel.contains("抿嘴");
        isCheekPuff = trainingLabel != null &&
                (trainingLabel.contains("鼓頰") || trainingLabel.contains("鼓臉") || trainingLabel.contains("鼓脸"));
        // 舌頭模式不靠 label，靠 CSV 欄位自動判斷
        Log.d(TAG, "label=" + trainingLabel + " 抿嘴=" + isLipClosingData + " 鼓臉=" + isCheekPuff);
    }

    private void setupSliders() {
        // baseline 倍數：-2.0 ~ 5.0
        baselineMultiplierSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                baselineMultiplier = -2.0 + (progress / 100.0) * 7.0;
                baselineMultiplierValue.setText(String.format("%.1f", baselineMultiplier));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { if (autoReanalyzeSwitch.isChecked()) reanalyzeWithCurrentParams(); }
        });

        // 峰併窗：0.5~5.0
        mergeDistanceSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                mergeDistance = 0.5 + (progress / 100.0) * 4.5;
                mergeDistanceValue.setText(String.format("%.1f", mergeDistance));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { if (autoReanalyzeSwitch.isChecked()) reanalyzeWithCurrentParams(); }
        });

        updateSliderValues();
    }

    private void updateSliderValues() {
        int baseProgress = (int) (((baselineMultiplier + 2.0) / 7.0) * 100.0);
        baseProgress = Math.max(0, Math.min(100, baseProgress));
        baselineMultiplierSlider.setProgress(baseProgress);

        int mergeProgress = (int) (((mergeDistance - 0.5) / 4.5) * 100.0);
        mergeProgress = Math.max(0, Math.min(100, mergeProgress));
        mergeDistanceSlider.setProgress(mergeProgress);

        baselineMultiplierValue.setText(String.format("%.1f", baselineMultiplier));
        mergeDistanceValue.setText(String.format("%.1f", mergeDistance));
    }

    private void setupButtons() {
        newCloseButton.setOnClickListener(v -> finish());
        newRefreshButton.setOnClickListener(v -> {
            if (isTongueMode) {
                // 舌頭模式：重新跑處理與畫圖
                processTongueSeriesAndPlot();
            } else {
                reanalyzeWithCurrentParams();
            }
        });
        newExportButton.setOnClickListener(v -> {
            Log.d(TAG, "匯出：\n" + newInfoText.getText());
            Toast.makeText(this, "📤 詳細數據已輸出到 Logcat", Toast.LENGTH_LONG).show();
        });
    }

    // ====== 自動載入（判斷是否舌頭模式） ======
    private void loadDataAuto() {
        if (csvFileName == null || csvFileName.isEmpty()) {
            showError("CSV 檔案名稱為空");
            return;
        }
        newInfoText.setText("🔄 載入數據中...");

        new Thread(() -> {
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(downloads, csvFileName);
                if (!f.exists()) throw new IllegalStateException("找不到檔案：" + f.getAbsolutePath());

                // 讀表頭判斷是否舌頭格式
                String header;
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    header = br.readLine();
                }
                if (header == null) throw new IllegalStateException("空檔案");

                String[] cols = header.split(",");
                boolean hasTongue = hasCol(cols, "time_seconds")
                        && hasCol(cols, "state")
                        && hasCol(cols, "tongue_detected")
                        && hasCol(cols, "x_norm")
                        && hasCol(cols, "y_norm");

                isTongueMode = hasTongue;

                if (isTongueMode) {
                    loadTongueSeriesFromCsv(f, cols);    // 填入 tTongue/det/xRaw/yRaw
                    processTongueSeriesAndPlot();        // 產出 xFill/yFill/xFeat/yFeat + 畫圖
                } else {
                    // 舊流程
                    if (isCheekPuff) {
                        loadCheekSeriesFromCsv(f, cols);  // 走鼓臉 magnitude
                    } else {
                        CSVPeakAnalyzer.DEBUGEnhancedAnalysisResult r =
                                CSVPeakAnalyzer.DEBUGPeakAnalyzeWithDetailedInfo(this, csvFileName);
                        if (!r.success) throw new RuntimeException(r.errorMessage);
                        allDataValues = new ArrayList<>();
                        for (Double v : r.allDataValues) {
                            allDataValues.add(isLipClosingData ? -v : v);
                        }
                        allTimePoints = new ArrayList<>(r.allTimePoints);
                        allPhases = new ArrayList<>(r.allPhases);
                        targetColumn = r.targetColumn;
                    }

                    analyzeBaselineSegments();
                    runOnUiThread(this::reanalyzeWithCurrentParams);
                }

            } catch (Exception e) {
                Log.e(TAG, "載入失敗", e);
                runOnUiThread(() -> showError("載入失敗: " + e.getMessage()));
            }
        }).start();
    }

    // ====== 舌頭：讀 CSV ======
    private void loadTongueSeriesFromCsv(File f, String[] headerCols) throws Exception {
        int ixTime = findCol(headerCols, "time_seconds");
        int ixState = findCol(headerCols, "state");
        int ixDet  = findCol(headerCols, "tongue_detected");
        int ixX    = findCol(headerCols, "x_norm");
        int ixY    = findCol(headerCols, "y_norm");
        if (ixTime < 0 || ixState < 0 || ixDet < 0 || ixX < 0 || ixY < 0) {
            throw new IllegalStateException("CSV 欄位缺少舌頭所需欄位");
        }

        List<Double> t = new ArrayList<>();
        List<Integer> det = new ArrayList<>();
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        List<String> phases = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",");
                double tt = parseDoubleSafe(v, ixTime, Double.NaN);
                if (Double.isNaN(tt)) continue;
                int dd = (int) parseDoubleSafe(v, ixDet, 0);
                double xx = parseDoubleSafe(v, ixX, Double.NaN);
                double yy = parseDoubleSafe(v, ixY, Double.NaN);
                String st = v[ixState];

                t.add(tt);
                det.add(dd);
                x.add(xx);
                y.add(yy);
                phases.add(st);
            }
        }

        // 忽略前 N 秒
        tTongue.clear(); detTongue.clear(); xRaw.clear(); yRaw.clear(); allPhases.clear();
        for (int i = 0; i < t.size(); i++) {
            if (t.get(i) >= IGNORE_FIRST_SEC) {
                tTongue.add(t.get(i));
                detTongue.add(det.get(i));
                xRaw.add(x.get(i));
                yRaw.add(y.get(i));
                allPhases.add(phases.get(i));
            }
        }

        // 估 fs
        fsEst = estimateFs(tTongue);
        Log.d(TAG, String.format("Tongue CSV loaded: N=%d, fs≈%.2f Hz", tTongue.size(), fsEst));
    }

    // 舊：鼓臉 magnitude
    private void loadCheekSeriesFromCsv(File f, String[] headerCols) throws Exception {
        int ixTime = findCol(headerCols, "time_seconds");
        int ixState = findCol(headerCols, "state");
        int ixLx = findCol(headerCols, "LI_X");
        int ixLy = findCol(headerCols, "LI_Y");
        int ixRx = findCol(headerCols, "RI_X");
        int ixRy = findCol(headerCols, "RI_Y");
        if (ixTime < 0 || ixState < 0 || ixLx < 0 || ixLy < 0 || ixRx < 0 || ixRy < 0) {
            throw new IllegalStateException("CSV 欄位缺少 LI_X/LI_Y/RI_X/RI_Y 或 time_seconds/state");
        }

        List<Double> times = new ArrayList<>();
        List<String> phases = new ArrayList<>();
        List<Double> mags = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] v = line.split(",");
                double tt = parseDoubleSafe(v, ixTime, Double.NaN);
                String st = v[ixState];
                double liX = parseDoubleSafe(v, ixLx, 0);
                double liY = parseDoubleSafe(v, ixLy, 0);
                double riX = parseDoubleSafe(v, ixRx, 0);
                double riY = parseDoubleSafe(v, ixRy, 0);
                double mixedX = liX - riX;
                double mixedY = liY + riY;
                double mag = Math.hypot(mixedX, mixedY);
                if (!Double.isNaN(tt)) {
                    times.add(tt);
                    phases.add(st);
                    mags.add(mag);
                }
            }
        }

        // 忽略前 N 秒
        allTimePoints.clear(); allPhases.clear(); allDataValues.clear();
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) >= IGNORE_FIRST_SEC) {
                allTimePoints.add(times.get(i));
                allPhases.add(phases.get(i));
                allDataValues.add(mags.get(i));
            }
        }
        targetColumn = "Cheek Magnitude (|[LI_X-RI_X, LI_Y+RI_Y]|)";
    }

    // ====== 舌頭：處理 + 畫圖 ======
    private void processTongueSeriesAndPlot() {
        if (!isTongueMode || tTongue.isEmpty()) {
            showError("沒有舌頭資料");
            return;
        }

        // 缺值標記：det=0 或 x/y 是 NaN 都當作缺值
        boolean[] valid = new boolean[tTongue.size()];
        for (int i = 0; i < valid.length; i++) {
            boolean ok = detTongue.get(i) != 0
                    && !xRaw.get(i).isNaN()
                    && !yRaw.get(i).isNaN();
            valid[i] = ok;
        }

        // EMA + leak 補值
        double[] xFillArr = emaLeaky(toArray(xRaw), valid, fsEst, SMOOTH_WIN_SEC, LEAK_PER_FRAME);
        double[] yFillArr = emaLeaky(toArray(yRaw), valid, fsEst, SMOOTH_WIN_SEC, LEAK_PER_FRAME);

        // 雙 EMA 高通
        double[] xFeatArr, yFeatArr;
        if (USE_HIGHPASS) {
            xFeatArr = sub(emaPlain(xFillArr, fsEst, FAST_WIN_SEC),
                    emaPlain(xFillArr, fsEst, SLOW_WIN_SEC));
            yFeatArr = sub(emaPlain(yFillArr, fsEst, FAST_WIN_SEC),
                    emaPlain(yFillArr, fsEst, SLOW_WIN_SEC));
        } else {
            xFeatArr = xFillArr;
            yFeatArr = yFillArr;
        }

        // 輸出到 List 供畫圖
        xFill.clear(); yFill.clear(); xFeat.clear(); yFeat.clear();
        for (double v : xFillArr) xFill.add(v);
        for (double v : yFillArr) yFill.add(v);
        for (double v : xFeatArr) xFeat.add(v);
        for (double v : yFeatArr) yFeat.add(v);

        // 畫圖
        runOnUiThread(() -> {
            drawTongueChart();
            // Info
            String info = String.format(
                    "📁 檔案: %s\n🏷️ 訓練: %s (舌頭)\n" +
                            "⏱ fs≈%.2f Hz, 忽略前 %.1fs\n" +
                            "EMA+leak: win=%.2fs, leak=%.2f/frm\n" +
                            "High-pass(fast-slow): %s (fast=%.2fs, slow=%.2fs)\n" +
                            "N=%d",
                    csvFileName, trainingLabel,
                    fsEst, IGNORE_FIRST_SEC,
                    SMOOTH_WIN_SEC, LEAK_PER_FRAME,
                    USE_HIGHPASS ? "ON":"OFF", FAST_WIN_SEC, SLOW_WIN_SEC,
                    tTongue.size()
            );
            newInfoText.setText(info);

            // 舊流程控制在舌頭模式下不需要 → 隱藏
            baselineMultiplierSlider.setVisibility(View.GONE);
            mergeDistanceSlider.setVisibility(View.GONE);
            baselineMultiplierValue.setVisibility(View.GONE);
            mergeDistanceValue.setVisibility(View.GONE);
            autoReanalyzeSwitch.setVisibility(View.GONE);
        });
    }

    private void drawTongueChart() {
        List<Entry> xRawE = new ArrayList<>();
        List<Entry> yRawE = new ArrayList<>();
        List<Entry> xFeatE = new ArrayList<>();
        List<Entry> yFeatE = new ArrayList<>();

        for (int i = 0; i < tTongue.size(); i++) {
            float tx = tTongue.get(i).floatValue();
            double xr = xRaw.get(i).isNaN() ? 0.0 : xRaw.get(i);
            double yr = yRaw.get(i).isNaN() ? 0.0 : yRaw.get(i);
            xRawE.add(new Entry(tx, (float) xr));
            yRawE.add(new Entry(tx, (float) yr));
            xFeatE.add(new Entry(tx, xFeat.get(i).floatValue()));
            yFeatE.add(new Entry(tx, yFeat.get(i).floatValue()));
        }

        LineDataSet xRawSet = new LineDataSet(xRawE, "x_norm (raw)");
        xRawSet.setColor(Color.DKGRAY);
        xRawSet.setLineWidth(1f);
        xRawSet.setDrawCircles(false);
        xRawSet.setDrawValues(false);


        LineDataSet yRawSet = new LineDataSet(yRawE, "y_norm (raw)");
        yRawSet.setColor(Color.GRAY);
        yRawSet.setLineWidth(1f);
        yRawSet.setDrawCircles(false);
        yRawSet.setDrawValues(false);


        LineDataSet xFeatSet = new LineDataSet(xFeatE, "X 活動 (左右)");
        xFeatSet.setColor(Color.RED);
        xFeatSet.setLineWidth(2.0f);
        xFeatSet.setDrawCircles(false);
        xFeatSet.setDrawValues(false);

        LineDataSet yFeatSet = new LineDataSet(yFeatE, "Y 活動 (上下)");
        yFeatSet.setColor(Color.BLUE);
        yFeatSet.setLineWidth(2.0f);
        yFeatSet.setDrawCircles(false);
        yFeatSet.setDrawValues(false);

        LineData lineData = new LineData();
        lineData.addDataSet(xRawSet);
        lineData.addDataSet(yRawSet);
        lineData.addDataSet(xFeatSet);
        lineData.addDataSet(yFeatSet);

        YAxis leftAxis = newPeakChart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // 舌頭模式不畫 baseline/閾值線

        newPeakChart.setData(lineData);
        newPeakChart.invalidate();

        Toast.makeText(this, "📈 舌頭波形已更新（紅=X左右、藍=Y上下）", Toast.LENGTH_SHORT).show();
    }

    // ====== 舊流程：baseline/峰值 ======
    private void analyzeBaselineSegments() {
        baselineSegments.clear();
        int currentCalibStart = -1;
        int currentMaintainStart = -1;
        BaselineSegment current = null;

        for (int i = 0; i < allPhases.size(); i++) {
            String phase = allPhases.get(i);
            if ("CALIBRATING".equals(phase)) {
                if (currentCalibStart == -1) {
                    currentCalibStart = i;
                    if (current != null && currentMaintainStart != -1) {
                        current.maintainEndIndex = i - 1;
                        baselineSegments.add(current);
                        current = null;
                        currentMaintainStart = -1;
                    }
                }
            } else if ("MAINTAINING".equals(phase)) {
                if (currentCalibStart != -1) {
                    current = new BaselineSegment();
                    current.calibStartIndex = currentCalibStart;
                    current.calibEndIndex = i - 1;
                    current.maintainStartIndex = i;
                    currentMaintainStart = i;
                    currentCalibStart = -1;

                    calculateBaselineStats(current);
                }
            }
        }
        if (current != null && currentMaintainStart != -1) {
            current.maintainEndIndex = allPhases.size() - 1;
            baselineSegments.add(current);
        }
        Log.d(TAG, "Baseline 段數: " + baselineSegments.size());
    }

    private void calculateBaselineStats(BaselineSegment seg) {
        List<Double> calib = new ArrayList<>();
        for (int i = seg.calibStartIndex; i <= seg.calibEndIndex; i++) {
            calib.add(allDataValues.get(i));
        }
        if (calib.isEmpty()) { seg.average = 0; seg.standardDeviation = 0; return; }

        double mean = 0;
        for (double v : calib) mean += v;
        mean /= calib.size();
        double var = 0;
        for (double v : calib) var += (v - mean) * (v - mean);
        var /= calib.size();
        seg.average = mean;
        seg.standardDeviation = Math.sqrt(var);
    }

    private void reanalyzeWithCurrentParams() {
        if (isTongueMode) {
            processTongueSeriesAndPlot();
            return;
        }
        if (allDataValues.isEmpty() || baselineSegments.isEmpty()) {
            showError("沒有數據或 BASELINE 段落");
            return;
        }
        newInfoText.setText("🔄 重新分析中...");

        new Thread(() -> {
            try {
                List<PeakPoint> allPeaks = new ArrayList<>();
                for (BaselineSegment seg : baselineSegments) {
                    if (seg.maintainStartIndex <= seg.maintainEndIndex) {
                        double th = seg.average + baselineMultiplier * seg.standardDeviation;
                        allPeaks.addAll(detectPeaksInSegment(seg, th));
                    }
                }
                List<PeakPoint> redistributed = redistributePeaks(allPeaks);

                runOnUiThread(() -> {
                    updateInfoDisplay(allPeaks, redistributed);
                    updateChart(allPeaks, redistributed);
                });
            } catch (Exception e) {
                Log.e(TAG, "重新分析錯誤", e);
                runOnUiThread(() -> showError("重新分析錯誤: " + e.getMessage()));
            }
        }).start();
    }

    private List<PeakPoint> detectPeaksInSegment(BaselineSegment seg, double threshold) {
        List<PeakPoint> peaks = new ArrayList<>();
        for (int i = seg.maintainStartIndex + 1; i < seg.maintainEndIndex; i++) {
            if (i - 1 < 0 || i + 1 >= allDataValues.size()) continue;
            double prev = allDataValues.get(i - 1);
            double cur = allDataValues.get(i);
            double next = allDataValues.get(i + 1);
            if (cur > prev && cur > next && cur > threshold) {
                peaks.add(new PeakPoint(allTimePoints.get(i), cur, allPhases.get(i), i, seg));
            }
        }
        return peaks;
    }

    private List<PeakPoint> redistributePeaks(List<PeakPoint> original) {
        List<PeakPoint> result = new ArrayList<>();
        List<PeakPoint> remain = new ArrayList<>(original);
        while (!remain.isEmpty()) {
            PeakPoint p = remain.remove(0);
            List<PeakPoint> group = new ArrayList<>();
            group.add(p);
            remain.removeIf(q -> {
                boolean sameSeg = q.baselineSegment == p.baselineSegment;
                boolean close = Math.abs(q.time - p.time) <= mergeDistance;
                if (sameSeg && close) { group.add(q); return true; }
                return false;
            });
            PeakPoint rep = group.get(0);
            for (PeakPoint g : group) if (g.value > rep.value) rep = g;
            result.add(rep);
        }
        return result;
    }

    private void updateInfoDisplay(List<PeakPoint> original, List<PeakPoint> redistributed) {
        StringBuilder sb = new StringBuilder();
        sb.append("📁 檔案: ").append(csvFileName).append("\n");
        sb.append("🏷️ 訓練: ").append(trainingLabel);
        if (isLipClosingData) sb.append(" (正數轉換)");
        if (isCheekPuff) sb.append(" [鼓臉 Magnitude]");
        sb.append("\n");
        sb.append("📊 數據點: ").append(allDataValues.size()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🎯 BASELINE 段落: ").append(baselineSegments.size()).append("\n");
        sb.append(String.format("🎛️ BASELINE 倍數: %.1f\n", baselineMultiplier));
        sb.append(String.format("🔄 合併距離: %.1f 秒\n", mergeDistance));
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");

        for (int i = 0; i < baselineSegments.size(); i++) {
            BaselineSegment seg = baselineSegments.get(i);
            double th = seg.average + baselineMultiplier * seg.standardDeviation;
            long cnt = redistributed.stream().filter(p -> p.baselineSegment == seg).count();
            sb.append(String.format("📊 段落 %d:\n", i + 1));
            sb.append(String.format("  🟡 校正: %.1f~%.1f s\n",
                    allTimePoints.get(seg.calibStartIndex), allTimePoints.get(seg.calibEndIndex)));
            sb.append(String.format("  🟢 維持: %.1f~%.1f s\n",
                    allTimePoints.get(seg.maintainStartIndex), allTimePoints.get(seg.maintainEndIndex)));
            sb.append(String.format("  📈 平均: %.4f, 標準差: %.4f, 閾值: %.4f, 峰值: %d\n",
                    seg.average, seg.standardDeviation, th, cnt));
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🔍 原始峰值: ").append(original.size()).append("\n");
        sb.append("🎯 重分布峰值: ").append(redistributed.size()).append("\n");

        newInfoText.setText(sb.toString());
    }

    private void updateChart(List<PeakPoint> original, List<PeakPoint> redistributed) {
        List<Entry> dataEntries = new ArrayList<>();
        List<Entry> origPeakEntries = new ArrayList<>();
        List<Entry> redistPeakEntries = new ArrayList<>();

        for (int i = 0; i < allDataValues.size(); i++) {
            dataEntries.add(new Entry(allTimePoints.get(i).floatValue(), allDataValues.get(i).floatValue()));
        }
        for (PeakPoint p : original) origPeakEntries.add(new Entry((float) p.time, (float) p.value));
        for (PeakPoint p : redistributed) redistPeakEntries.add(new Entry((float) p.time, (float) p.value));

        LineDataSet dataSet = new LineDataSet(
                dataEntries,
                isCheekPuff ? "鼓臉 Magnitude (|[LI_X-RI_X, LI_Y+RI_Y]|)"
                        : (isLipClosingData ? "原始數據 (正數轉換)" : "原始數據")
        );
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);

        LineDataSet origSet = new LineDataSet(origPeakEntries, "原始峰值");
        origSet.setColor(Color.TRANSPARENT);
        origSet.setCircleColor(Color.rgb(255, 165, 0));
        origSet.setCircleRadius(6f);
        origSet.setDrawCircles(true);
        origSet.setDrawValues(false);

        LineDataSet repSet = new LineDataSet(redistPeakEntries, "最終峰值");
        repSet.setColor(Color.TRANSPARENT);
        repSet.setCircleColor(Color.RED);
        repSet.setCircleRadius(8f);
        repSet.setDrawCircles(true);
        repSet.setDrawValues(true);
        repSet.setValueTextColor(Color.RED);
        repSet.setValueTextSize(10f);

        LineData lineData = new LineData();
        lineData.addDataSet(dataSet);
        if (!origPeakEntries.isEmpty()) lineData.addDataSet(origSet);
        if (!redistPeakEntries.isEmpty()) lineData.addDataSet(repSet);

        // baseline / threshold 線
        YAxis leftAxis = newPeakChart.getAxisLeft();
        leftAxis.removeAllLimitLines();
        for (int i = 0; i < baselineSegments.size(); i++) {
            BaselineSegment seg = baselineSegments.get(i);
            LimitLine base = new LimitLine((float) seg.average, "BASELINE " + (i + 1));
            base.setLineColor(Color.GREEN);
            base.setLineWidth(2f);
            base.enableDashedLine(10f, 5f, 0f);
            leftAxis.addLimitLine(base);

            double th = seg.average + baselineMultiplier * seg.standardDeviation;
            LimitLine thr = new LimitLine((float) th, "閾值 " + (i + 1));
            thr.setLineColor(Color.RED);
            thr.setLineWidth(1.5f);
            thr.enableDashedLine(5f, 3f, 0f);
            leftAxis.addLimitLine(thr);
        }

        newPeakChart.setData(lineData);
        newPeakChart.invalidate();

        Toast.makeText(this,
                isCheekPuff ? "📈 圖表已更新（鼓臉 Magnitude）"
                        : (isLipClosingData ? "📈 圖表已更新（基於 BASELINE，抿嘴正數轉換）"
                        : "📈 圖表已更新（基於 BASELINE）"),
                Toast.LENGTH_SHORT).show();
    }

    private void showError(String msg) {
        newInfoText.setText("❌ 錯誤: " + msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Log.e(TAG, msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "頁面銷毀");
    }

    // ====== 小工具 ======
    private static boolean hasCol(String[] headers, String name) {
        for (String h : headers) if (h.trim().equalsIgnoreCase(name)) return true;
        return false;
    }
    private static int findCol(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) if (headers[i].trim().equalsIgnoreCase(name)) return i;
        return -1;
    }
    private static double parseDoubleSafe(String[] v, int idx, double def) {
        try {
            if (idx < 0 || idx >= v.length) return def;
            return Double.parseDouble(v[idx]);
        } catch (Exception e) { return def; }
    }

    private static double estimateFs(List<Double> t) {
        if (t == null || t.size() < 2) return 30.0;
        List<Double> diff = new ArrayList<>();
        for (int i = 1; i < t.size(); i++) {
            double d = t.get(i) - t.get(i - 1);
            if (d > 0) diff.add(d);
        }
        if (diff.isEmpty()) return 30.0;
        diff.sort(Double::compareTo);
        double med = diff.get(diff.size() / 2);
        return (med > 1e-6) ? (1.0 / med) : 30.0;
    }

    private static double[] toArray(List<Double> L) {
        double[] a = new double[L.size()];
        for (int i = 0; i < L.size(); i++) a[i] = (L.get(i).isNaN() ? 0.0 : L.get(i));
        return a;
    }

    // EMA（無泄放）
    private static double[] emaPlain(double[] x, double fs, double winSec) {
        int n = x.length;
        double[] y = new double[n];
        int N = Math.max(3, (int) Math.round(winSec * fs));
        double alpha = 2.0 / (N + 1.0);
        double prev = x[0];
        y[0] = prev;
        for (int i = 1; i < n; i++) {
            double xi = x[i];
            prev = prev + alpha * (xi - prev);
            y[i] = prev;
        }
        return y;
    }
    // EMA + 泄放（缺值往 0 衰減）
    private static double[] emaLeaky(double[] x, boolean[] valid, double fs, double winSec, double leakPerFrame) {
        int n = x.length;
        double[] y = new double[n];
        int N = Math.max(3, (int) Math.round(winSec * fs));
        double alpha = 2.0 / (N + 1.0);
        y[0] = (valid[0] ? x[0] : 0.0);
        for (int i = 1; i < n; i++) {
            double xi = x[i];
            double yi = y[i - 1];
            if (valid[i]) {
                y[i] = yi + alpha * (xi - yi);
            } else {
                y[i] = yi * (1.0 - leakPerFrame);
            }
        }
        return y;
    }
    private static double[] sub(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = a[i] - b[i];
        return y;
    }
}
