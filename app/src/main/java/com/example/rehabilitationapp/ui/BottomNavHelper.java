package com.example.rehabilitationapp.ui;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import com.example.rehabilitationapp.MainActivity;
import com.example.rehabilitationapp.R;

// 提供舊版的xml的NAV跳轉功能
public class BottomNavHelper {

    /**
     * 設定底部導航點擊事件
     * @param activity 當前的 Activity
     * @param bottomNav 底部導航的 View (透過 findViewById(R.id.bottom_nav) 取得)
     */
    public static void setup(AppCompatActivity activity, View bottomNav) {
        if (bottomNav == null) return;

        View tabHome = bottomNav.findViewById(R.id.tab_home);
        View tabPlan = bottomNav.findViewById(R.id.tab_plan);
        View tabRecord = bottomNav.findViewById(R.id.tab_record);
        View tabSetting = bottomNav.findViewById(R.id.tab_setting);

        if (tabHome != null) {
            tabHome.setOnClickListener(v -> navigateToTab(activity, "home"));
        }
        if (tabPlan != null) {
            tabPlan.setOnClickListener(v -> navigateToTab(activity, "plan"));
        }
        if (tabRecord != null) {
            tabRecord.setOnClickListener(v -> navigateToTab(activity, "record"));
        }
        if (tabSetting != null) {
            tabSetting.setOnClickListener(v -> navigateToTab(activity, "setting"));
        }
    }

    /**
     * 跳轉到 MainActivity 並切換到指定 Tab
     */
    private static void navigateToTab(AppCompatActivity activity, String tabName) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("start_tab", tabName);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}