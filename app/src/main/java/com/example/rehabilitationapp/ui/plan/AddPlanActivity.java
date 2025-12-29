package com.example.rehabilitationapp.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.dao.TrainingPlanDao;
import com.example.rehabilitationapp.data.model.PlanWithItems;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import com.example.rehabilitationapp.ui.BottomNavHelper;

public class AddPlanActivity extends AppCompatActivity {

    private GridLayout gridTrainingItems;
    private Button btnSave;
    private EditText etName;

    private List<TrainingItem> items = new ArrayList<>();
    // 多選：記錄哪些「index」被選到
    private final Set<Integer> selectedIndexSet = new HashSet<>();

    // 編輯模式相關
    private int editingPlanId = -1;
    private boolean isEditMode = false;
    private String originalTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_plan);

        setTitle("新增 / 編輯訓練");

        // 讀 Intent：看有沒有 plan_id，如果有就是編輯模式
        if (getIntent() != null) {
            editingPlanId = getIntent().getIntExtra("plan_id", -1);
            originalTitle = getIntent().getStringExtra("plan_title");
            isEditMode = (editingPlanId > 0);
        }

        // 綁定 View
        ImageView backBtn = findViewById(R.id.back_btn);
        TextView homeText = findViewById(R.id.home_text);
        gridTrainingItems = findViewById(R.id.training_container);
        btnSave = findViewById(R.id.start_button);
        etName = findViewById(R.id.etName);

        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
        if (homeText != null) {
            homeText.setText(isEditMode ? "編輯訓練計畫" : "新增訓練計畫");
        }

        // 編輯模式 → 把原本名稱先塞到輸入框
        if (isEditMode && originalTitle != null && !originalTitle.isEmpty()) {
            etName.setText(originalTitle);
        }

        btnSave.setEnabled(false);   // 一開始不能按，至少要選一個動作才可以

        // 從 DB 抓訓練項目 +（若是編輯模式）原本有哪些項目被選
        loadTrainingItems();

        // 儲存按鈕ˇ
        btnSave.setOnClickListener(v -> onSaveClicked());
        // ★ 在最後加入這一行
        BottomNavHelper.setup(this, findViewById(R.id.bottom_nav));
    }

    // ==========================
    // 讀 DB → 建卡片（含「編輯時預選」）
    // ==========================
    private void loadTrainingItems() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            TrainingItemDao itemDao = db.trainingItemDao();
            TrainingPlanDao planDao = db.trainingPlanDao();

            // 1) 全部訓練動作
            List<TrainingItem> list = itemDao.getAllNow();

            // 2) 若是編輯模式，查出這個 plan 原本勾了哪些 item
            if (isEditMode && editingPlanId > 0) {
                PlanWithItems planWithItems = planDao.getPlanWithItemsById(editingPlanId);
                if (planWithItems != null && planWithItems.items != null) {

                    // 把原本「這個計畫底下的所有 item.id」收集起來
                    Set<Integer> selectedItemIds = new HashSet<>();
                    for (TrainingItem ti : planWithItems.items) {
                        selectedItemIds.add(ti.id);
                    }

                    // 轉成「在 list 裡對應的 index」→ 填到 selectedIndexSet
                    selectedIndexSet.clear();
                    for (int i = 0; i < list.size(); i++) {
                        TrainingItem candidate = list.get(i);
                        if (selectedItemIds.contains(candidate.id)) {
                            selectedIndexSet.add(i);
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                items = list;
                buildCards(items);
                // 如果是編輯模式，selectedIndexSet 可能已經被填好了
                btnSave.setEnabled(!selectedIndexSet.isEmpty());
            });
        });
    }

    // ==========================
    // 產生格子卡片
    // ==========================
    private void buildCards(List<TrainingItem> items) {
        LayoutInflater inflater = LayoutInflater.from(this);
        gridTrainingItems.removeAllViews();

        for (int i = 0; i < items.size(); i++) {

            TrainingItem item = items.get(i);
            View card = inflater.inflate(R.layout.training_card_item, gridTrainingItems, false);

            // 塞圖片文字
            int resId = getResources().getIdentifier(
                    item.imageResName, "drawable", getPackageName()
            );
            ImageView img = card.findViewById(R.id.card_image);
            TextView label = card.findViewById(R.id.card_label);

            if (resId != 0) img.setImageResource(resId);
            label.setText(item.title);

            final int index = i;
            card.setOnClickListener(v -> toggleSelectCard(v, index));

            // ⭐ 編輯模式：原本勾過的 index 一開始就變暗
            if (selectedIndexSet.contains(index)) {
                card.setAlpha(0.6f);
            } else {
                card.setAlpha(1f);
            }

            // margin
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(dp(9), dp(13), dp(9), dp(13));
            card.setLayoutParams(lp);

            gridTrainingItems.addView(card);
        }
    }

    // ==========================
    // 多選 / 取消
    // ==========================
    private void toggleSelectCard(View card, int index) {
        if (selectedIndexSet.contains(index)) {
            // 已選 → 取消
            selectedIndexSet.remove(index);
            card.setAlpha(1f);
        } else {
            // 未選 → 加入
            selectedIndexSet.add(index);
            card.setAlpha(0.6f);
        }

        btnSave.setEnabled(!selectedIndexSet.isEmpty());
    }

    // ==========================
    // 儲存：新增 or 編輯
    // ==========================
    private void onSaveClicked() {
        String title = etName.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "請先輸入復健計畫名稱", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedIndexSet.isEmpty()) {
            Toast.makeText(this, "請至少選擇一個訓練項目", Toast.LENGTH_SHORT).show();
            return;
        }

        // 對應選到的 TrainingItem
        List<TrainingItem> selectedItems = new ArrayList<>();
        for (Integer idx : selectedIndexSet) {
            if (idx != null && idx >= 0 && idx < items.size()) {
                selectedItems.add(items.get(idx));
            }
        }
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "選取的動作有問題，請重試", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            TrainingPlanDao planDao = db.trainingPlanDao();

            if (!isEditMode) {
                // ★ 新增模式：建立新 plan
                TrainingPlan newPlan = new TrainingPlan(title, "", "");
                long newPlanId = planDao.insertPlan(newPlan);

                for (TrainingItem item : selectedItems) {
                    planDao.insertCrossRef(newPlanId, item.id);
                }
            } else {
                // ★ 編輯模式：更新原本那一筆
                TrainingPlan plan = planDao.getById(editingPlanId);
                if (plan != null) {
                    plan.title = title;
                    planDao.updatePlan(plan);

                    // 先刪掉舊的關聯，再把新的關聯寫回去
                    planDao.deleteCrossRefsForPlan(editingPlanId);
                    for (TrainingItem item : selectedItems) {
                        planDao.insertCrossRef(editingPlanId, item.id);
                    }
                }
            }

            runOnUiThread(() -> {
                Toast.makeText(this,
                        isEditMode ? "已更新訓練計畫" : "已建立新訓練計畫",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
