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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 這份是完整可編譯的版本：
 * 1. Adapter 支援兩種 ViewHolder（正常 item + footer）
 * 2. 泛型用 RecyclerView.ViewHolder（因為要 return 兩種 ViewHolder）
 * 3. onCreateViewHolder / onBindViewHolder / getItemViewType / getItemCount 全部正確
 */
public class PlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TrainingPlan plan);

        // 這個是 footer 專用（你之後想分開就能用）
    }
    public interface OnAddClickListener{
         void onAddClick() ;
    }

    private final List<TrainingPlan> planList;
    private final OnItemClickListener listener;
    private final OnAddClickListener addClickListener;

    // ★ 兩種 view 類型
    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public PlanAdapter(@NonNull List<TrainingPlan> planList,
                       OnItemClickListener listener,OnAddClickListener addClickListener) {
        this.planList = planList;
        this.listener = listener;
        this.addClickListener = addClickListener;
    }

    /**
     * ★ 告訴 RecyclerView：這個 position 是 item 還是 footer？
     * 最後一筆固定是 footer，所以 size() 的位置就是 footer
     */
    @Override
    public int getItemViewType(int position) {
        if (position == planList.size()) {
            return VIEW_TYPE_FOOTER;
        }
        return VIEW_TYPE_NORMAL;
    }

    /**
     * ★ 根據 viewType 回傳不同的 ViewHolder
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_FOOTER) {
            View view = inflater.inflate(R.layout.item_plan_footer, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_plan_card, parent, false);
            return new PlanViewHolder(view);
        }
    }

    /**
     * ★ 綁定資料到對應 ViewHolder
     * Footer 不需要資料 → 直接 return
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        if (getItemViewType(position) == VIEW_TYPE_FOOTER) {
            // footer 不需要 bind 資料
            return;
        }

        TrainingPlan plan = planList.get(position);
        ((PlanViewHolder) holder).bind(plan, listener);
    }

    /**
     * ★ item 數量 + 1（因為最後一個是 footer）
     */
    @Override
    public int getItemCount() {
        return planList.size() + 1;
    }

    // --------------------------
    // ★ 正常 item ViewHolder
    // --------------------------
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

        void bind(final TrainingPlan plan,
                  final OnItemClickListener listener) {

            tvTitle.setText(plan.title != null ? plan.title : "");

            String today = new SimpleDateFormat("MM/dd/yy", Locale.getDefault())
                    .format(new Date());
            tvDate.setText(today);

            int resId = 0;
            if (plan.imageResName != null && !plan.imageResName.isEmpty()) {
                resId = itemView.getResources().getIdentifier(
                        plan.imageResName,
                        "drawable",
                        itemView.getContext().getPackageName()
                );
            }

            imgPlan.setImageResource(resId != 0
                    ? resId
                    : R.drawable.ic_home_cheekpuff);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(plan);
            });
        }
    }

    // --------------------------
    // ★ Footer（最下面那顆 +）
    // --------------------------
    class FooterViewHolder extends RecyclerView.ViewHolder {

        FloatingActionButton fabAddInside;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);

            fabAddInside = itemView.findViewById(R.id.fabAddInside);

            fabAddInside.setOnClickListener(v -> {
                if (addClickListener != null) {
                    addClickListener.onAddClick();
                }
            });
        }
    }
}
