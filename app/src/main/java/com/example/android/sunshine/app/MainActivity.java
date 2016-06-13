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

import android.accounts.Account;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;

public class MainActivity extends ActionBarActivity implements ForecastFragment.Callback {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    // stores the current location
    private String mLocation;

    private boolean mTwoPane;

    //private static final String FORECASTFRAGMENT_TAG = "ff_tag";

    private static final String DETAILFRAGMENT_TAG = "df_tag";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize mLocation to the current value stored in shared prefs
        mLocation = Utility.getPreferredLocation(this);

        // activity_main has a static fragment that always displays a ForecastFragment,
        // which always displays up to date data because it uses all that fancy ContentProvider
        // and Loader stuff
        setContentView(R.layout.activity_main);

        if(findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/fragment_detail-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;


            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {

                // want to load a detail fragment with 'today' if SIS is null so the app does
                // not start with a blank second panel from dead
                // normalizeDate takes a Time long in UTC milliseconds
//                long todaysDateInMillis = WeatherContract.normalizeDate(System.currentTimeMillis());
//
//                Uri todayUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
//                        Utility.getPreferredLocation(this), todaysDateInMillis);
//
//                getSupportFragmentManager().beginTransaction()
//                        .replace(R.id.weather_detail_container,
//                                DetailFragment.newInstance(todayUri), DETAILFRAGMENT_TAG)
//                        .commit();


                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container,
                            new DetailFragment(),
                            DETAILFRAGMENT_TAG)
                    .commit();
            }


        }
        else {
            mTwoPane = false;

            // get rid of shadow below action bar in phone mode, per design mocks
            getSupportActionBar().setElevation(0f);
        }


        // recall that a ForecastFragment is already loaded at this point, via a <fragment> xml tag
        // so tell it that the first forecast item should use the larger, fancier
        // list_item_forecast_today layout by setting it's mUseTodayLayout boolean
        ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().findFragmentById(
                R.id.fragment_forecast);

        if(ff != null) {
            ff.setUseTodayLayout(!mTwoPane);

            // tell FF to tell it's adapter that it should simulate a first row (today) click
            // so it that the <selector> drawable on the listview can make it light blue
            // but only do this the first time the app is run
            if(savedInstanceState == null && mTwoPane)
                ff.performTodayViewClick();

        }




        // set up and start up the sync adapter
        SunshineSyncAdapter.initializeSyncAdapter(this);




    }



    @Override
    public void onItemSelected(Uri dateUri) {

        if(mTwoPane) { // app is in tablet mode
            FragmentManager fm = getSupportFragmentManager();

            // unlike in my PopMoviesS1 code, I'm adding a fragment tag here
            // unlike Udacity's code, I'm using DetailFragment's static newInstance method
            // to create a new fragment, which is a bit more polite I think

            // PLUS as a big bonus, there is no need to repeatedly create and set the bundle in
            // every activity that hosts a DetailFragment (like DetailActivity)
            fm.beginTransaction().replace(R.id.weather_detail_container,
                    DetailFragment.newInstance(dateUri), DETAILFRAGMENT_TAG).commit();


        }
        else { // app is in phone mode
            Intent intent = new Intent(this, DetailActivity.class);

            // N8NOTE: not using an Intent EXTRA anymore, now using .setData
            intent.setData(dateUri);


            startActivity(intent);

        }


    }




    // N8NOTE: added this when making location change setting functional

    // so I think this will only work if the app relies on the 'back' button being used to go
    // from SettingsActivity back to this activity.  If an UP button was implemented, that always
    // launches an Intent, so mLocation would just always be set to whatever was just changed in
    // shared prefs from SettingsActivity.  You would need another way to tell ForecastFragment that
    // it needs to update it's data.. maybe something like I did in PopMovies S1 using
    // start activity for result.. I think that's cleaner anway, and more intuitive, IMHO

    // ACTUALLY, the UP button would be fine: because this activity would just be recreated and a
    // NEW DetailFragment would be created at that time in onCreate


    // FYI: Sunshine has an UP arrow for detail view, but not for settings
    @Override
    protected void onResume() {
        super.onResume();

        String location = Utility.getPreferredLocation(this);

        if(location != null && !location.equals(mLocation)) {

            Log.i(LOG_TAG, "in onResume and mLocation != shared prefs location");


            // basically check to see if mLocation is the same as whatever is stored in user's
            // shared prefs, and if it's NOT, then call onLocationChanged in forecast frag
            // see my javadocs notes about what onLocationChange does
            // remember that mLocation is set in onCreate, so it's possible that user may have
            // navigated to SettingsActivity, changed the location zip, then pressed 'back',
            // which would NOT necessarily mean this activity was recreated, so mLocation might
            // still be the old value, thus the checks here..

            // recall ff is a static fragment (ie added with xml <fragment> tage,
            // so looking it up by Id is guaranteed to get the correct one
            ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().
                    findFragmentById(R.id.fragment_forecast);

            if(ff != null) {
                Log.i(LOG_TAG, "  and ff was NOT null, so about to call ff.onLocationChanged()");
                ff.onLocationChanged();
            }


            // unlike ff, df is a dynamic fragment, so need to look it up by Tag to make sure we
            // get the one we want (ie the one that was created by THIS activity using THIS
            // activities DETAILFRAGMENT_TAG... you don't want to accidentally a df that was created
            // by DetailActivity.. but wait?!?  I thought the FragmentManager was unique to each
            // Activity, so I don't understand the need to use a fragment tag here, why not just call
            // it by id??  Ok, so if you had the same fragment being placed in different containers
            // with different Ids, you would need a way to identify the fragment with a tag since
            // the fragment_detail id would be different for the different containers

            // I do think you could use findFragmentById here too, because the df is always placed
            // in 'weather_detail_container'.. even though the same fragment_detail id is used in DetailActivity,
            // this activity and detail activity have separate fragment managers and they should not
            // get mixed up.. here's some info from Android Dev Guide:

            /*
            Some things that you can do with FragmentManager include:
            Get fragments that exist in the activity, with findFragmentById() (for fragments that provide a UI in the activity fragment_detail)
            or findFragmentByTag() (for fragments that do or don't provide a UI).
             */
            DetailFragment df = (DetailFragment) getSupportFragmentManager().
                    findFragmentByTag(DETAILFRAGMENT_TAG);
//            DetailFragment df = (DetailFragment) getSupportFragmentManager().
//                    findFragmentById(R.id.weather_detail_container);

            if(df != null) {
                df.onLocationChanged(location);
            }


            mLocation = location;

        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

//        if (id == R.id.action_map) {
//            // moved to ForecastFragment after changing from using zip to launch map to using
//            // OWM api returned coords, because the db access is already set up over there
//            openPreferredLocationInMap();
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

//    private void openPreferredLocationInMap() {
//        String location = Utility.getPreferredLocation(this);
//
//        // Using the URI scheme for showing a location found on a map.  This super-handy
//        // intent can is detailed in the "Common Intents" page of Android's developer site:
//        // http://developer.android.com/guide/components/intents-common.html#Maps
//        Uri geoLocation = Uri.parse("geo:0,0?").buildUpon()
//                .appendQueryParameter("q", location)
//                .build();
//
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.setData(geoLocation);
//
//        if (intent.resolveActivity(getPackageManager()) != null) {
//            startActivity(intent);
//        } else {
//            Log.d(LOG_TAG, "Couldn't call " + location + ", no receiving apps installed!");
//        }
//    }


}
