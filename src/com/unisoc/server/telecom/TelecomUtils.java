package com.unisoc.server.telecom;

import android.content.Context;
import android.telecom.Log;

import com.android.server.telecom.R;
import android.os.Bundle;
import android.provider.Settings;
import java.util.List;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.Log;
import com.android.server.telecom.Call;
import android.text.TextUtils;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.PhoneConstants;

public class TelecomUtils {

    private static final String TAG = "TelecomUtils";

    /* Unisoc FL0108020025: Show Rejected calls notifier feature. {@ */
    public static boolean isSupportShowRejectCallNotifier(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_show_reject_call_notifier);
    }
    /* @} */

    /**
     * Add method to get SlotId from PhoneAccountHandle.
     * @param handle
     * @return
     */
    public static int getSlotIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        //modify for bug1068713
        if(handle!=null){
            String iccId = handle.getId();

            List<SubscriptionInfo> result = SubscriptionManager.from(context).getActiveSubscriptionInfoList();

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            }
        }
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /**
     * Add method to get SubId from PhoneAccountHandle.
     */
    public static int getSubIdForPhoneAccountHandle(Context context,
                                                    PhoneAccountHandle phoneAccountHandle) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        try {
            PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            subId = telephonyManager.getSubIdForPhoneAccount(phoneAccount);
        } catch (NullPointerException e) {
            Log.e(TAG, e, "Exception raised during parsing int.");
        }
        return subId;
    }

    /**
     * Add method to get slot info by PhoneAccountHandle.
     */
    public static String getSlotInfoByPhoneAccountHandle(
            Context context, PhoneAccountHandle accountHandle) {
        if (accountHandle == null) {
            return "";
        }

        return getSlotInfoBySubId(context,
                getSubIdForPhoneAccountHandle(context, accountHandle));
    }

    /**
     * Add method to get slot info by subId.
     */
    public static String getSlotInfoBySubId(Context context, int subId) {
        //UNISOC:modify for bug1147838
        String card_string = "";
        if(context.getResources().getBoolean(R.bool.config_is_show_main_vice_card_feature)){
            Boolean isPrimaryCard = (SubscriptionManager.getDefaultDataSubscriptionId() == subId) ? true : false;
            if (isPrimaryCard) {
                card_string = context.getResources().getString(R.string.main_card_slot);
            } else {
                card_string = context.getResources().getString(R.string.vice_card_slot);
            }
        } else {
            int phoneId = SubscriptionManager.from(context).getPhoneId(subId);
            if (phoneId == 0) {
                card_string = context.getResources().getString(R.string.xliff_string1) + " ";
            } else {
                card_string = context.getResources().getString(R.string.xliff_string2) + " ";
            }
	    	card_string = "";
        }
        return card_string;
    }

    /**
     * Add method to get phone account label by PhoneAccountHandle.
     */
    public static String getPhoneAccountLabel(PhoneAccountHandle accountHandle, Context context) {
        String label = "";
        if (accountHandle == null) {
            return label;
        }
        PhoneAccount account = context.getSystemService(TelecomManager.class)
                .getPhoneAccount(accountHandle);
        if (account != null && !TextUtils.isEmpty(account.getLabel())) {
            label = account.getLabel().toString();
        }
        return label;
    }

    /**
     * Unisoc: Select PhoneAccountHandle by subId for bug597260  bug599640@{
     use dataCard to dial video and Conference
     */
    public static PhoneAccountHandle getSupportVideoPhoneAccout(List<PhoneAccountHandle> accounts,
                       boolean isEmergencyCall,Bundle extras, Context context) {
        PhoneAccountHandle videoPhoneAccountHandle = null;
        PhoneAccountHandle imsPhoneAccountHandle = null;

        if (extras == null || context == null) {
            Log.i("TelecomUtils","getSupportVideoPhoneAccout extras is null");
            return null;
        }

        boolean isConferenceDial = extras.getBoolean("android.intent.extra.IMS_CONFERENCE_REQUEST", false);
        int videoState = extras.getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_AUDIO_ONLY);
        boolean isVideoCall = VideoProfile.isVideo(videoState);//SPRD:modify bybug599640
        int subId = extras.getInt(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        // UNISOC: add for bug1197534
        final boolean isAirplaneModeOn =
                Settings.System.getInt(context.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                // UNISOC: add for bug1197534
                && (isConferenceDial || isVideoCall || (isAirplaneModeOn && !isEmergencyCall)) && accounts.size() > 1) {

            TelecomManager telecomManager = (TelecomManager) context
                    .getSystemService(Context.TELECOM_SERVICE);

            int supportVideoAccout = 0;
            int supportImsAccout = 0;
            for (PhoneAccountHandle accountHandle : accounts) {
                PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
                Log.d("TelecomUtils","getSupportVideoPhoneAccout accountHandle:"+accountHandle + " account:"+account);

                TelephonyManager telephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                boolean isImsRegistered = telephonyManager.isImsRegistered(getSubIdForPhoneAccountHandle(context,accountHandle));
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                    supportVideoAccout++;
                    videoPhoneAccountHandle = accountHandle;
                    Log.d("TelecomUtils","getSupportVideoPhoneAccout accout support video" + videoPhoneAccountHandle);
                }
                if(isImsRegistered) {//add for 1115906(1063975)
                    supportImsAccout++;
                    imsPhoneAccountHandle = accountHandle;
                    Log.d("TelecomUtils", "getSupportVideoPhoneAccout accout  support ims:" + imsPhoneAccountHandle);
                }
            }
             if ((isVideoCall && supportVideoAccout == 2) || (isConferenceDial && supportImsAccout == 2)) {
                //support dual volte, select an account
                return null;
            }
        }
        if (isVideoCall) {
            return videoPhoneAccountHandle;
        }
        if (isConferenceDial) {
            return imsPhoneAccountHandle;
        }
        return null;
    }
    /** @} */
    //UNISOC:add for bug1201575
    public static boolean showconferenceparticipantlabelconfig(Context context, Call call) {
        if (call != null) {
            CarrierConfigManager configManager =
                    (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle handle = call.getTargetPhoneAccount();
            int currentSubId = getSubIdForPhoneAccountHandle(context, handle);
            Log.i("TelecomUtils.showconferenceparticipantlabelconfig","currentSubId =" + currentSubId);
            if (-1 == currentSubId) {
                Log.i("TelecomUtils.showconferenceparticipantlabelconfig","currentSubId = -1");
                return  false;
            }
            if (configManager.getConfigForSubId(currentSubId) != null) {
                Log.i("TelecomUtils.showconferenceparticipantlabelconfig","configValue ="
                        + configManager.getConfigForSubId(currentSubId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_CONFERENCE_PARTICIPANT_LABEL));
                return configManager.getConfigForSubId(currentSubId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_CONFERENCE_PARTICIPANT_LABEL);
            } else {
                Log.i("TelecomUtils.showconferenceparticipantlabelconfig", "getConfigForDefaultPhone = null");
            }
        }
        return true;
    }
}
