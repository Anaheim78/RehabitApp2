package com.example.rehabilitationapp.ui.plan;

import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SelectableExerciseAdapter extends RecyclerView.Adapter<SelectableExerciseAdapter.ExerciseViewHolder> {

    private final List<TrainingItem> exerciseList;
    private final boolean isReadOnlyMode;
    private final SparseBooleanArray selectionMap = new SparseBooleanArray(); // 勾選狀態


    // 建構子 - 創建模式（可選取）
    public SelectableExerciseAdapter(@NonNull List<TrainingItem> exerciseList) {
        this(exerciseList, false);
    }

    // 建構子 - 可指定只讀模式
    public SelectableExerciseAdapter(@NonNull List<TrainingItem> exerciseList, boolean isReadOnlyMode) {
        this.exerciseList = exerciseList;
        this.isReadOnlyMode = isReadOnlyMode;

        if (isReadOnlyMode) {
            // 只讀模式預設全部顯示選取
            for (int i = 0; i < exerciseList.size(); i++) {
                selectionMap.put(i, true);
            }
        }
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 這裡要用你的白底卡片 layout 檔名（下面假設就是 selectable_exercise_item）
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.selectable_exercise_item, parent, false);
        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        TrainingItem item = exerciseList.get(position);
        boolean isChecked = selectionMap.get(position, false);
        holder.bind(item, isReadOnlyMode, isChecked);

        // 點整張卡切換勾選（只讀模式不動）
        /*
        holder.itemView.setOnClickListener(v -> {
            if (isReadOnlyMode) return;
            boolean newState = !selectionMap.get(holder.getBindingAdapterPosition(), false);
            selectionMap.put(holder.getBindingAdapterPosition(), newState);
            notifyItemChanged(holder.getBindingAdapterPosition());
        });*/
    }

    @Override
    public int getItemCount() {
        return exerciseList == null ? 0 : exerciseList.size();
    }

    // 取得已勾選清單
    public List<TrainingItem> getSelectedItems() {
        List<TrainingItem> selected = new ArrayList<>();
        for (int i = 0; i < exerciseList.size(); i++) {
            if (selectionMap.get(i, false)) {
                selected.add(exerciseList.get(i));
            }
        }
        return selected;
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final ImageView exerciseImage;
        final MaterialCardView cardContainer; // 新增

        ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.exercise_title);
            exerciseImage = itemView.findViewById(R.id.exercise_image);
            cardContainer = itemView.findViewById(R.id.card_container); // ✅ 初始化
        }

        void bind(@NonNull TrainingItem exercise, boolean isReadOnly, boolean isChecked) {
            // 標題
            titleText.setText(exercise.title == null ? "" : exercise.title);

            // 圖片
            int resId = 0;
            if (exercise.imageResName != null && !exercise.imageResName.isEmpty()) {
                resId = itemView.getResources().getIdentifier(
                        exercise.imageResName, "drawable", itemView.getContext().getPackageName());
            }
            exerciseImage.setImageResource(resId != 0 ? resId : R.drawable.ic_launcher_foreground);

            // ✅ 用框線顯示選中狀態
            cardContainer.setStrokeWidth(isChecked ? 4 : 0);
            cardContainer.setStrokeColor(
                    isChecked ? itemView.getResources().getColor(R.color.teal_700)
                            : itemView.getResources().getColor(android.R.color.transparent));

            // 只讀模式：視覺微弱化
            itemView.setAlpha(isReadOnly ? 0.95f : 1f);
        }
    }

}
