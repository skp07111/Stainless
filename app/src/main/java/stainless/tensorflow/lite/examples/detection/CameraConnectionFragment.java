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

package stainless.tensorflow.lite.examples.detection;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.tensorflow.lite.examples.detection.R;

import stainless.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import stainless.tensorflow.lite.examples.detection.env.Logger;

@SuppressLint("ValidFragment")
public class CameraConnectionFragment extends Fragment {
  private static final Logger LOGGER = new Logger();
  private static final int MINIMUM_PREVIEW_SIZE = 320;

  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  private static final String FRAGMENT_DIALOG = "dialog";
  private String cameraId = "0"; // 0은 후면카메라
  private final Semaphore cameraOpenCloseLock = new Semaphore(1);
  private final Object lock = new Object(); // 락 객체 추가
  private final OnImageAvailableListener imageListener;
  private final Size inputSize;
  private final int layout;
  private AutoFitTextureView textureView;
  private CameraCaptureSession previewSession;
  private CaptureRequest previewRequest;
  private CameraDevice cameraDevice;
  private CameraCharacteristics characteristics;
  private Integer sensorOrientation;
  private Size previewSize;
  private HandlerThread backgroundThread;
  private Handler backgroundHandler;
  private ImageReader previewReader;
  private CaptureRequest.Builder previewRequestBuilder;

  private ImageButton filmButton; // 촬영 버튼


  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }


  private final ConnectionCallback cameraConnectionCallback;
  private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureProgressed(
                    final CameraCaptureSession session,
                    final CaptureRequest request,
                    final CaptureResult partialResult) {}

            @Override
            public void onCaptureCompleted(
                    final CameraCaptureSession session,
                    final CaptureRequest request,
                    final TotalCaptureResult result) {}
          };

  private void openCamera(final int width, final int height) {
    try {
      final Activity activity = getActivity();
      final CameraManager manager = (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
      Log.e("TEST", "openCamera E");
      String[] cameraIds = manager.getCameraIdList();
      for (String id : cameraIds) {
        if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) { // 카메라의 특성이 후면 카메라일 경우
          cameraId = id;
          characteristics = manager.getCameraCharacteristics(cameraId); // characteristics 초기화
          break;
        }
      }
      StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
      previewSize = previewSizes[0];// 해상도 설정 (4032 * 3024)
      manager.openCamera(cameraId, stateCallback, null);
      Log.e("TEST", "openCamera X:" + previewSize.getWidth() + previewSize.getHeight());
    } catch (final CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                    final SurfaceTexture texture, final int width, final int height) {
              openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    final SurfaceTexture texture, final int width, final int height) {
              Log.e(TAG, "onSurfaceTextureSizeChanged");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
              return false;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
          };

  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice cd) {
              // This method is called when the camera is opened.  We start camera preview here.
              cameraOpenCloseLock.release();
              cameraDevice = cd;
              startPreview();
              Log.e(TAG, "onOpened");
            }

            @Override
            public void onDisconnected(final CameraDevice cd) {
              Log.e(TAG, "onDisconnected");
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
            }

            @Override
            public void onError(final CameraDevice cd, final int error) {
              Log.e(TAG, "onError");
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
              final Activity activity = getActivity();
              if (null != activity) {
                activity.finish();
              }
            }
          };

  private void startPreview() {
    try {
      //Log.e("test","!!"+textureView.isAvailable());

      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      final Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      Log.e("TEST", "preview:"+previewSize.getWidth() + previewSize.getHeight());

      // Create the reader for the preview frames.
      previewReader =
              ImageReader.newInstance(
                      previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice.createCaptureSession(
              Arrays.asList(surface, previewReader.getSurface()),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                  // The camera is already closed
                  if (null == cameraDevice) {
                    return;
                  }

                  // When the session is ready, we start displaying the preview.
                  previewSession = cameraCaptureSession;
                  try {
                    // Auto focus should be continuous for camera preview.
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Flash is automatically enabled when necessary.
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    // Finally, we start displaying the camera preview.
                    previewRequest = previewRequestBuilder.build();
                    previewSession.setRepeatingRequest(
                            previewRequest, captureCallback, backgroundHandler);
                  } catch (final CameraAccessException e) {
                    LOGGER.e(e, "Exception!");
                  }
                }

                @Override
                public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                  showToast("Failed");
                }
              },
              null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }



  //촬영 후 저장
//  protected void takePicture() {
//    if (null == cameraDevice) {
//      Log.e(TAG, "cameraDevice is null, return");
//      return;
//    }
//
//    try {
//      Size[] jpegSizes = null;
//      CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
//      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
//      StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//
//      int width = 640;
//      int height = 480;
//      if (jpegSizes != null && 0 < jpegSizes.length) {
//        width = jpegSizes[0].getWidth();
//        height = jpegSizes[0].getHeight();
//      }
//
//      ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
//      List<Surface> outputSurfaces = new ArrayList<Surface>(2);
//      outputSurfaces.add(reader.getSurface());
//      outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
//
//      final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//      captureBuilder.addTarget(reader.getSurface());
////            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//
//      captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//
//      // Orientation
//      int rotation = ((Activity) getActivity()).getWindowManager().getDefaultDisplay().getRotation();
//      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
//
//      Date date = new Date();
//      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
//
//      final File file = new File(Environment.getExternalStorageDirectory() + "/DCIM", "pic_" + dateFormat.format(date) + ".jpg");
//
//      //저장하기
//      ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
//        @Override
//        public void onImageAvailable(ImageReader reader) {
//          Image image = null;
//          image = reader.acquireLatestImage();
//          if(image != null){
//            synchronized (lock){ // 동기화 블록 추가
//                try{
//                  ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                  byte[] bytes = new byte[buffer.capacity()];
//                  buffer.get(bytes);
//                  save(bytes);
//                  Log.d(TAG, "save complete");
//                }catch (FileNotFoundException e) {
//                  e.printStackTrace();
//                } catch (IOException e) {
//                  e.printStackTrace();
//                } finally {
//                  if (image != null) {
//                    image.close();
//                    reader.close();
//                  }
//                }
//              }
//              }
//        }
//
//        private void save(byte[] bytes) throws IOException {
//          OutputStream output = null;
//          try {
//            output = new FileOutputStream(file);
//            output.write(bytes);
//          } finally {
//            if (null != output) {
//              output.close();
//            }
//          }
//        }
//      };
//
//      HandlerThread thread = new HandlerThread("CameraPicture");
//      thread.start();
//      final Handler backgroudHandler = new Handler(thread.getLooper());
//      reader.setOnImageAvailableListener(readerListener, backgroudHandler);
//
//      final Handler delayPreview = new Handler();
//
//      final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
//        @Override
//        public void onCaptureCompleted(CameraCaptureSession session,
//                                       CaptureRequest request, TotalCaptureResult result) {
//          super.onCaptureCompleted(session, request, result);
//          Toast.makeText(getActivity(), "Saved:" + file, Toast.LENGTH_SHORT).show();
////                    startPreview();
//        }
//
//      };
//
//      cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
//        @Override
//        public void onConfigured(CameraCaptureSession session) {
//          try {
//            Log.d(TAG, "CREATE_SESSION");
//            session.capture(captureBuilder.build(), captureListener, backgroudHandler);
//          } catch (CameraAccessException e) {
//            e.printStackTrace();
//          }
//        }
//
//        @Override
//        public void onConfigureFailed(CameraCaptureSession session) {
//
//        }
//      }, backgroudHandler);
//
//    } catch (CameraAccessException e) {
//      e.printStackTrace();
//    }
//  }// takePicture 메소드 종료

  public void onResume() {
    super.onResume();
    startBackgroundThread();
    textureView.setSurfaceTextureListener(surfaceTextureListener);
    Log.d(TAG, "onResume2");
  }

  @Override
  public void onPause() {
    Log.d(TAG, "onPause");
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != previewSession) {
        previewSession.close();
        previewSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  @Override
  public View onCreateView(
          final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(R.layout.tfe_od_activity_camera, container, false);
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    filmButton = view.findViewById(R.id.share_button);

    //filmButton.setOnClickListener(v->takePicture());

  }

  public void setCamera(String cameraId) {
    this.cameraId = cameraId;
  }

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("ImageListener");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

//  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  public interface ConnectionCallback {
    void onPreviewSizeChosen(Size size, int cameraRotation);
  }

  private CameraConnectionFragment(
          final ConnectionCallback connectionCallback,
          final OnImageAvailableListener imageListener,
          final int layout,
          final Size inputSize) {
    this.cameraConnectionCallback = connectionCallback;
    this.imageListener = imageListener;
    this.layout = layout;
    this.inputSize = inputSize;
  }

  public static CameraConnectionFragment newInstance(
          final ConnectionCallback callback,
          final OnImageAvailableListener imageListener,
          final int layout,
          final Size inputSize) {
    return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
  }


  protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
    final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
    final Size desiredSize = new Size(width, height);

    // Collect the supported resolutions that are at least as big as the preview Surface
    boolean exactSizeFound = false;
    final List<Size> bigEnough = new ArrayList<Size>();
    final List<Size> tooSmall = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.equals(desiredSize)) {
        // Set the size but don't return yet so that remaining sizes will still be logged.
        exactSizeFound = true;
      }

      if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
        bigEnough.add(option);
      } else {
        tooSmall.add(option);
      }
    }

    LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
    LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
    LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

    if (exactSizeFound) {
      LOGGER.i("Exact size match found.");
      return desiredSize;
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }



  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
              });
    }
  }



  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /** Shows an error message dialog. */
  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(final String message) {
      final ErrorDialog dialog = new ErrorDialog();
      final Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
              .setMessage(getArguments().getString(ARG_MESSAGE))
              .setPositiveButton(
                      android.R.string.ok,
                      new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialogInterface, final int i) {
                          activity.finish();
                        }
                      })
              .create();
    }
  }
}