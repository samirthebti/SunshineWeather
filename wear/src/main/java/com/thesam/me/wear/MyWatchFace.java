package com.thesam.me.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    public static final String LOG_TAG = MyWatchFace.class.getSimpleName();
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    public static final String KEY_WEATHER_ID = "_ID";
    public static final String KEY_WEATHER_LOW = "LOW";
    public static final String KEY_WEATHER_HEIGHT = "HEIGHT";
    public static final String KEY_WEATHER_ICON = "ICON";
    public static final String KEY_WEATHER_DESCRI = "DESCRI";
    private float aFloat;
    private Bitmap weatherIcon;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mLowTextPaint;
        Paint mHeightTextPaint;
        Paint mDescTextPaint;
        Paint mDay;
        Paint mDayName;
        Paint mMonth;
        Paint mYear;


        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        private GoogleApiClient mGoogleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private String low = "15";
        private String height = "25";
        private String desc = "Sunny";
        private String id = "1";


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);


            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mCalendar = Calendar.getInstance();
            mLowTextPaint = new Paint();
            mLowTextPaint = createTextPaint(resources.getColor(R.color.secondary_text));

            mHeightTextPaint = new Paint();
            mHeightTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDayName = new Paint();
            mDayName = createTextPaint(resources.getColor(R.color.secondary_text));

            mDay = new Paint();
            mDay = createTextPaint(resources.getColor(R.color.secondary_text));

            mYear = new Paint();
            mYear = createTextPaint(resources.getColor(R.color.secondary_text));

            mMonth = new Paint();
            mMonth = createTextPaint(resources.getColor(R.color.secondary_text));


            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                mGoogleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();

            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
//            Config the text size
            mTextPaint.setTextSize(textSize);
            mMonth.setTextSize(textSize / 3f);
            mDay.setTextSize(textSize / 3f);
            mDayName.setTextSize(textSize / 3f);
            mYear.setTextSize(textSize / 3f);
            mHeightTextPaint.setTextSize(textSize / 2);
            mLowTextPaint.setTextSize(textSize / 2);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));

            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            float widthOfTime = mTextPaint.measureText(text);
            float afterTimeXOffset = mXOffset + widthOfTime;
            float upperYOffset = mYOffset - mTextPaint.getTextSize() + 10;

            float tempYOffset = mYOffset - mHeightTextPaint.getTextSize() + 10;
            try {
                canvas.drawText(Utils.formatTempr(height) + "\u00b0" + "c", afterTimeXOffset + 25, tempYOffset, mHeightTextPaint);
                canvas.drawText(Utils.formatTempr(low) + "\u00b0" + "c", afterTimeXOffset + 25, mYOffset + 20, mLowTextPaint);
            } catch (Exception e) {
                e.printStackTrace();
            }
            aFloat = mYOffset + 30;

            float baseTextYOffset = mYOffset + 30;
            Date date = mCalendar.getTime();

            canvas.drawText(mCalendar.get(Calendar.DAY_OF_MONTH) + " " +
                            Utils.formatMonth(mCalendar.get(Calendar.MONTH)) + " " +
                            new String(mCalendar.get(Calendar.YEAR) + "").substring(2, 4) + ", " +
                            Utils.formatDaay(mCalendar.get(Calendar.DAY_OF_WEEK))
                    , mXOffset, baseTextYOffset, mDayName);

            canvas.drawLine(afterTimeXOffset + 15, upperYOffset, afterTimeXOffset + 15, baseTextYOffset + 15, mTextPaint);
            float iconXOffset = mXOffset + widthOfTime / 2;
            float iconYOffset = baseTextYOffset + 20;
            if (!isInAmbientMode()) {


                int icon = Utils.getImagesWithWeatherId(id);

                if (icon != -1) {
                    weatherIcon = BitmapFactory.decodeResource(getResources(), icon);
                } else {
                    weatherIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_full_sad);
                }
                canvas.drawBitmap(weatherIcon, iconXOffset, iconYOffset, mTextPaint);
            } else {
                canvas.drawText(desc, iconXOffset, iconYOffset, mTextPaint);
            }


        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);


        }

        @Override
        public void onConnectionSuspended(int i) {
//            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Log.d(LOG_TAG, "onConnectionSuspended: ");

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "onConnectionFailed: ");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "onDataChanged: ");
            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (("/wearable").equals(item.getUri().getPath())) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        if (dataMap.containsKey(KEY_WEATHER_HEIGHT)) {
                            height = dataMap.getString(KEY_WEATHER_HEIGHT);
                        }
                        if (dataMap.containsKey(KEY_WEATHER_LOW)) {
                            low = dataMap.getString(KEY_WEATHER_LOW);
                        }
                        if (dataMap.containsKey(KEY_WEATHER_ID)) {
                            id = dataMap.getString(KEY_WEATHER_ID);
                        }
                        if (dataMap.containsKey(KEY_WEATHER_DESCRI)) {
                            desc = dataMap.getString(KEY_WEATHER_DESCRI);
                        }
                        Log.d("Height", height + "Low :  " + low);
                    }
                }

            }
            invalidate();
        }


    }
}
