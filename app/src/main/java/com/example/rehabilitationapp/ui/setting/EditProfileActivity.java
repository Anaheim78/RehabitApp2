package com.example.rehabilitationapp.ui.setting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etEmail, etName, etBirthday;
    private FrameLayout genderSegment, styleSegment;
    private View genderThumb, styleThumb;
    private TextView tvMale, tvFemale, tvStyleOption1, tvStyleOption2, tvJoinDate;
    private Button btnConfirm;

    private boolean isMale = true;    // 性別預設
    private boolean styleOption1 = true; // 介面風格預設


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 先綁layout xml
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        // 從Intent讀取
        String userId = getIntent().getStringExtra("EXTRA_USER_ID");
        // 綁定 View
        etEmail = findViewById(R.id.etEmail);
        etName = findViewById(R.id.etName);
        etBirthday = findViewById(R.id.etBirthday);

        genderSegment = findViewById(R.id.genderSegment);
        genderThumb = findViewById(R.id.genderThumb);
        tvMale = findViewById(R.id.tvMale);
        tvFemale = findViewById(R.id.tvFemale);

        styleSegment = findViewById(R.id.styleSegment);
        styleThumb = findViewById(R.id.styleThumb);
        tvStyleOption1 = findViewById(R.id.tvStyleOption1);
        tvStyleOption2 = findViewById(R.id.tvStyleOption2);

        tvJoinDate = findViewById(R.id.tvJoinDate);
        btnConfirm = findViewById(R.id.btnConfirm);

        // 預設選擇，改DB帶入欄位預設值
        setContent(userId);
        selectGender(true);
        selectStyle(true);

        // 控件設定
        // 點擊切換性別
        tvMale.setOnClickListener(v -> selectGender(true));
        tvFemale.setOnClickListener(v -> selectGender(false));

        // 點擊切換介面風格
        tvStyleOption1.setOnClickListener(v -> selectStyle(true));
        tvStyleOption2.setOnClickListener(v -> selectStyle(false));

        // 確認按鈕
        btnConfirm.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String name = etName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();


            // UI → DB 的映射：中文顯示用，DB 用 "M"/"F"
            String genderDb = isMale ? "M" : "F";
            String styleDb  = styleOption1 ? "M" : "F";

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                UserDao userDao = db.userDao();

                // 先用 Intent 帶來的 userId，沒有就抓目前登入者
                String uid = userId;
                if (uid == null || uid.isEmpty()) {
                    User me = userDao.findLoggedInOne();   // 你已有這個查詢
                    if (me != null) uid = me.userId;
                }

                if (uid != null) {
                    // 一次更新六個欄位（你 DAO 已有）
                    userDao.updateProfile(uid, email, birthday, name, genderDb, styleDb);

                    runOnUiThread(() -> {
                        Toast.makeText(this, "已更新個人資料", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "找不到使用者，無法更新", Toast.LENGTH_SHORT).show()
                    );
                }
            }).start();
        });
    }

    private void selectGender(boolean male) {
        isMale = male;
        genderThumb.post(() -> {
            int halfWidth = genderSegment.getWidth() / 2;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) genderThumb.getLayoutParams();
            params.width = halfWidth;
            params.leftMargin = male ? 0 : halfWidth;
            genderThumb.setLayoutParams(params);
        });

        tvMale.setTextColor(getResources().getColor(male ? R.color.white : R.color.black));
        tvFemale.setTextColor(getResources().getColor(male ? R.color.black : R.color.white));
    }

    private void selectStyle(boolean option1) {
        styleOption1 = option1;
        styleThumb.post(() -> {
            int halfWidth = styleSegment.getWidth() / 2;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) styleThumb.getLayoutParams();
            params.width = halfWidth;
            params.leftMargin = option1 ? 0 : halfWidth;
            styleThumb.setLayoutParams(params);
        });

        tvStyleOption1.setTextColor(getResources().getColor(option1 ? R.color.white : R.color.black));
        tvStyleOption2.setTextColor(getResources().getColor(option1 ? R.color.black : R.color.white));
    }

    // EditProfileActivity.java 內部

    // 取用者資料並帶入 UI；userId 可為 null（就抓目前登入那位）
    private void setContent(@Nullable String userId) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            UserDao userDao = db.userDao();

            // 有帶 userId 就用它；沒有就用「目前已登入」那位
            User me = (userId != null && !userId.isEmpty())
                    ? userDao.findById(userId)
                    : userDao.findLoggedInOne();

            if (me == null) return;

            runOnUiThread(() -> {
                // 文字欄位
                etEmail.setText(nz(me.email));
                etName.setText(nz(me.name));
                etBirthday.setText(nz(me.birthday));
                if (tvJoinDate != null) tvJoinDate.setText(nz("加入日期：" + me.createdAtFormatted));

                // "M"/"F" → segment 左/右
                boolean isMaleFromDb = !"F".equalsIgnoreCase(me.gender);   // 預設男
                selectGender(isMaleFromDb);

                boolean styleOption1FromDb = !"F".equalsIgnoreCase(me.uiStyle); // 預設「男生」
                selectStyle(styleOption1FromDb);
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 取得目前登入使用者 ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = prefs.getString("current_user_id", null);

        if (userId == null) {
            finish();
            return;
        }

        // 顯示在畫面上
        TextView tvId = findViewById(R.id.tvIdReadonly);
        tvId.setText(userId);
    }


    // 小工具：null → 空字串，避免 setText(null) 異常
    private static String nz(String s) { return (s == null) ? "" : s; }

}
