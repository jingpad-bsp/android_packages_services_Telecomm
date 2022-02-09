package com.unisoc.server.settings;

import com.android.server.telecom.R;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;

public class FadeInSettingsFragment extends PreferenceFragment {

    private CheckBoxPreference mFadeInPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.fade_in_settings_ex);

        mFadeInPreference = (CheckBoxPreference) findPreference(
                TelecommCallSettings.KEY_FADE_IN_RINGER);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mFadeInPreference) {
            Settings.Global.putInt(getActivity().getApplicationContext().getContentResolver(),
                    TelecommCallSettings.FADE_IN_ON, mFadeInPreference.isChecked() ? 1 : 0);
            return true;
        }
        return true;
    }
}
