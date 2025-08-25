package com.example.rehabilitationapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.rehabilitationapp.databinding.ActivityMainBinding;
import com.example.rehabilitationapp.data.AppDatabase;


public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DB_DEBUG_TAG", "=== 1. MainActivity onCreate started ===");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 設置 Navigation
        setupNavigation();

        // 讓系統自動處理窗口插入
        setupAutoWindowInsets();

        try {
            Log.d("DB_DEBUG_TAG", "=== 2. Calling AppDatabase.getInstance ===");
            AppDatabase db = AppDatabase.getInstance(this);
            Log.d("DB_DEBUG_TAG", "=== 3. AppDatabase.getInstance returned: " + (db != null) + " ===");

            // 額外檢查
            if (db != null) {
                Log.d("DB_DEBUG_TAG", "=== 4. Database object created successfully ===");
            }
        } catch (Exception e) {
            Log.e("DB_DEBUG_TAG", "=== ERROR: " + e.getMessage() + " ===");
            e.printStackTrace();
        }

    }

    private void setupNavigation() {
        //可以設定監聽
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // 👇 在這裡加監聽
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.loginFragment) {
                binding.navView.setVisibility(View.GONE);
                getSupportActionBar().hide();
            } else {
                binding.navView.setVisibility(View.VISIBLE);
                getSupportActionBar().show();
            }
        });
    }

    private void setupAutoWindowInsets() {
        // 為整個根視圖設置自動窗口插入處理
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            // 讓系統自動處理狀態欄
            int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            view.setPadding(0, topInset, 0, 0);

            return windowInsets;
        });

        // 為 Fragment 容器設置底部窗口插入處理
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.nav_host_fragment_activity_main),
                (view, windowInsets) -> {
                    // 獲取系統導航欄高度
                    int bottomInset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

                    // 獲取 Bottom Navigation 的高度
                    binding.navView.post(() -> {
                        int navViewHeight = binding.navView.getHeight();
                        // 設置 Fragment 容器的底部內邊距 = 系統導航欄高度
                        // ConstraintLayout 已經處理了 Bottom Navigation，所以只需要處理系統導航欄
                        view.setPadding(0, 0, 0, bottomInset);
                    });

                    // 消費掉這個窗口插入，不讓子視圖再處理
                    return WindowInsetsCompat.CONSUMED;
                }
        );
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}