package com.example.rehabilitationapp.ui.plan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.model.TrainingPlan;

import java.util.List;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PlanViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TrainingPlan plan);
    }

    private List<TrainingPlan> planList;
    private OnItemClickListener listener;

    public PlanAdapter(List<TrainingPlan> planList, OnItemClickListener listener) {
        this.planList = planList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.plan_card_item, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        TrainingPlan plan = planList.get(position);
        holder.bind(plan, listener);
    }

    @Override
    public int getItemCount() {
        return planList.size();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView descText;
        ImageView image;

        public PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.plan_title);
            descText = itemView.findViewById(R.id.plan_description);
            image = itemView.findViewById(R.id.plan_image);
        }

        public void bind(TrainingPlan plan, OnItemClickListener listener) {
            titleText.setText(plan.title);
            descText.setText(plan.description);

            // 如果有設定圖片資源名，可載入對應圖片
            if (plan.imageResName != null) {
                int imageResId = itemView.getContext().getResources()
                        .getIdentifier(plan.imageResName, "drawable", itemView.getContext().getPackageName());
                if (imageResId != 0) {
                    image.setImageResource(imageResId);
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(plan);
            });
        }
    }
}
