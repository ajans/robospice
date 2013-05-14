package com.octo.android.robospice.spicelist;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.AbsListView;
import android.widget.GridView;

/**
 * This {@link GridView} is optimized to display some content that contains
 * image loaded from the network via RoboSpice. It uses a
 * {@link SpiceArrayAdapter} to hold data and create/update views. It can be
 * instanciated programmatically or via XML. Basically, it will load images only
 * when scrolling is stopped.
 * @author sni
 */
public class SpiceGridView extends GridView {

    // ----------------------------
    // --- CONSTRUCTORS
    // ----------------------------

    private SpiceListScrollListener spiceScrollListener;

    public SpiceGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    public SpiceGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public SpiceGridView(Context context) {
        super(context);
        initialize();
    }

    // ----------------------------
    // --- PUBLIC API
    // ----------------------------

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        if (spiceScrollListener != null) {
            spiceScrollListener.setNextOnScrollListener(l);
        }
    }

//    @Override
//    public void setAdapter(ListAdapter adapter) {
//        if (!(adapter instanceof SpiceArrayAdapter)) {
//            throw new IllegalArgumentException(
//                "SpiceLists only support SpiceArrayAdapters.");
//        }
//        super.setAdapter(adapter);
//
//    }
//
//    @Override
//    public SpiceArrayAdapter<?> getAdapter() {
//        return (SpiceArrayAdapter<?>) super.getAdapter();
//    }

    // ----------------------------
    // --- PRIVATE API
    // ----------------------------
    private void initialize() {
        spiceScrollListener = new SpiceListScrollListener();
        super.setOnScrollListener(spiceScrollListener);
    }

    // ----------------------------
    // --- INNER CLASS API
    // ----------------------------
    private final class SpiceListScrollListener implements OnScrollListener {
        private OnScrollListener nextListener;
        
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (getAdapter() != null && getAdapter() instanceof SpiceArrayAdapter<?>) {
                ((SpiceArrayAdapter<?>) getAdapter()).setNetworkFetchingAllowed(
                    scrollState == SCROLL_STATE_IDLE);
            }
            
            if (nextListener != null) {
                nextListener.onScrollStateChanged(view, scrollState);
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
            if (nextListener != null) {
                nextListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
            }
        }

        public void setNextOnScrollListener(OnScrollListener nextListener) {
            this.nextListener = nextListener;
        }
    }

}
