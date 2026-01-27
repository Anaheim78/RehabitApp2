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
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.Menu;
import android.view.MenuItem;


import com.example.rehabilitationapp.ui.BottomNavHelper;

public class TrainingDetailActivity extends AppCompatActivity {

    private RecyclerView exercisesRecycler;
    private ExecutorService executor;
    private boolean isCreateMode = false;  // 是否為「建立新計畫」模式

    // ★ 統一在這支 Activity 裡共用的 Plan 資訊
    private int planId = -1;
    private String planTitle = null;
    private TextView titleText;

    // ==============================
    // menu：右上角刪除
    // ==============================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isCreateMode) {
            // ★ 用欄位 planId，不再從 Intent 亂抓
            if (planId >= 3) {
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
                        if (planId != -1) {
                            executor.execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(this);
                                TrainingPlan plan = db.trainingPlanDao().getById(planId);
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
        setContentView(R.layout.activity_training_detail);

        executor = Executors.newSingleThreadExecutor();

        // ★ 一進來就把 planId / planTitle 讀出來，存到欄位
        planId = getIntent().getIntExtra("plan_id", -1);
        planTitle = getIntent().getStringExtra("plan_title");

        String mode = getIntent().getStringExtra("mode");
        isCreateMode = "create_new".equals(mode);
        Log.d("TrainDetailAct", "=== Mode: " + mode + ", isCreateMode: " + isCreateMode + ", planId=" + planId + " ===");

        titleText = findViewById(R.id.page_title);
        if (planTitle != null && !planTitle.isEmpty()) {
            titleText.setText(planTitle);
        }

        ImageView backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> finish());

        // ==========================
        // ★ 底下右邊「編輯」按鈕 → 跳到 AddPlanActivity
        // ==========================
        MaterialButton btnEdit = findViewById(R.id.btn_edit_plan);

        if (isCreateMode) {
            // 「建立新計畫」模式下，本來就不是針對某個 plan，禁用編輯按鈕
            btnEdit.setEnabled(false);
            btnEdit.setAlpha(0.4f);
        } else {
            btnEdit.setOnClickListener(v -> {

                if (planId <= 0) {
                    Toast.makeText(this, "找不到計畫 ID，無法編輯", Toast.LENGTH_SHORT).show();
                    Log.e("TrainDetailAct", "Edit click but planId=" + planId);
                    return;
                }

                Intent intent = new Intent(TrainingDetailActivity.this, AddPlanActivity.class);
                intent.putExtra("plan_id", planId);
                intent.putExtra("plan_title", planTitle);
                startActivity(intent);
            });
        }

        exercisesRecycler = findViewById(R.id.exercises_recycler);
        GridLayoutManager glm = new GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false);
        exercisesRecycler.setLayoutManager(glm);

        // 第一次進來先載一次
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
                // 開始訓練
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

        // 導航功能
        BottomNavHelper.setup(this, findViewById(R.id.bottom_nav));
    }

    // ==============================
    // ★ 回到這頁時，自動重新抓最新的「名稱＋動作」
    // ==============================
    @Override
    protected void onResume() {
        super.onResume();

        if (!isCreateMode && planId > 0) {
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);

                TrainingPlan plan = db.trainingPlanDao().getById(planId);
                PlanWithItems planWithItems = db.trainingPlanDao().getPlanWithItemsById(planId);

                runOnUiThread(() -> {
                    if (plan != null) {
                        planTitle = plan.title;          // 更新記憶中的標題
                        titleText.setText(plan.title);   // 更新畫面上方標題
                    }
                    if (planWithItems != null) {
                        SelectableExerciseAdapter adapter =
                                new SelectableExerciseAdapter(planWithItems.items, false);
                        exercisesRecycler.setAdapter(adapter);
                    }
                });
            });
        }
    }

    // ==============================
    // 載入動作內容（第一次 onCreate 用）
    // ==============================
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
                    int currentPlanId = planId;  // ★ 用欄位，不再 getIntExtra
                    List<PlanWithItems> allPlans = db.trainingPlanDao().getAllPlansWithItems();

                    for (PlanWithItems planWithItems : allPlans) {
                        if (planWithItems.plan.id == currentPlanId) {
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
    // ↓↓↓ 教學流程 & 工具方法（維持原樣） ↓↓↓
    // ==============================

    /** 在本頁顯示教學；按「開始」後才跳 FaceCircleCheckerActivity */
//    private void showGuideThenStart(TrainingItem item) {
//        String analysisType = (item.analysisType != null && !item.analysisType.isEmpty())
//                ? item.analysisType
//                : inferAnalysisTypeFromTitle(item.title);
//        String titleZh = item.title;
//
//        if (!shouldShowGuide(analysisType)) {
//            launchTraining(analysisType, titleZh);
//            return;
//        }
//
//        MotionGuideBottomSheet sheet = MotionGuideBottomSheet.newInstance(analysisType, titleZh);
//        sheet.setOnStartListener(() -> launchTraining(analysisType, titleZh));
//        sheet.show(getSupportFragmentManager(), "motion_guide_from_detail");
//    }

    private void showGuideThenStart(TrainingItem item) {
        String analysisType = (item.analysisType != null && !item.analysisType.isEmpty())
                ? item.analysisType
                : inferAnalysisTypeFromTitle(item.title);
        String titleZh = item.title;

        // ★ 直接開始訓練，不跳教學說明
        launchTraining(analysisType, titleZh);
    }

    /** 真的啟動 FaceCircleCheckerActivity（同時帶舊/新 key，避免相容性問題） */
    private void launchTraining(String analysisType, String titleZh) {
        Log.d("TrainDetailAct", "Start training: type=" + analysisType + ", title=" + titleZh);
        Toast.makeText(this, "開始「" + titleZh + "」訓練！", Toast.LENGTH_SHORT).show();

        // ★ 存 planId 和 planTitle，給結算頁「重做」按鈕用
        getSharedPreferences("training_prefs", MODE_PRIVATE)
                .edit()
                .putInt("last_plan_id", planId)
                .putString("last_plan_title", planTitle)
                .apply();

        Intent intent = new Intent(this, FaceCircleCheckerActivity.class);
        intent.putExtra("training_type", analysisType);
        intent.putExtra("training_title", titleZh);
        intent.putExtra("training_label", titleZh);

        startActivity(intent);
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
