package com.example.rehabilitationapp.ui.plan;

import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.model.TrainingItem;
import java.util.ArrayList;
import java.util.List;

public class SelectableExerciseAdapter extends RecyclerView.Adapter<SelectableExerciseAdapter.ExerciseViewHolder> {

    private List<TrainingItem> exerciseList;
    private boolean isReadOnlyMode = false;
    private SparseBooleanArray selectionMap = new SparseBooleanArray();  // ✅ 勾選狀態記錄器

    // 建構子 - 創建模式
    public SelectableExerciseAdapter(List<TrainingItem> exerciseList) {
        this.exerciseList = exerciseList;
        this.isReadOnlyMode = false;
    }

    // 建構子 - 檢視模式
    public SelectableExerciseAdapter(List<TrainingItem> exerciseList, boolean isReadOnlyMode) {
        this.exerciseList = exerciseList;
        this.isReadOnlyMode = isReadOnlyMode;

        if (isReadOnlyMode) {
            // ✅ 若為只讀模式，預設全部都勾選
            for (int i = 0; i < exerciseList.size(); i++) {
                selectionMap.put(i, true);
            }
        }
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
        TrainingItem exercise = exerciseList.get(position);
        boolean isChecked = selectionMap.get(position, false);
        holder.bind(exercise, isReadOnlyMode, isChecked, v -> {
            // ✅ 點擊時更新 selectionMap
            boolean newState = !selectionMap.get(position, false);
            selectionMap.put(position, newState);
            notifyItemChanged(position);  // 更新畫面
        });
    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
    }

    // ✅ 回傳所有有勾選的項目
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
        TextView titleText;
        TextView descText;
        ImageView exerciseImage;
        CheckBox checkbox;

        public ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.exercise_title);
            descText = itemView.findViewById(R.id.exercise_description);
            exerciseImage = itemView.findViewById(R.id.exercise_image);
            checkbox = itemView.findViewById(R.id.exercise_checkbox);
        }

        public void bind(TrainingItem exercise, boolean isReadOnly, boolean isChecked, View.OnClickListener toggleListener) {
            titleText.setText(exercise.title);
            descText.setText(exercise.description);

            // 圖片
            if (exercise.imageResName != null) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        exercise.imageResName, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    exerciseImage.setImageResource(resId);
                } else {
                    exerciseImage.setImageResource(R.drawable.cheeks);
                }
            } else {
                exerciseImage.setImageResource(R.drawable.cheeks);
            }

            checkbox.setChecked(isChecked);

            if (isReadOnly) {
                checkbox.setEnabled(false);
                checkbox.setAlpha(0.6f);
                itemView.setOnClickListener(null);
            } else {
                checkbox.setEnabled(true);
                checkbox.setAlpha(1f);
                itemView.setOnClickListener(toggleListener);
                checkbox.setOnClickListener(toggleListener);  // 讓點 Checkbox 也有反應
            }
        }
    }
}
