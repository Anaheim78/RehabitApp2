package com.example.rehabilitationapp.ui.setting;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.widget.SwitchCompat;

//SettingFragment : 綁定layout
//onViewCreated() : 初次渲染欄位格式等，不包括資料
//loadUserAndUpdateUI : 更新內文，重回跟進入都跑一遍

//btnEditProfile.setOnClickListener(v ->  按鍵綁定 "個人資料編輯"
//btnChangePassword.setOnClickListener(v ->  按鍵綁定 "密碼變更"
//btnContact.setOnClickListener(v ->  按鍵綁定 "聯洛"

public class SettingFragment extends Fragment {

    public SettingFragment() { super(R.layout.fragment_setting); }

    // === Views ===
    private Button btnEditProfile;
    private Button btnChangePassword;
    private Button btnContact;
    private TextView tvUserId, tvEmail, tvName, tvBirthday, tvGender, tvJoinDate;
    private View segmentUi, thumbUi;
    private SwitchCompat switchVideoRecording;
    // === DB ===
    private UserDao userDao;

    // === State ===
    private String currentUserId;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ---- Bind views ----
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnChangePassword = view.findViewById((R.id.btnChangePassword));
        btnContact = view.findViewById((R.id.btnContact));
        tvUserId   = view.findViewById(R.id.tvUserId);
        tvEmail    = view.findViewById(R.id.tvEmail);
        tvName     = view.findViewById(R.id.tvName);
        tvBirthday = view.findViewById(R.id.tvBirthday);
        tvGender   = view.findViewById(R.id.tvGender);
        tvJoinDate = view.findViewById(R.id.tvJoinDate);

        segmentUi  = view.findViewById(R.id.segmentUi);
        thumbUi    = view.findViewById(R.id.thumbUi);

        // 🆕 錄影開關
        switchVideoRecording = view.findViewById(R.id.switchVideoRecording);

        SharedPreferences appPrefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        boolean videoEnabled = appPrefs.getBoolean("video_recording_enabled", true);
        switchVideoRecording.setChecked(videoEnabled);

        switchVideoRecording.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPrefs.edit().putBoolean("video_recording_enabled", isChecked).apply();
        });
        // ---- DB ----
        userDao = AppDatabase.getInstance(requireContext()).userDao();
    }



    @Override
    public void onResume() {
        super.onResume();
        // 從 Edit 回來會走這裡 → 再刷一次
        loadUserAndUpdateUI();
    }

    /** 從 DB 載入目前登入使用者，並更新整個畫面 */
    private void loadUserAndUpdateUI() {
        new Thread(() -> {
            User me = userDao.findLoggedInOne();
            if (me == null || !isAdded()) return;

            currentUserId = me.userId;

            requireActivity().runOnUiThread(() -> {
                // ---- 文字欄位 ----
                tvUserId.setText("ID: " + nz(me.userId));
                if (me.email != null)     tvEmail.setText("e-mail: " + me.email);
                if (me.name != null)      tvName.setText("姓名：" + me.name);
                if (me.birthday != null)  tvBirthday.setText("生日：" + me.birthday);
                tvGender.setText("性別：" + ("F".equalsIgnoreCase(me.gender) ? "女" : "男"));
                if (me.createdAtFormatted != null)
                    tvJoinDate.setText("加入日期：" + me.createdAtFormatted);

                // ---- UI Style 滑塊 ----
                if (segmentUi != null && thumbUi != null) {
                    segmentUi.post(() -> {
                        int segmentWidth = segmentUi.getWidth();
                        int half = segmentWidth / 2;

                        ViewGroup.LayoutParams lp = thumbUi.getLayoutParams();
                        lp.width = half;
                        thumbUi.setLayoutParams(lp);

                        thumbUi.setX("F".equalsIgnoreCase(me.uiStyle) ? half : 0);
                    });
                }

                // ---- 在這裡綁三個按鈕（永遠用最新的 me.userId）----
                final String uid = me.userId;

                btnEditProfile.setOnClickListener(v -> {
                    Intent i = new Intent(requireContext(), EditProfileActivity.class);
                    i.putExtra("EXTRA_USER_ID", uid);
                    startActivity(i);
                });

                btnChangePassword.setOnClickListener(v -> {
                    Intent i = new Intent(requireContext(), EditPwdActivity.class);
                    i.putExtra("EXTRA_USER_ID", uid);
                    startActivity(i);
                });

                btnContact.setOnClickListener(v -> {
                    Intent i = new Intent(requireContext(), EditContactActivity.class);
                    i.putExtra("EXTRA_USER_ID", uid);
                    startActivity(i);
                });
            });
        }).start();
    }


    private static String nz(String s) { return s == null ? "" : s; }
}
