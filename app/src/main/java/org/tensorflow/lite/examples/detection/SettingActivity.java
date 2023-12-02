package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;

import com.google.android.material.floatingactionbutton.FloatingActionButton;


public class SettingActivity extends AppCompatActivity {

    private Switch switchVibration;
    private ImageButton backButton;
    private Button contactButton;
    private Button tmpAddButton;

    private Boolean isVibrate = false;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_setting);

        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        editor = preferences.edit();
        // 진동 설정 불러오기
        isVibrate = preferences.getBoolean("isVibrate", false);

        backButton = findViewById(R.id.back_button);
        contactButton = findViewById(R.id.contact_button);
        FloatingActionButton fabAddContact = findViewById(R.id.fab_add_contact);

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
            switchVibration.setText("진동 안내");
        } else {
            switchVibration.setChecked(false);
            switchVibration.setText("음성 안내");
        }
        switchVibration.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    isVibrate = true;
                    switchVibration.setText("진동 안내");
                } else {
                    isVibrate = false;
                    switchVibration.setText("음성 안내");
                }
                editor.putBoolean("isVibrate", isVibrate);
                editor.apply();
            }
        });

//        contactButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // ContactListFragment로 이동
//                ContactListFragment contactListFragment = new ContactListFragment();
//                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
//                transaction.replace(R.id.fragment_container, contactListFragment); // 'fragment_container'는 프래그먼트를 표시할 레이아웃의 ID입니다.
//                transaction.addToBackStack(null); // 이전 프래그먼트로 돌아갈 수 있게 스택에 추가
//                transaction.commit(); // 트랜잭션 실행
//
//                // FAB 표시
//                fabAddContact.setVisibility(View.VISIBLE);
//            }
//        });

//        fabAddContact.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                // 연락처 목록 화면으로 전환
//                startActivity(new Intent(SettingActivity.this, ContactListFragment.class));
//            }
//        });

//        tmpAddButton = findViewById(R.id.tmp_button);
//        tmpAddButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                // 연락처 목록 화면으로 전환
//                startActivity(new Intent(SettingActivity.this, TmpContactActivity.class));
//            }
//        });
    }

}