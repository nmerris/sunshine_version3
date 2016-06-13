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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.CursorLoader;
import android.database.Cursor;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.android.sunshine.app.data.WeatherContract;
//import com.example.android.sunshine.app.service.SunshineService;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link ListView} fragment_detail.
 */
public class ForecastFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    private static final int PENDING_INTENT_REQUEST_CODE = 3;

    private static final int FORECAST_FRAGMENT_LOADER_ID = 100;

    private static final String SELECTED_LIST_ITEM_POSITION = "list_item_position";

    //private static final String SELECTED_ITEM_URI = "list_item_selected_uri";

    private ForecastAdapter mForecastAdapter;

    private Callback mCallback;

    private TextView mEmptyForecastTV;
    private ListView mListView;
    // need to init to -1 so that .onLoadFinished can ignore .smoothScrollToPosition in case that
    // mListViewPosition was never actually set, which would be the case the first time this fragment
    // is loaded from dead and no list item was tapped by user yet, but maybe they did scroll down
    // in which case we want to let Android handle maintaining the screens views
    private int mListViewPosition = ListView.INVALID_POSITION;

    //private Uri mRowJustSelected;

    // even though ForecastAdapter actually uses the boolean to determine which today layout to load,
    // this fragment (which 'hosts' mForecastAdapter) needs to tell the adapter two different times..
    // once immediately after MainAct sets mTwoPane (through FF.setUseTodayLayout, which passes it
    // on to FA), and then also after FF.onCreateView has finished setting up FA.. because it's possible
    // that the adapter would not yet be set up when FF.setUseTodayLayout was called
    private boolean mUseTodayLayout;


    public ForecastFragment() {}



    /**
     * Required interface for any activity that hosts this fragment
     */
    public interface Callback {

        /**
         * Hosting Activity should determine what happens when a forecast row is tapped
         * @param dateUri the Uri that points to the data just selected
         */
        void onItemSelected(Uri dateUri);
    }



    public void setUseTodayLayout(boolean b) {

        mUseTodayLayout = b;

        // just passing the boolean on to mForecastAdapter, MainActivity called this method
        // after it set mTwoPane in onCreate

        // I think it would have been nice to use a static newInstance method to create this
        // fragment in MainActivity, then we could just pass the boolean arg over when this frag
        // was created, eliminating the slightly awkward need for this pass through method
        // that would also have required extra code with the newInstance method, and of course
        // the <fragment> tag would not have been used in the xml... I guess to me it just seems
        // more intuitive my way, <fragment> tags are pretty limited, potato^2
        if(mForecastAdapter != null)
            mForecastAdapter.setUseTodayLayout(b);
    }



    public void performTodayViewClick() {
        if(mForecastAdapter != null)
            mForecastAdapter.performTodayViewClick();
    }





    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        //Log.i(LOGTAG, "just entered onAttach()");

        // associate the fragment's mCallback object with the activity it was just attached to
        mCallback = (Callback) getActivity();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //Log.i(LOGTAG, "just entered onDetach()");

        mCallback = null; // need to make sure this member variable is up to date with the correct activity
        // so nullify it every time this fragment gets detached from it's hosting activity
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        // initialize the loader
        getLoaderManager().initLoader(
                FORECAST_FRAGMENT_LOADER_ID, // used to identify the loader b/c you might have more than one (but not here)
                null,      // optional Bundle
                this);     // the object that implements the loader that the loader id matches to, ie this class instance


        super.onActivityCreated(savedInstanceState);

    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);

    }


    @Override
    public void onSaveInstanceState(Bundle outState) {

        // store the position of the listview so it can be set back after rotating
        //mListViewPosition = mListView.getSelectedItemPosition();
//        outState.putInt(SELECTED_LIST_ITEM_POSITION, mListView.getSelectedItemPosition());
//
//        Log.i("ForecastFragment", "just put in outState selected item pos: " + mListView.getSelectedItemPosition());
        // it turns out that 'selected' is not actually recognized even with the xml that makes
        // the list items blue when you tap them.. I believe that would need to be done programmatically

        // but the 'position' of the listview is always ready to go
        outState.putInt(SELECTED_LIST_ITEM_POSITION, mListViewPosition);

        // save the last selected row's URI so it can be retrieved after activity recreation
        //outState.putParcelable(SELECTED_ITEM_URI, mRowJustSelected);


        Log.i("ForecastFragment", "just put in outState selected item pos: " + mListViewPosition);


        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             final Bundle savedInstanceState) {

            // The CursorAdapter will take data from our cursor and populate the ListView
            // However, we cannot use FLAG_AUTO_REQUERY since it is deprecated, so we will end
            // up with an empty list the first time we run.
//        mForecastAdapter = new ForecastAdapter(getActivity(), cur, 0);
            mForecastAdapter = new ForecastAdapter(getActivity(), null, 0);


            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            // Get a reference to the ListView, and attach this adapter to it.
            mListView = (ListView) rootView.findViewById(R.id.listview_forecast);
            mListView.setAdapter(mForecastAdapter);

            mEmptyForecastTV = (TextView) rootView.findViewById(R.id.message_no_weather_info);
            mListView.setEmptyView(mEmptyForecastTV);


            Log.i("ForecastFragment", "mListView id is: " + mListView.getId());



            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

                Log.i("ForecastFragment", "just clicked item at position: " + position);
                //Log.i("ForecastFragment", "  and View.isActivated is now: " + view.isActivated());

                mListViewPosition = position;

                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                // N8NOTE: AdapterView.getItemAtPosition returns Object
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);

                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    long date = cursor.getLong(COL_WEATHER_DATE);

                    Uri rowJustSelected = WeatherContract.WeatherEntry.
                            buildWeatherLocationWithDate(locationSetting, date);

                    Log.i(LOG_TAG, "***** uri for rowJustSelected: " + rowJustSelected);

                    // now pass the hosting Activity the weather location with date URI and let
                    // it decide what to do next: it will either be a fragment txn on a tablet,
                    // or launch an intent to DetailActivity on a phone
                    mCallback.onItemSelected(rowJustSelected);

                }
            }
        });


        // only want to set mListViewPosition when sis is NOT null, ie when rotating device
        // otherwise this fragment is being created from dead.. recall that mListViewPosition is
        // initialized to ListView.INVALID_POSITION, so in .onLoadFinished mListViewPosition is checked
        // and if it's not INVALID.. it will scroll to the correct position

        // the reason why we don't scroll the listview here is that the data probably isn't loaded
        // yet, so it's done in .onLoadFinished instead
        if(savedInstanceState != null && savedInstanceState.containsKey(SELECTED_LIST_ITEM_POSITION)) {
            mListViewPosition = savedInstanceState.getInt(SELECTED_LIST_ITEM_POSITION);
        }

//        if(savedInstanceState != null && savedInstanceState.containsKey(SELECTED_ITEM_URI)) {
//            mRowJustSelected = savedInstanceState.getParcelable(SELECTED_ITEM_URI);
//            mCallback.onItemSelected(mRowJustSelected);
//        }


        // need to tell FA again, just in case it was null when FF.setUseTodayLayout was called
        mForecastAdapter.setUseTodayLayout(mUseTodayLayout);


        return rootView;
    }











    // N8NOTE: this ginormous String array and the following static final ints are only here to improve
    // the efficiency of the cursor.get... operations in ForecastAdapter.java
    private final String[] FORECAST_COLUMNS_PROJECTION = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    // These indices are tied to FORECAST_COLUMNS_PROJECTION.  If FORECAST_COLUMNS_PROJECTION changes, these
    // must change.
    // N8NOTE: the order of these must match the order of FORECAST_COLUMNS_PROJECTION
    // the whole point of this and the String[] above is so that ForecastFragment.onCreateLoader can
    // pass in a projection String[] param, that's why after adding all this, it was possible to comment
    // out rows like this in convertCursorRowToUXFormat below:
    // int idx_max_temp = cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
    // so really, 0 is the first column, 1 is the second column, and so on

    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;



    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if(id == FORECAST_FRAGMENT_LOADER_ID) {

            String locationSetting = Utility.getPreferredLocation(getActivity());
            String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";


            return new CursorLoader(
                    // context
                    getActivity(),

                    // URI
                    WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                            locationSetting, System.currentTimeMillis()),

                    // projection: a String[] of columns to return, should only get the columns needed for efficiency
                    //null,
                    FORECAST_COLUMNS_PROJECTION,

                    // selection: null means all
                    null,

                    // String[] selectionArgs
                    null,

                    // sort order
                    sortOrder);
        }

        return null;

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.i("ForecastFragment", "just entered onLoadFinished");

        mForecastAdapter.swapCursor(data);



        if(!data.moveToFirst()) { // no database data to display
            if(!Utility.isConnectedToInternet(getActivity())) { // AND no internet access
                // so update the error msg onscreen, note that the default message is defined in
                // fragment_main.xml in the text view itself, this is just a more specific msg
                mEmptyForecastTV.setText(getString(R.string.message_no_network_access));
            }
        }


        // scroll to the position in the list view that was last on screen,
        // this is needed for tablet mode when the device is rotated (it took 2 consecutive
        // rotations to cause the list to not go back to the correct position)
        if(mListViewPosition != ListView.INVALID_POSITION)
            mListView.smoothScrollToPosition(mListViewPosition);



    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

        mForecastAdapter.swapCursor(null);

    }

    // *******************************************************************************************





    // N8NOTE: added this when making location setting change functional

    /**
     * 1. calls updateWeather(), which performs a FetchWeatherTask.execute()
     * <p>
     * FetchWeatherTask is passed in a location, then updates the db for that location in
     * FetchWeatherTask.getWeatherDataFromJson through the ContentProvider using a bulkInsert
     * </p>
     *
     *
     * 2. restarts the Loader, which creates a new CursorLoader.  the new loader is constructed with
     * a URI that uses, again, whatever is currently stored in shared prefs for location, and the
     * current time.  This creates a cursor that points to to data that reflects the current location,
     * and of course from the current time out to 14 days.
     *
     *<p>
     *3. finally LoaderManager.LoaderCallbacks.onLoadFinished is automatically called, which
     * swaps the cursor on mForecastAdapter, which is when the screen actually refreshes the view
     *</p>
     */
    void onLocationChanged() {


        // pretty sure I don't even need this method anymore


        updateWeather();

        // restart the loader
        // you must have to do this every time you make a new api call which, after it's done,
        // updates some data in the db that this loader monitors
        getLoaderManager().restartLoader(FORECAST_FRAGMENT_LOADER_ID, null, this);

    }




    // in here is where the actual api call to OWM is started, by calling syncImmediately, which
    // is where all the background thread code is that used to live in the asynctask
    private void updateWeather() {

        // used to be in asynctask:
//        // FetchWeatherTask.getWeatherDataFromJson is where the db is actually updated
//        // through the ContentProvider
//        FetchWeatherTask weatherTask = new FetchWeatherTask(getActivity());
//
//        // this grabs the current location right out of user's shared prefs location setting
//        String location = Utility.getPreferredLocation(getActivity());
//
//        // pass it over to FetchWeatherTask, which will check to see if location is already in
//        // the db (via the ContentProvider of course), if it isn't, it will create a new ContentValues
//        // object and update the db
//        weatherTask.execute(location);




        // or use a service:
//        // instead of using the AsyncTask, now using SunshineService to do background thread work:
//        Intent sunshineServiceIntent = new Intent(getActivity(), SunshineService.class);
//        sunshineServiceIntent.putExtra(SunshineService.LOCATION_QUERY_EXTRA,
//                Utility.getPreferredLocation(getActivity()));
//
//        ComponentName componentName = getActivity().startService(sunshineServiceIntent);
//        Log.i("ForecastFragment", "after starting SunshineService, the ComponentName returned by startService is: " + componentName.toString());
//
//
//
//        // the following is all to use the AlarmManager to start up SunshineService
//        // create an intent that targets SunshineReceiver and put in the location as an extra
//        Intent sunshineReceiverIntent = new Intent(getActivity(), SunshineService.SunshineReceiver.class);
//        sunshineReceiverIntent.putExtra(SunshineService.LOCATION_QUERY_EXTRA,
//                Utility.getPreferredLocation(getActivity()));
//
//        // create a PendingIntent which targets SunshineService.SunshineReceiver
//        // it wraps the intent that starts SunshineService.SunshineReceiver
//        // set it to only fire once
//        PendingIntent pendingSunshineSrvIntent = PendingIntent.getBroadcast(getActivity(),
//                PENDING_INTENT_REQUEST_CODE, sunshineReceiverIntent, PendingIntent.FLAG_ONE_SHOT);
//
//
//        // set the Android system AlarmaManager to wake the phone if necessary, go off if 5 sec,
//        // and hand it the PendingIntent above, which will start SunshineService
//        AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
//        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 5000, pendingSunshineSrvIntent);





        // or use a sync adapter:
        SunshineSyncAdapter.syncImmediately(getActivity(),
                Utility.getPreferredLocation(getActivity()));
//        SunshineSyncAdapter.syncImmediately(getActivity());

//
//        String location = Utility.getPreferredLocation(getActivity());
//
//        Bundle bundle = new Bundle();
//        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
//        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
//        //bundle.putString();
//
//        ContentResolver.requestSync(SunshineSyncAdapter.getSyncAccount(getActivity()),
//                getString(R.string.content_authority), bundle);



    }




//    @Override
//    public void onStart() {
//        super.onStart();
//        updateWeather();
//    }





    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // not being used anymore, no need after implementing a sync adapter that refreshes
        // the view automatically when it's data changes
//        if (id == R.id.action_refresh) {
//            //updateWeather();
//            return true;
//        }

        if (id == R.id.action_map) {
            openPreferredLocationInMap();
            return true;
        }


        return super.onOptionsItemSelected(item);
    }


    private void openPreferredLocationInMap() {



        // so it turns out you can just get a Cursor from mForecastAdapter.. I'm leaving this the
        // way it is, but that would have been more efficient, it really helped me enhance my
        // understanding of sql, databases, ContenProvierds, and all that the way I did it
        // the only advantage to my way is that the cursor only returns one day of data
//        Cursor cursor = mForecastAdapter.getCursor();




        // get the Uri needed to get the coords from the db
//        Uri weatherUri = WeatherContract.WeatherEntry
//                .buildWeatherLocation(Utility.getPreferredLocation(getActivity()));

        // better to only query for todays weather, since we only need the lat and long coords
        // and they will be the same for every day for the same location
        // should not need to normalize date here because buildWeatherLocationWithDate does that
        Uri weatherUriWithDate = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                Utility.getPreferredLocation(getActivity()),
                System.currentTimeMillis());


        // ContentResolver finds this apps ContentProvider implementation (which is WeatherProvider)
        // WeatherProvider.query(uri, ...) matches the passed in uri and calls
        // WeatherProvider.getWeatherByLocationSetting(uri, projection, sortOrder)
        // which then calls WeatherContract.WeatherEntry.getLocationSettingFromUri(uri),
        // which grabs the location from the uri by calling WeatherContract.WeatherEntry.getLocationSettingFromUri(uri)
        // the sql selection is then set depending on if a start date was provided -- in this case it was not,
        // because the Uri we passed to WeatherProvider.query was constructed from
        // WeatherContract.WeatherEntry.buildWeatherLocation
        //      (so selection = "location.location_setting = ? ") as opposed to
        // WeatherContract.WeatherEntry.buildWeatherLocationWithDate
        //      (selection would = "location.location_setting = ? AND date >= ? ")
        // finally the selectionArgs in this case is set to just {locationSetting}
        // but if we had a start date the selectionArges would be {locationSetting, startDate}
        // and after all that,
        // WeatherProvider.sWeatherByLocationSettingQueryBuilder.query(...) is called which returns
        // a cursor that points to what we want... that's a lot of stuff happening!

//        Cursor cursor = getActivity().getContentResolver()
//                .query(weatherUri, coordsProjection, null, null, null);

        // might as well just use the same projection here as elsewhere in the class,
        // it's technically more data than we need, but it doesn't seem worth making a whole new
        // String array and int constants to go with it, it's not like this is an operation that
        // is going to be performed a lot or rapidly, it's just a user clicking a setting to launch
        // a map intent
        Cursor cursor = getActivity().getContentResolver()
                .query(weatherUriWithDate, FORECAST_COLUMNS_PROJECTION, null, null, null);



        if(cursor != null && cursor.moveToFirst()) {
            float latitude = 0;
            float longitude = 0;
            latitude = cursor.getFloat(COL_COORD_LAT);
            longitude = cursor.getFloat(COL_COORD_LONG);

            Log.i(LOG_TAG, "in openPrefferedLocationInMap, lat: " + latitude + "and long: " + longitude);



            // Using the URI scheme for showing a location found on a map.  This super-handy
            // intent can is detailed in the "Common Intents" page of Android's developer site:
            // http://developer.android.com/guide/components/intents-common.html#Maps
//        Uri geoLocation = Uri.parse("geo:0,0?").buildUpon()
//                .appendQueryParameter("q", location)
//                .build();
            Uri geoLocation = Uri.parse("geo:" + latitude + "," + longitude);
            Log.i(LOG_TAG, "  and geoLocation uri is: " + geoLocation);


            // create and launch the intent to open the map
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(geoLocation);

            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Log.d(LOG_TAG, "Couldn't call " + geoLocation + ", no receiving apps installed!");
            }

            cursor.close();

        }


    }



}
