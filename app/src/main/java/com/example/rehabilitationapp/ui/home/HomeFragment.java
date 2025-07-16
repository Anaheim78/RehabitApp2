package com.example.rehabilitationapp.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private View selectedCard = null;
    private int selectedTrainingType = -1;

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

        return root;
    }

    private void initializeUI() {
        binding.titleGreeting.setText("Hi, Allen!");

        int[] imageIds = {
                R.drawable.cheeks, R.drawable.cheeks_reduction, R.drawable.pout_lips,
                R.drawable.sip_lips, R.drawable.tongueright, R.drawable.tongueleft
        };
        String[] labels = {
                "鼓頰", "縮頰", "嘟嘴",
                "抿嘴", "向左伸舌", "向右伸舌"
        };


        // 獲取容器
        GridLayout trainingContainer = binding.trainingContainer;
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        // 動態創建卡片
        for (int i = 0; i < imageIds.length; i++) {
            try {
                View card = layoutInflater.inflate(R.layout.training_card_item, null, false);

                // 設置卡片內容
                ImageView image = card.findViewById(R.id.card_image);
                TextView label = card.findViewById(R.id.card_label);
                ImageView indicator = card.findViewById(R.id.selected_indicator);

                if (image != null) {
                    image.setImageResource(imageIds[i]);
                }
                if (label != null) {
                    label.setText(labels[i]);
                }

                // 設置點擊事件
                final int trainingType = i;
                final String[] finalLabels = labels; // 為了在內部類中使用
                card.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectCard(v, trainingType);
                    }
                });

                // 設置布局參數
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(8, 32, 8, 32);
                card.setLayoutParams(params);

                // 添加到容器
                trainingContainer.addView(card);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "創建卡片失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        // 設置開始按鈕
        setupStartButton(labels);
    }

    private void setupStartButton(final String[] labels) {
        binding.startButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedTrainingType != -1) {
                    Toast.makeText(getContext(), "開始 " + labels[selectedTrainingType] + " 訓練！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "請先選擇一個訓練項目", Toast.LENGTH_SHORT).show();
                }
            }
        });
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        selectedCard = null;
    }
}