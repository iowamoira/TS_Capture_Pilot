package com.example.ts_capture_pliot;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class WebActivity extends AppCompatActivity {
    private WebView webView;
    private NativeCallJS nativeCallJS;

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
    public void onBackPressed() { webView.reload(); } // 웹뷰 리로드

    private void configWebPage() {
        // Init
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
        // webView Url Receiver
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl(); // JS의 호출을 감지

                if (uri.toString().contains("http")) { // Custom url scheme 아닌 일반적인 url이 들어올 때
                    webView.loadUrl(uri.toString());
                    return true;
                }

                nativeCallJS.setWebView(webView);
                nativeCallJS.setMode(uri.getQueryParameter("mode"));
                nativeCallJS.setMaxSize(uri.getQueryParameter("maxSize"));
                nativeCallJS.setCallback(uri.getQueryParameter("callback"));

                String location = uri.getQueryParameter("location");
                if (location.equals("camera")) moveToCamera(); // location에 따른 화면 이동
                else if (location.equals("gallery")) moveToGallery();

                return true;
            }
        });

        // webView JS Alert Listener
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });

        // webView Downloader
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String receivedUrl, String receivedUserAgent, String receivedfileName, String receivedApplication, long receivedFileLength) {
                String fileName = URLUtil.guessFileName(receivedUrl, receivedfileName, receivedApplication);

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(receivedUrl));

                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                downloadManager.enqueue(request);

                Toast.makeText(getApplicationContext(),"'" + fileName + "\n"+ receivedFileLength + "byte' 파일이\n'" + Environment.DIRECTORY_DOWNLOADS + "' 에 저장되었습니다.", Toast.LENGTH_LONG).show();
            }
        });

        // webView Loader
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
        startActivityForResult(Intent.createChooser(intent,null), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            ArrayList<Uri> willSendUris = new ArrayList<>();

            if(data.getData() != null) { // 싱글 모드
                willSendUris.add(data.getData());
            }else if(data.getClipData() != null) { // 멀티 모드
                ClipData clipData = data.getClipData();
                if (clipData.getItemCount() > 50) {
                    Toast.makeText(WebActivity.this, "최대 50장까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show();
                    moveToGallery();
                    return;
                }

                for (int i = 0; i < clipData.getItemCount(); i++) {
                    willSendUris.add(clipData.getItemAt(i).getUri());
                }
            }

            AsyncProcess(willSendUris);
        }
    }

    // Async
    private void AsyncProcess(ArrayList<Uri> receivedUris) {
        nativeCallJS.setAsyncTotalImage(receivedUris.size()); // 전체 갯수 알려주기

        while (receivedUris.size() != 0) {
            GalleryTask galleryTask = new GalleryTask(WebActivity.this, receivedUris.remove(0));
            galleryTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
}