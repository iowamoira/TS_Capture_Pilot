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
    /* Field 특별한 설명이 없는 것들은 변수명 그대로 이해 */
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private Handler mHandler;

    private CaptureRequest mPreviewRequest; // Preview 설정
    private CaptureRequest mCaptureRequest; // Camera 설정
    private CameraCaptureSession mCameraCaptureSession; // 상태에 따라 프리뷰와 카메라 설정을 받을 세션

    private MediaActionSound sound;
    private NativeCallJS nativeCallJS;
    private ArrayList<Bitmap> willSendImages;

    private Button btn_Capture, btn_Back, btn_Complete;
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

        btn_Capture = findViewById(R.id.btn_Capture);
        btn_Back = findViewById(R.id.btn_Back);
        btn_Complete = findViewById(R.id.btn_Complete);

        // Button Action
        btn_Capture.setOnClickListener(onClickListener); // 촬영
        btn_Back.setOnClickListener(onClickListener); // 취소
        btn_Complete.setOnClickListener(onClickListener); // 완료

        // 찰칵 사운드
        sound = new MediaActionSound();

        // 싱글톤 소환
        nativeCallJS = NativeCallJS.getInstance();

        // 촬영 저장할 변수
        willSendImages = new ArrayList<>();

        if (nativeCallJS.getMode().equals("S")) { focus.setVisibility(View.VISIBLE); } // 싱글 모드시 포커스 이미지 출력
    }

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_Capture :
                    takeImage();
                    break;
                case R.id.btn_Back :
                    finish();
                    break;
                case R.id.btn_Complete :
                    takenImage();
                    break;
                default:
                    break;
            }
        }
    };

    // Frame for preview
    public void setSurfaceView() {
        mSurfaceHolder = mSurfaceView.getHolder(); // surfaceView 제어
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
                public void onOpened(CameraDevice camera) { // 카메라 구성되면 이미지 리더 및 프리뷰 캡처 세팅
                    mCameraDevice = camera;
                    setOutputReader();
                    setPreview();
                    setCapture();
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
            mImageReader = ImageReader.newInstance(nativeCallJS.getMaxWidth(), nativeCallJS.getMaxHeight(), ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(final ImageReader reader) {
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

                                if (nativeCallJS.getMode().equals("S")) { takenImage(); } // 싱글 모드
                            }catch (Exception e) {
                                e.printStackTrace();
                            }finally {
                                image.close();
                            }
                        }
                    });
                }
            }, mainHandler); // main-thread
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // To get preview setting
    public void setPreview() {
        try {
            final CaptureRequest.Builder mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // 프리뷰 요청
            mPreviewRequestBuilder.addTarget(mSurfaceHolder.getSurface()); // 요청한 프리뷰를 표시할 타겟
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequest = mPreviewRequestBuilder.build(); // 카메라 촬영 후 프리뷰 모드로 복귀하기 위해 글로벌 저장

            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;

                    try {
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

    // To get capture setting
    private void setCapture() {
        try {
            CaptureRequest.Builder mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // capture
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface()); // 요청 결과 이미지 리더로 반환
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureRequest = mCaptureRequestBuilder.build();
        }catch (CameraAccessException e) {
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

    // Capture still image
    private void captureState() {
        try {
            if (mCameraDevice == null) { return; }
            sound.play(MediaActionSound.SHUTTER_CLICK); // 찰칵

            mCameraCaptureSession.capture(mCaptureRequest, mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Back to preview state
    private void previewState() {
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // State 변화에 따른 액션 핸들러
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            progress();
        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            progress();
        }
        private void progress() {
            colorChanger();
            switch (mState) {
                case STATE_PREVIEW: { break; } // 프리뷰 상태 캡처 버튼을 누르면 처리후 unlockFocus 통해 복귀
                case STATE_PICTURE_TAKING: { // 촬영 상태 전환
                    mState = STATE_PICTURE_TAKEN;
                    captureState();
                    break;
                }
                case STATE_PICTURE_TAKEN: {
                    mState = STATE_PREVIEW; // 프리뷰 상태 전환
                    previewState();
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