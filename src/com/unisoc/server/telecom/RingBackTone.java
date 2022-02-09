
package com.unisoc.server.telecom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.unisoc.server.telecom.TelecomUtils;
import com.android.server.telecom.CallsManagerListenerBase;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;
import com.android.server.telecom.R;
import android.text.TextUtils;
import android.telecom.Connection;
import com.android.internal.telephony.PhoneConstants;
import android.telecom.TelecomManager;


/**
 * Local RingBackTone Feature Plugin.
 */

public class RingBackTone extends CallsManagerListenerBase {

    private static final String TAG = "RingBackTone";

    private static Context mContext;
    private CallsManager mCallsManager;
    private SoundPool mRingBackSoundPool;
    private boolean mIsRingBackTonePlay = false;
    private int mRingBackToneId = -1;
    private TelephonyManager mTelephonyManager;
    private RadioInteractor mRadioInteractor;
    // UNISOC: add for bug1165098
    private RadioInteractorCallbackListener[] mCallbackListener;
    private CarrierConfigManager mConfigManager;

    static RingBackTone sInstance;

    private static final String INDIA_MCC = "404";
    private static final String INDIA_MCC2 = "405";

    public void RingbackTone() {
    }
    //add for bug1158485
    public static synchronized RingBackTone getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RingBackTone();
        }
        mContext = context;
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(Context context, CallsManager callsManager) {
        mCallsManager = callsManager;
        mCallsManager.addListener(this);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

    }
    /* UNISOC: add for bug1165098. @{ */
    private void registerOnEarlyMediaEvent() {
        Log.i(TAG, "registerOnEarlyMediaEvent");
        int phoneCount = mTelephonyManager.getPhoneCount();
        mCallbackListener = new RadioInteractorCallbackListener[phoneCount];
        mRadioInteractor = new RadioInteractor(mContext);
        for (int i = 0; i < phoneCount; i++) {
            mCallbackListener[i] = new RadioInteractorCallbackListener(i, Looper.getMainLooper()) {
                @Override
                public void onEarlyMediaEvent(Object data) {
                    Log.i(TAG, "RadioInteractorCallbackListener onActToneEvent. data : " + data);
                    if (data != null) {
                        AsyncResult ar;
                        ar = (AsyncResult) data;
                        int earlyMediaState = (int) ar.result;
                        if (1 == earlyMediaState) {
                            stopRingBackTone();
                        }
                    }

                }
            };
            if (mRadioInteractor != null) {
                mRadioInteractor.listen(mCallbackListener[i],
                        RadioInteractorCallbackListener.LISTEN_EARLY_MEDIA_EVENT,
                        false);
            }
        }
    }

    private void unRegisterEarlyMediaEvent() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            if (mRadioInteractor != null && mCallbackListener != null) {
                mRadioInteractor.listen(mCallbackListener[i],
                        RadioInteractorCallbackListener.LISTEN_NONE);
            }
        }
    }
    /*@}*/
    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        Log.d(TAG, "onCallStateChanged oldState : " + oldState + " newState : " + newState);
        onCallLeavingState(call, oldState);
        onCallEnteringState(call, newState);
    }

    @Override
    public void onCallAdded(Call call) {
        onCallEnteringState(call, call.getState());
    }

    private void onCallLeavingState(Call call, int state) {
        switch (state) {
            case CallState.DIALING:
            case CallState.RINGING:
                // stop ring back tone when leave dialing state.
                stopRingBackTone();
                break;
        }
    }

    private void onCallEnteringState(Call call, int state) {
        switch (state) {
            case CallState.DIALING:
                Log.i(TAG, "onCallEnteringState : isVolteEnable " + isVolteEnable()
                        +" isIndiaNetWork "+isIndiaNetWork());
                // set local tone before alerting.
                if (isVolteEnable() && (isIndiaNetWork() || isAiretelNetWork())) {
                    int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                            mContext, call.getTargetPhoneAccount());
                    if (mRadioInteractor == null) {
                        mRadioInteractor = new RadioInteractor(mContext);
                    }
                    Log.i(TAG, "setLocalTone 0. slotId : " + slotId);
                    mRadioInteractor.setLocalTone(0, slotId);
                    // UNISOC: add for bug1165098
                    registerOnEarlyMediaEvent();
                }
                break;
            case CallState.ACTIVE:
                stopRingBackTone();
                break;
            case CallState.DISCONNECTED:
                stopRingBackTone();
                // UNISOC: add for bug1165098
                unRegisterEarlyMediaEvent();
                break;
        }
    }

    /**
     * play ring back tone when we are dialing a volte call.
     */
    public synchronized void playRingBackTone() {
        Log.i(TAG, "playRingBackTone : isIndiaNetWork " + isIndiaNetWork());
        if (!mIsRingBackTonePlay && (isIndiaNetWork() || isAiretelNetWork())) {
            Log.i(TAG, "playRingBackTone.");
            if (mRadioInteractor == null) {
                mRadioInteractor = new RadioInteractor(mContext);
            }
            Call call = mCallsManager.getForegroundCall();
            if (call != null) {
                int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                        mContext, call.getTargetPhoneAccount());
                Log.i(TAG, "playRingBackTone --> setLocalTone 0. slotId : " + slotId);
                mRadioInteractor.setLocalTone(0, slotId);
            }
            int stream = AudioManager.STREAM_VOICE_CALL;
            mRingBackSoundPool = new SoundPool(5, stream, 0);
            // UNISOC: add for bug1165098
            mRingBackToneId = mRingBackSoundPool.load(mContext, R.raw.ring_back_tone_for_india, 1);
            mRingBackSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    Log.d(TAG, "playRingBackTone onLoadComplete sampleId:"
                            + sampleId + " status : " + status);
                    if (mRingBackSoundPool != null) {
                        mRingBackSoundPool.play(mRingBackToneId, 1, 1, 0, -1, 1);
                    }
                }
            });
            mIsRingBackTonePlay = true;
        }
    }

    public void stopRingBackTone() {
        if (mRingBackSoundPool != null) {
            Log.i(TAG, "stopRingBackTone.");
            mRingBackSoundPool.stop(mRingBackToneId);
            mRingBackSoundPool.unload(mRingBackToneId);
            mRingBackSoundPool.release();
            mRingBackSoundPool = null;
            synchronized (RingBackTone.this){//add for bug1158484
                mIsRingBackTonePlay = false;
            }
            if (mRadioInteractor == null) {
                mRadioInteractor = new RadioInteractor(mContext);
            }
            Call call = mCallsManager.getForegroundCall();
            if (call != null) {
                int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                        mContext, call.getTargetPhoneAccount());
                Log.i(TAG, "stopRingBackTone. setLocalTone 1. slotId : " + slotId);
                mRadioInteractor.setLocalTone(1, slotId);
            }
        }
    }

    public boolean isVolteEnable() {
        Call foregroundCall = mCallsManager.getForegroundCall();
        if (foregroundCall != null
            && foregroundCall.getExtras() != null
            && !foregroundCall.hasProperty(Connection.PROPERTY_WIFI)
            && foregroundCall.getExtras().getInt(TelecomManager.EXTRA_CALL_TECHNOLOGY_TYPE) == PhoneConstants.PHONE_TYPE_IMS) {
            return true;
        } else {
            return false;
        }
    }

    /*
    * We only support this feature for airtel.
    * */
    public boolean isAiretelNetWork() {
        Call call = mCallsManager.getForegroundCall();
        if (call != null) {
            int subId = TelecomUtils.getSubIdForPhoneAccountHandle(
                    mContext, call.getTargetPhoneAccount());
            mConfigManager = (CarrierConfigManager)
                                  mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PersistableBundle persistableBundle =  mConfigManager.getConfigForSubId(subId);
            if (persistableBundle != null) {
                return persistableBundle.getBoolean(
                        CarrierConfigManagerEx.KEY_FEATURE_AIRTEL_RINGBACKTONE_ENABLED_BOOL);
            }
        }

        return false;
    }

    /*
    *  support this feature for india.
    * */
    public boolean isIndiaNetWork() {
        Call call = mCallsManager.getForegroundCall();
        if (call != null) {
            int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                    mContext, call.getTargetPhoneAccount());
            String netWorkOperator = mTelephonyManager.getNetworkOperatorForPhone(slotId);
            Log.d(TAG, "isIndiaNetWork netWorkOperator: " + netWorkOperator+" slotId: "+slotId);
            if (!TextUtils.isEmpty(netWorkOperator) && (netWorkOperator.startsWith(INDIA_MCC)
                    || netWorkOperator.startsWith(INDIA_MCC2))) {
                return true;
            }
        }
        return false;
    }
}
