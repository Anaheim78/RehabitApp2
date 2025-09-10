// app/src/main/java/com/example/rehabilitationapp/ui/notifications/NotificationsFragment.java
package com.example.rehabilitationapp.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.rehabilitationapp.databinding.FragmentNotificationsBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;

    private TrainingCardAdapter row1Adapter;
    private TrainingCardAdapter row2Adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 1) CalendarView 監聽
        binding.calendar.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                // month 從 0 開始，顯示加 1
                refreshLists(year, month + 1, dayOfMonth);
            }
        });

        // 2) RecyclerView 設定（橫向）
        row1Adapter = new TrainingCardAdapter();
        row2Adapter = new TrainingCardAdapter();

        binding.listRow1.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.listRow1.setHasFixedSize(true);
        binding.listRow1.setAdapter(row1Adapter);

        binding.listRow2.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.listRow2.setHasFixedSize(true);
        binding.listRow2.setAdapter(row2Adapter);

        // （可選）加入間距
        // binding.listRow1.addItemDecoration(new HorizontalSpaceDecoration(8, container));
        // binding.listRow2.addItemDecoration(new HorizontalSpaceDecoration(8, container));

        // 3) 首次進來先用當天日期填資料
        java.util.Calendar c = java.util.Calendar.getInstance();
        refreshLists(c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));

        return root;
    }

    private void refreshLists(int y, int m, int d) {
        // TODO: 這裡把假資料換成 DB 查詢結果
        row1Adapter.setItems(mockRowItems(y, m, d, 1));
        row2Adapter.setItems(mockRowItems(y, m, d, 2));
    }

    private List<String> mockRowItems(int y, int m, int d, int row) {
        // 先給假資料，之後換成你的 AppDatabase 查詢
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            list.add(String.format(Locale.getDefault(), "R%d - %02d/%02d  #%d", row, m, d, i));
        }
        return list;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
