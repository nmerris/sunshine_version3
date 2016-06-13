package com.example.android.sunshine.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.sunshine.app.data.WeatherContract;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = DetailFragment.class.getSimpleName();


    private static final int DETAIL_VIEW_LOADER_ID = 100;


    private static final String ARG_WEATHER_WITH_DATE_URI = "weather_with_date";


    private static final String FORECAST_SHARE_HASHTAG = " #SunshineApp";
    private String mForecastStr;

    ShareActionProvider mShareActionProvider;

    // represents the currently display weather data URI
    private Uri mUri;



    // trying out ButterKnife
    @Bind(R.id.detail_day_textview) TextView       mDayTextView;
    @Bind(R.id.detail_date_textview) TextView      mDateTextView;
    @Bind(R.id.detail_high_temp_textview) TextView mHighTextView;
    @Bind(R.id.detail_low_temp_textview) TextView  mLowTextView;
    @Bind(R.id.detail_icon_imageview) ImageView    mIconImageView;
    @Bind(R.id.detail_icon_text_textview) TextView mIconShortDescTextView;
    @Bind(R.id.detail_humidity_textview) TextView  mHumidityTextView;
    @Bind(R.id.detail_wind_textview) TextView      mWindTextView;
    @Bind(R.id.detail_pressure_textview) TextView  mPressureTextView;

    @Bind(R.id.detail_custom_windview) WindDirectionView mWindDirectionView;
    
    
    
    
    // N8NOTE: the reason we can't reuse ForecastFragment.FORECAST_DETAIL_COLS_PROJ is
    // because that would defeat the purpose of making the projection array as small as possible
    // which we want to do here for efficiency.. otherwise it's ok to just reference that larger
    // array, or more likely to ref the static final ints that go hand in hand with that larger
    // array, like is done in ForecastAdapter.bindView.. not using a projection there, so might
    // as well just use the static final int's instead of searching for them again through a
    // bunch of db queries
    private final String[] FORECAST_DETAIL_COLS_PROJ = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_HUMIDITY,
            WeatherContract.WeatherEntry.COLUMN_WIND_SPEED,
            WeatherContract.WeatherEntry.COLUMN_DEGREES,
            WeatherContract.WeatherEntry.COLUMN_PRESSURE,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,

    };
    // these ints must match the order of FORECAST_DETAIL_COLS_PROJ above
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_WEATHER_HUMIDITY = 5;
    static final int COL_WEATHER_WIND_SPEED = 6;
    static final int COL_WEATHER_WIND_DEGREES = 7;
    static final int COL_WEATHER_PRESSURE = 8;
    static final int COL_WEATHER_CONDITION_ID = 9;




    public DetailFragment() {
        setHasOptionsMenu(true);
    }



    public static DetailFragment newInstance(Uri locationWithDate) {

        DetailFragment df = new DetailFragment();

        Bundle args = new Bundle();
        args.putParcelable(ARG_WEATHER_WITH_DATE_URI, locationWithDate);

        df.setArguments(args);
        return df;

    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_detail, container, false);





        Bundle arguments = getArguments();
        if(arguments != null) {

            // get the URI for the weather data to display from the fragment argument sent by
            // whatever Activity is hosting this fragment (which is MainActivity
            mUri = arguments.getParcelable(ARG_WEATHER_WITH_DATE_URI);

        }




//            if (intent != null && intent.hasExtra(Intent.EXTRA_TEXT)) {
//                mForecastStr = intent.getStringExtra(Intent.EXTRA_TEXT);
//                ((TextView) rootView.findViewById(R.id.detail_text))
//                        .setText(mForecastStr);
//            }

        // this MUST come after intent.getData above
        getLoaderManager().initLoader(DETAIL_VIEW_LOADER_ID, null, this);



        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.detailfragment, menu);

        // Retrieve the share menu item
        MenuItem menuItem = menu.findItem(R.id.action_share);

        // Get the provider and hold onto it to set/change the share intent.
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(menuItem);

        // Attach an intent to this ShareActionProvider.  You can update this at any time,
        // like when the user selects a new piece of data they might like to share.

        // but don't want to update the share intent if mForecastStr is null, which it will be
        // until onLoadFinished completes, which also sets the share intent.. the idea here
        // is that since onCreateLoader runs in another thread, it is possible that it can
        // finish at any time, perhaps before onCreateOptionsMenu, perhaps after
        if (mShareActionProvider != null && mForecastStr != null) {
            mShareActionProvider.setShareIntent(createShareForecastIntent());
        } else {
            Log.d(LOG_TAG, "Share Action Provider is null?");
        }
    }

    private Intent createShareForecastIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        //shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                mForecastStr + FORECAST_SHARE_HASHTAG);
        return shareIntent;
    }


    void onLocationChanged(String newLocation) {
        // replace the uri, since the location has changed
        Uri uri = mUri;

        if (uri != null) {
            long currentDate = WeatherContract.WeatherEntry.getDateFromUri(uri);

            Uri updatedUri = WeatherContract.WeatherEntry.
                    buildWeatherLocationWithDate(newLocation, currentDate);

            mUri = updatedUri;

            getLoaderManager().restartLoader(DETAIL_VIEW_LOADER_ID, null, this);
        }
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if(id == DETAIL_VIEW_LOADER_ID) {

            // The detail Activity called via intent.  Inspect the intent for forecast data.
//            Intent intent = getActivity().getIntent();
//
//            if (intent == null || intent.getData() == null) {
//                return null;
//            }

            // get the Uri attached to the intent instead of the EXTRA
            //mDetailViewDataUri = intent.getData();

            if(mUri != null) {

                return new CursorLoader(
                        // context
                        getActivity(),

                        // URI
                        mUri,

                        // projection: a String[] of columns to return, should only get the columns needed for efficiency
                        //null,
                        FORECAST_DETAIL_COLS_PROJ,

                        // selection: null means all
                        null,

                        // String[] selectionArgs
                        null,

                        // sort order
                        null);
            }
        }

        return null;

    }





    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        // Fragment.getView returns the rootview that was inflated in this fragments onCreateView callback
        View rootView = getView();


        // must check that rootView still exists because it's theoretically possible that Android
        // kill this Activity between when onCreateLoader was called and when onLoadFinished was called
        // from the background thread that was running onCreateLoader

        // I believe that if rootView is not null, that would also guarantee that mViewHolder
        // and mForecastStr and mShareActionProvider are also not null
        if(rootView != null && data.moveToFirst()) {

            ButterKnife.bind(this, rootView);

            Boolean isMetric = Utility.isMetric(getActivity());
            long date = data.getLong(COL_WEATHER_DATE);


            String day = Utility.getDayName(getActivity(), date);
            mDayTextView.setText(day);

            String dateString = Utility.getFormattedMonthDay(getActivity(), date);
            mDateTextView.setText(dateString);

            String tempHigh = Utility.formatTemperature(getActivity(),
                    data.getDouble(COL_WEATHER_MAX_TEMP), isMetric);
            mHighTextView.setText(tempHigh);
            mHighTextView.setContentDescription("High " + tempHigh);

            String tempLow = Utility.formatTemperature(getActivity(),
                    data.getDouble(COL_WEATHER_MIN_TEMP), isMetric);
            mLowTextView.setText(tempLow);
            mLowTextView.setContentDescription("Low " + tempLow);

            String humidity = String.format(getString(R.string.format_humidity),
                    data.getFloat(COL_WEATHER_HUMIDITY));
            mHumidityTextView.setText(humidity);

            String wind = Utility.getFormattedWind(getActivity(),
                    data.getFloat(COL_WEATHER_WIND_SPEED),
                    data.getFloat(COL_WEATHER_WIND_DEGREES));
            mWindTextView.setText(wind);


            String[] parts = wind.split(": ");
            mWindDirectionView.setText(parts[1]);
            mWindDirectionView.invalidate();


            String pressure = String.format(getString(R.string.format_pressure),
                    data.getFloat(COL_WEATHER_PRESSURE));
            mPressureTextView.setText(pressure);

            String weatherDesc = data.getString(COL_WEATHER_DESC);
            mIconShortDescTextView.setText(weatherDesc);


            int weatherIconId = data.getInt(COL_WEATHER_CONDITION_ID);
            mIconImageView.setImageDrawable(getResources().
                    getDrawable(Utility.getArtResourceForWeatherCondition(weatherIconId)));

            mIconImageView.setContentDescription(weatherDesc);



            // might as well update mForecastStr so that the share action provider has really
            // awesome data to share!
            mForecastStr = dateString + ": " + weatherDesc + ", High " + tempHigh
                    + ", Low " + tempLow + ", " + humidity + ", " +
                    wind + ", " + pressure;





            // must use getLong here or the date will overflow and give bogus results,
            // NOTE: sqlite only allows a data type of integer, but it must be equivalent
            // to a Java Long
//                String date = Utility.formatDate(data.getLong(COL_WEATHER_DATE));
//
//                String tempHigh = Utility.formatTemperature(getActivity(),
//                        data.getDouble(COL_WEATHER_MAX_TEMP), isMetric);
//                String tempLow = Utility.formatTemperature(getActivity(),
//                        data.getDouble(COL_WEATHER_MIN_TEMP), isMetric);
//
//                mForecastStr = date + " - " +
//                        data.getString(COL_WEATHER_DESC) + " - " +
//                        tempHigh + " / " + tempLow;

            // finally update the text in the text view
            //((TextView) rootView.findViewById(R.id.detail_text)).setText(mForecastStr);





            // if onCreateOptionsMenu has already happened, now we need to update the
            // shared Intent, since the cursor loader is finished.. no need to check for
            // a null mForecastStr because if this is executing, it's already got data from above
            if(mShareActionProvider != null) {
                mShareActionProvider.setShareIntent(createShareForecastIntent());
            }

        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // intentionally blank
    }
}
