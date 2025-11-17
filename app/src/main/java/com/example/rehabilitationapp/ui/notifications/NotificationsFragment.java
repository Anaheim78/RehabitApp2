// app/src/main/java/com/example/rehabilitationapp/ui/notifications/NotificationsFragment.java
package com.example.rehabilitationapp.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.rehabilitationapp.data.model.TrainingHistoryWithTitle;
import com.example.rehabilitationapp.databinding.FragmentNotificationsBinding;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.model.TrainingHistory;
import com.example.rehabilitationapp.data.model.TrainingItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private TrainingCardAdapter trainingAdapter;

    // analysisType(大寫) -> title 對照表（啟動時快取一次）
    private final Map<String, String> typeTitleMap = new HashMap<>();
    private final SimpleDateFormat HHMM = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // RecyclerView：4 欄 Grid（外觀不變）
        trainingAdapter = new TrainingCardAdapter();
        binding.trainingRecordsList.setLayoutManager(new GridLayoutManager(getContext(), 4));
        binding.trainingRecordsList.setHasFixedSize(true);
        binding.trainingRecordsList.setAdapter(trainingAdapter);

        // 載入 item 對照表（背景）
        new Thread(this::loadTypeMapIfNeeded).start();

        // 日曆選日 → 依當天查 DB
        binding.calendar.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int monthZeroBased, int dayOfMonth) {
                loadDay(year, monthZeroBased, dayOfMonth);
            }
        });

        // 首次進來先載入今天
        Calendar c = Calendar.getInstance();
        loadDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        return root;
    }

    /** 把 training_items 讀成 map：analysisType(大寫) -> title */
    private void loadTypeMapIfNeeded() {
        if (!typeTitleMap.isEmpty()) return;
        AppDatabase db = AppDatabase.getInstance(requireContext());
        List<TrainingItem> items = db.trainingItemDao().getAll();
        for (TrainingItem it : items) {
            if (it.analysisType != null && it.title != null) {
                typeTitleMap.put(it.analysisType.toUpperCase(Locale.ROOT), it.title);
            }
        }
    }

    /** 依日期查當天歷程，排序後丟給 adapter（顯示三段文字） */
    private void loadDay(int year, int monthZeroBased, int day) {
        // 計算當日 00:00 ~ 23:59:59.999 (local) 的毫秒
        Calendar cal = Calendar.getInstance();
        cal.set(year, monthZeroBased, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startMs = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endMs = cal.getTimeInMillis() - 1;

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // 依時間排序的查詢（請在 TrainingHistoryDao 提供下列方法）
            // @Query("SELECT * FROM trainingHistory WHERE createAt BETWEEN :startMs AND :endMs ORDER BY createAt ASC")
            // List<TrainingHistory> getByDay(long startMs, long endMs);
            List<TrainingHistoryWithTitle> list = db.trainingHistoryDao().getHistoryWithTitleForDay(startMs, endMs);
            // 這裡要產生 List<UiRow>，不是 List<String>
            List<TrainingCardAdapter.UiRow> display = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                TrainingHistoryWithTitle h = list.get(i);

                // 名稱（JOIN 成功就用 title，否則用 trainingLabel）
                String labelKey = h.trainingLabel == null ? "" : h.trainingLabel.toUpperCase(Locale.ROOT);
                String name = typeTitleMap.getOrDefault(labelKey, h.title != null ? h.title : h.trainingLabel);
                if (name != null && name.length() > 4) name = name.substring(0, 4);

                // 時間 HH:mm
                String time = HHMM.format(h.createAt);
                int count = h.achievedTimes;

                display.add(new TrainingCardAdapter.UiRow(name, time, count));
            }

            requireActivity().runOnUiThread(() -> trainingAdapter.setItems(display));
            // 如果你的 adapter 已經有 setData(List<UiRow>) 可改成：
            // requireActivity().runOnUiThread(() -> adapter.setData(uiRows));
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
