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

    public static class UiRow {
        public final String title;   // 名稱（最多 4 字，UI 再限寬）
        public final String time;    // HH:mm
        public UiRow(String title, String time) { this.title = title; this.time = time; }
    }

    private final List<UiRow> items = new ArrayList<>();

    public void setItems(List<UiRow> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.his_record_training_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TrainingCardAdapter.UiRow row = items.get(position);
        h.tvOrder.setText(String.format(Locale.getDefault(), "%02d", position + 1)); // ① 序號
        h.tvName.setText(row.title);                                                 // ② 名稱
        h.tvTime.setText(row.time);                                                  // ③ 時:分
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrder, tvName, tvTime;
        VH(@NonNull View itemView) {
            super(itemView);
            tvOrder = itemView.findViewById(R.id.tvOrder);
            tvName  = itemView.findViewById(R.id.tvName);
            tvTime  = itemView.findViewById(R.id.tvTime);
        }
    }
}
