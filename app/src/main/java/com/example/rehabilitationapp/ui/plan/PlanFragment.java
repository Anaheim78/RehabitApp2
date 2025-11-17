package com.example.rehabilitationapp.ui.plan;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.example.rehabilitationapp.ui.plan.AddPlanActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanFragment extends Fragment {

    private RecyclerView recyclerView;
    private ExecutorService executor;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 設定標題
        requireActivity().setTitle("訓練計畫");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_plan, container, false);

        // 綁 RecyclerView
        recyclerView = root.findViewById(R.id.rvPlans);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 綁 FAB（加號）
//        FloatingActionButton fab = root.findViewById(R.id.fabAddPlan);
//        fab.setOnClickListener(v -> {
//            Intent intent = new Intent(requireContext(), TrainingDetailActivity.class);
//            startActivity(intent);
//        });

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
                        //lamda是要看傳到物件(PlanAdapter)的第幾格，那一格一定要是指有一個method的interface
                        PlanAdapter adapter = new PlanAdapter(plans, plan -> {
                            Log.d("test_PlanDetail", "=== Clicked plan ID: " + plan.id + " ===");
                            // 跳轉到詳細頁面
                            Intent intent = new Intent(getContext(), TrainingDetailActivity.class);
                            intent.putExtra("plan_title", plan.title);
                            intent.putExtra("plan_description", plan.description);
                            intent.putExtra("plan_id", plan.id);
                            startActivity(intent);
                        },
                                () -> {
                                    Intent intent = new Intent(getContext(), AddPlanActivity.class);
                                    startActivity(intent);
                                }

                        );
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
    public void onResume() {
        super.onResume();
        loadPlans();
    }
}

