// app/src/main/java/com/example/rehabilitationapp/ui/notifications/TrainingCardAdapter.java
package com.example.rehabilitationapp.ui.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rehabilitationapp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrainingCardAdapter extends RecyclerView.Adapter<TrainingCardAdapter.VH> {

    // 一筆要顯示在小卡上的資料
    public static class UiRow {
        public final String trainingID;   // ★ 新增：用來識別是哪一筆紀錄
        public final String title;        // 名稱（最多 4 字，UI 再限寬）
        public final String time;         // HH:mm
        public final int achievedTimes;   // 完成次數

        public UiRow(String trainingID, String title, String time, int achievedTimes) {
            this.trainingID = trainingID;  // ★ 新增
            this.title = title;
            this.time = time;
            this.achievedTimes = achievedTimes;
        }
    }

    // ★ 點擊回調介面
    public interface OnItemClickListener {
        void onItemClick(UiRow row);
    }

    private OnItemClickListener listener;

    // ★ 設定點擊監聽器
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }


    private final List<UiRow> items = new ArrayList<>();

    public void setItems(List<UiRow> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.his_record_training_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        UiRow row = items.get(position);

        // ① 序號（01, 02, 03...）
        h.tvOrder.setText(String.format(Locale.getDefault(), "%02d", position + 1));

        // ② 名稱
        h.tvName.setText(row.title);

        // ③ 時間
        h.tvTime.setText(row.time);

        // ④ 完成次數：顯示在時間右邊
        h.tvCount.setText(row.achievedTimes + "次");

        // ★ 點擊事件
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(row);
            }
        });

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrder, tvName, tvTime, tvCount;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOrder = itemView.findViewById(R.id.tvOrder);
            tvName  = itemView.findViewById(R.id.tvName);
            tvTime  = itemView.findViewById(R.id.tvTime);
            tvCount = itemView.findViewById(R.id.tvCount);
        }
    }
}
