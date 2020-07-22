package com.example.ts_capture_pliot;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.IOException;

public class GalleryTask extends AsyncTask {
    Context mContext;
    Uri uri;
    NativeCallJS nativeCallJS;

    public GalleryTask(Context mContext, Uri uri) {
        this.mContext = mContext;
        this.uri = uri;
        nativeCallJS = NativeCallJS.getInstance();
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        Bitmap bitmap;

        try {
            /* MediaStore를 통해 받는 방법 */
            bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), uri);

            // Rotation
            String filePath = getFilePathFromUri(uri);
            int rotation = getOrientationFromFilePath(filePath);

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

            // Scale
            float widthRatio = (float)nativeCallJS.getMaxWidth() / (float)bitmap.getWidth();
            float heightRatio = (float)nativeCallJS.getMaxHeight() / (float)bitmap.getHeight();

            int newSizeWidth, newSizeHeight;

            if (widthRatio > heightRatio) { // 비율이 깨지지 않기 위해 작은 비율로 화면 축소
                newSizeWidth = (int)(bitmap.getWidth() * heightRatio);
                newSizeHeight = (int)(bitmap.getHeight() * heightRatio);
            } else {
                newSizeWidth = (int)(bitmap.getWidth() * widthRatio);
                newSizeHeight = (int)(bitmap.getHeight() * widthRatio);
            }

            bitmap = Bitmap.createScaledBitmap(bitmap, newSizeWidth, newSizeHeight,false);

            // 압축률 조정 및 JS 호출
            nativeCallJS.doneChildCallMom(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Uri를 통해 File Path 구하기
    private String getFilePathFromUri(Uri uri) {
        /* Uri의 형태에 따라 1, 2번 방법으로 다르게 처리 추후 새로운 Uri 형태가 들어오면 추가적인 예외처리 필요 */

        // 1. content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3Dencoded%3Du8mmE8f4JIn9%2F%2BAjonZTOhL%2BfvfNKTPMKXZmmYmgOqP7b%2Fsh94V%2BEySL
        if (uri.getPath().contains("encoded=")) { return uri.getPath(); }

        // 2. content://com.android.providers.media.documents/document/image%3A2119
        String id = DocumentsContract.getDocumentId(uri).split(":")[1];
        String[] columns = { MediaStore.Files.FileColumns.DATA };
        String selection = MediaStore.Files.FileColumns._ID + " = " + id;
        Cursor cursor = mContext.getContentResolver().query(MediaStore.Files.getContentUri("external"), columns, selection, null, null);
        try {
            int columnIndex = cursor.getColumnIndex(columns[0]);
            if (cursor.moveToFirst()) { return cursor.getString(columnIndex); }
        } finally {
            cursor.close();
        }

        return null;
    }

    // File Path를 통해 Image Orientation 정보 구하기
    private int getOrientationFromFilePath(String filePath) {
        ExifInterface exifInterface = null;

        try {
            exifInterface = new ExifInterface(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);

        if (orientation != -1) {
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
            }
        }

        return 0;
    }
}