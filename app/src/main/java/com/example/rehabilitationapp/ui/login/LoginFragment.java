package com.example.rehabilitationapp.ui.login;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginFragment extends Fragment {

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etId = view.findViewById(R.id.etId);
        EditText etPassword = view.findViewById(R.id.etPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        AppDatabase localDb = AppDatabase.getInstance(requireContext());
        UserDao userDao = localDb.userDao();

        btnLogin.setOnClickListener(v -> {
            String id = etId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (id.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "請輸入帳號和密碼", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("Users")
                    .whereEqualTo("user_id", id)
                    .whereEqualTo("password", password)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);

                            // 取 Firebase 欄位
                            long createdAt = doc.getLong("createdAt") != null
                                    ? doc.getLong("createdAt") : System.currentTimeMillis();
                            String createdAtFormatted = doc.getString("createdAtFormatted");

                            // 存到 Room
                            User user = new User();
                            user.userId = id;
                            user.password = password;
                            user.createdAt = createdAt;
                            user.createdAtFormatted = createdAtFormatted;

                            new Thread(() -> userDao.insert(user)).start();

                            // 導航到首頁，清掉 LoginFragment
                            NavController navController = Navigation.findNavController(view);
                            NavOptions navOptions = new NavOptions.Builder()
                                    .setPopUpTo(R.id.loginFragment, true)
                                    .build();
                            navController.navigate(R.id.navigation_home, null, navOptions);

                        } else {
                            Toast.makeText(getContext(), "帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
