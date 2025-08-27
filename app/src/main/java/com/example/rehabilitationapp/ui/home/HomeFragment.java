package com.example.rehabilitationapp.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.example.rehabilitationapp.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.rehabilitationapp.databinding.FragmentHomeBinding;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.GridLayout;
import android.content.Intent;
import com.example.rehabilitationapp.ui.facecheck.FaceCircleCheckerActivity;
import java.util.List;
import java.util.concurrent.Executors;

import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.model.TrainingItem;
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private View selectedCard = null;
    private int selectedTrainingType = -1;
    private List<TrainingItem> items;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 在這裡設定標題
        requireActivity().setTitle("首頁");  // 或 "訓練計畫"
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化界面
        initializeUI();
        // onViewCreated 或 initializeUI 之後
        //HorizontalScrollView hsv = binding.hsTraining; // 先在 XML 給 HorizontalScrollView 加個 id: @+id/hs_training
        //hsv.setFillViewport(true);
        //hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

// 禁止水平滑動，但保留點擊：只攔截 MOVE，不攔截 DOWN/UP
        //hsv.setOnTouchListener((v, ev) -> ev.getAction() == MotionEvent.ACTION_MOVE);
        return root;
    }

    private void initializeUI() {
        binding.titleGreeting.setText("Hi, Allen!");

        // 1) 用 DAO 讀資料（背景執行緒）
        Executors.newSingleThreadExecutor().execute(() -> {
            TrainingItemDao dao = AppDatabase.getInstance(requireContext()).trainingItemDao();

            List<TrainingItem> list = dao.getAllNow(); // 同步查詢

            // 2) 回到主執行緒渲染 UI
            requireActivity().runOnUiThread(() -> {
                items = list;
                buildCards(items);
            });
        });

        // 3) 開始按鈕
        binding.startButton.setOnClickListener(v -> onStartClicked());
    }

    private void onStartClicked() {
        if (selectedTrainingType == -1 || items == null || selectedTrainingType >= items.size()) {
            Toast.makeText(getContext(), "請先選擇一個訓練項目", Toast.LENGTH_SHORT).show();
            return;
        }
        TrainingItem item = items.get(selectedTrainingType);

        Toast.makeText(getContext(), "開始 " + item.title + " 訓練！", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(getActivity(), FaceCircleCheckerActivity.class);
        intent.putExtra("training_type", item.analysisType); // 用 DB 裡的 type
        intent.putExtra("training_label", item.title);
        startActivity(intent);
    }

    private void selectCard(View card, int trainingType) {
        // 清除之前選中的卡片
        if (selectedCard != null) {
            selectedCard.setSelected(false);
            ImageView prevIndicator = selectedCard.findViewById(R.id.selected_indicator);
            if (prevIndicator != null) {
                prevIndicator.setVisibility(View.GONE);
            }
        }

        // 設置新選中的卡片
        selectedCard = card;
        selectedTrainingType = trainingType;
        card.setSelected(true);

        ImageView indicator = card.findViewById(R.id.selected_indicator);
        if (indicator != null) {
            indicator.setVisibility(View.VISIBLE);
        }

        // 啟用開始按鈕
        binding.startButton.setEnabled(true);
    }

    private void buildCards(List<TrainingItem> items) {
        GridLayout trainingContainer = binding.trainingContainer;
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        trainingContainer.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            TrainingItem item = items.get(i);
            View card = layoutInflater.inflate(R.layout.training_card_item, trainingContainer, false);

            ImageView image = card.findViewById(R.id.card_image);
            TextView  label = card.findViewById(R.id.card_label);

            int resId = getResources().getIdentifier(
                    item.imageResName, "drawable", requireContext().getPackageName()
            );
            if (resId != 0) image.setImageResource(resId);
            label.setText(item.title);

            final int index = i;
            card.setOnClickListener(v -> selectCard(v, index));

            // ✅ 父容器為 GridLayout → 用 GridLayout.LayoutParams
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            // 若要 Figma 的水平 18 / 垂直 13，左右各 9、上下各 13
            lp.setMargins(dp(9), dp(13), dp(9), dp(13));
            card.setLayoutParams(lp);

            trainingContainer.addView(card);
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        selectedCard = null;
    }
}