package com.example.ts_capture_pliot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
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
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private MediaActionSound sound;
    private NativeCallJS nativeCallJS;
    private ArrayList<Bitmap> willSendImages;

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

        // Button Action
        findViewById(R.id.btn_Back).setOnClickListener(new View.OnClickListener() { // 취소
            @Override
            public void onClick(View view) { finish(); }
        });
        findViewById(R.id.btn_Capture).setOnClickListener(new View.OnClickListener() { // 촬영
            @Override
            public void onClick(View v) { takeImage(); }
        });
        findViewById(R.id.btn_Complete).setOnClickListener(new View.OnClickListener() { // 완료
            @Override
            public void onClick(View view) { takenImage(); }
        });

        // 찰칵 사운드
        sound = new MediaActionSound();

        // 싱글톤 소환
        nativeCallJS = NativeCallJS.getInstance();

        // 촬영 저장할 변수
        willSendImages = new ArrayList<>();
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
        Handler mainHandler = new Handler(getMainLooper());

        String mCameraId = mCameraDevice.getId(); // 후면 카메라 사용
        CameraManager mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size largestPreviewSize = map.getOutputSizes(ImageFormat.JPEG)[0]; // Maximum available size of camera

            mImageReader = ImageReader.newInstance(largestPreviewSize.getWidth(), largestPreviewSize.getHeight(), ImageFormat.JPEG, 30);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (willSendImages.size() < 20) {
                        Image image = reader.acquireNextImage(); // Reading image
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                        Matrix matrix = new Matrix(); // Rotation
                        matrix.postRotate(90);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                        willSendImages.add(bitmap);

                        if (nativeCallJS.getMode().equals("S")) { takenImage(); }
                    }else {
                        Toast.makeText(CameraActivity.this, "최대 20장까지 촬영할 수 있습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mainHandler); // main-thread
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // To get preview
    public void setPreview() {
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // 프리뷰 요청
            mCaptureRequestBuilder.addTarget(mSurfaceHolder.getSurface()); // 요청한 프리뷰를 표시할 타겟
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;

                    try {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewRequest = mCaptureRequestBuilder.build(); // 카메라 촬영 포커싱 후 프리뷰 모드로 복귀하기 위해 프리뷰 세팅 글로벌 저장
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
    private static final int STATE_PREVIEW = 0; // Showing camera preview
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3; // Waiting for the exposure state to be something other than precapture
    private static final int STATE_PICTURE_TAKEN = 4;

    // Shoot
    public void takeImage() {
        lockFocus();
    }

    // AF(Auto Focus) - 자동 초점 조절
    private void lockFocus() {
        try {
            mState = STATE_WAITING_LOCK; // AF 상태 전환

            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START); // 초점 맞추기
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // AF 해제
    private void unlockFocus() {
        try {
            mState = STATE_PREVIEW; // 프리뷰 상태 전환

            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mHandler);

            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // AE(Auto Exposure) - 광량 조건 감지, 셔터 속도와 조리개 값을 자동 조절하여 적정한 노출 얻기
    private void runPrecaptureSequence() {
        try {
            mState = STATE_WAITING_PRECAPTURE; // AE 상태 전환

            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START); // 적절한 Exposure 가져오기
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // To capture still image
    private void captureStillImage() {
        try {
            if (mCameraDevice == null) { return; }
            sound.play(MediaActionSound.SHUTTER_CLICK); // 찰칵

            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // 카메라 촬영은 프리뷰 모드와 별개 구성
            captureRequestBuilder.addTarget(mImageReader.getSurface()); // 요청 결과 이미지 리더로 반환

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            mCameraCaptureSession.stopRepeating(); // 반복되는 캡처 요청 취소
            mCameraCaptureSession.abortCaptures(); // 보류중인 카메라 캡처 모두 버리기
            mCameraCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    unlockFocus();
                }
            }, null);
        } catch (CameraAccessException e) {
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
            switch (mState) {
                case STATE_PREVIEW: { break; } // 프리뷰 상태, 캡처 후 unlockFocus 통해 복귀
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE); // 촬영 버튼을 눌러 result의 AF 상태가 존재하면 사진 촬영 함수 호출
                    if (afState == null) { // AF 상태 확인 null이면 캡처
                        captureStillImage();
                    } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) { // ae가 모여있거나 null이면 capture
                            mState = STATE_PICTURE_TAKEN;
                            captureStillImage();
                        } else { // AE
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillImage();
                    }
                    break;
                }
            }
        }
    };

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
                String convertedImage = nativeCallJS.imageProcessor(willSendImages.remove(willSendImages.size() - 1)); // 이미지 프로세싱
                isDone = 0 == willSendImages.size() ? "Y" : "N"; // 마지막 사진인지 확인
                nativeCallJS.doneChildCallMom(convertedImage, isDone); // JS 호출
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
