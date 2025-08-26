package com.example.rehabilitationapp.ui.setting;


import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.rehabilitationapp.R;


public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";

    private TextView tvUserId, tvEmail, tvName, tvBirthday, tvGender;

    private ActivityResultLauncher<Intent> editProfileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    // 收取設定頁回傳的更新資料（若你之後要）
                    String newName = data.getStringExtra("EXTRA_NAME");
                    String newEmail = data.getStringExtra("EXTRA_EMAIL");
                    String newBirthday = data.getStringExtra("EXTRA_BIRTHDAY");
                    String newGender = data.getStringExtra("EXTRA_GENDER");

                    if (newName != null) tvName.setText("姓名：" + newName);
                    if (newEmail != null) tvEmail.setText("e-mail: " + newEmail);
                    if (newBirthday != null) tvBirthday.setText("生日：" + newBirthday);
                    if (newGender != null) tvGender.setText("性別：" + newGender);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_profile);

        tvUserId = findViewById(R.id.tvUserId);
        tvEmail = findViewById(R.id.tvEmail);
        tvName = findViewById(R.id.tvName);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvGender = findViewById(R.id.tvGender);

        Button btnEditProfile = findViewById(R.id.btnEditProfile);
        btnEditProfile.setOnClickListener(v -> {
            // 從顯示字串取得 ID：格式 "ID: xxx"
            String raw = tvUserId.getText().toString();
            String userId = raw.replace("ID:", "").trim();

            Intent intent = new Intent(ProfileActivity.this, com.example.rehabilitationapp.ui.setting.EditProfileActivity.class);
            intent.putExtra(EXTRA_USER_ID, userId);
            // 你也可以把目前值都帶去預填
            intent.putExtra("EXTRA_EMAIL", tvEmail.getText().toString().replace("e-mail:","").trim());
            intent.putExtra("EXTRA_NAME", tvName.getText().toString().replace("姓名：","").trim());
            intent.putExtra("EXTRA_BIRTHDAY", tvBirthday.getText().toString().replace("生日：","").trim());
            intent.putExtra("EXTRA_GENDER", tvGender.getText().toString().replace("性別：","").trim());
            editProfileLauncher.launch(intent);
        });
    }
}
