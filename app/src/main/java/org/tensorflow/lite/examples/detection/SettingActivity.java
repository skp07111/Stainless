package org.tensorflow.lite.examples.detection;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class SettingActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private static final int PICK_CONTACT_REQUEST = 1;

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;
    private Switch switchVibration;
    private ImageButton backButton;
    private Button contactButton;
    private Button tmpAddButton;

    private Boolean isVibrate = false;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;

    private List<ContactModel> contactList;
    private ContactAdapter contactAdapter;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_setting);

        // READ_CONTACTS 권한을 확인하고 요청
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, READ_CONTACTS_PERMISSION_REQUEST_CODE);
        } else {
            // 이미 권한이 허용된 경우에 실행할 초기화 코드
            initialize();
        }

        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        editor = preferences.edit();
        // 진동 설정 불러오기
        isVibrate = preferences.getBoolean("isVibrate", false);

        backButton = findViewById(R.id.back_button);
        contactButton = findViewById(R.id.contact_button);
        // FloatingActionButton fabAddContact = findViewById(R.id.fab_add_contact);

        // TTS 초기화
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    // TTS 준비 완료, 버튼 클릭 리스너에서 TTS 사용 가능
                    // 언어 데이터 누락이나 지원하지 않는 언어일 경우 에러 처리
                }
            } else {
                // 초기화 실패 처리
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 버튼이 클릭되면 다음 액티비티로 이동
                Intent intent = new Intent(SettingActivity.this, DetectorActivity.class);

                if (tts != null) {
                    tts.speak("이전 화면으로", TextToSpeech.QUEUE_FLUSH, null, null);
                }

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
                    if (tts != null) {
                        tts.speak("진동 안내", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                } else {
                    isVibrate = false;
                    switchVibration.setText("음성 안내");
                    if (tts != null) {
                        tts.speak("음성 안내", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }
                editor.putBoolean("isVibrate", isVibrate);
                editor.apply();
            }
        });


        findViewById(R.id.contact_button).setOnClickListener(view -> pickContact());
        if (tts != null) {
            tts.speak("사진 공유 상대 등록하기", TextToSpeech.QUEUE_FLUSH, null, null);
        }

        contactList = new ArrayList<>();
        contactAdapter = new ContactAdapter(contactList, this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactAdapter);

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
    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            Uri contactUri = data.getData();
            displayContactDetails(contactUri);
        }
    }

    // 예제: 연락처 정보를 저장하는 코드
    private void displayContactDetails(Uri contactUri) {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(contactUri, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));

            Log.d("ContactDetails", "Name: " + displayName);

            // 전화번호 가져오기
            Cursor phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{contactId},
                    null
            );

            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                Log.d("ContactDetails", "Phone Number: " + phoneNumber);

                // RecyclerView에 연락처 추가
                contactList.add(new ContactModel(displayName, phoneNumber));
                contactAdapter.notifyDataSetChanged();

                // 저장 메서드 호출
                saveContact(displayName, phoneNumber);

                phoneCursor.close();
            }

            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initialize() {
        contactList = new ArrayList<>();
        contactAdapter = new ContactAdapter(contactList, this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactAdapter);

        // 저장된 연락처 정보 불러오기
        loadContactList();
    }

    private void loadContactList() {// SharedPreferences에서 저장된 데이터 불러오기
        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);

        // "contactCount" 키를 통해 저장된 연락처 개수를 가져옴
        int contactCount = preferences.getInt("contactCount", 0);

        // 저장된 연락처 정보를 반복문을 통해 불러와서 RecyclerView에 추가
        for (int i = 0; i < contactCount; i++) {
            // "contactName_i"와 "contactNumber_i" 키를 통해 이름과 전화번호를 가져옴
            String contactName = preferences.getString("contactName_" + i, "");
            String contactNumber = preferences.getString("contactNumber_" + i, "");

            // 가져온 연락처 정보를 RecyclerView에 추가
            contactList.add(new ContactModel(contactName, contactNumber));
        }

        // RecyclerView 갱신
        contactAdapter.notifyDataSetChanged();
    }

    // 연락처 정보를 저장하는 메서드
    private void saveContact(String name, String number) {
        // SharedPreferences에 데이터 저장
        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        editor = preferences.edit();

        // 현재 저장된 연락처 개수를 가져옴
        int contactCount = preferences.getInt("contactCount", 0);

        // "contactName_i"와 "contactNumber_i" 키를 통해 이름과 전화번호를 저장
        editor.putString("contactName_" + contactCount, name);
        editor.putString("contactNumber_" + contactCount, number);

        // 저장된 연락처 개수를 증가시킴
        editor.putInt("contactCount", contactCount + 1);

        // 변경사항을 반영
        editor.apply();
    }


}
