package stainless.tensorflow.lite.examples.detection;

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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.tensorflow.lite.examples.detection.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class SettingActivity extends AppCompatActivity {
    private static final int PICK_CONTACT_REQUEST = 1;

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;
    private Switch switchVibration;
    private ImageButton backButton;
    private Button contactButton;
    private Button tmpAddButton;

    private Boolean isVibrate = false;
    SharedPreferences preferences;
    SharedPreferences preferences2;
    SharedPreferences.Editor editor;
    SharedPreferences.Editor editor2;

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

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 버튼이 클릭되면 다음 액티비티로 이동
                Intent intent = new Intent(SettingActivity.this, DetectorActivity.class);

                startActivity(intent);
            }
        });

        switchVibration = findViewById(R.id.switch_vibration);

        switchVibration.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    isVibrate = true;
                } else {
                    isVibrate = false;
                }
                editor.putBoolean("isVibrate", isVibrate);
                editor.apply();
            }
        });


        findViewById(R.id.contact_button).setOnClickListener(view -> pickContact());

        contactList = new ArrayList<>();
        contactAdapter = new ContactAdapter(contactList, this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactAdapter);

        loadContactList(); // SharedPreference로 저장된 데이터 불러오기
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
            @SuppressLint("Range") String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            @SuppressLint("Range") String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));

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
                @SuppressLint("Range") String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                Log.d("ContactDetails", "Phone Number: " + phoneNumber);

                if (contactList.size() < 3) {
                    // RecyclerView에 연락처 추가
                    contactList.add(new ContactModel(displayName, phoneNumber));
                    contactAdapter.notifyDataSetChanged();
                    // 저장 메서드 호출
                    saveContact(displayName, phoneNumber);
                }
                else {
                    Toast.makeText(this, "연락처는 최대 3개까지 등록할 수 있습니다.", Toast.LENGTH_SHORT).show();
                }

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

        // RecyclerView의 아이템 클릭 이벤트 처리
        contactAdapter.setOnItemClickListener(new ContactAdapter.OnItemClickListener() {
            @Override
            public void onDeleteClick(int position) {
                // 삭제 버튼 클릭 시 해당 위치의 연락처 삭제
                deleteContact(position);
            }
        });
    }

    private void loadContactList() { // SharedPreferences에서 저장된 데이터 불러오기
        preferences2 = getSharedPreferences("ContactPreferences", Context.MODE_PRIVATE);

        // "contactCount" 키를 통해 저장된 연락처 개수를 가져옴
        int contactCount = preferences2.getInt("contactCount", 0);

        // 저장된 연락처 정보를 반복문을 통해 불러와서 RecyclerView에 추가
        for (int i = 0; i < contactCount; i++) {
            // "contactName_i"와 "contactNumber_i" 키를 통해 이름과 전화번호를 가져옴
            String contactName = preferences2.getString("contactName_" + i, "");
            String contactNumber = preferences2.getString("contactNumber_" + i, "");

            // 가져온 연락처 정보를 RecyclerView에 추가
            contactList.add(new ContactModel(contactName, contactNumber));
        }

        // RecyclerView 갱신
        contactAdapter.notifyDataSetChanged();
    }

    // 연락처 정보를 저장하는 메서드
    private void saveContact(String name, String number) {

        // SharedPreferences에 데이터 저장
        preferences2 = getSharedPreferences("ContactPreferences", Context.MODE_PRIVATE);
        editor2 = preferences2.edit();

        // 현재 저장된 연락처 개수를 가져옴
        int contactCount = preferences2.getInt("contactCount", 0);

        if (contactCount < 3) {
            // "contactName_i"와 "contactNumber_i" 키를 통해 이름과 전화번호를 저장
            editor2.putString("contactName_" + contactCount, name);
            editor2.putString("contactNumber_" + contactCount, number);
            // 저장된 연락처 개수를 증가시킴
            editor2.putInt("contactCount", contactCount + 1);
        }
        // 변경사항을 반영
        editor2.apply();
    }

    private void deleteContact(int position) {
        // 삭제할 연락처 정보 가져오기
        ContactModel deletedContact = contactList.get(position);

        // SharedPreferences에서 삭제
        preferences2 = getSharedPreferences("ContactPreferences", Context.MODE_PRIVATE);
        editor2 = preferences2.edit();

        // "contactCount" 키를 통해 저장된 연락처 개수를 가져옴
        int contactCount = preferences2.getInt("contactCount", 0);

        // 삭제할 연락처의 위치 이후의 연락처들을 앞으로 당기기
        for (int i = position; i < contactCount - 1; i++) {
            String nextName = preferences2.getString("contactName_" + (i + 1), "");
            String nextNumber = preferences2.getString("contactNumber_" + (i + 1), "");
            editor2.putString("contactName_" + i, nextName);
            editor2.putString("contactNumber_" + i, nextNumber);
        }

        // 마지막 위치에 있던 연락처 정보 삭제
        editor2.remove("contactName_" + (contactCount - 1));
        editor2.remove("contactNumber_" + (contactCount - 1));

        // 저장된 연락처 개수 감소
        editor2.putInt("contactCount", contactCount - 1);

        // 변경사항을 반영
        editor2.apply();

        // RecyclerView에서도 삭제
        contactList.remove(position);
        contactAdapter.notifyItemRemoved(position);
    }
}