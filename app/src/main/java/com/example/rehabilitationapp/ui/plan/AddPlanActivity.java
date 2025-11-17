package com.example.rehabilitationapp.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.model.TrainingItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class AddPlanActivity extends AppCompatActivity {

    private GridLayout gridTrainingItems;
    private Button btnSave;

    private List<TrainingItem> items;
    // ✅ 多選：用一個 Set 記錄有哪些 index 被選到
    private final Set<Integer> selectedIndexSet = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_plan);

        setTitle("新增訓練");

        gridTrainingItems = findViewById(R.id.training_container);
        btnSave = findViewById(R.id.start_button);
        btnSave.setEnabled(false);   // 一開始不能按

        // 讀 DB
        loadTrainingItems();

        // ✅ 按確定：這裡就能知道選了哪些項目
        btnSave.setOnClickListener(v -> {
            if (selectedIndexSet.isEmpty()) {
                Toast.makeText(this, "請先選擇至少一個訓練項目", Toast.LENGTH_SHORT).show();
                return;
            }

            // 這裡只是示範：把選到的 title 串起來秀出來
            StringBuilder sb = new StringBuilder();
            for (Integer idx : selectedIndexSet) {
                if (idx >= 0 && idx < items.size()) {
                    sb.append(items.get(idx).title).append("、");
                }
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 1); // 去掉最後一個頓號

            Toast.makeText(this, "你選了：" + sb, Toast.LENGTH_LONG).show();

            // TODO: 之後在這裡把「計畫名稱 + selectedIndexSet 對應的 items」
            //       寫進 TrainingPlan / PlanItemCrossRef，再 finish()
        });
    }

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

            // 圖片與文字
            int resId = getResources().getIdentifier(
                    item.imageResName, "drawable", getPackageName()
            );
            card.<android.widget.ImageView>findViewById(R.id.card_image)
                    .setImageResource(resId);
            card.<android.widget.TextView>findViewById(R.id.card_label)
                    .setText(item.title);

            final int index = i;
            card.setOnClickListener(v -> toggleSelect(card, index));

            // margin
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(dp(9), dp(13), dp(9), dp(13));
            card.setLayoutParams(lp);

            gridTrainingItems.addView(card);
        }
    }

    // ✅ 多選 + 可取消：點一次選中再點一次取消
    private void toggleSelect(View card, int index) {
        if (selectedIndexSet.contains(index)) {
            // 已經選過 → 取消
            selectedIndexSet.remove(index);
            card.setAlpha(1f);          // 還原亮度
            card.setSelected(false);
        } else {
            // 還沒選 → 加進集合
            selectedIndexSet.add(index);
            card.setAlpha(0.6f);        // 變暗表示被選
            card.setSelected(true);
        }

        // 只要有選任何一個，確定按鈕就可以按；全部取消則 disable
        btnSave.setEnabled(!selectedIndexSet.isEmpty());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
