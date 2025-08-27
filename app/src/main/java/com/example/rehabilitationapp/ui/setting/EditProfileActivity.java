package com.example.rehabilitationapp.ui.setting;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;

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

        styleSegment = findViewById(R.id.styleSegment);
        styleThumb = findViewById(R.id.styleThumb);
        tvStyleOption1 = findViewById(R.id.tvStyleOption1);
        tvStyleOption2 = findViewById(R.id.tvStyleOption2);

        tvJoinDate = findViewById(R.id.tvJoinDate);
        btnConfirm = findViewById(R.id.btnConfirm);

        // 預設選擇
        selectGender(true);
        selectStyle(true);

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
            String gender = isMale ? "男" : "女";
            String style = styleOption1 ? "男生" : "女生";

            android.util.Log.d("EditProfileActivity", "Email: " + email +
                    ", Name: " + name +
                    ", Birthday: " + birthday +
                    ", Gender: " + gender +
                    ", Style: " + style);

            //TO DO..寫入room
            finish();
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
}
