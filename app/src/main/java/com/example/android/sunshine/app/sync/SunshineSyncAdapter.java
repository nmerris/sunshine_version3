package com.example.android.sunshine.app.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.Time;
import android.util.Log;

import com.example.android.sunshine.app.BuildConfig;
import com.example.android.sunshine.app.MainActivity;
import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Vector;


// the point of a sync adapter is to allow efficient background data syncs to a remote server,
// in this app's case, we want to sync with OpenWeatherMap every so often, so that our app always
// has up to date weather data.. sync adapter is nice because it does a lot of automatic battery
// saving stuff for us, like trying to schedule the sync while other apps are also syncing, which
// is more efficient than just checking every hour on the hour, or whatever.. there is a big battery
// penalty to turning on and off the radios, plus they stay on for a while afterwards
// ideally, we would use google cloud messenger, which would keep an eye on OWM, and only tell this
// adapter to perform a sync when weather data has changed.. actually that prob. doesn't make a lot
// of sense for this app since weather data is always changing, at least a little bit... but that
// would be useful for other client-server data syncing scenarios.. like when an app ONLY needed to
// sync to the server when the servers data had changed.. GCM would eliminate the need to keep polling
// the server to check, because it would just tell this app on this device that data had changed
public class SunshineSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String LOG_TAG = SunshineSyncAdapter.class.getSimpleName();

    private static final String LOCATION_KEY = "location_key";

    //private ContentProviderClient mContentProviderClient;

    // Interval at which to sync with the weather, in seconds.
    // 3 hrs * 60 min * 60 sec
    public static final int SYNC_INTERVAL = 3 * 60 * 60;
//    public static final int SYNC_INTERVAL = 20; // short text interval

    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    //private Context mContext;

    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final int WEATHER_NOTIFICATION_ID = 3004;



    public static final int DO_NOT_ALTER_NOTIFICATION = 10; // not functional, just here for clarity
    public static final int CANCEL_NOTIFICATION = 11;
    public static final int SHOW_NOTIFICATION_IMMEDIATELY = 12;



    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    public SunshineSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }




    // creates a notification tray weather notification for the current day
    // this is called every time the weather db is updated, but this method only shows a 'new'
    // notification every 24 hrs.. however I modded it so that it still updates the weather every
    // time it is called IF the notification is still visible, otherwise it will just pop up a new
    // notification every 24 hrs, this way the user can swipe it away and it won't obnoxiously come back
    // well that's a load of crap... NotificationManager.getActiveNotifications is only API-23+ !!
    public static void notifyWeatherIfAppropriate(Context context, int notificationStatus) {

        Log.i(LOG_TAG, "just entered notifyWeatherIfAppropriate");

        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lastNotificationKey = context.getString(R.string.pref_last_notification);
        // first time app is installed, lastSync defaults to 0 as desired
        long lastSync = prefs.getLong(lastNotificationKey, 0);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        boolean notificationsEnabled = prefs
                .getBoolean(context.getString(R.string.pref_enable_notifications_key), false);



        // user just toggled the notification setting to ON, so immediately show it
        // this must preceded the next if statement
        if(notificationStatus == SHOW_NOTIFICATION_IMMEDIATELY) {
            Log.i(LOG_TAG, "inside notifyWeatherIfAppropriate, about to call updateNotification because SHOW_NOTIFICATION_IMMEDIATELY was set");
            updateNotification(context, prefs, lastNotificationKey, notificationManager);
            return;
        }



        // do not show notifications if user has turned them off in settings
        // could have just happend (thus CANCEL_NOTIFICATION will be set)
        // or could just be turned off and so, while this adapter does periodic network calls,
        // we need to respect the user's settings
        // and clear any current notification
        if(!notificationsEnabled || notificationStatus == CANCEL_NOTIFICATION) {
            Log.i(LOG_TAG, "inside notifyWeatherIfAppropriate, cancelling notification because CANCEL_NOTIFICATION was set or notifications disabled in settings");
            notificationManager.cancel(WEATHER_NOTIFICATION_ID);
            return;
        }




        // check for Marshmellow.. really want to get this notification update in the tray
        // will only update the notification on marshmellow if it's already visible and it's been
        // less than 24 hrs, if > 24 hrs, always update it (which will make it show up again)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // returns an array list of ACTIVE (ie visible) notifications.. all of them
            StatusBarNotification[] notifications = notificationManager.getActiveNotifications();

            for (StatusBarNotification notification : notifications) {

                Log.i(LOG_TAG, "inside notifyWeatherIfAppropriate and package name is: " + notification.getPackageName());

                if(notification.getPackageName().equals("com.example.android.sunshine.app")
                        && notification.getId() == WEATHER_NOTIFICATION_ID) {
                    // only update the notification if it's for this app, so check package, and
                    // for good measure also check the ID
                    Log.i(LOG_TAG, "  and device is running Marshmallow, notification is visible, has the correct ID, and about to update it");
                    updateNotification(context, prefs, lastNotificationKey, notificationManager);
                    return;
                }
            }
        }




        // otherwise just update the notification every 3 hours
        if (System.currentTimeMillis() - lastSync >= DAY_IN_MILLIS) {
            Log.i(LOG_TAG, "inside notifyWeatherIfAppropriate, about to call updateNotification.. the last call in notifyWeatherIfAppropriate");
            updateNotification(context, prefs, lastNotificationKey, notificationManager);
        }



    }

    // updates an existing notification or creates a new one, but always uses the same ID, so in
    // either case you are only ever going to have one notification in the tray at any time
    // if this method is called, it always puts a a notification in the tray
    private static void updateNotification(Context context, SharedPreferences prefs,
            String lastNotificationKey, NotificationManager notificationManager) {

        Log.i(LOG_TAG, "inside updateNotification, about to create/update the weather notification");

        // Last sync was more than 1 day ago, let's send a notification with the weather.
        String locationQuery = Utility.getPreferredLocation(context);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);
            double high = cursor.getDouble(INDEX_MAX_TEMP);
            double low = cursor.getDouble(INDEX_MIN_TEMP);
            String desc = cursor.getString(INDEX_SHORT_DESC);

            int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
            String title = context.getString(R.string.app_name);

            // Define the text of the forecast.
            Boolean isMetric = Utility.isMetric(context);
            String contentText = String.format(context.getString(R.string.format_notification),
                    desc,
                    Utility.formatTemperature(context, high, isMetric),
                    Utility.formatTemperature(context, low, isMetric));




            Log.i(LOG_TAG, "in updateNotification, iconId is: " + iconId);

            //build your notification here.
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setSmallIcon(iconId)
                    .setContentTitle(title)
                    .setContentText(contentText);

            Intent resultIntent = new Intent(context, MainActivity.class);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

            stackBuilder.addNextIntent(resultIntent);

            PendingIntent pendingIntent = stackBuilder.getPendingIntent(0,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(pendingIntent);

            // this is where the notification actually gets created and displayed
            notificationManager.notify(WEATHER_NOTIFICATION_ID, builder.build());



            //refreshing last sync
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(lastNotificationKey, System.currentTimeMillis());
            editor.commit();
        }


    }





    /**
     * Helper method to have the sync adapter sync immediately.
     *
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context, @Nullable String location) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);


        //String location = Utility.getPreferredLocation(context);

        // I added this and changed the signature to support it
        if (location == null)
            bundle.putString(LOCATION_KEY, Utility.getPreferredLocation(context));
        else
            bundle.putString(LOCATION_KEY, location);


        Log.i("SunshineSyncAdapter", "inside syncImmediately, about to request sync, attaching bundle with location: " + location);


        // tell Android to schedule an sync.. IMMEDIATELY!
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }
//    public static void syncImmediately(Context context) {
//        Bundle bundle = new Bundle();
//        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
//        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
//
//        // I added this and changed the signature to support it
//        bundle.putString(LOCATION_KEY, Utility.getPreferredLocation(context));
//
//        ContentResolver.requestSync(getSyncAccount(context),
//                context.getString(R.string.content_authority), bundle);
//    }



    // only to be called the first time a new account is created, even if it's a dummy account
    private static void onAccountCreated(Account newAccount, Context context) {
        /*
         * Since we've created an account
         */
        SunshineSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context, null);
    }


    /**
     * 1. calls getSyncAccount, which checks to see if an account already exists for this app,
     * if it does, do nothing because everything is already set up and app will automatically sync as-is
     *
     * <p>
     * 2. if no account exists, create one and set up the sync interval, which depends on the
     * version of Android the device is running
     * </p>
     *
     * 3. and also call SunshineSyncService.syncImmediately so that the weather is up to date
     *
     * <p>The idea is that the account will only ever be set up the first time the app is installed,
     * or also if the user decides to remove the account from this app in device settings</p>
     *
     */
    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }



    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            // it's nicer when device is > KITKAT, because the flexTime (1 hour, see constants at top
            // of this class), will allow Android to scedule a synce as often as every hour, which
            // it will do efficiently by trying to batch it together with other sync stuff..
            // no noticeable hit on battery life, but a nice advantage in more frequent syncs
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    // Bundle.build validates all the sync builder stuff before it
                    setExtras(new Bundle()).build();

            ContentResolver.requestSync(request);
        }
        else { // build ver < KITKAT, so just sync every 3 hrs (it syncInterval constant in this class)
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }



    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if (accountManager.getPassword(newAccount) == null) {
            // the account does not exist yet, so newAccount will be returned below

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */


            onAccountCreated(newAccount, context);


        }
        return newAccount;
    }









    // called when the OS sync manager needs this adapter to perform a sync
    // this is where the code that was originally in AsyncTask will end up
    // recall that we also had that code in SunshineService for a while too (or it might still be
    // there but is not being used anymore because now were using this sync adapter
    // onPerformSync runs in a worker thread, like IntentService.onHandleIntent

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "onPerformSync Called, and authority passed in is: " + authority);



        // well sonofu.... getContext is available in here!
        // so didn't need to pass the location through in the Bundle, but it's cooler that way
        // could have just used Utility.getPreferredLocation(getContext())
        // also didn't need to use provider here.. could have just used getContext everywhere the
        // db access happens below... so I guess that means that the entire app's namespace is
        // available when a sync adapter is activated by the OS??

        // however, any time this method is NOT called via syncImmediately, we still need to get
        // the location from the users sharedPrefs.. basically this will happen for all periodic
        // syncs that Android does via syncmanager calling back to this method.. when that happens,
        // there would be no LOCATION_KEY in the Bundle, so just grab it from Utility.getPreferredLocation

        // this method is more complicate than it needs to be, I'm just trying stuff out to make sure
        // I understand how it all fits together.. really would have been better to never pass any
        // location data through any extras of bundles and just have this method always get location
        // from Utility.getPreferredLocation


        String locationQuery = extras.getString(LOCATION_KEY);

        if(locationQuery == null) {
            locationQuery = Utility.getPreferredLocation(getContext());
        }

        Log.i(LOG_TAG, "inside onPerformSync, locationQuery is: " + locationQuery);



        // don't use provider.. it's not the custom provider we wrote
        // use getContext().getContentResolver().whatever instead
        //mContentProviderClient = getContext().getContentResolver()



        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String FORECAST_BASE_URL =
                    "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "APPID";

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, locationQuery)
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return;
            }
            forecastJsonStr = buffer.toString();


            // meaty
            getWeatherDataFromJson(forecastJsonStr, locationQuery);


        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }



        //mContentProviderClient.release();


    }




    /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
    private void getWeatherDataFromJson(String forecastJsonStr,
                                        String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.

        // Location information
        final String OWM_CITY = "city";
        final String OWM_CITY_NAME = "name";
        final String OWM_COORD = "coord";

        // Location coordinate
        final String OWM_LATITUDE = "lat";
        final String OWM_LONGITUDE = "lon";

        // Weather information.  Each day's forecast info is an element of the "list" array.
        final String OWM_LIST = "list";

        final String OWM_PRESSURE = "pressure";
        final String OWM_HUMIDITY = "humidity";
        final String OWM_WINDSPEED = "speed";
        final String OWM_WIND_DIRECTION = "deg";

        // All temperatures are children of the "temp" object.
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";

        final String OWM_WEATHER = "weather";
        final String OWM_DESCRIPTION = "main";
        final String OWM_WEATHER_ID = "id";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
            String cityName = cityJson.getString(OWM_CITY_NAME);

            JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
            double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
            double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);

            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<>(weatherArray.length());

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();


            Log.i(LOG_TAG, "in getWeatherDataFromJson, returned json string is: " + forecastJsonStr + "\n");
            Log.i(LOG_TAG, "  zip code queried was: " + locationSetting);


            for(int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.
                long dateTime;
                double pressure;
                int humidity;
                double windSpeed;
                double windDirection;

                double high;
                double low;

                String description;
                int weatherId;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);

                pressure = dayForecast.getDouble(OWM_PRESSURE);
                humidity = dayForecast.getInt(OWM_HUMIDITY);
                windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
                windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.
                JSONObject weatherObject =
                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                weatherId = weatherObject.getInt(OWM_WEATHER_ID);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                high = temperatureObject.getDouble(OWM_MAX);
                low = temperatureObject.getDouble(OWM_MIN);

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, dateTime);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                cVVector.add(weatherValues);

                if(i == 0) {
                    Log.i(LOG_TAG, "  today's high value put in db was: " + high);
                    Log.i(LOG_TAG, "  today's low value put in db was: " + low);
                }


            }

            Log.i(LOG_TAG, "    and cVVector.size was: " + cVVector.size());

            int inserted = 0;
            // add to database
            if ( cVVector.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);



//                inserted = mContext.getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);
//                inserted = getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);
                // bull pucky
                inserted = getContext().getContentResolver()
                        .bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // also call notifyWeatherIfAppropriate after syncing, which will check if it has been > 1 day
                // since last displaying a notification in the notification drawer, if it has been,
                // it will grab the new weather data just inserted and display it
                // the notification will not be changed, so if it's already been swiped away it
                // will remain that way, and if it's visible, it will remain that way, it will
                // just continue updating as usual according to SYNC_INTERVAL
                notifyWeatherIfAppropriate(getContext(), DO_NOT_ALTER_NOTIFICATION);




                // determine yesterdays date and remove that data from the db..
//                Calendar rightNow = Calendar.getInstance();
//                Log.i(LOG_TAG, "                                               rightNow: " + rightNow);
//                Log.i(LOG_TAG, "                                     rightNow in millis: " + rightNow.getTimeInMillis());
//                Log.i(LOG_TAG, "                                    rightNow normalized: " + WeatherContract.normalizeDate(rightNow.getTimeInMillis()));
//
//                rightNow.add(Calendar.DAY_OF_YEAR, -1);
//                Log.i(LOG_TAG, "  after subtracting 1 day from DAY_OF_YEAR, rightNow is: " + rightNow);
//
//                long yesterdayInMillis = rightNow.getTimeInMillis();
//                Log.i(LOG_TAG, "                                      yesterdayInMillis: " + yesterdayInMillis);
//
//                // don't need this here because WeatherContract handles it, but good for diag
//                long normalizedYesterday = WeatherContract.normalizeDate(rightNow.getTimeInMillis());
//                Log.i(LOG_TAG, "       WeatherContract.normalizeDate(yesterdayInMillis): " + normalizedYesterday);
//
//                String[] selectionArgs = {Long.toString(normalizedYesterday)};
//
//                int deleted = 0;
//                deleted = getContext().getContentResolver()
//                        .delete(
//                                // content URI
//                                WeatherContract.WeatherEntry.CONTENT_URI,
//
//                                // selection
//                                WeatherContract.WeatherEntry.COLUMN_DATE + " <= ?",
//
//                                // selection args
//                                selectionArgs);

                // simpler way to do same thing as above:
                int deleted = 0;
                deleted = getContext().getContentResolver()
                        .delete(
                                // content URI
                                WeatherContract.WeatherEntry.CONTENT_URI,

                                // selection
                                WeatherContract.WeatherEntry.COLUMN_DATE + " <= ?",

                                // selection args
                                new String[] {Long.toString(dayTime.setJulianDay(julianStartDay-1))});
                // URI = content://com.example.android.sunshine.app/weather
                // SQL = DELETE FROM weather WHERE date <= [yesterday]
                Log.i(LOG_TAG, "      num records deleted: " + deleted);



            }

            Log.d(LOG_TAG, "FetchWeatherTask Complete. " + inserted + " Inserted");

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }




    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param cityName A human-readable city name, e.g "Mountain View"
     * @param lat the latitude of the city
     * @param lon the longitude of the city
     * @return the row ID of the added location.
     */
    long addLocation(String locationSetting, String cityName, double lat, double lon) {
        long locationId;





        // First, check if the location with this city name exists in the db
//        Cursor locationCursor = mContext.getContentResolver().query(
//                WeatherContract.LocationEntry.CONTENT_URI,
//                new String[]{WeatherContract.LocationEntry._ID},
//                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
//                new String[]{locationSetting},
//                null);

//        Cursor locationCursor = getContentResolver().query(
//                WeatherContract.LocationEntry.CONTENT_URI,
//                new String[] {WeatherContract.LocationEntry._ID},
//                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
//                new String[] {locationSetting},
//                null);

        Cursor locationCursor;
        locationCursor = getContext().getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);


        if (locationCursor.moveToFirst()) { // there was already a row for locationSetting in the db

            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);

        } else { // since the passed in locationSetting was not already in the db, add it now

            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);





            // Finally, insert location data into the database.
//            Uri insertedUri = mContext.getContentResolver().insert(
//                    WeatherContract.LocationEntry.CONTENT_URI,
//                    locationValues
//            );

//            Uri insertedUri = getContentResolver().insert(
//                    WeatherContract.LocationEntry.CONTENT_URI,
//                    locationValues
//            );

            Uri insertedUri = null;
            try {
                insertedUri = getContext().getContentResolver().insert(
                WeatherContract.LocationEntry.CONTENT_URI,
                locationValues
                );
            } catch (Exception e) {
                e.printStackTrace();
            }





            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }



}