//package com.example.android.sunshine.app.service;
//
//import android.app.IntentService;
//import android.content.BroadcastReceiver;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.Intent;
//import android.database.Cursor;
//import android.net.Uri;
//import android.text.format.Time;
//import android.util.Log;
//
//import com.example.android.sunshine.app.BuildConfig;
//import com.example.android.sunshine.app.data.WeatherContract;
//
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.Vector;
//
///**
// * Created by ENNE EIGHT on 4/20/2016.
// */
//public class SunshineService extends IntentService{
//
//    // other Activities/Fragments should use this when creating an Intent to start this service
//    public static final String LOCATION_QUERY_EXTRA = "lqe";
//
//    private final String LOG_TAG = SunshineService.class.getSimpleName();
//
//
//
//    // IntentService is a convenient extension of Service that just makes it easier to use Services
//    // essentially it handles the meaty part by creating a background thread queue, and does a bunch
//    // of automatic service starting and stopping stuff
//    // if I wasn't using this class and instead decided to extend directly from Service, it would be
//    // a lot more work to make all that happen.. note in that case I would call startService directly
//
//    // NOTE: I do not think that extending from Service directly would create worker threads at all,
//    // so IntentService is really really nice, HOWEVER, any service does have a LIFECYCLE that is
//    // independent of the Activity that created it
//
//    // basically this replaces what the AsyncTask used to do...... well NOT REALLY.. the huge diff
//    // is that a Service is meant to keep running in the background and will only ever rarely be
//    // killed, even if the activity in which it runs has been destroyed long ago, it's also better
//    // to use a Service for repeated data fetching type things, like when this app updates the weather
//    // periodically in the background, so that when the user returns to the app later in the day after
//    // checking weather in the morning, it will already have up to date weather data.. maybe this
//    // app doesn't really need a service?  A really good example is a music playing app, or my podcasting
//    // app that periodically checks for new podcasts in the background.. actually, I think this app
//    // is going to have a Notification that shows the current weather, so that's an obvious reason
//    // to use a service
//
//    /**
//     * Creates an IntentService.  Invoked by your subclass's constructor.
//     * SunshineService fetches weather data from OpenWeatherMap and updates the sqlite db in this app
//     *
//     * @param name Used to name the worker thread, important only for debugging.
//     */
//    public SunshineService(/*String name*/) {
//
//        // a service MUST have a default constructor, if not the manifest complains and will not compile
//        super("SunshineService");
//    }
//
//
//
//
//
//    // this is a CALLBACK from Android OS, it will be called after some other Activity or Fragment
//    // creates and Intent and plops it in Context.startService(Intent i).. i would have to identifiy
//    // which Service it wants to start, if it's this one, i will be passed in below
//    // for UNBOUND services: the other Service lifecycle calls are: onCreate, onStartCommand, onDestroy
//    // for BOUND services: onCreate, onBind, onUnbind, onDestroy
//    @Override // from IntentService
//    protected void onHandleIntent(Intent incomingIntent) {
//
//        // the idea is to move all the stuff that used to happen in FetchWeatherTasks's doInBackground
//        // method to here... both places are in worker threads that Android automatically manages for me
//
//        String locationQuery;
//        // first get the location from the incoming Intnet
//        if(incomingIntent != null && incomingIntent.hasExtra(LOCATION_QUERY_EXTRA)) {
//            locationQuery = incomingIntent.getStringExtra(LOCATION_QUERY_EXTRA);
//        }
//        else return;
//
//
//
//        Log.i(LOG_TAG, "inside onHandleIntent, locationQuery is: " + locationQuery);
//
//
//        // These two need to be declared outside the try/catch
//        // so that they can be closed in the finally block.
//        HttpURLConnection urlConnection = null;
//        BufferedReader reader = null;
//
//        // Will contain the raw JSON response as a string.
//        String forecastJsonStr = null;
//
//        String format = "json";
//        String units = "metric";
//        int numDays = 14;
//
//        try {
//            // Construct the URL for the OpenWeatherMap query
//            // Possible parameters are avaiable at OWM's forecast API page, at
//            // http://openweathermap.org/API#forecast
//            final String FORECAST_BASE_URL =
//                    "http://api.openweathermap.org/data/2.5/forecast/daily?";
//            final String QUERY_PARAM = "q";
//            final String FORMAT_PARAM = "mode";
//            final String UNITS_PARAM = "units";
//            final String DAYS_PARAM = "cnt";
//            final String APPID_PARAM = "APPID";
//
//            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
//                    .appendQueryParameter(QUERY_PARAM, locationQuery)
//                    .appendQueryParameter(FORMAT_PARAM, format)
//                    .appendQueryParameter(UNITS_PARAM, units)
//                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
//                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
//                    .build();
//
//            URL url = new URL(builtUri.toString());
//
//            // Create the request to OpenWeatherMap, and open the connection
//            urlConnection = (HttpURLConnection) url.openConnection();
//            urlConnection.setRequestMethod("GET");
//            urlConnection.connect();
//
//            // Read the input stream into a String
//            InputStream inputStream = urlConnection.getInputStream();
//            StringBuffer buffer = new StringBuffer();
//            if (inputStream == null) {
//                // Nothing to do.
//                return;
//            }
//            reader = new BufferedReader(new InputStreamReader(inputStream));
//
//            String line;
//            while ((line = reader.readLine()) != null) {
//                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
//                // But it does make debugging a *lot* easier if you print out the completed
//                // buffer for debugging.
//                buffer.append(line + "\n");
//            }
//
//            if (buffer.length() == 0) {
//                // Stream was empty.  No point in parsing.
//                return;
//            }
//            forecastJsonStr = buffer.toString();
//
//
//            // meaty
//            getWeatherDataFromJson(forecastJsonStr, locationQuery);
//
//
//
//        } catch (IOException e) {
//            Log.e(LOG_TAG, "Error ", e);
//            // If the code didn't successfully get the weather data, there's no point in attempting
//            // to parse it.
//        } catch (JSONException e) {
//            Log.e(LOG_TAG, e.getMessage(), e);
//            e.printStackTrace();
//        } finally {
//            if (urlConnection != null) {
//                urlConnection.disconnect();
//            }
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (final IOException e) {
//                    Log.e(LOG_TAG, "Error closing stream", e);
//                }
//            }
//        }
//
//    }
//
//
//    // a BroadcastReciever can recieve system alarms from AlarmManager.. useful so that Sunshine can update the weather
//    // in the notification drawer every so often
//    // a BroadcastReceiver object's life ends as soon as onReceive returns, so you can not count on
//    // it to still be valid after than, so no asynchronous stuff in here because when the asynch
//    // stuff returns, this BR obj may no longer exist
//
//    // so basically, this app will set up and send a PendingIntent to AlarmManager, which is controlled
//    // by the Android OS.  A PI allows the AM to then send an Itent to this BR as if the Intent came
//    // directly from this app with the same permissions and all that.. that's how this app can control
//    // how often the AM should tell this BR to update the weather
//    public static class SunshineReceiver extends BroadcastReceiver {
//
//
//
//        @Override
//        public void onReceive(Context context, Intent incomingSystemInitiatedIntent) {
//
//            Log.i("SS.SunshineReceiver", "just entered onReceive," +
//                    " about to start SunshineService and will pass it the intent that was passed here" +
//                    " but first will get the location from the incoming intent, then will pass it on" +
//                    " as an extra that launches SunshineService");
//
//
//            // get the location from the incoming intent (which actually came from a PendingIntent
//            // that was sent by Android OS AlarmManager
//            String location = "";
//            if(incomingSystemInitiatedIntent != null && incomingSystemInitiatedIntent.hasExtra(LOCATION_QUERY_EXTRA)) {
//                Log.i("SS.SunshineReceiver", "  and the location from the incoming Intent extra is: " + location);
//                location = incomingSystemInitiatedIntent.getStringExtra(LOCATION_QUERY_EXTRA);
//            }
//            else return;
//
//            // now launch an intent that will, somewhat confusingly, launch the Service that this
//            // Receiver lives inside.. can't call super or anything because this is a a static class,
//            // as it must be so that Android OS can call it
//            Intent sunshineServiceIntent = new Intent(context, SunshineService.class);
//            sunshineServiceIntent.putExtra(SunshineService.LOCATION_QUERY_EXTRA, location);
//            context.startService(sunshineServiceIntent);
//
//
//        }
//
//
//
//    }
//
//
//
//
//
//
//    /**
//     * Take the String representing the complete forecast in JSON Format and
//     * pull out the data we need to construct the Strings needed for the wireframes.
//     *
//     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
//     * into an Object hierarchy for us.
//     */
//    private void getWeatherDataFromJson(String forecastJsonStr,
//                                        String locationSetting)
//            throws JSONException {
//
//        // Now we have a String representing the complete forecast in JSON Format.
//        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
//        // into an Object hierarchy for us.
//
//        // These are the names of the JSON objects that need to be extracted.
//
//        // Location information
//        final String OWM_CITY = "city";
//        final String OWM_CITY_NAME = "name";
//        final String OWM_COORD = "coord";
//
//        // Location coordinate
//        final String OWM_LATITUDE = "lat";
//        final String OWM_LONGITUDE = "lon";
//
//        // Weather information.  Each day's forecast info is an element of the "list" array.
//        final String OWM_LIST = "list";
//
//        final String OWM_PRESSURE = "pressure";
//        final String OWM_HUMIDITY = "humidity";
//        final String OWM_WINDSPEED = "speed";
//        final String OWM_WIND_DIRECTION = "deg";
//
//        // All temperatures are children of the "temp" object.
//        final String OWM_TEMPERATURE = "temp";
//        final String OWM_MAX = "max";
//        final String OWM_MIN = "min";
//
//        final String OWM_WEATHER = "weather";
//        final String OWM_DESCRIPTION = "main";
//        final String OWM_WEATHER_ID = "id";
//
//        try {
//            JSONObject forecastJson = new JSONObject(forecastJsonStr);
//            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);
//
//            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
//            String cityName = cityJson.getString(OWM_CITY_NAME);
//
//            JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
//            double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
//            double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);
//
//            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);
//
//            // Insert the new weather information into the database
//            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());
//
//            // OWM returns daily forecasts based upon the local time of the city that is being
//            // asked for, which means that we need to know the GMT offset to translate this data
//            // properly.
//
//            // Since this data is also sent in-order and the first day is always the
//            // current day, we're going to take advantage of that to get a nice
//            // normalized UTC date for all of our weather.
//
//            Time dayTime = new Time();
//            dayTime.setToNow();
//
//            // we start at the day returned by local time. Otherwise this is a mess.
//            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
//
//            // now we work exclusively in UTC
//            dayTime = new Time();
//
//            for(int i = 0; i < weatherArray.length(); i++) {
//                // These are the values that will be collected.
//                long dateTime;
//                double pressure;
//                int humidity;
//                double windSpeed;
//                double windDirection;
//
//                double high;
//                double low;
//
//                String description;
//                int weatherId;
//
//                // Get the JSON object representing the day
//                JSONObject dayForecast = weatherArray.getJSONObject(i);
//
//                // Cheating to convert this to UTC time, which is what we want anyhow
//                dateTime = dayTime.setJulianDay(julianStartDay+i);
//
//                pressure = dayForecast.getDouble(OWM_PRESSURE);
//                humidity = dayForecast.getInt(OWM_HUMIDITY);
//                windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
//                windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);
//
//                // Description is in a child array called "weather", which is 1 element long.
//                // That element also contains a weather code.
//                JSONObject weatherObject =
//                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
//                description = weatherObject.getString(OWM_DESCRIPTION);
//                weatherId = weatherObject.getInt(OWM_WEATHER_ID);
//
//                // Temperatures are in a child object called "temp".  Try not to name variables
//                // "temp" when working with temperature.  It confuses everybody.
//                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
//                high = temperatureObject.getDouble(OWM_MAX);
//                low = temperatureObject.getDouble(OWM_MIN);
//
//                ContentValues weatherValues = new ContentValues();
//
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, dateTime);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, high);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, low);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
//
//                cVVector.add(weatherValues);
//            }
//
//            int inserted = 0;
//            // add to database
//            if ( cVVector.size() > 0 ) {
//                ContentValues[] cvArray = new ContentValues[cVVector.size()];
//                cVVector.toArray(cvArray);
//
////                inserted = mContext.getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);
//                inserted = getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);
//
//            }
//
//            Log.d(LOG_TAG, "FetchWeatherTask Complete. " + inserted + " Inserted");
//
//        } catch (JSONException e) {
//            Log.e(LOG_TAG, e.getMessage(), e);
//            e.printStackTrace();
//        }
//    }
//
//
//
//
//    /**
//     * Helper method to handle insertion of a new location in the weather database.
//     *
//     * @param locationSetting The location string used to request updates from the server.
//     * @param cityName A human-readable city name, e.g "Mountain View"
//     * @param lat the latitude of the city
//     * @param lon the longitude of the city
//     * @return the row ID of the added location.
//     */
//    long addLocation(String locationSetting, String cityName, double lat, double lon) {
//        long locationId;
//
//        // First, check if the location with this city name exists in the db
////        Cursor locationCursor = mContext.getContentResolver().query(
////                WeatherContract.LocationEntry.CONTENT_URI,
////                new String[]{WeatherContract.LocationEntry._ID},
////                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
////                new String[]{locationSetting},
////                null);
//        Cursor locationCursor = getContentResolver().query(
//                WeatherContract.LocationEntry.CONTENT_URI,
//                new String[] {WeatherContract.LocationEntry._ID},
//                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
//                new String[] {locationSetting},
//                null);
//
//        if (locationCursor.moveToFirst()) { // there was already a row for locationSetting in the db
//
//            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
//            locationId = locationCursor.getLong(locationIdIndex);
//
//        } else { // since the passed in locationSetting was not already in the db, add it now
//
//            // Now that the content provider is set up, inserting rows of data is pretty simple.
//            // First create a ContentValues object to hold the data you want to insert.
//            ContentValues locationValues = new ContentValues();
//
//            // Then add the data, along with the corresponding name of the data type,
//            // so the content provider knows what kind of value is being inserted.
//            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
//            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
//            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
//            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);
//
//            // Finally, insert location data into the database.
////            Uri insertedUri = mContext.getContentResolver().insert(
////                    WeatherContract.LocationEntry.CONTENT_URI,
////                    locationValues
////            );
//            Uri insertedUri = getContentResolver().insert(
//                    WeatherContract.LocationEntry.CONTENT_URI,
//                    locationValues
//            );
//
//            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
//            locationId = ContentUris.parseId(insertedUri);
//        }
//
//        locationCursor.close();
//        // Wait, that worked?  Yes!
//        return locationId;
//    }
//
//
//
//}
