package com.example.rehabilitationapp.ui.plan;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import com.example.rehabilitationapp.ui.facecheck.FaceCircleCheckerActivity;
import com.example.rehabilitationapp.ui.facecheck.MotionGuideBottomSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.Menu;
import android.view.MenuItem;

public class TrainingDetailActivity extends AppCompatActivity {

    private RecyclerView exercisesRecycler;
    private ExecutorService executor;
    private boolean isCreateMode = false;  // 新增：模式標記

    //plan_detail_menu先棄用
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isCreateMode) {
            long planId = getIntent().getIntExtra("plan_id", -1);
            if (planId >= 3) {
//                getMenuInflater().inflate(R.menu.plan_detail_menu, menu);
                getMenuInflater().inflate(R.menu.plan_detail_menu, menu);
            } else {
                Log.d("Menu", "預設計畫，不顯示刪除選單");
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("刪除訓練計畫")
                    .setMessage("確定要刪除這個訓練計畫嗎？")
                    .setPositiveButton("確定", (dialog, which) -> {
                        long planId = getIntent().getIntExtra("plan_id", -1);
                        if (planId != -1) {
                            executor.execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(this);
                                TrainingPlan plan = db.trainingPlanDao().getById((int) planId);
                                if (plan != null) {
                                    db.trainingPlanDao().delete(plan);
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "已刪除訓練計畫", Toast.LENGTH_SHORT).show();
                                        finish();
                                    });
                                }
                            });
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==============================
    // lifecycle
    // ==============================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //20251117目前基礎/進階計劃_跑這邊
        setContentView(R.layout.activity_training_detail);

        executor = Executors.newSingleThreadExecutor();

        String mode = getIntent().getStringExtra("mode");
        isCreateMode = "create_new".equals(mode);
        Log.d("TrainDetailAct", "=== Mode: " + mode + ", isCreateMode: " + isCreateMode + " ===");

        String planTitle = getIntent().getStringExtra("plan_title");
        TextView titleText = findViewById(R.id.page_title);
        // 可自行決定是否顯示標題
        // titleText.setText(planTitle != null ? planTitle : "自訂訓練計畫");

        ImageView backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> finish());

        exercisesRecycler = findViewById(R.id.exercises_recycler);
        GridLayoutManager glm = new GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false);
        exercisesRecycler.setLayoutManager(glm);

        loadExercises();

        Button createBtn = findViewById(R.id.start_plan_btn);
        if (isCreateMode) {
            createBtn.setText("建立計畫");
        } else {
            createBtn.setText("開始訓練");
        }
        createBtn.setOnClickListener(v -> {
            Log.d("TrainDetailAct","click start/create button");
            if (isCreateMode) {
                // 建立新計畫流程
                EditText input = new EditText(this);
                input.setHint("請輸入計劃名稱");

                new AlertDialog.Builder(this)
                        .setTitle("建立新訓練計劃")
                        .setView(input)
                        .setPositiveButton("確認", (dialog, which) -> {
                            String enteredTitle = input.getText().toString().trim();
                            if (enteredTitle.isEmpty()) {
                                Toast.makeText(this, "請輸入計劃名稱", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            SelectableExerciseAdapter adapter = (SelectableExerciseAdapter) exercisesRecycler.getAdapter();
                            List<TrainingItem> selectedItems = adapter.getSelectedItems();
                            if (selectedItems.isEmpty()) {
                                Toast.makeText(this, "請至少選擇一項運動項目", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            executor.execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(this);
                                TrainingPlan newPlan = new TrainingPlan(enteredTitle,"","");
                                long newPlanId = db.trainingPlanDao().insertPlan(newPlan);
                                for (TrainingItem item : selectedItems) {
                                    db.trainingPlanDao().insertCrossRef(newPlanId, item.id);
                                }
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "已建立新計劃", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();

            } else {
                // 👉 這裡改成：先在本頁跳教學，按「開始」才真正進 FaceCircleCheckerActivity
                SelectableExerciseAdapter adapter = (SelectableExerciseAdapter) exercisesRecycler.getAdapter();
                List<TrainingItem> selectedItems = adapter.getSelectedItems();
                if (selectedItems.isEmpty()) {
                    Toast.makeText(this, "請先選擇一個訓練項目", Toast.LENGTH_SHORT).show();
                    return;
                }
                TrainingItem selectedItem = selectedItems.get(0);
                showGuideThenStart(selectedItem);
            }
        });
    }

    private void loadExercises() {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);

                if (isCreateMode) {
                    List<TrainingItem> allExercises = db.trainingItemDao().getAll();
                    runOnUiThread(() -> {
                        SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(allExercises,false);
                        exercisesRecycler.setAdapter(adapter);
                    });

                } else {
                    long planId = getIntent().getIntExtra("plan_id", 0);
                    List<PlanWithItems> allPlans = db.trainingPlanDao().getAllPlansWithItems();

                    for (PlanWithItems planWithItems : allPlans) {
                        if (planWithItems.plan.id == planId) {
                            List<TrainingItem> exercises = planWithItems.items;
                            runOnUiThread(() -> {
                                SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(exercises,false);
                                exercisesRecycler.setAdapter(adapter);
                            });
                            return;
                        }
                    }
                    runOnUiThread(() -> {
                        SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(new ArrayList<>());
                        exercisesRecycler.setAdapter(adapter);
                    });
                }

            } catch (Exception e) {
                Log.e("TrainDetailAct", "Error: " + e.getMessage(), e);
            }
        });
    }

    // ==============================
    // ↓↓↓ 下面是這次新增的教學流程 & 工具方法 ↓↓↓
    // ==============================

    /** 在本頁顯示教學；按「開始」後才跳 FaceCircleCheckerActivity */
    private void showGuideThenStart(TrainingItem item) {
        // 代號：優先用 DB 的 analysisType；若空就用中文推測
        String analysisType = (item.analysisType != null && !item.analysisType.isEmpty())
                ? item.analysisType
                : inferAnalysisTypeFromTitle(item.title);
        String titleZh = item.title;

        // 若使用者勾過「不再顯示」→ 直接進訓練頁
        if (!shouldShowGuide(analysisType)) {
            launchTraining(analysisType, titleZh);
            return;
        }

        // 顯示教學 BottomSheet（延用你已實作的）
        MotionGuideBottomSheet sheet = MotionGuideBottomSheet.newInstance(analysisType, titleZh);
        sheet.setOnStartListener(() -> launchTraining(analysisType, titleZh));
        sheet.show(getSupportFragmentManager(), "motion_guide_from_detail");
    }

    /** 真的啟動 FaceCircleCheckerActivity（同時帶舊/新 key，避免相容性問題） */
    private void launchTraining(String analysisType, String titleZh) {
        Log.d("TrainDetailAct", "Start training: type=" + analysisType + ", title=" + titleZh);
        Toast.makeText(this, "開始「" + titleZh + "」訓練！", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, FaceCircleCheckerActivity.class);
        // ★ 新版/統一讀法
        intent.putExtra("training_type", analysisType); // 供 FaceCircle 判斷動作
        intent.putExtra("training_title", titleZh);     // 中文標題（顯示/教學）

        // ★ 相容舊讀法（你之前 FaceCircle 可能讀這個）
        intent.putExtra("training_label", titleZh);

        startActivity(intent);
        // 是否 finish() 依你 UX 決定；現在保留不關，讓使用者返回本頁也行
        // finish();
    }

    /** 是否應顯示教學（沿用 BottomSheet 的 SharedPreferences 規則） */
    private boolean shouldShowGuide(String trainingType) {
        String key = "guide_hide_" + canonicalMotion(trainingType);
        return !getSharedPreferences("motion_prefs", MODE_PRIVATE)
                .getBoolean(key, false);
    }

    /** 將代號正規化：pout/close/tongue… */
    private String canonicalMotion(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase();
        if (x.contains("pout")) return "poutLip";
        if (x.contains("close") || x.contains("sip") || x.contains("slip") || x.contains("抿")) return "closeLip";
        if (x.contains("tongue_left"))  return "TONGUE_LEFT";
        if (x.contains("tongue_right")) return "TONGUE_RIGHT";
        if (x.contains("tongue_foward")) return "TONGUE_FOWARD";
        if (x.contains("tongue_back"))   return "TONGUE_BACK";
        if (x.contains("tongue_up"))     return "TONGUE_UP";
        if (x.contains("tongue_down"))   return "TONGUE_DOWN";
        return s;
    }

    /** 若沒有 analysisType，就用中文標題推測代號 */
    private String inferAnalysisTypeFromTitle(String title) {
        if (title == null) return "POUT_LIPS";
        String t = title.trim();
        if (t.contains("噘嘴"))       return "POUT_LIPS";
        if (t.contains("抿嘴"))       return "SIP_LIPS";
        if (t.contains("鼓起") || t.contains("鼓頰")) return "PUFF_CHEEK";
        if (t.contains("舌頭往左"))   return "TONGUE_LEFT";
        if (t.contains("舌頭往右"))   return "TONGUE_RIGHT";
        if (t.contains("舌頭往前"))   return "TONGUE_FOWARD";
        if (t.contains("舌頭往後"))   return "TONGUE_BACK";
        if (t.contains("舌頭上"))     return "TONGUE_UP";
        if (t.contains("舌頭下"))     return "TONGUE_DOWN";
        return "POUT_LIPS";
    }
}
