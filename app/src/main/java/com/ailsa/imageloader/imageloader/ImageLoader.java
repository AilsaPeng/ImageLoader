package com.ailsa.imageloader.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ailsa.
 * 加载图片时:先去内存缓存中找，若找到则返回图片；
 * 若未找到则去磁盘缓存中找，若找到则返回图片，并将图片添加进内存缓存；
 * 若仍未找到，则从网络上下载，并存储的磁盘缓存中。
 */

public class ImageLoader {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POLL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POLL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE_TIME = 10L;

    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int DISK_CACHE_INDEX = 0;

    private static final ThreadFactory mFactory = new ThreadFactory() {
        private AtomicInteger mCount = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoder:"+mCount.getAndIncrement());
        }
    };
    private static final ThreadPoolExecutor mExecutor = new ThreadPoolExecutor(CORE_POLL_SIZE,
            MAXIMUM_POLL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            mFactory);
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            LoadResult result = (LoadResult) msg.obj;
            ImageView imageView = result.imageView;
            Bitmap bitmap = result.bitmap;
            String uri = (String)imageView.getTag();
            if(uri.equals(result.uri)){
                imageView.setImageBitmap(bitmap);
            }
        }
    };
    private Context mContext;
    private ImageResize mImageResize;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskCache;
    private boolean hasDisCache = false;
    public ImageLoader(Context context){
        mContext = context;
        mImageResize = new ImageResize();
        int cacheSize = (int) Runtime.getRuntime().maxMemory() / 1024 / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(mContext, "image");
        if(!diskCacheDir.exists()){
            diskCacheDir.mkdirs();
        }
        if(getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE){
            try {
                mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                hasDisCache = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void putMemmoryCache(String key, Bitmap bitmap){
        if(mMemoryCache.get(key) == null){
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getMemmoryCache(String key){
        return mMemoryCache.get(key);
    }

    public void bindBitmap(final String url, final ImageView imageView, final int imageWidth, final int imageHeight){
        imageView.setTag(url);
        Bitmap bitmap = loadBitmapFromMemmoryCache(url);
        if(bitmap != null){
            imageView.setImageBitmap(bitmap);
            return ;
        }
        Runnable loadTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, imageWidth, imageHeight);
                if(bitmap != null){
                    LoadResult loadResult = new LoadResult(imageView, url, bitmap);
                    mHandler.obtainMessage(1, loadResult).sendToTarget();
                }
            }
        };
        mExecutor.execute(loadTask);
    }

    public Bitmap loadBitmap(String url, int imageWidth, int imageHeight){
//        Bitmap bitmap = loadBitmapFromMemmoryCache(url);
//        if(bitmap != null){
//            return bitmap;
//        }
        Bitmap bitmap = loadBitmapFromDiskCache(url, imageWidth, imageHeight);
        if(bitmap != null){
            return bitmap;
        }
        bitmap = loadBitmapFromHttp(url, imageWidth, imageHeight);
        if(bitmap == null && !hasDisCache){
            bitmap = downloadBitmapFromUrl(url);
        }
        return bitmap;
    }

    public Bitmap loadBitmapFromMemmoryCache(String url){
        String key = hashKey(url);
        Bitmap bitmap = getMemmoryCache(key);
        return bitmap;
    }

    public Bitmap loadBitmapFromHttp(String url, int imageWidth, int imageHeight){
        if(!hasDisCache){
            return null;
        }
        String key = hashKey(url);
        try {
            DiskLruCache.Editor editor = mDiskCache.edit(key);
            if(editor != null){
                OutputStream os = editor.newOutputStream(DISK_CACHE_INDEX);
                if(downloadUrlToStream(url, os)){
                    editor.commit();
                } else{
                    editor.abort();
                }
            }
            mDiskCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadBitmapFromDiskCache(url, imageWidth, imageHeight);
    }

    public Bitmap loadBitmapFromDiskCache(String url, int imageWidth, int imageHeight){
        if(!hasDisCache){
            return null;
        }
        String key = hashKey(url);
        Bitmap bitmap = null;
        try {
            DiskLruCache.Snapshot snapshot = mDiskCache.get(key);
            if(snapshot != null){
                FileInputStream is = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fd = is.getFD();
                bitmap = mImageResize.decodeBitmapFromFileDescriptor(fd, imageWidth, imageHeight);
                if(bitmap != null) {
                    putMemmoryCache(key, bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public boolean downloadUrlToStream(String urlString, OutputStream outputStream){
        HttpURLConnection connection = null;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(connection.getInputStream());
            bos = new BufferedOutputStream(outputStream);
            int bytes;
            while((bytes = bis.read()) != -1){
                bos.write(bytes);
            }
            bis.close();
            bos.close();
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }
        return false;
    }

    public Bitmap downloadBitmapFromUrl(String url){
        Bitmap bitmap = null;
        try {
            URL image = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) image.openConnection();
            BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
            bitmap = BitmapFactory.decodeStream(bis);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public String hashKey(String uri){
        String key = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(uri.getBytes());
            key = bytesToHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            key = String.valueOf(uri.hashCode());
        }
        return key;
    }

    public String bytesToHex(byte[] bytes){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < bytes.length; i++){
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if(hex.length() == 1){
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    public File getDiskCacheDir(Context context, String fileName){
        String diskCachePath = null;
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            diskCachePath = context.getExternalCacheDir().getPath();
        } else {
            diskCachePath = context.getCacheDir().getPath();
        }
        return new File(diskCachePath + File.separator + fileName);
    }

    public long getUsableSpace(File file){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
            return file.getUsableSpace();
        }
        StatFs statFs = new StatFs(file.getPath());
        return statFs.getBlockSize() * statFs.getAvailableBlocks();
    }
    class LoadResult{
        ImageView imageView;
        String uri;
        Bitmap bitmap;
        public LoadResult(ImageView imageView, String uri, Bitmap bitmap){
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }
}
