package com.android.server.telecom;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.android.server.telecom.CallState;
import com.unisoc.server.settings.TelecommCallSettings;

import android.provider.Settings;
import android.telephony.TelephonyManager;

/**
 * UNISOC Feature Porting: Flip to silence from incoming calls. See bug#693159
 * Handle registering and unregistering accelerometer sensor relating to call state.
 */
public class InCallAccelerometerSensorControllerEx extends CallsManagerListenerBase
        implements SensorEventListener {

    private Context mContext;
    private CallsManager mCallsManager;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Ringer mRinger;
    static InCallAccelerometerSensorControllerEx sInstance;

    private static final float DOWN_ANGLE =  -5.0f;
    private static final float UP_ANGLE =  5.0f;
    private static final int FLAG_UP = 0;
    private static final int FLAG_DOWN = 1;
    private static final int FLAG_UNKNOW = -1;
    private int mFlagOfZAxis = FLAG_UNKNOW;

    public InCallAccelerometerSensorControllerEx() {
    }

    public void init(Context context, CallsManager callsManager, Ringer ringer) {
        mContext = context;
        mCallsManager = callsManager;
        mRinger = ringer;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        callsManager.addListener(this);
    }

    public static InCallAccelerometerSensorControllerEx getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new InCallAccelerometerSensorControllerEx();
        }
        return sInstance;
    }

    @Override
    public void onCallAdded(Call call) {
        if (isFeatrueFlipToSilenceEnabled()) {
            Call ringingCall = mCallsManager.getRingingCall();

            if (mSensor != null && ringingCall != null) {
                mSensorManager.registerListener(this, mSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (mSensor != null && oldState == CallState.RINGING && newState != CallState.RINGING) {
            mSensorManager.unregisterListener(this, mSensor);
            mFlagOfZAxis = FLAG_UNKNOW;
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (mSensor != null) {
            mSensorManager.unregisterListener(this, mSensor);
            mFlagOfZAxis = FLAG_UNKNOW;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[2] >= UP_ANGLE) {
            mFlagOfZAxis = FLAG_UP;
        } else if (event.values[2] <= DOWN_ANGLE && mFlagOfZAxis == FLAG_UP) {
            mFlagOfZAxis = FLAG_DOWN;
            if (mCallsManager.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
                mRinger.stopRinging();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Check if configured flip to silent incoming call alerting feature.
     */
    private boolean isFeatrueFlipToSilenceEnabled() {
        boolean isFlipToSilenceEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                TelecommCallSettings.FLIPPING_SILENCE_DATA, 0) != 0;
        return isFlipToSilenceEnabled;
    }
}
