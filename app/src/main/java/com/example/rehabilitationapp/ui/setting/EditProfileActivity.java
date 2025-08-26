package com.example.rehabilitationapp.ui.setting;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.rehabilitationapp.R;


public class EditProfileActivity extends AppCompatActivity {

    private TextView tvIdReadonly, tvMale, tvFemale;
    private EditText etEmail, etName, etBirthday;
    private View genderThumb;
    private FrameLayout genderSegment;

    private boolean isMale = true; // 預設

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        tvIdReadonly = findViewById(R.id.tvIdReadonly);
        etEmail = findViewById(R.id.etEmail);
        etName = findViewById(R.id.etName);
        etBirthday = findViewById(R.id.etBirthday);
        tvMale = findViewById(R.id.tvMale);
        tvFemale = findViewById(R.id.tvFemale);
        genderThumb = findViewById(R.id.genderThumb);
        genderSegment = findViewById(R.id.genderSegment);
        Button btnConfirm = findViewById(R.id.btnConfirm);

        // 1) 取得傳入資料，預填
        Intent intent = getIntent();
        String userId = intent.getStringExtra(ProfileActivity.EXTRA_USER_ID);
        String preEmail = intent.getStringExtra("EXTRA_EMAIL");
        String preName = intent.getStringExtra("EXTRA_NAME");
        String preBirthday = intent.getStringExtra("EXTRA_BIRTHDAY");
        String preGender = intent.getStringExtra("EXTRA_GENDER");

        if (userId != null) tvIdReadonly.setText(userId);
        if (preEmail != null) etEmail.setText(preEmail);
        if (preName != null) etName.setText(preName);
        if (preBirthday != null) etBirthday.setText(preBirthday);
        if (preGender != null) {
            isMale = preGender.contains("男");
        }

        // 2) 性別滑軌：在元件測量完成後，設定滑塊寬度與位置
        genderSegment.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int w = genderSegment.getWidth();
            ViewGroup.LayoutParams lp = genderThumb.getLayoutParams();
            lp.width = w / 2; // 滑塊 = 一半寬
            genderThumb.setLayoutParams(lp);
            // 依 isMale 初始位置
            genderThumb.setTranslationX(isMale ? 0 : w / 2f);
        });

        // 3) 點左右切換（帶動畫）
        View.OnClickListener maleClick = v -> animateGender(true);
        View.OnClickListener femaleClick = v -> animateGender(false);
        tvMale.setOnClickListener(maleClick);
        tvFemale.setOnClickListener(femaleClick);
        // 也讓點擊滑塊本身切換
        genderThumb.setOnClickListener(v -> animateGender(!isMale));

        // 4) 確認：回傳資料給 ProfileActivity
        btnConfirm.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("EXTRA_NAME", etName.getText().toString().trim());
            data.putExtra("EXTRA_EMAIL", etEmail.getText().toString().trim());
            data.putExtra("EXTRA_BIRTHDAY", etBirthday.getText().toString().trim());
            data.putExtra("EXTRA_GENDER", isMale ? "男" : "女");
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void animateGender(boolean toMale) {
        if (genderSegment.getWidth() == 0) return;
        isMale = toMale;
        float targetX = toMale ? 0f : genderSegment.getWidth() / 2f;

        ObjectAnimator animator = ObjectAnimator.ofFloat(genderThumb, "translationX", genderThumb.getTranslationX(), targetX);
        animator.setDuration(180);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }
}
