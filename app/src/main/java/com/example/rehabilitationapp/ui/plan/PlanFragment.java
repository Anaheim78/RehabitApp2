package com.example.rehabilitationapp.ui.plan;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.model.TrainingPlan;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanFragment extends Fragment {

    private RecyclerView recyclerView;
    private ExecutorService executor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_plan, container, false);

        recyclerView = root.findViewById(R.id.recycler_plan);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 設置 + 號按鈕的點擊事件
        ImageView addPlanBtn = root.findViewById(R.id.add_plan_btn);
        addPlanBtn.setOnClickListener(v -> {
            //Toast.makeText(getContext(), "點擊了 + 號，準備創建新計劃", Toast.LENGTH_SHORT).show();
            Log.d("PlanFragment","set Listener for addPlan");
            // 跳轉到詳細頁面，但使用特殊模式顯示所有運動項目
            Intent intent = new Intent(getContext(), TrainingDetailActivity.class);
            intent.putExtra("mode", "create_new");  // 特殊模式標記
            intent.putExtra("plan_title", "創建新計劃");
            startActivity(intent);
        });

        loadPlans();

        return root;
    }

    private void loadPlans() {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                List<TrainingPlan> plans = db.trainingPlanDao().getAll();

                Log.d("DB_DEBUG_TAG", "=== Found " + plans.size() + " training plans ===");

                if (getActivity() != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        PlanAdapter adapter = new PlanAdapter(plans, plan -> {
                            Log.d("test_PlanDetail", "=== Clicked plan ID: " + plan.id + " ===");
                            // 跳轉到詳細頁面
                            Intent intent = new Intent(getContext(), TrainingDetailActivity.class);
                            intent.putExtra("plan_title", plan.title);
                            intent.putExtra("plan_description", plan.description);
                            intent.putExtra("plan_id", plan.id);
                            startActivity(intent);
                        });
                        recyclerView.setAdapter(adapter);
                    });
                }
            } catch (Exception e) {
                Log.e("DB_DEBUG_TAG", "Error loading plans", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    @Override
    public  void onResume() {
        super.onResume();
        loadPlans();
    }

}