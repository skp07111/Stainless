package org.tensorflow.lite.examples.detection;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class GuideActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TTS 초기화
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.KOREAN);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 언어 데이터 누락이나 지원하지 않음 처리
                    }
                } else {
                    // 초기화 실패 처리
                }
            }
        });

        // 제스처 디텍터 초기화
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (tts != null && tts.isSpeaking()) {
                    tts.stop(); // TTS가 말하고 있으면 정지
                }
                return super.onDoubleTap(e);
            }
        });

        // 액티비티의 루트 뷰에 터치 리스너 설정
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    // TTS를 사용하여 텍스트 읽기
    private void speakOut(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    // 호출 예시
    private void InfoButtonClick() {
        speakOut("안녕하세요, 시각장애인을 위한 얼룩탐지서비스 Stainless입니다. 지금부터 사용법 안내를 시작합니다." +
                "그만 듣고 싶으시다면 화면을 빠르게 두번 누르세요."+
                " ");
    }

    // 앱 종료 시 TTS 리소스 해제
    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}