package com.example.rehabilitationapp.ui.plan;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.Menu;
import android.view.MenuItem;

public class TrainingDetailActivity extends AppCompatActivity {

    private RecyclerView exercisesRecycler;
    private ExecutorService executor;
    private boolean isCreateMode = false;  // 新增：模式標記
    //可能用不到了
    //是否為創建模式，頂端垃圾桶刪除
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isCreateMode) {
            long planId = getIntent().getIntExtra("plan_id", -1);

            if (planId >= 3) {
                getMenuInflater().inflate(R.menu.plan_detail_menu, menu);
            } else {
                Log.d("Menu", "預設計畫，不顯示刪除選單");
            }
        }
        return true;
    }

    //刪除確認
    //刪除點選+確認
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {
            // 彈出確認視窗
            new AlertDialog.Builder(this)
                    .setTitle("刪除訓練計畫")
                    .setMessage("確定要刪除這個訓練計畫嗎？")
                    .setPositiveButton("確定", (dialog, which) -> {
                        long planId = getIntent().getIntExtra("plan_id", -1);
                        if (planId != -1) {
                            executor.execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(this);
                                TrainingPlan plan = db.trainingPlanDao().getById((int) planId); // ID 是 int
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


    //中間 動作清單詳細
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training_detail);

        executor = Executors.newSingleThreadExecutor();

        // 檢查是否為創建模式
        String mode = getIntent().getStringExtra("mode");
        isCreateMode = "create_new".equals(mode);
        Log.d("test_PlanDetail", "=== Mode Check: " + mode + ", isCreateMode: " + isCreateMode + " ===");

        // 設置標題
        String planTitle = getIntent().getStringExtra("plan_title");
        TextView titleText = findViewById(R.id.page_title);
        //titleText.setText(planTitle != null ? planTitle : "自訂訓練計畫");

        // 設置返回按鈕 可以保留並且全局使用
        ImageView backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> finish());

        // 設置 RecyclerView
        exercisesRecycler = findViewById(R.id.exercises_recycler);
        //exercisesRecycler.setLayoutManager(new LinearLayoutManager(this));
        // 兩列、橫向滑動
        GridLayoutManager glm = new GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false);
        exercisesRecycler.setLayoutManager(glm);

        // 載入運動項目
        loadExercises();

        // 設置按鈕
        Button createBtn = findViewById(R.id.start_plan_btn);

        //根據模式配置文字，先不管
        if (isCreateMode) {
            createBtn.setText("建立計畫");
        } else {
            createBtn.setText("開始訓練");
        }
        createBtn.setOnClickListener(v -> {
            Log.d("TrainDetailAct","into the createBtn");
            if (isCreateMode) {
                Log.d("TrainDetailAct","into the createBtn__isCreateMode");
                // 準備 AlertDialog 讓使用者輸入名稱
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

                            // 拿勾選的項目
                            SelectableExerciseAdapter adapter = (SelectableExerciseAdapter) exercisesRecycler.getAdapter();
                            List<TrainingItem> selectedItems = adapter.getSelectedItems();

                            if (selectedItems.isEmpty()) {
                                Toast.makeText(this, "請至少選擇一項運動項目", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 寫入資料庫
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
                Log.d("TrainDetailAct","into the createBtn__is not CreateMode");
                Toast.makeText(this, "開始訓練", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadExercises() {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);

                if (isCreateMode) {
                    // 創建模式：載入所有運動項目
                    Log.d("test_PlanDetail", "=== 創建模式：載入所有運動項目 ===");
                    List<TrainingItem> allExercises = db.trainingItemDao().getAll();
                    Log.d("test_PlanDetail", "找到 " + allExercises.size() + " 個運動項目");

                    runOnUiThread(() -> {
                        SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(allExercises,false);
                        exercisesRecycler.setAdapter(adapter);
                        Log.d("test_PlanDetail", "創建模式 Adapter 設置完成");
                    });

                } else {
                    // 原有模式：載入特定計劃的運動項目
                    Log.d("test_PlanDetail", "=== 查看模式：載入特定計劃 ===");

                    // 獲取傳過來的計劃 ID
                    long planId = getIntent().getIntExtra("plan_id", 0);
                    Log.d("test_PlanDetail ", "=== Received planId: " + planId + " ===");

                    // 查詢所有計劃和運動項目
                    List<PlanWithItems> allPlans = db.trainingPlanDao().getAllPlansWithItems();
                    Log.d("test_PlanDetail", "Total plans found: " + allPlans.size());

                    // 顯示所有計劃的 ID
                    for (PlanWithItems planWithItems : allPlans) {
                        Log.d("test_PlanDetail", "Plan ID: " + planWithItems.plan.id +
                                ", Items count: " + planWithItems.items.size());
                    }

                    // 找到指定計劃的運動項目
                    for (PlanWithItems planWithItems : allPlans) {
                        Log.d("test_PlanDetail", "Checking: " + planWithItems.plan.id + " == " + planId);

                        if (planWithItems.plan.id == planId) {
                            List<TrainingItem> exercises = planWithItems.items;

                            Log.d("test_PlanDetail", "MATCH FOUND! Exercise count: " + exercises.size());

                            runOnUiThread(() -> {
                                SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(exercises,true);
                                exercisesRecycler.setAdapter(adapter);
                                Log.d("test_PlanDetail", "Adapter set successfully");
                            });
                            return;
                        }
                    }

                    // 如果沒找到計劃
                    Log.d("test_PlanDetail", "NO MATCH FOUND for planId: " + planId);
                    runOnUiThread(() -> {
                        SelectableExerciseAdapter adapter = new SelectableExerciseAdapter(new ArrayList<>());
                        exercisesRecycler.setAdapter(adapter);
                    });
                }

            } catch (Exception e) {
                Log.e("test_PlanDetail", "Error: " + e.getMessage(), e);
            }
        });
    }
}