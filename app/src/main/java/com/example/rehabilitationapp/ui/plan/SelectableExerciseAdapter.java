package com.example.rehabilitationapp.ui.plan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.model.TrainingItem;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SelectableExerciseAdapter extends RecyclerView.Adapter<SelectableExerciseAdapter.ExerciseViewHolder> {

    private final List<TrainingItem> exerciseList;
    //全部都改成isReadOnlyMode _ false，後來UI需求不同，都要可以改，只影響點下去有沒有框
    private final boolean isReadOnlyMode;
    private int selectedPosition = RecyclerView.NO_POSITION; // 單選互斥

    // 建構子 - 創建模式（可選取）
    public SelectableExerciseAdapter(@NonNull List<TrainingItem> exerciseList) {
        this(exerciseList, false);
    }

    // 建構子 - 可指定只讀模式
    public SelectableExerciseAdapter(@NonNull List<TrainingItem> exerciseList, boolean isReadOnlyMode) {
        this.exerciseList = exerciseList;
        this.isReadOnlyMode = isReadOnlyMode;
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.selectable_exercise_item, parent, false);
        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        TrainingItem item = exerciseList.get(position);
        boolean isSelected = (position == selectedPosition);
        holder.bind(item, isReadOnlyMode, isSelected);
        // 🔍 加入這行確認點擊監聽器有被設定
        android.util.Log.d("SelectableAdapter", "Setting click listener for position: " + position);

        holder.itemView.setOnClickListener(v -> {
            // 🔍 加入這行確認點擊事件有被觸發
            android.util.Log.d("SelectableAdapter", "Click detected!");
            android.util.Log.d("SelectableAdapter", "isReadOnlyMode: " + isReadOnlyMode);
            if (isReadOnlyMode) return;
            android.util.Log.d("SelectableAdapter", "pass the isReadOnlyMode check");
            int clickedPos = holder.getAdapterPosition();
            if (clickedPos == RecyclerView.NO_POSITION) return;

            // 點同一個：保持選中（避免看起來只閃一下）
            if (selectedPosition == clickedPos) return;

            int oldPos = selectedPosition;
            selectedPosition = clickedPos;
            // 🔍 加入這行來確認選擇狀態
            android.util.Log.d("SelectableAdapter", "Selected position: " + selectedPosition);

            if (oldPos != RecyclerView.NO_POSITION) notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return exerciseList == null ? 0 : exerciseList.size();
    }

    // 取得已勾選清單（單選：0 或 1 筆）
    public List<TrainingItem> getSelectedItems() {
        List<TrainingItem> selected = new ArrayList<>();
        if (selectedPosition != RecyclerView.NO_POSITION) {
            selected.add(exerciseList.get(selectedPosition));
        }
        return selected;
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final ImageView exerciseImage;
        final MaterialCardView cardContainer;

        ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.exercise_title);
            exerciseImage = itemView.findViewById(R.id.exercise_image);
            cardContainer = itemView.findViewById(R.id.card_container);
        }

        void bind(@NonNull TrainingItem exercise, boolean isReadOnly, boolean isSelected) {
            // 標題
            android.util.Log.d("SelectableAdapter", "Bind position: " + getAdapterPosition());
            titleText.setText(exercise.title == null ? "" : exercise.title);

            // 圖片
            int resId = 0;
            if (exercise.imageResName != null && !exercise.imageResName.isEmpty()) {
                resId = itemView.getResources().getIdentifier(
                        exercise.imageResName, "drawable", itemView.getContext().getPackageName());
            }
            exerciseImage.setImageResource(resId != 0 ? resId : R.drawable.ic_launcher_foreground);

            // ✅ 框線顯示（單選）
            cardContainer.setStrokeWidth(isSelected ? 4 : 0);
            cardContainer.setStrokeColor(
                    isSelected
                            ? ContextCompat.getColor(itemView.getContext(), R.color.button_border)
                            : ContextCompat.getColor(itemView.getContext(), android.R.color.transparent)
            );

            // 只讀模式：視覺淡化
            itemView.setAlpha(isReadOnly ? 0.95f : 1f);
        }
    }
}
