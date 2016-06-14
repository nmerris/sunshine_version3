package com.example.android.sunshine.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Created by Nathan Merris on 6/14/2016.
 */
public class LocationEditTextPreference extends EditTextPreference {
    private static final String LOG_TAG = LocationEditTextPreference.class.getSimpleName();

    private int mMinUserInputLength;

    public LocationEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // get the custom attribute from the AttributeSet and store a ref to it
        TypedArray typedArray = context
                .obtainStyledAttributes(attrs, R.styleable.LocationEditTextPreference, 0, 0);
        try {
            mMinUserInputLength = typedArray.getInteger(
                    R.styleable.LocationEditTextPreference_minUserInputLength, 0);
            Log.i(LOG_TAG, "in constructor, read in minUserInputLength attribute from xml: " + mMinUserInputLength);
        }
        finally {
            typedArray.recycle();
        }
    }


    // could add methods to allow programmatic changes to this custom view, but not sure if that makes
    // a lot of sense for an EditTextPreference, at least not in this very simple case
//    public void setMinUserInputLength(int minLength) {
//        mMinUserInputLength = minLength;
//    }




}
