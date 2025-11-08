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

public class EditContactActivity extends AppCompatActivity {

    private EditText etOldPwd, etNewPwd, etConfirmPwd;
    private Button btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_profile);

        // 綁定 View
        btnConfirm = findViewById(R.id.btnConfirm);

        // 從 Intent 讀取 userId（可能為 null）
        String userId = getIntent().getStringExtra("EXTRA_USER_ID");

        // 點擊確認
        btnConfirm.setOnClickListener(v -> {
            finish();
        });
    }
}
