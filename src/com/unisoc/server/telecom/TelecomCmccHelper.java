package com.unisoc.server.telecom;

import android.content.Context;
import android.telecom.VideoProfile;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.AsyncResult;
import android.os.Message;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Call;
import android.widget.Toast;
import com.android.server.telecom.R;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.SubscriptionManager;

import java.util.Collection;

/**
 * This class is used to manager Telecom CMCC Plugin Helper.
 * bug 693518
 */

public class TelecomCmccHelper {

    private static final String TAG = "TelecomCmccHelper";

    static TelecomCmccHelper sInstance;
    private static Context mContext;
    private TelecomHandler mHandler;

    private static final int EVENT_ADD_CALL_NOT_ALLOWED             = 1;

    public static TelecomCmccHelper getInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        }
        mContext = context;
        sInstance = new TelecomCmccHelper();
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public TelecomCmccHelper() {
        mHandler = new TelecomHandler(mContext.getMainLooper());
    }

    /* Mark reject call as missed type feature for cmcc case */
    public boolean isSupportMarkRejectCallAsMissedType() {
        return false;
    }

    private class TelecomHandler extends Handler {

        //private Context mContext;

        public TelecomHandler(Looper looper) {
            super(looper);
            //mContext = context;
        }
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            Log.i(TAG, "handleMessage: "+msg.what);
            switch (msg.what) {
                case EVENT_ADD_CALL_NOT_ALLOWED:
                    Toast.makeText(mContext, mContext.getString(R.string.duplicate_video_call_not_allowed),
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        }
    }

    //add for hangup  incoming call when in vt call
    public boolean hangUpIncomingCall(Call incomingCall, Call activeCall, Call dialingCall) {
        Log.d(TAG, "hangUpIncomingCall...... ");
        /* @} */
        //Unsioc:add for bug746517
        if(incomingCall != null && incomingCall.hasProperty(android.telecom.Call.Details.PROPERTY_WIFI)){
            Log.d(TAG, "hangUpIncomingCall is not support,is wifi call");
            return false;
        }

        if(isSupportVideoCallOnly(incomingCall)) {
            Log.d(TAG, "hangUpIncomingCall is support video call only");
            if ((incomingCall != null
                    && activeCall != null
                    && ((VideoProfile.isVideo(activeCall.getVideoState()) || (!VideoProfile
                    .isVideo(activeCall.getVideoState()) && VideoProfile.isVideo(incomingCall
                    .getVideoState()))))) ||
                    (incomingCall != null
                            && dialingCall != null
                            && ((VideoProfile.isVideo(dialingCall.getVideoState()) || (!VideoProfile
                            .isVideo(dialingCall.getVideoState()) && VideoProfile.isVideo(incomingCall
                            .getVideoState())))))) {
                return true;
            }
        }
        return false;
    }

    /* SPRD: Add for CMCC requirement bug574817 &662669 */
    public boolean shouldPreventAddVideoCall(CallsManager callsManager, int intentVideoState, boolean hasVideocall) {
        Log.d(TAG, "shouldPreventAddVideoCall false");

        //SPRD:add for bug746517
        if (callsManager != null) {
            for (Call call : callsManager.getCalls()) {
                if (call.hasProperty(android.telecom.Call.Details.PROPERTY_WIFI)) {
                    return false;
                }
            }
        }

        if (callsManager != null) {
            for (Call call : callsManager.getCalls()) {
                if(isSupportVideoCallOnly(call)) {
                    Log.d(TAG, "shouldPreventAddVideoCall is support video call only");
                    if ((callsManager != null && hasVideocall)
                            || (VideoProfile.isBidirectional(intentVideoState) && callsManager.getActiveCall() != null)) {
                        Message msg = mHandler.obtainMessage(EVENT_ADD_CALL_NOT_ALLOWED);
                        mHandler.sendMessage(msg);
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }
    /* @} */

    /* Unisoc: Add for CMCC requirement bug662669 and bug710992 */
    public boolean isSupportVideoCallOnly(Call call){

        int activeSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if(call != null) {
            activeSubId = TelecomUtils.getSubIdForPhoneAccountHandle(mContext, call.getTargetPhoneAccount());
            if (configManager != null && configManager.getConfigForSubId(activeSubId) != null) {
                return !configManager.getConfigForSubId(activeSubId).getBoolean(
                        CarrierConfigManagerEx.KEY_CARRIER_SUPPORT_MULTI_VIDEO_CALL);
            }
        }
        return false;
    }
    /* @} */

}
