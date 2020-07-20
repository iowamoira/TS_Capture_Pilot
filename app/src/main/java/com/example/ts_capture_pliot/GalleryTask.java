package com.example.ts_capture_pliot;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.ArrayList;

public class GalleryTask extends AsyncTask {
    Context mContext;
    Uri receivedUris;
    String isDone;
    NativeCallJS nativeCallJS;

    public GalleryTask(Context mContext, Uri receivedUris, String isDone) {
        this.mContext = mContext;
        this.receivedUris = receivedUris;
        this.isDone = isDone;
        nativeCallJS = NativeCallJS.getInstance();
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        Bitmap bitmap;
//        InputStream in = null;
//        String isDone;
//        while(receivedUris.size() != 0) {
        try {
            /* MediaStore를 통해 받는 방법 */
            bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), receivedUris);

            /* Input Stream으로 받는 방법 */
//                in = mContext.getContentResolver().openInputStream(receivedUris.remove(receivedUris.size() - 1));

//                isDone = 0 == receivedUris.size() ? "Y" : "N"; // 마지막 사진인지 확인
            nativeCallJS.doneChildCallMom(bitmap, isDone); // 이미지 프로세싱 및 JS 호출

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
//        }
        return null;
    }
}

