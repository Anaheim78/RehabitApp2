package com.example.rehabilitationapp;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.rehabilitationapp.ui.home.HomeFragment;
import com.example.rehabilitationapp.ui.notifications.NotificationsFragment;
import com.example.rehabilitationapp.ui.plan.PlanFragment;
import com.example.rehabilitationapp.ui.setting.SettingFragment;

public class MainActivity extends AppCompatActivity {

    // tab 容器
    private FrameLayout tabHome, tabPlan, tabRecord, tabSetting;

    // icon
    private ImageView iconHome, iconPlan, iconRecord, iconSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 綁定元件（容器）
        tabHome   = findViewById(R.id.tab_home);
        tabPlan   = findViewById(R.id.tab_plan);
        tabRecord = findViewById(R.id.tab_record);
        tabSetting= findViewById(R.id.tab_setting);

        // 綁定元件（圖示）
        iconHome   = findViewById(R.id.icon_home);
        iconPlan   = findViewById(R.id.icon_plan);
        iconRecord = findViewById(R.id.icon_record);
        iconSetting= findViewById(R.id.icon_setting);

        // 預設顯示 Home
        switchFragment(new HomeFragment());
        selectTab(R.id.tab_home);

        // 點擊事件
        tabHome.setOnClickListener(v -> {
            switchFragment(new HomeFragment());
            selectTab(R.id.tab_home);
        });

        tabPlan.setOnClickListener(v -> {
            switchFragment(new PlanFragment());
            selectTab(R.id.tab_plan);
        });

        tabRecord.setOnClickListener(v -> {
            switchFragment(new NotificationsFragment());
            selectTab(R.id.tab_record);
        });

        tabSetting.setOnClickListener(v -> {
            switchFragment(new SettingFragment());
            selectTab(R.id.tab_setting);
        });
    }

    /** 切換 Fragment */
    private void switchFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.nav_host_fragment_activity_main, fragment);
        tx.commit();
    }

    /** 切換 Tab 選中狀態（會觸發 selector） */
    private void selectTab(int tabId) {
        iconHome.setSelected(false);
        iconPlan.setSelected(false);
        iconRecord.setSelected(false);
        iconSetting.setSelected(false);

        if (tabId == R.id.tab_home)       iconHome.setSelected(true);
        else if (tabId == R.id.tab_plan)  iconPlan.setSelected(true);
        else if (tabId == R.id.tab_record)iconRecord.setSelected(true);
        else if (tabId == R.id.tab_setting) iconSetting.setSelected(true);
    }
}
