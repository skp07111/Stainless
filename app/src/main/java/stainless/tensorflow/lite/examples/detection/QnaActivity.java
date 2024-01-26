package stainless.tensorflow.lite.examples.detection;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.examples.detection.R;

public class QnaActivity extends AppCompatActivity {

    private Button emailButton;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qna_layout);

        emailButton = findViewById(R.id.askButton);
        backButton = findViewById(R.id.back_button);

        emailButton.setOnClickListener(view -> {

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);

            emailIntent.setType("text/plain");
            emailIntent.setData(Uri.parse("mailto:green7170@swu.ac.kr"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Stainless 앱 문의"); // 이메일 제목
            this.startActivity(Intent.createChooser(emailIntent, "이메일 앱 선택"));
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 버튼이 클릭되면 다음 액티비티로 이동
                Intent intent = new Intent(QnaActivity.this, DetectorActivity.class);

                startActivity(intent);
            }
        });
    }
}
