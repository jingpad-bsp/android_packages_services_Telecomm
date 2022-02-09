package com.unisoc.server.plugins.LocalTone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.RemoteException;
import android.telecom.Connection;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.IImsRegisterListener;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.unisoc.server.plugins.LocalTone.LocalToneHelper;
import com.unisoc.server.telecom.TelecomUtils;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;
import android.telecom.TelecomManager;
import android.telecom.Connection;
import com.android.internal.telephony.PhoneConstants;

import com.android.server.telecom.R;

/**
 * Volte Local Tone Feature Plugin.
 */

public class LocalTonePlugin extends LocalToneHelper {

    private static final String TAG = "LocalTonePlugin";

    private static final String ACTION_SUPP_SERVICE_NOTIFICATION =
            "com.android.ACTION_SUPP_SERVICE_NOTIFICATION";
    private static final String ACTION_MT_CONFERENCE_NOTIFICATION =
            "com.android.ACTION_MT_CONFERENCE_NOTIFICATION";
    private static final String SUPP_SERV_CODE_EXTRA = "supp_serv_code";
    private static final String SUPP_SERV_NOTIFICATION_TYPE_EXTRA = "supp_serv_notification_type";

    private static Context mContext;
    private CallsManager mCallsManager;
    private SuppServiceNotificationReciver suppServiceNotificationReciver;

    private SoundPool mCallOnHoldSoundPool;
    private SoundPool mRingBackSoundPool;
    private SoundPool mConferenceWarningSoundPool;
    private boolean mIsCallHoldTonePlay = false;
    private boolean mIsRemoteHeld = false;   //add for bug987085
    private boolean mIsRingBackTonePlay = false;
    private boolean mIsConferenceWarningTonePlay = false;
    private int mCallHoldToneId = -1;
    private int mRingBackToneId = -1;
    private int mConferenceWarningToneId = -1;
    private TelephonyManager mTelephonyManager;
    private RadioInteractor mRadioInteractor;
    private RadioInteractorCallbackListener[] mCallbackListener;

    private Boolean mEarlyMediaState = false;

    // UNISOC: add for bug1194917 1285705
    private boolean mIsImsListenerRegistered;
    private IImsServiceEx mIImsServiceEx;
    private final IImsRegisterListener.Stub mImsUtListenerExBinder = new IImsRegisterListener.Stub(){
        @Override
        public void imsRegisterStateChange(boolean isRegistered){
            boolean isVolteEnable = isVolteEnable();
            Log.i(TAG, " isRegistered: " + isRegistered + ", isVolteEnable:" + isVolteEnable);
            if (!isVolteEnable && mIsCallHoldTonePlay) {
                mIsRemoteHeld = false;
                stopCallOnHoldTone();
            }
        }
    };

    public LocalTonePlugin(Context context) {
        mContext = context;
    }

    public void init(Context context, CallsManager callsManager) {
        mCallsManager = callsManager;
        mCallsManager.addListener(this);

        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_SUPP_SERVICE_NOTIFICATION);
        mIntentFilter.addAction(ACTION_MT_CONFERENCE_NOTIFICATION);
        suppServiceNotificationReciver = new SuppServiceNotificationReciver();
        mContext.registerReceiver(suppServiceNotificationReciver, mIntentFilter);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

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
                mEarlyMediaState = false;
                break;
        }
    }

    private void onCallEnteringState(Call call, int state) {
        switch (state) {
            case CallState.DIALING:
                // set local tone before alerting.
                if (isVolteCall()) {
                    int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                            mContext, call.getTargetPhoneAccount());
                    if (mRadioInteractor == null) {
                        mRadioInteractor = new RadioInteractor(mContext);
                    }
                    Log.i(TAG, "setLocalTone 0. slotId : " + slotId);
                    if (isValidSlotIndex(slotId)) {
                        mRadioInteractor.setLocalTone(0, slotId);
                    }
                    registerOnEarlyMediaEvent();
                }

                break;
            case CallState.ACTIVE:
                stopRingBackTone();
                // UNISOC: add for bug1285705
                mIsRemoteHeld = call.isRemotelyHeldForTone();
                if(mIsRemoteHeld == true && mIsCallHoldTonePlay == false){ //add for bug987085
                    Log.i(TAG, "CallState.ACTIVE. mIsRemoteHeld:true, mIsCallHoldTonePlay:false");
                    playCallOnHoldTone();
                }
                // play conference warning tone when is conference call and call is not manager.
                // 1. when remote part merge the call to conference, local call will disconnected
                // and callsmanager will create a new conference. so we only stop conference warning
                // tone when call is conference.
                // 2. call.isConference() is usable when remote part merge the call to conference
                // Connection.PROPERTY_IS_MT_CONFERENCE_CALL is usabled when remote part dial a
                // multipart call.
         /*       if ((call.isConference() || call.hasProperty(Connection.PROPERTY_IS_MT_CONFERENCE_CALL))
                        && !call.can(Connection.CAPABILITY_MANAGE_CONFERENCE)) {
                    playConferenceWarningTone();
                }*/   //modify for bug1007990,no need for new reqirenment
                break;
            case CallState.ON_HOLD:  //add for bug987085
                if(mIsRemoteHeld == true && mIsCallHoldTonePlay == true){
                    Log.i(TAG, "CallState.ON_HOLD. mIsRemoteHeld:true, mIsCallHoldTonePlay:true");
                    stopCallOnHoldTone();
                }
                break;
            case CallState.DISCONNECTED:
                stopRingBackTone();
                stopCallOnHoldTone();
                //        stopConferenceWarningTone();  //modify for bug1007990, no need for new reqirenment
                unRegisterEarlyMediaEvent();
                mIsRemoteHeld = false; // UNISOC: add for bug1194917
                break;
        }
    }

    private void registerOnEarlyMediaEvent() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        mCallbackListener = new RadioInteractorCallbackListener[phoneCount];
        mRadioInteractor = new RadioInteractor(mContext);
        for (int i = 0; i < phoneCount; i++) {
            mCallbackListener[i] = new RadioInteractorCallbackListener(i, Looper.getMainLooper()) {
                @Override
                public void onEarlyMediaEvent(Object data) {
                    Log.d(TAG, "RadioInteractorCallbackListener onActToneEvent. data : " + data);
                    if (data != null) {
                        AsyncResult ar;
                        ar = (AsyncResult) data;
                        int earlyMediaState = (int) ar.result;
                        if (1 == earlyMediaState) {
                            mEarlyMediaState = true;
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

    public synchronized void playCallOnHoldTone() {
        Log.i(TAG, "playCallOnHoldTone isVolteCall:"+isVolteCall()+", mIsCallHoldTonePlay:"+mIsCallHoldTonePlay);
        if (isVolteCall() && !mIsCallHoldTonePlay) {
            Log.i(TAG, "playCallOnHoldTone.");
            int stream = AudioManager.STREAM_VOICE_CALL;
            mCallOnHoldSoundPool = new SoundPool(5, stream, 0);
            mCallHoldToneId = mCallOnHoldSoundPool.load(mContext, R.raw.call_on_hold_tone, 1);
            mCallOnHoldSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    Log.d(TAG, "playCallOnHoldTone onLoadComplete sampleId:"
                            + sampleId + " status : " + status);
                    mCallOnHoldSoundPool.play(mCallHoldToneId, 1, 1, 0, -1, 1);
                }
            });
            mIsCallHoldTonePlay = true;
            tryRegisterImsListener(); // UNISOC: add for bug1194917
        }
    }

    public void stopCallOnHoldTone() {
        if (mCallOnHoldSoundPool != null) {
            Log.i(TAG, "stopCallOnHoldTone.");
            mCallOnHoldSoundPool.stop(mCallHoldToneId);
            mCallOnHoldSoundPool.unload(mCallHoldToneId);
            mCallOnHoldSoundPool.release();
            mCallOnHoldSoundPool = null;
            mIsCallHoldTonePlay = false;
            unTryRegisterImsListener(); // UNISOC: add for bug1194917
        }
    }

    /**
     * play ring back tone when we are dialing a volte call.
     */
    public synchronized void playRingBackTone() {
        if (isVolteCall() && !mIsRingBackTonePlay && !mEarlyMediaState) {
            Log.i(TAG, "playRingBackTone.");
            if (mRadioInteractor == null) {
                mRadioInteractor = new RadioInteractor(mContext);
            }
            Call call = mCallsManager.getForegroundCall();
            if (call != null) {
                int slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                        mContext, call.getTargetPhoneAccount());
                Log.i(TAG, "playRingBackTone --> setLocalTone 0. slotId : " + slotId);
                if (isValidSlotIndex(slotId)) {
                    mRadioInteractor.setLocalTone(0, slotId);
                }
            }

            int stream = AudioManager.STREAM_VOICE_CALL;
            mRingBackSoundPool = new SoundPool(5, stream, 0);
            mRingBackToneId = mRingBackSoundPool.load(mContext, R.raw.ring_back_tone, 1);
            mRingBackSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    Log.d(TAG, "playRingBackTone onLoadComplete sampleId:"
                            + sampleId + " status : " + status);
                    mRingBackSoundPool.play(mRingBackToneId, 1, 1, 0, -1, 1);
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
            synchronized (LocalTonePlugin.this){//add for bug1158482
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
                if (isValidSlotIndex(slotId)) {
                    mRadioInteractor.setLocalTone(1, slotId);
                }
            }
        }
    }

    public synchronized void playConferenceWarningTone() {
        if (isVolteCall() && !mIsConferenceWarningTonePlay && !mEarlyMediaState) {
            Log.i(TAG, "playConferenceWarningTone. ");
            int stream = AudioManager.STREAM_VOICE_CALL;
            mConferenceWarningSoundPool = new SoundPool(5, stream, 0);
            mConferenceWarningToneId = mConferenceWarningSoundPool.load(mContext, R.raw.conference_warning_tone, 1);
            mConferenceWarningSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    Log.d(TAG, "playConferenceWarningTone onLoadComplete sampleId:"
                            + sampleId + " status : " + status);
                    mConferenceWarningSoundPool.play(mConferenceWarningToneId, 1, 1, 0, -1, 1);
                }
            });
            mIsConferenceWarningTonePlay = true;
        }
    }

    public void stopConferenceWarningTone() {
        if (mConferenceWarningSoundPool != null) {
            Log.d(TAG, "stopConferenceWarningTone.");
            mConferenceWarningSoundPool.stop(mConferenceWarningToneId);
            mConferenceWarningSoundPool.unload(mConferenceWarningToneId);
            mConferenceWarningSoundPool.release();
            mConferenceWarningSoundPool = null;
            synchronized (LocalTonePlugin.this){//add for bug1158481
                mIsConferenceWarningTonePlay = false;
            }
        }
    }

    public boolean isVolteCall() {
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


    public boolean isValidSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < mTelephonyManager.getDefault().getSimCount();
    }

    private class SuppServiceNotificationReciver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "SuppServiceNotificationReciver onReceive " + intent);
            if (ACTION_SUPP_SERVICE_NOTIFICATION.equals(intent.getAction())) {
                // type of notification: 0 = MO; 1 = MT, MO -> CSSI, MT -> CSSU.
                int notificationType = intent.getIntExtra(SUPP_SERV_NOTIFICATION_TYPE_EXTRA, -1);
                if (notificationType == 1) {
                    int code = intent.getIntExtra(SUPP_SERV_CODE_EXTRA, -1);
                    Log.d(TAG, "SuppServiceNotificationReciver code: " + code);
                    if (code == SuppServiceNotification.CODE_2_CALL_ON_HOLD ) {
                        // play call on hold tone when remote part put call to hold.
                        mIsRemoteHeld = true;  //add for bug987085
                        playCallOnHoldTone();
                    } else if (code == SuppServiceNotification.CODE_2_CALL_RETRIEVED
                            || code == SuppServiceNotification.CODE_2_ON_HOLD_CALL_RELEASED ) {
                        // stop call on hold tone when remote part unhold the call or hangup the call.
                        mIsRemoteHeld = false;  //add for bug987085
                        stopCallOnHoldTone();
                    }
                }
            }
        }
    }
    // UNISOC: add for bug1194917 1285705
    private synchronized void tryRegisterImsListener() {
        if(mContext != null
                && (ImsManager.isVolteEnabledByPlatform(mContext) || ImsManager.isWfcEnabledByPlatform(mContext))) {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (mIImsServiceEx != null) {
                try {
                    if (!mIsImsListenerRegistered) {
                        mIsImsListenerRegistered = true;
                        mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                        Log.i(TAG, "tryRegisterImsListener");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, " e = " + e);
                }
            }
        }
    }

    private synchronized void unTryRegisterImsListener() {
        if (mContext != null
                && (ImsManager.isVolteEnabledByPlatform(mContext) || ImsManager.isWfcEnabledByPlatform(mContext))) {
            try {
                if (mIsImsListenerRegistered) {
                    mIsImsListenerRegistered = false;
                    mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                    Log.i(TAG, "unTryRegisterImsListener");
                }
            } catch(RemoteException e) {
                Log.e(TAG, " e = " + e);
            }
        }
    }
    public boolean isVolteEnable() {
        Call call = mCallsManager.getForegroundCall();
        int slotId = -1;
        if (call != null) {
            slotId = TelecomUtils.getSlotIdForPhoneAccountHandle(
                    mContext, call.getTargetPhoneAccount());

            Log.i(TAG, "isVolteEnable. slotId: " + slotId);
        }
        return ImsManager.isVolteEnabledByPlatform(mContext)
                && ImsManagerEx.isVoLTERegisteredForPhone(slotId);
    }
}
