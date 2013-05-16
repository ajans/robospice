package com.octo.android.robospice.spicelist;

import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;
import com.octo.android.robospice.request.simple.BitmapRequest;

import roboguice.util.temp.Ln;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.widget.CursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An adapter that is optimized for {@link SpiceListView} instances. It offers
 * to update ImageViews contained in {@link SpiceListItemView} instances with
 * images loaded from the network. All you have to do is to Override
 * {@link #createRequest(Object)} to define a bitmapRequest for each object in
 * the list that is associated an image to display. Also please note that in
 * your {@link #getView(int, android.view.View, android.view.ViewGroup)} method,
 * you must call
 * {@link #updateListItemViewAsynchronously(Object, SpiceListItemView)} in order
 * for your {@link SpiceListItemView} to be updated automagically.
 * @author sni
 * @param <T>
 *            the type of data displayed by the list.
 */
public abstract class SpiceSuggestionsAdapter extends CursorAdapter {

    private int imageWidth = 0;
    private int imageHeight = 0;

    /**
     * Indicates wether to use the network to update data. This is set by the
     * {@link SpiceListView}.
     */
    private boolean isNetworkFetchingAllowed = true;
    /**
     * A {@link SpiceManager} that will be used to fetch binaries. It's
     * lifecycle has to be managed at the context level (usually fragment or
     * activity).
     */
    private SpiceManager spiceManagerBinary;
    /**
     * List of event listeners to get notified of network fetching allowed
     * changes.
     */
    private List<NetworkFetchingAuthorizationStateChangeAdapter> networkFetchingAuthorizationStateChangeListenerList = Collections
        .synchronizedList(new ArrayList<NetworkFetchingAuthorizationStateChangeAdapter>());
    /**
     * Contains all images that have been added recently to the list. They will
     * be animated when first displayed.
     */
    private Set<Object> freshDrawableSet = new HashSet<Object>();
    /** The default drawable to display during image loading from the network. */
    private Drawable defaultDrawable;
    private Animation animation;
    private long imageCacheExpiryDuration = DurationInMillis.ALWAYS_EXPIRED;

    // ----------------------------
    // --- CONSTRUCTOR
    // ----------------------------
    public SpiceSuggestionsAdapter(Context context, BitmapSpiceManager spiceManagerBinary, Cursor cursor) {
        super(context, cursor, 0);
        initialize(context, spiceManagerBinary);
    }

    // ----------------------------
    // --- PUBLIC API
    // ----------------------------

    public void setDefaultUserDrawable(Drawable defaultUserDrawable) {
        this.defaultDrawable = defaultUserDrawable;
    }

    /* package-private */void setNetworkFetchingAllowed(boolean isNetworkFetchingAllowed) {
        boolean changed = isNetworkFetchingAllowed != this.isNetworkFetchingAllowed;
        this.isNetworkFetchingAllowed = isNetworkFetchingAllowed;
        if (isNetworkFetchingAllowed && changed) {
            fireOnNetworkFetchingAllowedChange();
            Ln.d("calling state change listeners");
        }
    }

    /**
     * Updates a {@link SpiceListItemView} containing some data. The method
     * {@link #createRequest(Object)} will be applied to data to know which
     * bitmapRequest to execute to get data from network if needed. This method
     * must be called during
     * {@link #getView(int, android.view.View, android.view.ViewGroup)}.
     * @param data
     *            the data to update the {@link SpiceListItemView} with.
     * @param spiceListItemView
     *            the {@link SpiceListItemView} that displays an image to
     *            represent data.
     */
    protected void updateListItemViewAsynchronously(Cursor data, SpiceListItemView<Cursor> spiceListItemView) {
        if (!registered(spiceListItemView)) {
            addSpiceListItemView(spiceListItemView);
        }
        imageWidth = Math.max(imageWidth, spiceListItemView.getImageView().getWidth());
        imageHeight = Math.max(imageHeight, spiceListItemView.getImageView().getHeight());
        new ThumbnailAsynTask(createRequest(data, imageWidth, imageHeight)).execute(data, spiceListItemView);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        SpiceListItemView<Cursor> spiceListItemView = (SpiceListItemView<Cursor>) view;
        spiceListItemView.update(cursor);
        // this is the most important line. It will update views automatically
        // ----------------------------------------
        updateListItemViewAsynchronously(cursor, spiceListItemView);
        // ----------------------------------------
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        SpiceListItemView<Cursor> spiceListItemView = createView(mContext, parent);
        return (View) spiceListItemView;
    }

    public abstract SpiceListItemView<Cursor> createView(Context context, ViewGroup parent);

    public abstract BitmapRequest createRequest(Cursor data, int requestImageWidth, int requestImageHeight);

    // ----------------------------
    // --- PRIVATE API
    // ----------------------------

    private void addSpiceListItemView(SpiceListItemView<Cursor> spiceListItemView) {
        this.networkFetchingAuthorizationStateChangeListenerList.add(new NetworkFetchingAuthorizationStateChangeAdapter(spiceListItemView));
    }

    private boolean registered(SpiceListItemView<Cursor> view) {
        for (NetworkFetchingAuthorizationStateChangeAdapter listener : networkFetchingAuthorizationStateChangeListenerList) {
            if (listener.getView() == view) {
                return true;
            }
        }
        return false;
    }

    private void fireOnNetworkFetchingAllowedChange() {
        synchronized (networkFetchingAuthorizationStateChangeListenerList) {
            for (NetworkFetchingAuthorizationStateChangeAdapter networkFetchingAuthorizationStateChangeListener : networkFetchingAuthorizationStateChangeListenerList) {
                Ln.d("calling state change listener");
                networkFetchingAuthorizationStateChangeListener.onNetworkFetchingAllowedChange(isNetworkFetchingAllowed);
            }
        }
    }

    private void initialize(Context context, BitmapSpiceManager spiceManagerBinary) {
        this.spiceManagerBinary = spiceManagerBinary;
        defaultDrawable = context.getResources().getDrawable(android.R.drawable.picture_frame);
        animation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
        animation.setDuration(context.getResources().getInteger(android.R.integer.config_mediumAnimTime));
    }

    // ----------------------------
    // --- INNER CLASSES
    // ----------------------------

    private class OctoImageRequestListener implements RequestListener<Bitmap> {

        private SpiceListItemView<Cursor> spiceListItemView;
        private Cursor data;
        private ImageView thumbImageView;
        private String imageFileName;

        public OctoImageRequestListener(Cursor data, SpiceListItemView<Cursor> spiceListItemView, String imageFileName) {
            this.data = data;
            this.spiceListItemView = spiceListItemView;
            this.thumbImageView = spiceListItemView.getImageView();
            this.imageFileName = imageFileName;

        }

        @Override
        public void onRequestFailure(SpiceException spiceException) {
            Ln.e(SpiceListItemView.class.getName(), "Unable to retrive image", spiceException);
            thumbImageView.setImageDrawable(defaultDrawable);
        }

        @Override
        public void onRequestSuccess(Bitmap bitmap) {
            freshDrawableSet.add(data);
            if (this.data.equals(spiceListItemView.getData())) {
                loadBitmapAsynchronously(data, thumbImageView, imageFileName);
            }
        }
    }

    protected void loadBitmapAsynchronously(Cursor octo, ImageView thumbImageView, String tempThumbnailImageFileName) {
        if (thumbImageView.getTag() != null && thumbImageView.getTag().equals(tempThumbnailImageFileName)) {
            return;
        }

        if (cancelPotentialWork(tempThumbnailImageFileName, thumbImageView)) {
            final BitmapWorkerTask task = new BitmapWorkerTask(thumbImageView, octo);
            task.fileName = tempThumbnailImageFileName;
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mContext.getResources(), task);
            thumbImageView.setImageDrawable(asyncDrawable);
            thumbImageView.setTag(tempThumbnailImageFileName);
            task.execute(tempThumbnailImageFileName);
        }
    }

    private boolean cancelPotentialWork(String fileName, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final String bitmapFileName = bitmapWorkerTask.fileName;
            if (bitmapFileName == null || !bitmapFileName.equals(fileName)) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was
        // cancelled
        return true;
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof SpiceSuggestionsAdapter.AsyncDrawable) {
                @SuppressWarnings("unchecked")
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public void setImageCacheExpiryDuration(long imageCacheExpiryDuration) {
        this.imageCacheExpiryDuration = imageCacheExpiryDuration;
    }
    
    // ----------------------------------
    // INNER CLASSES
    // ----------------------------------
    protected class ThumbnailAsynTask extends AsyncTask<Object, Void, Boolean> {

        private Cursor data;
        private SpiceListItemView<Cursor> spiceListItemView;
        private String tempThumbnailImageFileName = "";
        private BitmapRequest bitmapRequest;

        public ThumbnailAsynTask(BitmapRequest request) {
            this.bitmapRequest = request;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Boolean doInBackground(Object... params) {
            data = (Cursor) params[0];
            spiceListItemView = (SpiceListItemView<Cursor>) params[1];

            File tempThumbnailImageFile = bitmapRequest.getCacheFile();
            tempThumbnailImageFileName = tempThumbnailImageFile.getAbsolutePath();
            String requestKey = tempThumbnailImageFile.getName();
            Ln.d("Filename : " + tempThumbnailImageFileName);

            if (!tempThumbnailImageFile.exists()) {
                if (isNetworkFetchingAllowed) {
                    OctoImageRequestListener octoImageRequestListener = new OctoImageRequestListener(data, spiceListItemView,
                            tempThumbnailImageFileName);
                    spiceManagerBinary.execute(bitmapRequest, requestKey, SpiceSuggestionsAdapter.this.imageCacheExpiryDuration,
                        octoImageRequestListener);
                }
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean isImageAvailableInCache) {
            if (isImageAvailableInCache) {
                loadBitmapAsynchronously(data, spiceListItemView.getImageView(), tempThumbnailImageFileName);
            } else {
                spiceListItemView.getImageView().setImageDrawable(defaultDrawable);
            }
        }
    }

    private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        String fileName = "";
        private Cursor data;
//        private Animation animation;

        public BitmapWorkerTask(ImageView imageView, Cursor data) {
            // Use a WeakReference to ensure the ImageView can be garbage
            // collected
            imageViewReference = new WeakReference<ImageView>(imageView);
            this.data = data;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
//            animation = AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in);
//            animation.setDuration(mContext.getResources().getInteger(android.R.integer.config_mediumAnimTime));
            fileName = params[0];
            return BitmapFactory.decodeFile(fileName, null);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                if (data.toString().equals("JFA")) {
                    Ln.d(data.toString() + " : cancel decoding bitmap");
                }
                bitmap = null;
            }

            if (imageViewReference != null && bitmap != null) {
                if (data.toString().equals("JFA")) {
                    Ln.d(data.toString() + " : bitmapworkertask");
                }
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask && imageView != null) {
                    if (freshDrawableSet.contains(data)) {
                        freshDrawableSet.remove(data);
//                        imageView.startAnimation(animation);
                    }
                    imageView.setImageBitmap(bitmap);
                    // no used anymore.
                    // imageView.setTag( null );
                }
            }
        }
    }

    private class NetworkFetchingAuthorizationStateChangeAdapter {

        private WeakReference<SpiceListItemView<Cursor>> weakReferenceSpiceListItemView;

        public NetworkFetchingAuthorizationStateChangeAdapter(SpiceListItemView<Cursor> spiceListItemView) {
            this.weakReferenceSpiceListItemView = new WeakReference<SpiceListItemView<Cursor>>(spiceListItemView);
        }

        public void onNetworkFetchingAllowedChange(boolean networkFetchingAllowed) {
            SpiceListItemView<Cursor> spiceListItemView = weakReferenceSpiceListItemView.get();
            if (spiceListItemView != null) {
                Cursor data = spiceListItemView.getData();
                new ThumbnailAsynTask(createRequest(data, imageWidth, imageHeight)).execute(data, spiceListItemView);
            }
        }

        public SpiceListItemView<Cursor> getView() {
            return weakReferenceSpiceListItemView.get();
        }
    }

    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, BitmapWorkerTask bitmapWorkerTask) {
            super(res);
            bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

}
