package com.example.ts_capture_pliot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();
    }

    // 퍼미션 요청 후 웹뷰 로드
    private void requestPermission() {
        String[] permissions = {Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6 (API LEVEL23) 이상 런타임 권한 지원기기일 경우 묻기
            if (havePermissions(permissions)) moveToWeb(); // 권한 있으면 웹뷰 로드
            else requestPermissions(permissions, 0); // 권한 없으면 권한 요청
        }
    }

    // 앱 퍼미션 상태 확인
    private boolean havePermissions(String[] permissions) {
        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) return false;
        }
        return true;
    }

    // requestPermissions() 결과 반환
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (havePermissions(permissions)) moveToWeb();
        else alert();
    }

    // 웹뷰 로드
    private void moveToWeb() {
        startActivity(new Intent(MainActivity.this, WebActivity.class));
        finish();
    }

    // 권한 경고
    private void alert() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("앱 권한")
                .setMessage("앱 권한을 확인해주세요.\n앱이 종료됩니다.")
                .setCancelable(false)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
        alert.create().show();
    }
}