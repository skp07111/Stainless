/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
//        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
  private TextToSpeech tts;
  private GestureDetector gestureDetector;
  private static final Logger LOGGER = new Logger();
  private Camera camera;

  private static final int PERMISSIONS_REQUEST = 1;
  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
  private static final String PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS;
  private static final String ASSET_PATH = "";
  SharedPreferences preferences2;
  SharedPreferences.Editor editor2;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  protected Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  protected int defaultModelIndex = 0;
  protected int defaultDeviceIndex = 1; // cpu:0 , gpu:1, nnpi: 2
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  protected ArrayList<String> modelStrings = new ArrayList<String>();

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  protected ListView deviceView;
  protected TextView threadsTextView;
  protected ListView modelView;

  //메인홈 버튼
  private ImageButton infoButton;
  private ImageButton filmButton; // 촬영 버튼
  private ImageButton settingsButton;
  private boolean isPreviewPaused = false;

  private Button newShareButton; // 새로운 '공유하기' 버튼
  private Button cancelButton;


  //공유하기 버튼 클릭시 하단 탭 교체
  private ConstraintLayout bottomTabLayout;
  private ConstraintLayout shareTabLayout;
  private ConstraintLayout settingsLayout;

  //공유할 상대
  private ImageButton person1;
  private ImageButton person2;
  private ImageButton person3;
  private TextView person1_name;
  private TextView person2_name;
  private TextView person3_name;
  private TextView person1_number;
  private TextView person2_number;
  private TextView person3_number;

  //연락처 추가
  private ImageButton add_button;

  SharedPreferences preferences;
  SharedPreferences.Editor editor;

  @Override
  public void onBackPressed() {
    if (shareTabLayout.getVisibility() == View.VISIBLE || settingsLayout.getVisibility() == View.VISIBLE) {
      shareTabLayout.setVisibility(View.GONE);
      settingsLayout.setVisibility(View.GONE);
      bottomTabLayout.setVisibility(View.VISIBLE);
    } else {
      super.onBackPressed();
    }
  }

  /** Current indices of device and model. */
  int currentDevice = -1;
  int currentModel = -1;
  int currentNumThreads = -1;

  ArrayList<String> deviceStrings = new ArrayList<String>();

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.tfe_od_activity_camera);
    FrameLayout previewLayout=findViewById(R.id.container);
    infoButton = findViewById(R.id.info_button);
    filmButton = findViewById(R.id.share_button); // 촬영 버튼
    settingsButton = findViewById(R.id.setting_button);
    newShareButton = findViewById(R.id.newShareButton);
    cancelButton = findViewById(R.id.cancelButton);

    // 하단 탭 버튼 클릭시 화면 교체
    bottomTabLayout = findViewById(R.id.bottom_tab_layout);
    shareTabLayout = findViewById(R.id.share_tab_layout);
    settingsLayout = findViewById(R.id.settings_layout);

    add_button = findViewById(R.id.add_button);

    person1 = findViewById(R.id.person1);
    person2 = findViewById(R.id.person2);
    person3 = findViewById(R.id.person3);
    person1_name = findViewById(R.id.person1_name);
    person2_name = findViewById(R.id.person2_name);
    person3_name = findViewById(R.id.person3_name);
    person1_number = findViewById(R.id.person1_phone);
    person2_number = findViewById(R.id.person2_phone);
    person3_number = findViewById(R.id.person3_phone);

    preferences2 = getSharedPreferences("ContactPreferences", Context.MODE_PRIVATE);

    // "contactCount" 키를 통해 저장된 연락처 개수를 가져옴
    int contactCount = preferences2.getInt("contactCount", 0);

    // "contactName_i"와 "contactNumber_i" 키를 통해 이름과 전화번호를 가져옴
    String contactName1 = preferences2.getString("contactName_0", "");
    String contactNumber1 = preferences2.getString("contactNumber_0", "");
    String contactName2 = preferences2.getString("contactName_1", "");
    String contactNumber2 = preferences2.getString("contactNumber_1", "");
    String contactName3 = preferences2.getString("contactName_2", "");
    String contactNumber3 = preferences2.getString("contactNumber_2", "");
    person1_name.setText(contactName1);
    person1_number.setText(contactNumber1);
    person2_name.setText(contactName2);
    person2_number.setText(contactNumber2);
    person3_name.setText(contactName3);
    person3_number.setText(contactNumber3);

// TTS 초기화
    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        int result = tts.setLanguage(Locale.KOREAN);
        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
          // TTS 준비 완료, 버튼 클릭 리스너에서 TTS 사용 가능
          // 언어 데이터 누락이나 지원하지 않는 언어일 경우 에러 처리
          LOGGER.e("TTS 언어 설정에 실패했습니다.");
        }
      } else {
        // 초기화 실패 처리
        LOGGER.e("TTS 초기화에 실패했습니다.");
      }
    });

    // 제스처 디텍터 초기화
    gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDoubleTap(MotionEvent e) {
        if (tts != null && tts.isSpeaking()) {
          tts.stop(); // TTS가 말하고 있으면 정지
          return true;
        }
        return super.onDoubleTap(e);
      }
    });

    // 더블 탭을 감지하기 위한 터치 리스너 설정
    View rootView = findViewById(android.R.id.content);
    rootView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
      }
    });

    infoButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
//        // '사용법 안내' TTS안내 시작
//        if (tts != null) {
//          String toSpeak = "안녕하세요 시각장애인을 위한 얼룩탐지서비스 Stainless입니다. 지금부터 사용법 안내를 시작합니다." +
//                  "그만 듣고 싶으시다면 화면을 빠르게 두번 누르세요.";
//          tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
//        }

        // QnaActivity 시작
        Intent intent = new Intent(CameraActivity.this, QnaActivity.class);
        startActivity(intent);

      }

    });

    filmButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 카메라 미리보기 정지
        pauseCameraPreview();
        // 현재 프레임을 이미지로 저장
        takePicture();
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        buttonContainer.setVisibility(View.VISIBLE);
      }


    });

    newShareButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 기존 하단 탭 숨기기
        bottomTabLayout.setVisibility(View.GONE);
        // 새로운 공유하기 탭 표시
        shareTabLayout.setVisibility(View.VISIBLE);
      }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //공유하기/취소 버튼 제거
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        buttonContainer.setVisibility(View.GONE);
        bottomTabLayout.setVisibility(View.VISIBLE);
        shareTabLayout.setVisibility(View.GONE);
      }
    });


    settingsButton.setOnClickListener(v -> startActivity(new Intent(CameraActivity.this, SettingActivity.class)));

    person1.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 토스트 메시지 표시
        Toast.makeText(getApplicationContext(), "메세지를 보냈습니다", Toast.LENGTH_SHORT).show();
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        buttonContainer.setVisibility(View.GONE);
        bottomTabLayout.setVisibility(View.VISIBLE);
        shareTabLayout.setVisibility(View.GONE);
      }
    });
    person2.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 토스트 메시지 표시
        Toast.makeText(getApplicationContext(), "메세지를 보냈습니다", Toast.LENGTH_SHORT).show();
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        buttonContainer.setVisibility(View.GONE);
        bottomTabLayout.setVisibility(View.VISIBLE);
        shareTabLayout.setVisibility(View.GONE);
      }
    });
    person3.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 토스트 메시지 표시
        Toast.makeText(getApplicationContext(), "메세지를 보냈습니다", Toast.LENGTH_SHORT).show();
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        buttonContainer.setVisibility(View.GONE);
        bottomTabLayout.setVisibility(View.VISIBLE);
        shareTabLayout.setVisibility(View.GONE);
      }
    });

    settingsButton.setOnClickListener(v -> startActivity(new Intent(CameraActivity.this, SettingActivity.class)));

    add_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // 연락처 버튼을 누르면 현재 등록되어있는 사람 3명이 리스트로 뜨고, 아래의 +버튼을 누르면 연락처를 불러오기
        // 사용자의 연락처에 접근하여 목록을 받아오고, 새로추가 혹은 등록할사람 선택 후 확인버튼을 누르면 다시 리스트로 화면전환
        // 여기서 이미 등록되었는데 제거하고싶은 사람은... 나중에 구현하기


      }
    });

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    threadsTextView = findViewById(R.id.threads);
    currentNumThreads = Integer.parseInt(threadsTextView.getText().toString().trim());
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    deviceView = findViewById(R.id.device_list);
    deviceStrings.add("CPU");
    deviceStrings.add("GPU");
    deviceStrings.add("NNAPI");
    deviceView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    ArrayAdapter<String> deviceAdapter =
            new ArrayAdapter<>(
                    CameraActivity.this , R.layout.deviceview_row, R.id.deviceview_row_text, deviceStrings);
    deviceView.setAdapter(deviceAdapter);
    deviceView.setItemChecked(defaultDeviceIndex, true);
    currentDevice = defaultDeviceIndex;
    deviceView.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {
              @Override
              public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateActiveModel();
              }
            });

    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
    modelView = findViewById((R.id.model_list));

    modelStrings = getModelStrings(getAssets(), ASSET_PATH);
    modelView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    ArrayAdapter<String> modelAdapter =
            new ArrayAdapter<>(
                    CameraActivity.this , R.layout.listview_row, R.id.listview_row_text, modelStrings);
    modelView.setAdapter(modelAdapter);
    modelView.setItemChecked(defaultModelIndex, true);
    currentModel = defaultModelIndex;
    modelView.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {
              @Override
              public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateActiveModel();
              }
            });

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                  gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                  gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                //                int width = bottomSheetLayout.getMeasuredWidth();
                int height = gestureLayout.getMeasuredHeight();

                sheetBehavior.setPeekHeight(height);
              }
            });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
            new BottomSheetBehavior.BottomSheetCallback() {
              @Override
              public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                  case BottomSheetBehavior.STATE_HIDDEN:
                    break;
                  case BottomSheetBehavior.STATE_EXPANDED:
                  {
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                  }
                  break;
                  case BottomSheetBehavior.STATE_COLLAPSED:
                  {
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                  }
                  break;
                  case BottomSheetBehavior.STATE_DRAGGING:
                    break;
                  case BottomSheetBehavior.STATE_SETTLING:
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                    break;
                }
              }

              @Override
              public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
            });

    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);

  }
  // 카메라 미리보기 일시 정지
  private void pauseCameraPreview() {
    isPreviewPaused = true;
  }
  // 카메라 미리보기 재개
  private void resumeCameraPreview() {
    isPreviewPaused = false;
  }
  // 이미지를 캡처하고 저장하는 메서드
  private void takePicture() {
    //공유하기/취소 버튼 생성
    LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
    buttonContainer.setVisibility(View.VISIBLE);

    // 카메라 이미지 캡처 로직 구현
    if (camera != null) {
      camera.takePicture(null, null, new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
          // 사진 데이터를 처리합니다. 예를 들어, 파일로 저장할 수 있습니다.
          saveImageToFile(data);

          // 필요한 경우 카메라 미리보기를 다시 시작합니다.
          camera.startPreview();
        }
      });
    }
  }
  private void saveImageToFile(byte[] data) {
    File pictureFile = getOutputMediaFile(); // 이미지 파일을 저장할 경로
    if (pictureFile == null) {
      Log.d("Camera", "Error creating media file, check storage permissions");
      return;
    }

    try {
      FileOutputStream fos = new FileOutputStream(pictureFile);
      fos.write(data);
      fos.close();
    } catch (FileNotFoundException e) {
      Log.d("Camera", "File not found: " + e.getMessage());
    } catch (IOException e) {
      Log.d("Camera", "Error accessing file: " + e.getMessage());
    }
  }
  /** 이미지를 저장할 파일 객체를 생성합니다. */
  private File getOutputMediaFile() {
    // 이미지 저장 경로 설정 (예: 외부 저장소의 Pictures 디렉토리)
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "MyCameraApp");

    // 디렉토리가 없다면 생성합니다.
    if (!mediaStorageDir.exists()) {
      if (!mediaStorageDir.mkdirs()) {
        Log.d("MyCameraApp", "failed to create directory");
        return null;
      }
    }

    // 이미지 파일 이름 생성
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File mediaFile;
    mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            "IMG_" + timeStamp + ".jpg");

    return mediaFile;
  }
  protected ArrayList<String> getModelStrings(AssetManager mgr, String path){
    ArrayList<String> res = new ArrayList<String>();
    try {
      String[] files = mgr.list(path);
      for (String file : files) {
        String[] splits = file.split("\\.");
        if (splits[splits.length - 1].equals("tflite")) {
          res.add(file);
        }
      }

    }
    catch (IOException e){
      System.err.println("getModelStrings: " + e.getMessage());
    }
    return res;
  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame|| isPreviewPaused) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
            new Runnable() {
              @Override
              public void run() {
                ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
              }
            };

    postInferenceCallback =
            new Runnable() {
              @Override
              public void run() {
                camera.addCallbackBuffer(bytes);
                isProcessingFrame = false;
              }
            };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
              new Runnable() {
                @Override
                public void run() {
                  ImageUtils.convertYUV420ToARGB8888(
                          yuvBytes[0],
                          yuvBytes[1],
                          yuvBytes[2],
                          previewWidth,
                          previewHeight,
                          yRowStride,
                          uvRowStride,
                          uvPixelStride,
                          rgbBytes);
                }
              };

      postInferenceCallback =
              new Runnable() {
                @Override
                public void run() {
                  image.close();
                  isProcessingFrame = false;
                }
              };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    //tts리소스 해제
    if (tts != null) {
      tts.stop();
      tts.shutdown();
      tts = null; // 참조 제거
    }
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                        CameraActivity.this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                .show();
      }
      requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_READ_CONTACTS}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
          CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
                (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        || isHardwareLevelSupported(
                        characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
              CameraConnectionFragment.newInstance(
                      new CameraConnectionFragment.ConnectionCallback() {
                        @Override
                        public void onPreviewSizeChosen(final Size size, final int rotation) {
                          previewHeight = size.getHeight();
                          previewWidth = size.getWidth();
                          CameraActivity.this.onPreviewSizeChosen(size, rotation);
                        }
                      },
                      this,
                      getLayoutId(),
                      getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
              new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

//  @Override
//  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//    setUseNNAPI(isChecked);
//    if (isChecked) apiSwitchCompat.setText("NNAPI");
//    else apiSwitchCompat.setText("TFLITE");
//  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    }
  }

  protected void showFrameInfo(String frameInfo) {
    frameValueTextView.setText(frameInfo);
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected abstract void updateActiveModel();
  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);

}