package com.ailsa.imageloader.imageloader;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;
import java.io.InputStream;

/**
 * Created by 58 on 2017/5/4.
 */

public class ImageResize {

    public Bitmap decodeBitmapFromResource(Resources res, int resId, int imageWidth, int imageHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        int size = calculateInSampleSize(options, imageWidth, imageHeight);
        options.inSampleSize = size;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public Bitmap decodeBitmapFromFileDescriptor(FileDescriptor fd, int imageWidth, int imageHeight){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        int size = calculateInSampleSize(options, imageWidth, imageHeight);
        options.inSampleSize = size;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    public Bitmap decodeBitmapFromStream(InputStream is, int imageWidth, int imageHeight){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        int size = calculateInSampleSize(options, imageWidth, imageHeight);
        options.inSampleSize = size;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(is, null, options);
    }


    public int calculateInSampleSize(BitmapFactory.Options options, int imageWidth, int imageHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int size = 1;
        if (width > imageWidth || height > imageHeight) {
            int halfW = width / 2;
            int halfH = height / 2;
            while(halfH / size >= imageHeight && halfW / size >= imageWidth){
                size *= 2;
            }
        }
        return size;
    }
}
