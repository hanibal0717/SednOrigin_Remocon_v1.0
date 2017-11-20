package com.inucreative.sednremocon;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * Created by Jskim on 2016-06-30.
 */
public class VolumeLayout extends LinearLayout {
    static final int MOVE_EVENT_HANDLING_INTERVAL = 100; // ignore ACTION_MOVE event within 200ms
    private long last_event_time;
    private OnVolumeLevelChangedListener mVolumeLevelChangedListener;

    public interface OnVolumeLevelChangedListener {
        void onVolumeLevelChanged(float positionRatio);
    }

    public VolumeLayout(Context context) {
        super(context);
        last_event_time = 0;
        mVolumeLevelChangedListener = null;
    }
    public VolumeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        last_event_time = 0;
        mVolumeLevelChangedListener = null;
    }

    public void setOnVolumeLevelChangedListener(OnVolumeLevelChangedListener listener) {
        mVolumeLevelChangedListener = listener;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_MOVE) {
            if(event.getEventTime() - last_event_time < MOVE_EVENT_HANDLING_INTERVAL)
                return true;
        }
        last_event_time = event.getEventTime();

        LogUtil.d("onTouchEvent " + event.toString());
        mVolumeLevelChangedListener.onVolumeLevelChanged(event.getX() / getWidth());
        return true;
    }
}
