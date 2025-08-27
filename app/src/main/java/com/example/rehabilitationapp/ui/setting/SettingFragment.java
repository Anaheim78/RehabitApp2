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

public class SettingFragment extends Fragment {

    public SettingFragment() { super(R.layout.fragment_setting); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btn = view.findViewById(R.id.btnEditProfile);
        TextView tvUserId = view.findViewById(R.id.tvUserId);
        TextView tvEmail  = view.findViewById(R.id.tvEmail);

        // 滑塊
        View thumbUi = view.findViewById(R.id.thumbUi);

        // DB
        final AppDatabase db = AppDatabase.getInstance(requireContext());
        final UserDao userDao = db.userDao();

        // 存當前登入 userId
        final String[] currentUserId = { null };

        new Thread(() -> {
            User me = userDao.findLoggedInOne(); // 你在 UserDao 要有這個查詢

            if (me != null) {
                currentUserId[0] = me.userId;

                requireActivity().runOnUiThread(() -> {
                    // 更新畫面顯示
                    tvUserId.setText("ID: " + me.userId);
                    if (tvEmail != null && me.email != null) {
                        tvEmail.setText("e-mail: " + me.email);
                    }

                    // UI Style → 黃色滑塊位置
                    if (thumbUi != null) {
                        // 先取父容器寬度（segmentUi 寬度會在 layout 完成後才知道）
                        thumbUi.post(() -> {
                            View segment = view.findViewById(R.id.segmentUi);
                            if (segment != null) {
                                int segmentWidth = segment.getWidth();
                                int half = segmentWidth / 2;

                                ViewGroup.LayoutParams lp = thumbUi.getLayoutParams();
                                lp.width = half;
                                thumbUi.setLayoutParams(lp);

                                if ("F".equalsIgnoreCase(me.uiStyle)) {
                                    // 女生 → 放右邊
                                    thumbUi.setX(half);
                                } else {
                                    // 預設男生 → 放左邊
                                    thumbUi.setX(0);
                                }
                            }
                        });
                    }
                });
            }
        }).start();

        // 「修改個人資料」
        btn.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), EditProfileActivity.class);
            i.putExtra("EXTRA_USER_ID", currentUserId[0]);
            startActivity(i);
        });
    }
}
