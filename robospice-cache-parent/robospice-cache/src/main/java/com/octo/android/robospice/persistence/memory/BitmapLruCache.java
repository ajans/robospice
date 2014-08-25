package com.octo.android.robospice.persistence.memory;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.util.LruCache;

/**
 * {@link LruCache} for {@link Bitmap}s.
 * @author David Stemmer
 * @author Mike Jancola
 * @author SNI
 */
public class BitmapLruCache extends LruCache<Object, CacheItem<Bitmap>> {
    private static final int BYTES_PER_KBYTE = 1024;

    public BitmapLruCache(int maxSize) {
        super(maxSize);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    protected int sizeOf(Object key, CacheItem<Bitmap> value) {
        Bitmap data = value.getData();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return data.getRowBytes() * data.getHeight() / BYTES_PER_KBYTE;
        } else {
            return data.getByteCount() / BYTES_PER_KBYTE;
        }
    }
}
