package com.example.ts_capture_pliot;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;

public class WebActivity extends AppCompatActivity {
    WebView webView;
    NativeCallJS nativeCallJS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // webView Debug 허가
            webView.setWebContentsDebuggingEnabled(true);
        }

        configWebPage();
        loadWebPage();
    }

    @Override
    public void onBackPressed() { } // 뒤로가기 불능

    private void configWebPage() {
        // init
        webView = findViewById(R.id.webview);
        nativeCallJS = NativeCallJS.getInstance(); // Singleton 객체 발동

        // Web Setting
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // JSP의 자바스크립트를 구동할 수 있도록 해줌

        // Custom UserAgent 할당
        String userAgent = webSettings.getUserAgentString(); // 기존 UserAgent 가져오기
        webSettings.setUserAgentString(userAgent + "/webscanner"); // 새로운 UserAgent 설정
    }

    private void loadWebPage() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl(); // JS의 호출을 감지

                nativeCallJS.setWebView(webView);
                nativeCallJS.setMode(uri.getQueryParameter("mode"));
                nativeCallJS.setCallback(uri.getQueryParameter("callback"));

                String location = uri.getQueryParameter("location");
                if (location.equals("camera")) moveToCamera(); // location에 따른 화면 이동
                else if (location.equals("gallery")) moveToGallery();

                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });
        webView.loadUrl(getString(R.string.CoreAddress2));
    }

    private void moveToCamera() {
        Intent intent = new Intent(WebActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private void moveToGallery() {
        boolean selector = nativeCallJS.getMode().equals("S") ? false : true; // Single or Multi Select

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, selector);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent,null),0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == -1) { // 갤러리에서 반환 받은 데이터가 있다면
            ArrayList<Bitmap> willSendImages = new ArrayList<>();

            // 갤러리에서 선택한 이미지를 bitmap으로
            if (data.getData() != null) {
                Bitmap bitmap;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                    willSendImages.add(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                Bitmap bitmap;
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    try {
                        if (clipData.getItemCount() > 10) { //  사진 선택 제한
                            moveToGallery();
                            Toast.makeText(this, "최대 10장까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show();
                            break;
                        }
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), clipData.getItemAt(i).getUri());
                        willSendImages.add(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // bitmap 이미지를 base64 인코딩 및 완료 시 call JS
            String isDone;
            while(willSendImages.size() != 0) {
                String convertedImage = nativeCallJS.imageProcessor(willSendImages.remove(willSendImages.size()-1)); // 이미지 프로세싱
                isDone = 0 == willSendImages.size() ? "Y" : "N"; // 마지막 사진인지 확인
                nativeCallJS.doneChildCallMom(convertedImage, isDone); // JSON으로 쌓다가 마지막 사진이 확인되면 call JS
            }
        }
    }
}
