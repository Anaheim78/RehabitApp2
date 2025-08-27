package com.example.rehabilitationapp;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.ui.home.HomeFragment;
import com.example.rehabilitationapp.ui.login.LoginFragment;
import com.example.rehabilitationapp.ui.notifications.NotificationsFragment;
import com.example.rehabilitationapp.ui.plan.PlanFragment;
import com.example.rehabilitationapp.ui.setting.SettingFragment;

public class MainActivity extends AppCompatActivity {

    private FrameLayout tabHome, tabPlan, tabRecord, tabSetting;
    private ImageView iconHome, iconPlan, iconRecord, iconSetting;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabHome   = findViewById(R.id.tab_home);
        tabPlan   = findViewById(R.id.tab_plan);
        tabRecord = findViewById(R.id.tab_record);
        tabSetting= findViewById(R.id.tab_setting);

        iconHome   = findViewById(R.id.icon_home);
        iconPlan   = findViewById(R.id.icon_plan);
        iconRecord = findViewById(R.id.icon_record);
        iconSetting= findViewById(R.id.icon_setting);

        userDao = AppDatabase.getInstance(this).userDao();

        new Thread(() -> {
            boolean loggedIn = userDao.countLoggedIn() > 0;
            runOnUiThread(() -> {
                if (!loggedIn) {
                    switchFragment(new LoginFragment());
                } else {
                    switchFragment(new HomeFragment());
                    selectTab(R.id.tab_home);
                }
            });
        }).start();

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

    public void switchFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.nav_host_fragment_activity_main, fragment);
        tx.commit();
    }

    public void selectTab(int tabId) {
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
