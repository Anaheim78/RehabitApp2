package com.example.rehabilitationapp.ui.plan;

import android.util.Log;
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
import java.util.List;

public class SelectableExerciseAdapter extends RecyclerView.Adapter<SelectableExerciseAdapter.ExerciseViewHolder> {

    private List<TrainingItem> exerciseList;

    public SelectableExerciseAdapter(List<TrainingItem> exerciseList) {
        this.exerciseList = exerciseList;
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.selectable_exercise_item, parent, false);

        Log.d("SelectableExcerciseAdaoater  ","input to bind");

        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        TrainingItem exercise = exerciseList.get(position);
        holder.bind(exercise);
    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
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

        public void bind(TrainingItem exercise) {
            Log.d("SelectableExcerciseAdaoater  ","input to bind");
            titleText.setText(exercise.title);
            descText.setText(exercise.description);

            // 載入圖片
            if (exercise.imageResName != null) {
                int imageResId = itemView.getContext().getResources()
                        .getIdentifier(exercise.imageResName, "drawable", itemView.getContext().getPackageName());
                Log.d("SelectableExcerciseAdaoater "," prepare to set Image:，iconID=="+imageResId);
                if (imageResId != 0) {
                    exerciseImage.setImageResource(imageResId);
                } else {
                    exerciseImage.setImageResource(R.drawable.cheeks); // 預設圖片
                }
            } else {
                exerciseImage.setImageResource(R.drawable.cheeks); // 預設圖片
            }

            // 點擊整行切換勾選狀態
            itemView.setOnClickListener(v -> {
                checkbox.setChecked(!checkbox.isChecked());
            });
        }
    }
}