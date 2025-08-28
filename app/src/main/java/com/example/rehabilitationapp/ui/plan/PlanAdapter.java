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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PlanViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TrainingPlan plan);
    }

    private final List<TrainingPlan> planList;
    private final OnItemClickListener listener;

    public PlanAdapter(@NonNull List<TrainingPlan> planList, OnItemClickListener listener) {
        this.planList = planList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plan_card, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        TrainingPlan plan = planList.get(position);
        holder.bind(plan, listener);
    }

    @Override
    public int getItemCount() {
        return planList == null ? 0 : planList.size();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPlan;
        TextView tvTitle;
        TextView tvDate;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPlan = itemView.findViewById(R.id.imgPlan);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate  = itemView.findViewById(R.id.tvDate);
        }

        void bind(final TrainingPlan plan, final OnItemClickListener listener) {
            // 標題
            tvTitle.setText(plan.title != null ? plan.title : "");

            // ✅ 永遠顯示今天日期（MM/dd/yy，例如 08/28/25）
            String today = new SimpleDateFormat("MM/dd/yy", Locale.getDefault())
                    .format(new Date());
            tvDate.setText(today);

            // 圖片（imageResName -> drawable）
            int resId = 0;
            if (plan.imageResName != null && !plan.imageResName.isEmpty()) {
                resId = itemView.getResources().getIdentifier(
                        plan.imageResName, "drawable", itemView.getContext().getPackageName()
                );
            }
            imgPlan.setImageResource(resId != 0 ? resId : R.drawable.ic_home_cheekpuff);

            // 點擊
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(plan);
            });
        }
    }
}
