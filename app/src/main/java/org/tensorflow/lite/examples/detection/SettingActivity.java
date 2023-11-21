package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;


public class SettingActivity extends AppCompatActivity {

    private Switch switchVibration;
    private ImageButton backButton;
    private Boolean isVibrate = false;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        editor = preferences.edit();
        // 진동 설정 불러오기
        isVibrate = preferences.getBoolean("isVibrate", false);

        backButton = findViewById(R.id.back_button);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 버튼이 클릭되면 다음 액티비티로 이동
                Intent intent = new Intent(SettingActivity.this, DetectorActivity.class);

                // 값을 전달하기 위해 putExtra 사용
                // intent.putExtra("isVibrate", isVibrate);

                startActivity(intent);
            }
        });

        switchVibration = findViewById(R.id.switch_vibration);
        if (isVibrate == true) {
            switchVibration.setChecked(true);
            switchVibration.setText("진동 ON");
        } else {
            switchVibration.setChecked(false);
            switchVibration.setText("진동 OFF");
        }
        switchVibration.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    isVibrate = true;
                    switchVibration.setText("진동 ON");
                } else {
                    isVibrate = false;
                    switchVibration.setText("진동 OFF");
                }
                editor.putBoolean("isVibrate", isVibrate);
                editor.apply();
            }
        });

    }
}