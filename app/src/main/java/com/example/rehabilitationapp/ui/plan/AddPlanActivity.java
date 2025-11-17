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
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class AddPlanActivity extends AppCompatActivity {

    private GridLayout gridTrainingItems;
    private Button btnSave;
    private EditText etName;

    private List<TrainingItem> items = new ArrayList<>();
    // ✅ 多選：記錄「第幾個 index 被選到」
    private final Set<Integer> selectedIndexSet = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_plan);

        setTitle("新增訓練");

        // 上方返回鍵（如果 layout 有 back_btn）
        ImageView backBtn = findViewById(R.id.back_btn);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        gridTrainingItems = findViewById(R.id.training_container);
        btnSave = findViewById(R.id.start_button);
        etName = findViewById(R.id.etName);

        btnSave.setEnabled(false);   // 一開始不能按

        // 從 DB 抓動作清單
        loadTrainingItems();

        // 按「確定動作」：真正寫入 DB
        btnSave.setOnClickListener(v -> onSaveClicked());
    }

    // ==========================
    // 讀 DB → 把動作變卡片
    // ==========================
    private void loadTrainingItems() {
        Executors.newSingleThreadExecutor().execute(() -> {
            TrainingItemDao dao = AppDatabase.getInstance(this).trainingItemDao();
            List<TrainingItem> list = dao.getAllNow();

            runOnUiThread(() -> {
                items = list;
                buildCards(items);
            });
        });
    }

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

            // ✅ 點一下：選 / 取消選
            card.setOnClickListener(v -> toggleSelectCard(v, index));

            // 如果這個 index 本來就有在選（例如之後你要重建畫面），要同步外觀
            if (selectedIndexSet.contains(index)) {
                card.setAlpha(0.6f);
            } else {
                card.setAlpha(1f);
            }

            // 設置 margin
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(dp(9), dp(13), dp(9), dp(13));
            card.setLayoutParams(lp);

            gridTrainingItems.addView(card);
        }
    }

    // ==========================
    // 點卡片 → 多選 / 取消
    // ==========================
    private void toggleSelectCard(View card, int index) {
        if (selectedIndexSet.contains(index)) {
            // 原本有選 → 取消
            selectedIndexSet.remove(index);
            card.setAlpha(1f);
        } else {
            // 原本沒選 → 加進來
            selectedIndexSet.add(index);
            card.setAlpha(0.6f);  // 變暗一點表示被選中
        }

        // 只要有至少一個被選，才能按「確定動作」
        btnSave.setEnabled(!selectedIndexSet.isEmpty());
    }

    // ==========================
    // 按「確定動作」 → 寫 DB
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

        // 把選到的 TrainingItem 收集起來
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

        // 背景執行緒寫入 DB
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            TrainingPlanDao planDao = db.trainingPlanDao();

            // 建一個新的 plan
            TrainingPlan newPlan = new TrainingPlan(title, "", "");
            long newPlanId = planDao.insertPlan(newPlan);

            // 建立 plan 和 item 的關聯
            for (TrainingItem item : selectedItems) {
                planDao.insertCrossRef(newPlanId, item.id);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "已建立新訓練計畫", Toast.LENGTH_SHORT).show();
                finish();   // 回到上一頁（PlanFragment）
            });
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
