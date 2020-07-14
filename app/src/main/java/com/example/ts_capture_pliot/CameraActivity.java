package com.example.ts_capture_pliot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
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
import android.util.Log;
import android.util.Size;
import android.view.Surface;
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
    String TAG = "TS_Capture_Camera";

    private Handler mHandler;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private MediaActionSound sound;
    private NativeCallJS nativeCallJS;
    private ArrayList<Bitmap> willSendImages;

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
            public void onClick(View v) { takeAPicture(); }
        });
        findViewById(R.id.btn_Complete).setOnClickListener(new View.OnClickListener() { // 완료
            @Override
            public void onClick(View view) { activityDone(); }
        });

        // 셔터 사운드 출력
        sound = new MediaActionSound();

        // 싱글톤 소환
        nativeCallJS = NativeCallJS.getInstance();

        // 촬영 저장할 변수
        willSendImages = new ArrayList<>();
    }

    // SurfaceListener 정의, SurfaceView 생성, 변경, 소멸
    public void setSurfaceView() {
        Log.v(TAG, "initSurfaceView()");

        mSurfaceHolder = mSurfaceView.getHolder(); // surfaceView를 제어
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                Log.v(TAG, "surfaceCreated()");
                setCameraIO(); // 카메라 설정
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.v(TAG, "surfaceChanged()");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                Log.v(TAG, "surfaceDestoryed()");
                unsetActivity();
            }
        });
    }

    // 카메라의 데이터를 받아줄 이미지 리더 설정 후 카메라 오픈
    public void setCameraIO() {
        Log.v(TAG, "setCameraIO()");

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            return;
        }

        // mHandler에 카메라 올려두고 촬영하면 callback 함수 발동 main Thread의 ImageReader가 받아 처리
        HandlerThread handlerThread = new HandlerThread("camera2");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()); // Camera on the other thread
        Handler mainHandler = new Handler(getMainLooper()); // Image reader on the main thread

        try {
            String mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT); // 후면 카메라 사용(FRONT)
            CameraCharacteristics characteristics = null;
            CameraManager mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            // StreamConfigurationMap 객체에는 카메라의 각종 지원 정보가 담겨있음.
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largestPreviewSize = map.getOutputSizes(ImageFormat.JPEG)[0]; // 촬영 디바이스의 최대 가용 사이즈 구하기

            // 이미지 리더 만들어 주기
            mImageReader = ImageReader.newInstance(largestPreviewSize.getWidth(), largestPreviewSize.getHeight(), ImageFormat.JPEG, 50);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mainHandler);

            mCameraManager.openCamera(mCameraId, deviceStateCallback, mHandler); // 카메라 오픈
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 카메라 오픈
    private CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.v(TAG, "deviceStateCallback()");
            mCameraDevice = camera;
            takePreview(); // 카메라 화면 셋팅
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) { }

        @Override
        public void onError(CameraDevice camera, int error) {
            Toast.makeText(CameraActivity.this, "카메라를 열지 못했습니다.", Toast.LENGTH_SHORT).show();
        }
    };

    // 카메라 프리뷰 화면 세팅
    public void takePreview() {
        Log.v(TAG, "takePreview()");
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // 프리뷰 요청
            mCaptureRequestBuilder.addTarget(mSurfaceHolder.getSurface()); // 요청한 프리뷰를 표시할 타겟
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), mSessionPreviewStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Session 상태 알림
    // 카메라 세션 상태에 대한 업데이트를 수신
    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) { // 카메라 장치가 구성을 완료하고 세션이 캡쳐 요청 처리를 시작할 수 있는 경우 호출
            Log.v(TAG, "mSessionPreviewStateCallback()");
            mCameraCaptureSession = session;

            try {
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // Auto Focus(AF) 모드
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH); // 피사체 밝기 기준 조리개
                mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mHandler);
            } catch(CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) { // 구성 에러 시 호출
            Log.v(TAG, "mSessionPreviewStateCallback() : onConfigureFailed");
        }
    };

    // Session 처리 알림
    // 카메라 장치에 제출된 진행률을 추적하기 위한 콜백, 캡쳐가 완료되면 호출
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            mCameraCaptureSession = session;
            unlockFocus();
        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            mCameraCaptureSession = session;
        }
        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    // Preview용 설정으로 복원
    private void unlockFocus() {
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL); // AF Trigger 취소
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mSessionCaptureCallback, mHandler); // AF 트리거의 취소를 실행한다
            // Preview를 유지하기 위해 setRepeatingRequest 함수를 실행한다
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), mSessionCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // willSendImages에 비트맵 형식으로 이미지 저장
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.v(TAG, "mOnImageAvailableListener()");

            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            // 사진 촬영 갯수 제한
            if (willSendImages.size() <= 20) {
                willSendImages.add(bitmap);
                sound.play(MediaActionSound.SHUTTER_CLICK); // 셔터 사운드 출력
            }else if (willSendImages.size() > 20) {
                Toast.makeText(CameraActivity.this, "최대 10장까지 촬영할 수 있습니다.", Toast.LENGTH_SHORT).show();
            }

            if (nativeCallJS.getMode().equals("S")) { activityDone(); }
        }
    };

    // 카메라 촬영
    public void takeAPicture() {
        Log.v(TAG, "TakeAPicture()");

        try {
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            int mDeviceRotation = getResources().getConfiguration().orientation;
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mDeviceRotation);
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, mSessionCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // CameraActivity 종료 시 웹뷰에 데이터 건네주도록 셋팅
    private void activityDone() {
        try {
            // bitmap 이미지를 base64 인코딩 및 완료 시 call JS
            String isDone;
            while(willSendImages.size() != 0) {
                String convertedImage = nativeCallJS.imageProcessor(willSendImages.remove(willSendImages.size() - 1)); // 이미지 프로세싱
                isDone = 0 == willSendImages.size() ? "Y" : "N"; // 마지막 사진인지 확인
                nativeCallJS.doneChildCallMom(convertedImage, isDone); // JSON으로 쌓다가 마지막 사진이 확인되면 call JS
            }
            finish();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            unsetActivity();
        }
    }

    private void unsetActivity() {
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if(mHandler != null) {
            mHandler = null;
        }
    }

}
