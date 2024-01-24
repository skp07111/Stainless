package stainless.tensorflow.lite.examples.detection;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.examples.detection.R;

public class QnaActivity extends AppCompatActivity {

    private Button emailButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qna_layout);

        emailButton = findViewById(R.id.askButton);
        emailButton.setOnClickListener(view -> {

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);

            emailIntent.setType("text/plain");
            emailIntent.setData(Uri.parse("mailto:green7170@swu.ac.kr"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Stainless 앱 문의"); // 이메일 제목
            this.startActivity(Intent.createChooser(emailIntent, "이메일 앱 선택"));
        });
    }
}
