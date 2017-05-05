package com.ailsa.imageloader.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.ailsa.imageloader.R;
import com.ailsa.imageloader.imageloader.ImageLoader;

/**
 * Created by 58 on 2017/5/5.
 */

public class ImageAdapter extends BaseAdapter {
    private Drawable defaultDrawable;
    private String[] imageUrl;
    private LayoutInflater inflater;
    private ImageLoader imageLoader;
    private boolean isScroll = false;
    private int imageWidth;
    public ImageAdapter(Context context, String[] url, ImageLoader loader, int width){
        imageUrl = url;
        imageLoader = loader;
        inflater = LayoutInflater.from(context);
        defaultDrawable = context.getResources().getDrawable(R.mipmap.image_default);
        imageWidth = width;
    }

    public void setIsScroll(boolean scroll){
        isScroll = scroll;
    }
    @Override
    public int getCount() {
        return imageUrl.length;
    }

    @Override
    public Object getItem(int position) {
        return imageUrl[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder = null;
        if(convertView == null){
            convertView = inflater.inflate(R.layout.image_item, null);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView)convertView.findViewById(R.id.image);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        ImageView imageView = viewHolder.imageView;
        String tag = (String)imageView.getTag();
        String url = imageUrl[position];
        if(!url.equals(tag)){
            //加载默认显示图片
            imageView.setImageDrawable(defaultDrawable);
        }
        if(!isScroll){
            //未滚动，可加载图片
            imageView.setTag(url);
            imageLoader.bindBitmap(url, viewHolder.imageView, imageWidth, imageWidth);
        }
        return convertView;
    }
    class ViewHolder{
        public ImageView imageView;
    }
}
