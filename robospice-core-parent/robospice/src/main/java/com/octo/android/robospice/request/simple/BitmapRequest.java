package com.octo.android.robospice.request.simple;

import com.octo.android.robospice.request.ProgressByteProcessor;
import com.octo.android.robospice.request.SpiceRequest;

import org.apache.commons.io.IOUtils;

import roboguice.util.temp.Ln;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Downloads big images in size as bitmaps. All data is passed to the listener
 * using file system.
 * @author sni
 */
public class BitmapRequest extends SpiceRequest<Bitmap> {

    private static final int BUF_SIZE = 4096;

    private static final int DEFAULT_MAX_BITMAP_WIDTH = 2000;
    private static final int DEFAULT_MAX_BITMAP_HEIGHT = 2000;

    private String url;
    private BitmapFactory.Options options;
    private File cacheFile;

    private int height = -1;
    private int width = -1;

    /**
     * Creates a BitmapRequest able to fetch a {@link Bitmap} from the network.
     * @param url
     *            the url of the bitmap to fetch.
     * @param options
     *            used to decode the data received from the network.
     * @param cacheFile
     *            a file used to store data during download.
     */
    public BitmapRequest(String url, BitmapFactory.Options options,
        File cacheFile) {
        super(Bitmap.class);
        this.url = url;
        this.options = options;
        this.cacheFile = cacheFile;
    }

    /**
     * Creates a BitmapRequest able to fetch a {@link Bitmap} from the network.
     * @param url
     *            the url of the bitmap to fetch.
     * @param width
     *            the requested width of the image.
     * @param height
     *            the requested height of the image.
     * @param cacheFile
     *            a file used to store data during download.
     */
    public BitmapRequest(String url, int width, int height, File cacheFile) {
        super(Bitmap.class);
        this.url = url;
        this.width = width;
        this.height = height;
        this.cacheFile = cacheFile;
    }

    @Override
    public final Bitmap loadDataFromNetwork() throws Exception {
        try {
            final HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(
                url).openConnection();
            processStream(httpURLConnection.getContentLength(),
                httpURLConnection.getInputStream());

            if (width != -1 && height != -1) {
                this.options = new BitmapFactory.Options();
                options.inScaled = false;
                options.inPurgeable = true;
                options.inInputShareable = true;
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);
                options.inSampleSize = calculateInSampleSize(options, width,
                    height);
                options.inJustDecodeBounds = false;
                options.inScaled = false;
                options.inPurgeable = true;
                options.inInputShareable = true;
                Bitmap unscaledBitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(),
                    options);
                int unscaledWidth = unscaledBitmap.getWidth();
                int unscaledHeight = unscaledBitmap.getHeight();
                if (unscaledWidth > getMaximumPossibleBitmapWidth()
                        || unscaledHeight > getMaximumPossibleBitmapHeight()) {
                    if (unscaledWidth > unscaledHeight) {
                        int newWidth = getMaximumPossibleBitmapWidth();
                        float scaleDownRatio = (float) newWidth / (float) unscaledWidth;
                        int newHeight = Math.round((float) (unscaledHeight * scaleDownRatio));
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(unscaledBitmap, newWidth, newHeight, true);
                        return scaledBitmap;
                    } else {
                        int newHeight = getMaximumPossibleBitmapHeight();
                        float scaleDownRatio = (float) newHeight / (float) unscaledHeight;
                        int newWidth = Math.round((float) (unscaledWidth * scaleDownRatio));
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(unscaledBitmap, newWidth, newHeight, true);
                        return scaledBitmap;
                    }
                }
                return unscaledBitmap;
            } else {
                if (this.options == null) {
                    this.options = new BitmapFactory.Options();
                }

                options.inScaled = false;
                options.inPurgeable = true;
                options.inInputShareable = true;
                return BitmapFactory.decodeFile(cacheFile.getAbsolutePath(),
                    options);
            }
        } catch (final MalformedURLException e) {
            Ln.e(e, "Unable to create URL");
            throw e;
        } catch (final IOException e) {
            Ln.e(e, "Unable to download binary");
            throw e;
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private int getMaximumPossibleBitmapWidth() {
        Canvas canvas = new Canvas();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && canvas.isHardwareAccelerated()) {
            return canvas.getMaximumBitmapWidth();
        } else {
            return DEFAULT_MAX_BITMAP_WIDTH;
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private int getMaximumPossibleBitmapHeight() {
        Canvas canvas = new Canvas();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && canvas.isHardwareAccelerated()) {
            return canvas.getMaximumBitmapHeight();
        } else {
            return DEFAULT_MAX_BITMAP_HEIGHT;
        }
    }

    protected final String getUrl() {
        return this.url;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public void processStream(int contentLength, final InputStream inputStream)
        throws IOException {
        OutputStream fileOutputStream = null;
        try {
            // touch
            boolean isTouchedNow = cacheFile.setLastModified(System
                .currentTimeMillis());
            if (!isTouchedNow) {
                fileSetLastModifiedWorkaround(cacheFile);
            }
            fileOutputStream = new FileOutputStream(cacheFile);
            readBytes(inputStream, new ProgressByteProcessor(this,
                fileOutputStream, contentLength));
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    // workaround for https://code.google.com/p/android/issues/detail?id=18624
    private boolean fileSetLastModifiedWorkaround(File cacheFile) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(cacheFile, "rw");
        } catch (FileNotFoundException e) {
            Log.e(getClass().getSimpleName(),
                    String.format("Modification time of file %s could not be changed normally, file note found!",
                    cacheFile.getAbsolutePath()));
            return false;
        }
        long length;
        try {
            length = raf.length();
            raf.setLength(length + 1);
            raf.setLength(length);
            raf.close();
        } catch (IOException e) {
            Log.e(getClass().getSimpleName(),
                    String.format("Modification time of file %s could not be changed normally, file could not be modified!",
                    cacheFile.getAbsolutePath()));
            return false;
        }
        return true;
    }

    /**
     * Inspired from Guava com.google.common.io.ByteStreams
     */
    protected void readBytes(final InputStream in,
        final ProgressByteProcessor processor) throws IOException {
        final byte[] buf = new byte[BUF_SIZE];
        try {
            int amt;
            do {
                amt = in.read(buf);
                if (amt == -1) {
                    break;
                }
            } while (processor.processBytes(buf, 0, amt));
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
        int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }
}
