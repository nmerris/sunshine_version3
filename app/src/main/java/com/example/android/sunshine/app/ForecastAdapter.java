package com.example.android.sunshine.app;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * {@link ForecastAdapter} exposes a list of weather forecasts
 * from a {@link android.database.Cursor} to a {@link android.widget.ListView}.
 */
public class ForecastAdapter extends CursorAdapter {


    // used in getItemViewType and newView to determine which fragment_detail to inflate
    private final int VIEW_TYPE_TODAY = 0;
    private final int VIEW_TYPE_FUTURE_DAY = 1;

    //private final int VIEWHOLDER_TAG_KEY = 2;
    //private final int TODAY_VIEW_TAG_KEY = 3;
    //public static final Integer PLACEHOLDER_OBJ = 4;


    // is set based on if mSinglePane is true in MainActivity, because we want this adapter to display
    // different layout for first list item but ONLY in tablet mode (because you alreayd have a detail
    // view on screen so you don't need an extra large 'today' layout
    private boolean mSinglePane;

    private boolean mPerformTodayViewClick = false;


    private ListView mListView;


    //private Context mContext;


    public ForecastAdapter(Context context, Cursor c, int flags) {

        super(context, c, flags);

        //mContext = context;

    }



    // default implementation returns 1
    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        // only want to use VIEW_TYPE_TODAY if cursor is at first row, which is always 'today'
        // AND app is not running in tablet mode, in which case mSinglePane will be set false
        // by setUseTodayLayout, which is called by hosting activity when mSinglePane is detected
        return (position == 0 && mSinglePane) ? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;


        //return VIEW_TYPE_FUTURE_DAY;
        //return VIEW_TYPE_TODAY;
    }




    // called by this adapters fragment, basically MainAct calls FF.setUseTodayLayout,
    // which passes the boolean through to here
    // there is no need so store this in a bundle because MainActivity calls this method every
    // time it's onCreate method runs
    public void setUseTodayLayout(boolean b) {
        mSinglePane = b;
    }


    // called by this adapters fragment.. should be set to true only the first time the fragment
    // is loaded and has a savedInstanceState of null, otherwise set false so that Android can
    // automatically take care of preserving which view in the listview is currently highlighted
    // light blue.. basically setting this to true simulates a user click of the first item in list
    public void performTodayViewClick() {
        mPerformTodayViewClick = true;
    }





    // so heres the deal: Butterknife is convenient and very tidy, but it does not replace the need
    // for a ViewHolder.. .bind basically makes a (faster?) .findViewById call, so it would not be
    // good enough to just plop that inside the bindView method (like the previous commit).. still
    // it's nice to use, so it's now inside this class' ViewHolder inner class below
    public static class ViewHolder {

        // trying out ButterKnife
        @Bind(R.id.list_item_icon) ImageView iconView;
        @Bind(R.id.list_item_date_textview) TextView dateTextView;
        @Bind(R.id.list_item_forecast_textview) TextView forecastTextView;
        @Bind(R.id.list_item_high_textview) TextView highTextView;
        @Bind(R.id.list_item_low_textview) TextView lowTextView;

        public ViewHolder(View view) {
            // use this Butterknife method when NOT in an activity
            ButterKnife.bind(this, view);
        }

    }



    /*
    Remember that these views are reused as needed.
 */
    // N8NOTE: using newView instead of getView, Android takes care of reusing the same view as
    // needed while scrolling through the listview
    // NOTE: BaseAdapter, from which CursorAdapter extends, does NOT have a newView method,
    // so it's just easier and convenient to use newView when possible, I think
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {

        ViewHolder viewHolder;

        int viewType = getItemViewType(cursor.getPosition());

        int layoutId = (viewType == VIEW_TYPE_TODAY) ?
                R.layout.list_item_forecast_today : R.layout.list_item_forecast;

        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);

        viewHolder = new ViewHolder(view);
        // setTag is a convenient way to attach any data (param is Object) to any view,
        // the VIEWHOLDER_TAG_KEY key is optional if only using one tag, but you need it to distinguish
        // between tags if using more than 1 tag, as I am here
        view.setTag(R.id.listview_viewholder_key, viewHolder);


        // set the background color to light blue the first time the first listview view is created
        // but only in tablet mode b/c no need to highlight if only listview is on the screen
        if(cursor.getPosition() == 0) {
            Log.i("ForecastAdapter", "in newView, about to setId tad for view type today");
            //view.setBackgroundColor(context.getResources().getColor(R.color.sunshine_light_blue));

            // not actually using this anywhere, just left it here FYI or whatever
            view.setId(R.id.listview_today_view_key);


            // just checking that parent is same as mListView in FF.. it is
            Log.i("ForecastAdapter", "in newView, cursor pos = 0, parent.getId: " + parent.getId());
//            Log.i("ForecastAdapter", "  and this newView's id was set to: " + view.getId());

        }


        mListView = (ListView) parent;


        return view;
    }






/*
        This is where we fill-in the views with the contents of the cursor.
     */

    // N8NOTE: view comes from overridden newView above, context and cursor come from this class'
    // constructor
    @Override
    public void bindView(View view, Context context, Cursor cursor) {


        //Log.i("ForecastAdapter", "in bindView, and View.isActivated is now: " + view.isActivated());


        ViewHolder viewHolder = (ViewHolder) view.getTag(R.id.listview_viewholder_key);




        // get the weather icon id for a particular day from the cursor
        // in this app, the drawables are not downloaded, they are packaged with the app
        int weatherId = cursor.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);
        if (getItemViewType(cursor.getPosition()) == VIEW_TYPE_FUTURE_DAY) {

            viewHolder.iconView.setImageDrawable(context.getResources().
                    getDrawable(Utility.getIconResourceForWeatherCondition(weatherId)));
        }
        else if(getItemViewType(cursor.getPosition()) == VIEW_TYPE_TODAY) {

            viewHolder.iconView.setImageResource(Utility.
                    getArtResourceForWeatherCondition(weatherId));
        }

        // get the forecast string from cursor and update the textview text
        String forecast = cursor.getString(ForecastFragment.COL_WEATHER_DESC);
        viewHolder.forecastTextView.setText(forecast);

        viewHolder.iconView.setContentDescription(forecast);

        // get the date textview, format it to look nice, and update the textview text
        long dateInMillis = cursor.getLong(ForecastFragment.COL_WEATHER_DATE);
        viewHolder.dateTextView.setText(Utility.getFriendlyDayString(context, dateInMillis));


        
        // get the high temp and update the textview
        Double highTemp = cursor.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
        String formattedHighText = Utility.formatTemperature(mContext,
                highTemp, Utility.isMetric(context));
        viewHolder.highTextView.setText(formattedHighText);
        viewHolder.highTextView.setContentDescription("High " + formattedHighText);

        // get the low temp and update the textview
        Double lowTemp = cursor.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
        String formattedLowText = Utility.formatTemperature(mContext,
                lowTemp, Utility.isMetric(context));
        viewHolder.lowTextView.setText(formattedLowText);
        viewHolder.lowTextView.setContentDescription("Low "+ formattedLowText);


        // simulate a ListView item click to highlight the first row when app is first started,
        // but only if in tablet mode because otherwise the app will look like it starts in the
        // detail view on phones.. techncially it is would be loading a FF and then immediately
        // simulating a click event on the first row in the listview, which is no good
        // also, the controlling fragment (ForecastFragment in this case) needs to tell this adapter
        // if it should simulate a click or not, which should only be the first time the app is
        // loaded from dead

        // I tried to put this is FF, but ALL this adapter code gets executed after basically
        // everything in FF is done, including .onLoadFinished,  could have made a callback, but does
        // not seem worth it.. what would be the point of that?

        // I think doing this here means I can get rid of the fragment arg stuff that starts
        // FF with the current day to show in the detail pane... this sill nicely do that for me?
        // Indeed!  This works well, no need to for anything else in FF!!
        if(cursor.getPosition() == 0 && !mSinglePane && mPerformTodayViewClick)
            mListView.performItemClick(view, 0, 0);

    }


}