package com.example.rehabilitationapp.ui.results;

import com.example.rehabilitationapp.R;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalysisResultActivity extends AppCompatActivity {

    private TextView tvTitle;
    private ImageView ivAvatar1;
    private TextView tvMotionName1, tvCount1, tvPercent1, tvDuration1;
    private TextView tvSystemTips;
    private ImageButton btnBackArrow;
    private MaterialButton btnRetryCircle, btnShareCircle, btnSave;
    private BottomNavigationView bottomNav;
    private LineChart lineChart;

    // === 名稱正規化：把所有寫法統一成 poutLip / closeLip ===
    private String canonicalMotion(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(Locale.ROOT);
        if (x.contains("pout")) return "poutLip";
        if (x.contains("close") || x.contains("sip") || x.contains("slip") || x.contains("抿")) return "closeLip";
        return s;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        // 綁定 View
        tvTitle       = findViewById(R.id.tvTitle);
        ivAvatar1     = findViewById(R.id.ivAvatar1);
        tvMotionName1 = findViewById(R.id.tvMotionName1);
        tvCount1      = findViewById(R.id.tvCount1);
        tvPercent1    = findViewById(R.id.tvPercent1);
        tvDuration1   = findViewById(R.id.tvDuration1);
        tvSystemTips  = findViewById(R.id.tvSystemTips);
        btnBackArrow  = findViewById(R.id.btnBackArrow);
        btnRetryCircle= findViewById(R.id.btnRetryCircle);
        btnShareCircle= findViewById(R.id.btnShareCircle);
        btnSave       = findViewById(R.id.btnSave);
        lineChart     = findViewById(R.id.lineChart);

        // 讀取 Intent
        Intent i = getIntent();
        String trainingLabel = i.getStringExtra("training_label");
        int actualCount      = i.getIntExtra("actual_count", 0);
        int targetCount      = i.getIntExtra("target_count", 4);
        int durationSec      = i.getIntExtra("training_duration", 0);
        String csvFileName   = i.getStringExtra("csv_file_name");
        String apiJson       = i.getStringExtra("api_response_json");

        // 嘟嘴用
        double[] times       = i.getDoubleArrayExtra("ratio_times");
        double[] ratios      = i.getDoubleArrayExtra("ratio_values");

        // 抿嘴用
        double[] lipTimes    = i.getDoubleArrayExtra("lip_times");
        double[] lipTotals   = i.getDoubleArrayExtra("lip_totals");

        // 若有 API JSON，覆寫顯示數值（同時正規化 motion 名稱）
        if (!TextUtils.isEmpty(apiJson)) {
            try {
                JSONObject obj = new JSONObject(apiJson);
                if (obj.has("motion")) {
                    trainingLabel = canonicalMotion(obj.optString("motion", trainingLabel));
                } else {
                    trainingLabel = canonicalMotion(trainingLabel);
                }
                // 嘟嘴/抿嘴各自的欄位都試著讀，沒有就保留原值
                if (obj.has("pout_count")) {
                    actualCount = obj.optInt("pout_count", actualCount);
                }
                if (obj.has("close_count")) {
                    actualCount = obj.optInt("close_count", actualCount);
                }
                if (obj.has("total_hold_time")) {
                    durationSec = (int) Math.round(obj.optDouble("total_hold_time", durationSec));
                }
                if (obj.has("total_close_time")) {
                    durationSec = (int) Math.round(obj.optDouble("total_close_time", durationSec));
                }
                if (obj.has("message")) {
                    tvSystemTips.setText(obj.optString("message", "系統提醒訊息"));
                }
            } catch (JSONException ignore) {
                trainingLabel = canonicalMotion(trainingLabel);
            }
        } else {
            trainingLabel = canonicalMotion(trainingLabel);
        }

        // 顯示數值
        tvTitle.setText("訓練結果");
        tvMotionName1.setText(toDisplayMotion(trainingLabel));
        tvCount1.setText(String.format(Locale.getDefault(), "%02d/%02d", actualCount, targetCount));
        int percent = (int) Math.round(100.0 * actualCount / Math.max(1, targetCount));
        tvPercent1.setText(String.format(Locale.getDefault(), "%d%%", percent));
        tvDuration1.setText(String.format(Locale.getDefault(), "%d秒", durationSec));

        // ★ 根據動作顯示圖示
        setMotionIcon(trainingLabel);

        // 返回
        btnBackArrow.setOnClickListener(v -> finish());

        // 重新
        btnRetryCircle.setOnClickListener(v -> {
            Toast.makeText(this, "重新測量", Toast.LENGTH_SHORT).show();
            finish();
        });

        // 分享
        btnShareCircle.setOnClickListener(v -> {
            // TODO: 加入分享邏輯
        });

        // 儲存（示意）
        btnSave.setOnClickListener(v ->
                Toast.makeText(this, "已儲存結果（示意）", Toast.LENGTH_SHORT).show()
        );

        // ===== 畫圖 =====
        setupChart(lineChart);

        if ("poutLip".equalsIgnoreCase(trainingLabel)) {
            plotRatio(lineChart, times, ratios);            // 嘟嘴 → 比值
        } else if ("closeLip".equalsIgnoreCase(trainingLabel)) {
            plotLipArea(lineChart, lipTimes, lipTotals);    // 抿嘴 → 總面積
        }

        shadeSegmentsFromApi(lineChart, apiJson);
        lineChart.invalidate();
    }

    // 依動作顯示對應 ICON
    private void setMotionIcon(String label) {
        String canon = canonicalMotion(label);
        int resId;
        CharSequence desc;

        if ("poutLip".equalsIgnoreCase(canon)) {
            resId = R.drawable.ic_home_lippout;
            desc  = "噘嘴圖示";
        } else if ("closeLip".equalsIgnoreCase(canon)) {
            resId = R.drawable.ic_home_lipsip;
            desc  = "抿嘴圖示";
        } else {
            resId = android.R.drawable.sym_def_app_icon;
            desc  = "訓練圖示";
        }

        ivAvatar1.setImageResource(resId);
        ivAvatar1.setContentDescription(desc);
    }

    private void setupChart(LineChart chart) {
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
    }

    private void plotRatio(LineChart chart, double[] times, double[] ratios) {
        if (chart == null) return;
        if (times == null || ratios == null || times.length == 0 || times.length != ratios.length) {
            chart.setData(null);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            entries.add(new Entry((float) times[i], (float) ratios[i]));
        }
        LineDataSet ds = new LineDataSet(entries, "Height/Width Ratio");
        ds.setColor(Color.BLUE);
        ds.setDrawValues(false);
        ds.setDrawCircles(false);
        ds.setLineWidth(1.7f);
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#335A87FF")); // 藍色透明底
        chart.setData(new LineData(ds));
    }

    // 抿嘴（closeLip）用 total_lip_area 畫折線，底色改和嘟嘴一樣
    private void plotLipArea(LineChart chart, double[] times, double[] totals) {
        if (chart == null) return;
        if (times == null || totals == null || times.length == 0 || times.length != totals.length) {
            chart.setData(null);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            entries.add(new Entry((float) times[i], (float) totals[i]));
        }
        LineDataSet ds = new LineDataSet(entries, "Total Lip Area");
        ds.setColor(Color.BLUE);
        ds.setDrawValues(false);
        ds.setDrawCircles(false);
        ds.setLineWidth(1.7f);
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#335A87FF")); // 藍色透明底，和嘟嘴一致
        chart.setData(new LineData(ds));
    }

    private void shadeSegmentsFromApi(LineChart chart, String apiJson) {
        if (chart == null || TextUtils.isEmpty(apiJson)) return;
        try {
            JSONObject obj = new JSONObject(apiJson);
            JSONArray segs = obj.optJSONArray("segments");
            if (segs == null) return;
            for (int k = 0; k < segs.length(); k++) {
                JSONObject seg = segs.getJSONObject(k);
                float start = (float) seg.optDouble("start_time", Float.NaN);
                float end   = (float) seg.optDouble("end_time",   Float.NaN);
                if (Float.isNaN(start) || Float.isNaN(end)) continue;
                LimitLine llStart = new LimitLine(start);
                llStart.setLineColor(Color.RED);
                llStart.setLineWidth(0.8f);
                chart.getXAxis().addLimitLine(llStart);
                LimitLine llEnd = new LimitLine(end);
                llEnd.setLineColor(Color.GREEN);
                llEnd.setLineWidth(0.8f);
                chart.getXAxis().addLimitLine(llEnd);
            }
        } catch (Exception ignore) { }
    }

    private String toDisplayMotion(String codeOrName) {
        if (TextUtils.isEmpty(codeOrName)) return "訓練";
        String c = canonicalMotion(codeOrName);
        switch (c) {
            case "poutLip":
                return "噘嘴";
            case "closeLip":
                return "抿嘴";
            default:
                return codeOrName;
        }
    }
}
