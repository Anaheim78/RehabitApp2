package com.example.rehabilitationapp.ui.setting;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rehabilitationapp.R;

public class SettingFragment extends Fragment {

    public SettingFragment() { super(R.layout.fragment_setting); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button btn = view.findViewById(R.id.btnEditProfile);
        TextView tvUserId = view.findViewById(R.id.tvUserId);

        btn.setOnClickListener(v -> {
            String raw = tvUserId != null ? tvUserId.getText().toString() : "";
            String userId = raw.replace("ID:", "").trim();

            Intent i = new Intent(requireContext(), com.example.rehabilitationapp.ui.setting.EditProfileActivity.class);
            i.putExtra("EXTRA_USER_ID", userId);
            startActivity(i);
        });
    }
}
