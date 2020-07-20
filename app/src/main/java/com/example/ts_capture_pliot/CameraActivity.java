package com.example.ts_capture_pliot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class CameraActivity extends AppCompatActivity {
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private Handler mHandler;

    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest mPreviewRequest; // Preview
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession.CaptureCallback mStillCaptureCallback;

    private MediaActionSound sound;
    private NativeCallJS nativeCallJS;
    private ArrayList<Bitmap> willSendImages;

    private int maxWidth, maxHeight;
    private Button btn_Capture;
    private Button btn_Back, btn_Complete;
    private ImageView focus;

    /* ------------------------------------------------- Initialize Activity ------------------------------------------------- */
    /* ------------------------------------------------- Initialize Activity ------------------------------------------------- */
    /* ------------------------------------------------- Initialize Activity ------------------------------------------------- */
    /* ------------------------------------------------- Initialize Activity ------------------------------------------------- */
    /* ------------------------------------------------- Initialize Activity ------------------------------------------------- */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        setInit();
        setSurfaceView();
    }

    // 프로그램 시작 후 Id 외 프로그램 실행에 필요한 초기화 수행
    private void setInit() {
        // Preview Id
        mSurfaceView = findViewById(R.id.surfaceView);

        focus = findViewById(R.id.focus);

        btn_Back = findViewById(R.id.btn_Back);
        btn_Capture = findViewById(R.id.btn_Capture);
        btn_Complete = findViewById(R.id.btn_Complete);

        // Button Action
        btn_Back.setOnClickListener(new View.OnClickListener() { // 취소
            @Override
            public void onClick(View view) { finish(); }
        });
        btn_Capture.setOnClickListener(new View.OnClickListener() { // 촬영
            @Override
            public void onClick(View v) { takeImage(); }
        });
        btn_Complete.setOnClickListener(new View.OnClickListener() { // 완료
            @Override
            public void onClick(View view) { takenImage(); }
        });

        // 찰칵 사운드
        sound = new MediaActionSound();

        // 싱글톤 소환
        nativeCallJS = NativeCallJS.getInstance();

        // 촬영 저장할 변수
        willSendImages = new ArrayList<>();

        if (nativeCallJS.getMode().equals("S")) { focus.setVisibility(View.VISIBLE); }

        maxWidth = Integer.parseInt(nativeCallJS.getMaxSize());
        maxHeight = (int) ((float) maxWidth / 1.7777777777777);
    }

    // Frame for preview
    public void setSurfaceView() {
        mSurfaceHolder = mSurfaceView.getHolder(); // surfaceView를 제어
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) { setCamera(); } // Surface 생성되면 카메라 구성
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) { }
            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) { unsetActivity(); } // Surface 파괴되면 액티비티 정리
        });
    }

    /* ------------------------------------------------- Thread Configuration ------------------------------------------------- */
    /* ------------------------------------------------- Thread Configuration ------------------------------------------------- */
    /* ------------------------------------------------- Thread Configuration ------------------------------------------------- */
    /* ------------------------------------------------- Thread Configuration ------------------------------------------------- */
    /* ------------------------------------------------- Thread Configuration ------------------------------------------------- */

    // Camera set on the sub-thread
    public void setCamera() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "카메라 권한을 확인해주세요.", Toast.LENGTH_SHORT).show();
            finish();
        } // Camera permission

        HandlerThread handlerThread = new HandlerThread("camera2");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        String mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT); // Back camera
        CameraManager mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) { // 카메라 구성되면 이미지 리더 및 프리뷰 세팅
                    mCameraDevice = camera;
                    setOutputReader();
                    setPreview();
                }
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) { }
                @Override
                public void onError(CameraDevice camera, int error) { }
            }, mHandler); // sub-thread
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Image reader run on the main-thread
    private void setOutputReader() {
        final Handler mainHandler = new Handler(getMainLooper());

        try {
            mImageReader = ImageReader.newInstance(maxWidth, maxHeight, ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(final ImageReader reader) {
                    if (willSendImages.size() < 80) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Image image = reader.acquireNextImage(); // Reading image

                                try {
                                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                    byte[] bytes = new byte[buffer.remaining()];
                                    buffer.get(bytes);
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                                    Matrix matrix = new Matrix(); // Rotation
                                    matrix.postRotate(90);
                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                                    willSendImages.add(bitmap);
                                    btn_Capture.setText(String.valueOf(willSendImages.size()));

                                    if (nativeCallJS.getMode().equals("S")) { takenImage(); }
                                }catch (Exception e) {
                                    e.printStackTrace();
                                }finally {
                                    image.close();
                                }
                            }
                        });
                    }else {
                        Toast.makeText(CameraActivity.this, "최대 50장까지 촬영할 수 있습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mainHandler); // main-thread
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // To get preview
    public void setPreview() {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // 프리뷰 요청
            mPreviewRequestBuilder.addTarget(mSurfaceHolder.getSurface()); // 요청한 프리뷰를 표시할 타겟
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;

                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewRequest = mPreviewRequestBuilder.build(); // 카메라 촬영 포커싱 후 프리뷰 모드로 복귀하기 위해 프리뷰 세팅 글로벌 저장
                        mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler);
                    } catch(CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) { }
            }, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /* ------------------------------------------------- Take A Photo ------------------------------------------------- */
    /* ------------------------------------------------- Take A Photo ------------------------------------------------- */
    /* ------------------------------------------------- Take A Photo ------------------------------------------------- */
    /* ------------------------------------------------- Take A Photo ------------------------------------------------- */
    /* ------------------------------------------------- Take A Photo ------------------------------------------------- */

    // Camera state
    private int mState = STATE_PREVIEW;
    private static final int STATE_PREVIEW = 0; // 프리뷰 상태
    private static final int STATE_PICTURE_TAKING = 1; // 촬영 완료를 기다리는 상태
    private static final int STATE_PICTURE_TAKEN = 2; // 촬영 완료 * 이 구간이 없으면 촬영이 되었는지 아닌지 판단히 모호하기 때문에 여러장 찍힘

    // Shoot
    public void takeImage() { mState = STATE_PICTURE_TAKING; }

    // To capture still image
    private void captureStillImage() {
        try {
            if (mCameraDevice == null) { return; }
            sound.play(MediaActionSound.SHUTTER_CLICK); // 찰칵

            preCapture();

            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mStillCaptureCallback,null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void preCapture() { // 촬영 사전 준비
        try {
            if (mCaptureRequestBuilder == null) {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // 카메라 촬영은 프리뷰 모드와 별개 구성
                mCaptureRequestBuilder.addTarget(mImageReader.getSurface()); // 요청 결과 이미지 리더로 반환
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
            if (mStillCaptureCallback == null) {
                mStillCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        try {
                            mState = STATE_PREVIEW; // 프리뷰 상태 전환
                            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler); // 실제 프리뷰 전환
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // State 변화에 따른 액션 핸들러
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            progress(result);
        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            progress(partialResult);
        }
        private void progress(CaptureResult result) {
            colorChanger();
            switch (mState) {
                case STATE_PREVIEW: { break; } // 프리뷰 상태 캡처 버튼을 누르면 처리후 unlockFocus 통해 복귀
                case STATE_PICTURE_TAKING: {
                    mState = STATE_PICTURE_TAKEN;
                    captureStillImage();
                    break;
                }
            }
        }
    };

    // 촬영 상태 구분을 위한 UI 색감 변화
    private void colorChanger() {
        if (mState == STATE_PREVIEW) {
            btn_Back.setTextColor(Color.parseColor("#374957"));
            btn_Capture.setTextColor(Color.parseColor("#374957"));
            btn_Complete.setTextColor(Color.parseColor("#374957"));
        }else if (mState == STATE_PICTURE_TAKING) {
            btn_Back.setTextColor(Color.parseColor("#30374957"));
            btn_Capture.setTextColor(Color.parseColor("#30374957"));
            btn_Complete.setTextColor(Color.parseColor("#30374957"));
        }
    }

    /* ------------------------------------------------- Finish Activity ------------------------------------------------- */
    /* ------------------------------------------------- Finish Activity ------------------------------------------------- */
    /* ------------------------------------------------- Finish Activity ------------------------------------------------- */
    /* ------------------------------------------------- Finish Activity ------------------------------------------------- */
    /* ------------------------------------------------- Finish Activity ------------------------------------------------- */

    // bitmap 이미지를 base64 인코딩 및 완료 시 call JS
    private void takenImage() {
        try {
            String isDone;
            while(willSendImages.size() != 0) {
                isDone = 1 == willSendImages.size() ? "Y" : "N"; // 마지막 사진인지 확인
                nativeCallJS.doneChildCallMom(willSendImages.remove(willSendImages.size() - 1), isDone); // 이미지 프로세싱 후 JS 호출
            }
            finish();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // Activity 정리
    private void unsetActivity() {
        if(mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mHandler != null) {
            mHandler = null;
        }
    }
}

