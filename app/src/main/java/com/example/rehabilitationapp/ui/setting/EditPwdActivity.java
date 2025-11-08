package com.example.rehabilitationapp.ui.setting;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executors;

public class EditPwdActivity extends AppCompatActivity {

    private EditText etOldPwd, etNewPwd, etConfirmPwd;
    private Button btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password_profile);

        // 綁定 View
        etOldPwd = findViewById(R.id.etName);         // 舊密碼
        etNewPwd = findViewById(R.id.etBirthday01);   // 新密碼
        etConfirmPwd = findViewById(R.id.etBirthday); // 再次確認密碼
        btnConfirm = findViewById(R.id.btnConfirm);

        // 從 Intent 讀取 userId（可能為 null）
        String userId = getIntent().getStringExtra("EXTRA_USER_ID");

        // 點擊確認
        btnConfirm.setOnClickListener(v -> {
            String oldPwd = etOldPwd.getText().toString().trim();
            String newPwd = etNewPwd.getText().toString().trim();
            String confirmPwd = etConfirmPwd.getText().toString().trim();

            // === Step 1: 檢查輸入 ===
            if (oldPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
                Toast.makeText(this, "請完整輸入三個欄位", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPwd.length() < 8) {
                Toast.makeText(this, "密碼長度需至少 8 個字元", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPwd.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
                Toast.makeText(this, "密碼需包含英文字母與數字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPwd.equals(confirmPwd)) {
                Toast.makeText(this, "兩次輸入的密碼不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            // === Step 2: DB 檢查與更新 ===
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                UserDao userDao = db.userDao();

                // 優先使用 Intent 的 userId，否則取目前登入者
                User user = null;
                if (userId != null && !userId.isEmpty()) {
                    user = userDao.findById(userId);
                } else {
                    user = userDao.findLoggedInOne();
                }

                if (user == null) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "找不到使用者", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                // 驗證舊密碼
                if (!oldPwd.equals(user.password)) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "舊密碼不正確", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                // 更新新密碼
                user.password = newPwd;
                user.need_sync = 1; // 標記要同步 Firestore
                userDao.update(user);

                runOnUiThread(() ->
                        Toast.makeText(this, "密碼更新成功", Toast.LENGTH_SHORT).show()
                );

                // === Step 3: （可選）同步到 Firestore ===
                // === Step 3: 同步 Firestore ===
                FirebaseFirestore fs = FirebaseFirestore.getInstance();

                // 用 user_id 查出真正的亂碼 docId
                fs.collection("Users")
                        .whereEqualTo("user_id", user.userId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!querySnapshot.isEmpty()) {
                                String docId = querySnapshot.getDocuments().get(0).getId();

                                fs.collection("Users").document(docId)
                                        .update("password", newPwd,
                                                "updateTime", System.currentTimeMillis())
                                        .addOnSuccessListener(aVoid ->
                                                runOnUiThread(() ->
                                                        Toast.makeText(this, "雲端密碼同步成功", Toast.LENGTH_SHORT).show()
                                                ))
                                        .addOnFailureListener(e ->
                                                runOnUiThread(() ->
                                                        Toast.makeText(this, "雲端更新失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                ));
                            } else {
                                runOnUiThread(() ->
                                        Toast.makeText(this, "找不到對應的 Firestore 文件", Toast.LENGTH_SHORT).show()
                                );
                            }
                        })
                        .addOnFailureListener(e ->
                                runOnUiThread(() ->
                                        Toast.makeText(this, "查詢 Firestore 失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show()
                                ));

            });
        });
    }
}
