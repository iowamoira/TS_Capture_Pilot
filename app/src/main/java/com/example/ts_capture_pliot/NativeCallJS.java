package com.example.ts_capture_pliot;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class NativeCallJS { /* - Initialization on demand holder idiom 방식의 Singleton 객체로 구현 */
    private NativeCallJS() { } /* 1. Singleton 객체이기 때문에 외부 클래스에서 직접 인스턴스 생성을 막고자 private Constructor 구성 */
    public static NativeCallJS getInstance() { /* 2. 발동 버튼, Singleton inner class 내에서 인스턴스를 생성하고 메모리에 올린다 */
        return LazyHolder.INSTANCE;
    }
    private static class LazyHolder { /* 3. 단 한번만 생성되며 외부 클래스에서 유일한 객체 인스턴스를 공유한다 */
        public static final NativeCallJS INSTANCE = new NativeCallJS();
    }

    // WebView, Callback 공유
    private WebView webView;
    private String callback;
    private String mode;

    public void setWebView(WebView webView) { this.webView = webView; }
    public void setCallback(String callback) { this.callback = callback; }
    public void setMode(String mode) { this.mode = mode; }

    public String getMode() { return mode; }

    // 이미지 변환 및 Base64 인코딩
    public String imageProcessor(Bitmap receivedBitmap) {
        // Scale
        float widthRatio = (float)1920 / (float)receivedBitmap.getWidth();
        float heightRatio = (float)1080 / (float)receivedBitmap.getHeight();

        int newSizeWidth, newSizeHeight;

        if (widthRatio > heightRatio) { // 비율이 깨지지 않기 위해 작은 비율로 화면 축소
            newSizeWidth = (int)(receivedBitmap.getWidth() * heightRatio);
            newSizeHeight = (int)(receivedBitmap.getHeight() * heightRatio);
        } else {
            newSizeWidth = (int)(receivedBitmap.getWidth() * widthRatio);
            newSizeHeight = (int)(receivedBitmap.getHeight() * widthRatio);
        }

        receivedBitmap = Bitmap.createScaledBitmap(receivedBitmap, newSizeWidth, newSizeHeight,false);

        // Compression
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        receivedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);

        /* Base64 Encoding Flag를 DEFAULT로 두면 76자마다 \n 붙음 주의, NO_WRAP 사용 */
        String convertedImage = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);

        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return convertedImage;
    }

    // Webscanner의 JS를 호출하는 함수
    public void doneChildCallMom(String receivedimage, String isDone) {
        final String base64String = callback + "('" + "{\"imgData\":\"data:image/jpeg;base64," + receivedimage + "\",\"isEnd\":\"" + isDone + "\"}" + "')";

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(base64String, null);
            }
        });
    }
}