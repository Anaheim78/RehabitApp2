package com.example.rehabilitationapp.ui.results;

import com.example.rehabilitationapp.R;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        // 綁定 View（名稱需與 XML 完全一致）
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

        // 讀取 Intent 資料
        Intent i = getIntent();
        String trainingLabel = i.getStringExtra("training_label");
        int actualCount      = i.getIntExtra("actual_count", 0);
        int targetCount      = i.getIntExtra("target_count", 4);
        int durationSec      = i.getIntExtra("training_duration", 0);
        String csvFileName   = i.getStringExtra("csv_file_name");
        String apiJson       = i.getStringExtra("api_response_json"); // 可能為 null
        double[] times       = i.getDoubleArrayExtra("ratio_times");
        double[] ratios      = i.getDoubleArrayExtra("ratio_values");

        // 若有 API JSON，優先覆寫可覆寫欄位
        if (!TextUtils.isEmpty(apiJson)) {
            try {
                JSONObject obj = new JSONObject(apiJson);
                if (obj.has("motion")) {
                    trainingLabel = obj.optString("motion", trainingLabel);
                }
                if (obj.has("pout_count")) {
                    actualCount = obj.optInt("pout_count", actualCount);
                }
                if (obj.has("total_hold_time")) {
                    durationSec = (int) Math.round(obj.optDouble("total_hold_time", durationSec));
                }
                if (obj.has("message")) {
                    tvSystemTips.setText(obj.optString("message", "系統提醒訊息"));
                }
            } catch (JSONException ignore) { /* 保底用 Intent 值 */ }
        }

        // 顯示數值
        tvTitle.setText("訓練結果");
        tvMotionName1.setText(toDisplayMotion(trainingLabel));
        tvCount1.setText(String.format(Locale.getDefault(), "%02d/%02d", actualCount, targetCount));

        int percent = (int) Math.round(100.0 * actualCount / Math.max(1, targetCount));
        tvPercent1.setText(String.format(Locale.getDefault(), "%d%%", percent));
        tvDuration1.setText(String.format(Locale.getDefault(), "%d秒", durationSec));

        // === 這裡做「final 副本」→ 給 lambda 使用 ===
        final String fLabel    = trainingLabel;
        final int    fActual   = actualCount;
        final int    fTarget   = targetCount;
        final int    fDuration = durationSec;
        final String fCsv      = csvFileName;
        final int    fPercent  = percent;
        final String fApiJson  = apiJson;

        // 上一頁
        btnBackArrow.setOnClickListener(v -> finish());

        // 重新
        btnRetryCircle.setOnClickListener(v -> {
            Toast.makeText(this, "重新測量", Toast.LENGTH_SHORT).show();
            finish();
        });

        // 分享
        btnShareCircle.setOnClickListener(v -> {
            String text = "訓練：" + toDisplayMotion(fLabel)
                    + "；次數：" + fActual + "/" + fTarget
                    + "；完成度：" + fPercent + "%"
                    + "；持續：" + fDuration + "秒"
                    + (TextUtils.isEmpty(fCsv) ? "" : "；CSV：" + fCsv);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(share, "分享訓練結果"));
        });

        // 儲存（示意）
        btnSave.setOnClickListener(v ->
                Toast.makeText(this, "已儲存結果（示意）", Toast.LENGTH_SHORT).show()
        );

        // 底部導覽（動態加 items）


        // ===== 畫 Ratio 折線圖 + segments 區段 =====
        setupChart(lineChart);
        plotRatio(lineChart, times, ratios);
        shadeSegmentsFromApi(lineChart, fApiJson);
        lineChart.invalidate();
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

        // 底色區塊（使用 setFillColor；顏色字串含透明度 #AARRGGBB）
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#335A87FF")); // 33=~20%透明的藍

        chart.setData(new LineData(ds));
    }

    private void shadeSegmentsFromApi(LineChart chart, String apiJson) {
        if (chart == null || TextUtils.isEmpty(apiJson)) return;
        try {
            JSONObject obj = new JSONObject(apiJson);
            JSONArray segs = obj.optJSONArray("segments");
            if (segs == null) return;

            // 用 LimitLine 當邊界線；若要整塊底色效果需客製 renderer
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
        switch (codeOrName) {
            case "poutLip":
            case "POUT_LIPS":
                return "噘嘴";
            case "SIP_LIPS":
                return "抿嘴";
            case "PUFF_CHEEK":
                return "鼓頰";
            default:
                return codeOrName;
        }
    }
}
