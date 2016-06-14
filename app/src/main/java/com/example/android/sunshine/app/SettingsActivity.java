/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;

/**
 * A {@link PreferenceActivity} that presents a set of application settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String LOG_TAG = SettingsActivity.class.getSimpleName();

    // this was added per Udacity to fix a bug
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Intent getParentActivityIntent() {
        return super.getParentActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }


    // Registers a shared preference change listener that gets notified when preferences change
    @Override
    protected void onResume() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        super.onResume();
    }

    // Unregisters a shared preference change listener
    @Override
    protected void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add 'general' preferences, defined in the XML file
        addPreferencesFromResource(R.xml.pref_general);

        // For all preferences, attach an OnPreferenceChangeListener so the UI summary can be
        // updated when the preference changes.
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_units_key)));

        findPreference(getString(R.string.pref_enable_notifications_key))
                .setOnPreferenceChangeListener(this);

    }

    /**
     * Attaches a listener so the summary is always updated with the preference value.
     * Also fires the listener once, to initialize the summary (so it shows up before the value
     * is changed.)
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);

        // set preference summaries
        setPreferenceSummary(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }


    // ***** THIS IS CALLED BEFORE THE VALUE IS WRITTEN TO S.P. ******
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();
        Log.i("SettingsActivity", "in onPreferenceChange, stringValue passed in was: " + stringValue);
        setPreferenceSummary(preference, value);
        return true;

//        if (preference instanceof ListPreference) { // temp units
//            // For list preferences, look up the correct display value in
//            // the preference's 'entries' list (since they have separate labels/values).
//            ListPreference listPreference = (ListPreference) preference;
//            int prefIndex = listPreference.findIndexOfValue(stringValue);
//            if (prefIndex >= 0) {
//                preference.setSummary(listPreference.getEntries()[prefIndex]);
//            }
//        } else if(preference instanceof EditTextPreference) { // location entry
//            Log.i(LOG_TAG, "in onPreferenceChange, edittext pref just changed");
//            preference.setSummary();
//        }
//
//        else {
//            // For other preferences, set the summary to the value's simple string representation.
//            preference.setSummary(stringValue);
//        }
//
//
//
//
//
//
//        if(preference.getKey().equals(getString(R.string.pref_location_key))){
//            // reset the error message status to default/unknown since user just changed the location
//            Utility.resetLocationStatus(this);
//
//            // sync,the status of the notification will not change
//            SunshineSyncAdapter.syncImmediately(this, stringValue);
//        }
//        // if this condition is satisfied, that means the 'notification enabled' checkbox was just toggled
//        else if(preference.getKey().equals(getString(R.string.pref_enable_notifications_key))) {
//            if(stringValue.equals("true")) {
//                // sync, then immediately show a notification b/c it was just toggled on
//                SunshineSyncAdapter.notifyWeatherIfAppropriate(this,
//                        SunshineSyncAdapter.SHOW_NOTIFICATION_IMMEDIATELY);
//            }
//            else {
//                // sync and then immediately cancel the notification, this makes an uncessary network call.. blargh
//                SunshineSyncAdapter.notifyWeatherIfAppropriate(this,
//                        SunshineSyncAdapter.CANCEL_NOTIFICATION);
//            }
//        }

    }


    // ***** THIS IS CALLED AFTER THE VALUE IS WRITTEN TO S.P. ******
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if ( key.equals(getString(R.string.pref_location_key)) ) {
            // we've changed the location, so first clear locationStatus
            Utility.resetLocationStatus(this);

            // next get the new value and restart tell sync immediately
            String stringValue = sharedPreferences.getString(getString(R.string.pref_location_key), "");

            // recall that the value of sharedPrefs key_sharedprefs_sync_result is updated in
            // SunshineSyncAdapter after the API call returns (or not if it failed)
            SunshineSyncAdapter.syncImmediately(this, stringValue);
        } else if ( key.equals(getString(R.string.pref_units_key)) ) {
            // units have changed. update lists of weather entries accordingly
            getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
        } else if ( key.equals(getString(R.string.key_sharedprefs_sync_result)) ) {
            // our location status has changed.  Update the summary accordingly
            Preference locationPreference = findPreference(getString(R.string.pref_location_key));
            bindPreferenceSummaryToValue(locationPreference);
        }
    }
    
    
    private void setPreferenceSummary(Preference preference, Object value) {
        String stringValue = value.toString();
        String key = preference.getKey();
        
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate labels/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else if (key.equals(getString(R.string.pref_location_key))) {
            // using custom @IntDef annotation to get type checking for status below
            @SunshineSyncAdapter.LocationStatus int status = Utility.getLocationStatus(this);
            switch (status) {
                case SunshineSyncAdapter.LOCATION_STATUS_OK:
                        preference.setSummary(stringValue);
                        break;
                case SunshineSyncAdapter.LOCATION_STATUS_UNKNOWN:
                        // cool: the 2nd arg is a format string that is within pref_location_unknown_description
                        // so it will print '[pref_location_unknown_desc] ([value.toString])'
                        preference.setSummary(getString(R.string.pref_location_unknown_description, value.toString()));
                        break;
                case SunshineSyncAdapter.LOCATION_STATUS_INVALID:
                        preference.setSummary(getString(R.string.pref_location_error_description, value.toString()));
                        break;
                default:
                        // Note --- if the server is down we still assume the value
                        // is valid
                        preference.setSummary(stringValue);
            }
        } else {
            // For other preferences, set the summary to the value's simple string representation.
            preference.setSummary(stringValue);
        }
        
    }




    
}