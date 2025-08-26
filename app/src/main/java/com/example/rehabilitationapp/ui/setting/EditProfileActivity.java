package com.example.rehabilitationapp.ui.setting;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etEmail, etName, etBirthday;
    private FrameLayout genderSegment;
    private View genderThumb;
    private TextView tvMale, tvFemale;
    private Button btnConfirm;

    private boolean isMale = true; // 預設性別

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // 綁定 View
        etEmail = findViewById(R.id.etEmail);
        etName = findViewById(R.id.etName);
        etBirthday = findViewById(R.id.etBirthday);

        genderSegment = findViewById(R.id.genderSegment);
        genderThumb = findViewById(R.id.genderThumb);
        tvMale = findViewById(R.id.tvMale);
        tvFemale = findViewById(R.id.tvFemale);

        btnConfirm = findViewById(R.id.btnConfirm);

        // 預設選男生
        selectGender(true);

        // 點擊切換性別
        tvMale.setOnClickListener(v -> selectGender(true));
        tvFemale.setOnClickListener(v -> selectGender(false));

        // 點擊確認
        btnConfirm.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String name = etName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String gender = isMale ? "男" : "女";

            // TODO: 這裡可以存到 DB 或傳到 API
            // 先簡單測試用 Log
            android.util.Log.d("EditProfileActivity", "Email: " + email +
                    ", Name: " + name +
                    ", Birthday: " + birthday +
                    ", Gender: " + gender);

            finish(); // 返回上一頁
        });
    }

    private void selectGender(boolean male) {
        isMale = male;

        // 調整 thumb 的寬度 & 位置
        genderThumb.post(() -> {
            int halfWidth = genderSegment.getWidth() / 2;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) genderThumb.getLayoutParams();
            params.width = halfWidth;
            params.leftMargin = male ? 0 : halfWidth;
            genderThumb.setLayoutParams(params);
        });

        // 文字樣式
        tvMale.setTextColor(getResources().getColor(male ? R.color.white : R.color.black));
        tvFemale.setTextColor(getResources().getColor(male ? R.color.black : R.color.white));
    }
}
