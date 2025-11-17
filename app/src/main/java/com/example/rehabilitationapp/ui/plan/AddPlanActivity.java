package com.example.rehabilitationapp.ui.plan;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rehabilitationapp.R;

public class AddPlanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_plan);

        setTitle("新增訓練");
    }
}