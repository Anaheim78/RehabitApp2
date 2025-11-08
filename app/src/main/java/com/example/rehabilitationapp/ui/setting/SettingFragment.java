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

//SettingFragment : 綁定layout
//onViewCreated() : 初次渲染欄位格式等，不包括資料
//loadUserAndUpdateUI : 更新內文，重回跟進入都跑一遍

//btnEditProfile.setOnClickListener(v ->  按鍵綁定 "個人資料編輯"
//btnChangePassword.setOnClickListener(v ->  按鍵綁定 "密碼變更"

public class SettingFragment extends Fragment {

    public SettingFragment() { super(R.layout.fragment_setting); }

    // === Views ===
    private Button btnEditProfile;
    private Button btnChangePassword;
    private TextView tvUserId, tvEmail, tvName, tvBirthday, tvGender, tvJoinDate;
    private View segmentUi, thumbUi;

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

        tvUserId   = view.findViewById(R.id.tvUserId);
        tvEmail    = view.findViewById(R.id.tvEmail);
        tvName     = view.findViewById(R.id.tvName);
        tvBirthday = view.findViewById(R.id.tvBirthday);
        tvGender   = view.findViewById(R.id.tvGender);
        tvJoinDate = view.findViewById(R.id.tvJoinDate);

        segmentUi  = view.findViewById(R.id.segmentUi);
        thumbUi    = view.findViewById(R.id.thumbUi);

        // ---- DB ----
        userDao = AppDatabase.getInstance(requireContext()).userDao();

        // ---- 初次載入 ----
        loadUserAndUpdateUI();

        // ---- 進入編輯 ----
        btnEditProfile.setOnClickListener(v -> {
            // 以防 currentUserId 還沒載完，這裡再保險查一次
            new Thread(() -> {
                String uid = currentUserId;
                if (uid == null || uid.isEmpty()) {
                    User me = userDao.findLoggedInOne();
                    if (me != null) uid = me.userId;
                }
                if (uid == null) return;

                final String finalUid = uid;
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Intent i = new Intent(requireContext(), EditProfileActivity.class);
                    i.putExtra("EXTRA_USER_ID", finalUid);
                    startActivity(i);
                });
            }).start();
        });

        //--進入修改密碼--
        btnChangePassword.setOnClickListener( v ->{
            // 以防 currentUserId 還沒載完，這裡再保險查一次
            new Thread(() -> {
                String uid = currentUserId;
                if (uid == null || uid.isEmpty()) {
                    User me = userDao.findLoggedInOne();
                    if (me != null) uid = me.userId;
                }
                if (uid == null) return;

                final String finalUid = uid;
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Intent i = new Intent(requireContext(), EditPwdActivity.class);
                    i.putExtra("EXTRA_USER_ID", finalUid);
                    startActivity(i);
                });
            }).start();
        });
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
                    // 等 segment 量到寬度後再設定 thumb
                    segmentUi.post(() -> {
                        int segmentWidth = segmentUi.getWidth();
                        int half = segmentWidth / 2;

                        ViewGroup.LayoutParams lp = thumbUi.getLayoutParams();
                        lp.width = half;
                        thumbUi.setLayoutParams(lp);

                        // "F" → 右側；預設 "M" → 左側
                        thumbUi.setX("F".equalsIgnoreCase(me.uiStyle) ? half : 0);
                    });
                }
            });
        }).start();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
