package com.example.android.sunshine.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Playing around making my own custom view class.
 *
 * SEE Android example PieChart.java
 *
 * Created by ENNE EIGHT on 4/18/2016.
 */
public class WindDirectionView extends View {

    private static final String LOGTAG = "###WindDirectionView###";



    // member variables are either read in from xml in the constructors, or set programmatically
    // via setter methods.. the size of the View will default to the width of the wind description
    // text if not otherwise specified (ie if wrap_content is specified in the xml, then this View
    // will size itself to be as wide as the text, which is always about the same.. the text is
    // 'Wind: 18 mph NW' or whatever
    private float mCircleRadius;
    private int mPointerColor;
    private int mCircleColor;

    private float mTextSize;
    private int mTextColor;

    // obtained from the normal android text xml attr below
    // setting this to any default string is useless b/c it gets set back to null in the meaty
    // constructor (ie the only one that is implemented at this time.. the xml constructor),
    // the problem is that in tablet mode, the detail view is drawn before the loader gets to
    // onLoadFinished, which is where the text for this customer view is set.. so I just check for
    // null in onDraw to avoid a null pointer exception.. not too difficult to fix, but just trying
    // to keep moving, can't make everything perfect in this practive app
    private String mWindDescription;

    private float mWindDescriptionLength;



    private Context mContext;
    private Paint mTextPaint;
    private Paint mCirclePaint;
    private Paint mPointerPaint;

    // these are obtained from the standard android width and height xml attributes that any view must have
    private int mMaxX;
    private int mMaxY;


    // use to create this view in code
    // Not implemented, do not use!!

    /**
     * use to programmatically create this View.....
     * NOT IMPLEMENTED: DO NOT USE!!!!
     *
     * @param context
     */
    public WindDirectionView(Context context) {
        super(context);

        mContext = context;

    }



    // called when inflating from XML
    // NOTE: attrs encapsulates all the XML attributes that this view would have it was inflated
    // from XML.. which could actually include my own CUSTOM attributes, like perhaps the color of
    // the 'arrow' in the wind direction compass, or maybe if it should or should not have a text
    // description underneath the graphic..
    // of course these could all be done programmatically, in which case the above constructor
    // would be called and not this one, in that case you would need to set the wind direction
    // arrow color with something like mWindDirectionView.setArrowColor(Color c) or whatever

    /**
     * called by Android to create this view from XML, do not call this method from code
     *
     * @param context
     * @param set
     */
    public WindDirectionView(Context context, AttributeSet set) {
        super(context, set);

        mContext = context;

        // get a container for the custom xml attributes associated with this view
        TypedArray a = context.getTheme().obtainStyledAttributes(set,
                R.styleable.WindDirectionView, 0, 0);

        try {
            // Retrieve the values from the TypedArray and store into
            // fields of this class.
            //
            // The R.styleable.PieChart_* constants represent the index for
            // each custom attribute in the R.styleable.PieChart array.
            // NOTE: syntax is [<declare-stleable name>]_[<attr name>]
            mPointerColor = a.getColor(R.styleable.WindDirectionView_pointerColor, Color.CYAN);
            mCircleRadius = a.getDimension(R.styleable.WindDirectionView_circleRadius, 50.0f);
            mTextColor = a.getColor(R.styleable.WindDirectionView_textColor, Color.DKGRAY);
            mTextSize = a.getDimension(R.styleable.WindDirectionView_textSize, 24.0f);
            mCircleColor = a.getColor(R.styleable.WindDirectionView_circleColor, Color.BLUE);

            mWindDescription = a.getString(R.styleable.WindDirectionView_windDescription);
            if(mWindDescription == null) {
                mWindDescription = "NULL!";
            }


        } finally {
            // release the TypedArray so that it can be reused.
            a.recycle();
        }



        init();


    }

    // called when inflating from XML
    public WindDirectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }



    // called when this View's parent is laying out its children
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int hSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        int wSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int wSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        
//        int myHeight = hSpecSize;
        mMaxX = getWidth();
        mMaxY = getHeight();

        // getHeight and getWidth are going to return whatever was set in this View's XML width and
        // height attributes.. but the problem is that it's possible that this View was created
        // programmatically (or changed during runtime), so I need to make these member variables
        // and set myHeight and myWidth in the XML inflated constructors, and also in some setter
        // methods... I guess it depends on how deep into this I want to go
        int myHeight = mMaxY;
        int myWidth = mMaxX;

        Log.i(LOGTAG,"in onMeasure, myHeight = mMaxY set to: " + myHeight + ", and myWidth = mMaxX set to: " + myWidth);

        
        if(hSpecMode == MeasureSpec.EXACTLY) {
            myHeight = hSpecSize;
            Log.i(LOGTAG, "  and MeasureSpec is EXACTLY, so myHeight set to: " + hSpecSize);
        }
        else if(hSpecMode == MeasureSpec.AT_MOST) {
            Log.i(LOGTAG, "  and MeasureSpec is AT_MOST, next checking if myHeight > hSpecSize..");
            if(myHeight > hSpecSize) {
                Log.i(LOGTAG, "    myHeight > hSpecSize, setting myHeight to: " + hSpecSize);
                myHeight = hSpecSize;
            }
            Log.i(LOGTAG, "    myHeight < hSpecSize, so not changing myHeight");
        }
        // else if hSpecMode == MeasureSpec.UNSPECIFIED then this view can be any size it wants


        if(wSpecMode == MeasureSpec.EXACTLY) {
            myWidth = wSpecSize;
        }
        else if(wSpecMode == MeasureSpec.AT_MOST) {
            if(myWidth > wSpecSize) {
                myWidth = wSpecSize;
            }
        }


        // git R done
        setMeasuredDimension(myWidth, myHeight);
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }



    public void setText(String text) {

        mWindDescription = text;

        invalidate();
    }



    private void init() {

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(mTextSize);
        mTextPaint.setColor(mTextColor);

//        mWindDescriptionLength = mTextPaint.measureText(mWindDescription);
//        Log.i(LOGTAG, "in init, mWindDescLen: " + mWindDescriptionLength);



        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setColor(mCircleColor);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeWidth(5);


        mPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointerPaint.setColor(mPointerColor);
        mPointerPaint.setStyle(Paint.Style.STROKE);
        mPointerPaint.setStrokeWidth(10);
    }


    // onDraw might be called many times per second, so it's a best practice to NOT have objects
    // created in here.. instead, move them out to this class' scope
    // typically you would have an init method that allocates field variables in a class scope,
    // like the Paint objects, or whatever
    // NOTE: to force a redraw of this view, the main UI thread must call View.invalidate().. that
    // will just call onDraw here
    // NOTE: have to call postInvalidate() on the View if not in the main UI thread
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 8;

        float cx = mMaxX * 0.5f; // center circle horizontally
        float cy = mCircleRadius + padding; // circle nearly touches top of canvas
        canvas.drawCircle(cx, cy, mCircleRadius, mCirclePaint);



        String windDir = "";
//        String windStrParts[] = {};
        // get the wind direction, it's the last char block in the string
        if(mWindDescription != null) {
            windDir = mWindDescription.substring(mWindDescription.lastIndexOf(" ") + 1);
//            windStrParts = mWindDescription.split(": ");

        }



        // circle center coords
        float lineEndX = cx;
        float lineEndY = cy;

        // basic 45-45-90 right triangle geometry
        float deltaXorY = 0.707107f * mCircleRadius;

        // now determine the end points for the line that represents the cardinal wind dir that goes from
        // the center of the circle to the circumference
        switch(windDir) {
            case "N":
                lineEndX = cx;
                lineEndY = cy - mCircleRadius;
                break;
            case "NE":
                lineEndX = cx + deltaXorY;
                lineEndY = cy - deltaXorY;
                break;
            case "E":
                lineEndX = cx + mCircleRadius;
                lineEndY = cy;
                break;
            case "SE":
                lineEndX = cx + deltaXorY;
                lineEndY = cy + deltaXorY;
                break;
            case "S":
                lineEndX = cx;
                lineEndY = cy + mCircleRadius;
                break;
            case "SW":
                lineEndX = cx - deltaXorY;
                lineEndY = cy + deltaXorY;
                break;
            case "W":
                lineEndX = cx - mCircleRadius;
                lineEndY = cy;
                break;
            case "NW":
                lineEndX = cx - deltaXorY;
                lineEndY = cy - deltaXorY;
                break;
        }

        // draw the line from center of circle to appropriate cardinal direction
        canvas.drawLine(cx, cy, lineEndX, lineEndY, mPointerPaint);


        Log.i(LOGTAG, "in onDraw, mWindDescription is: " + mWindDescription);

        if(mWindDescription != null) {
            mWindDescriptionLength = mTextPaint.measureText(mWindDescription);
            float textStart = (mMaxX - mWindDescriptionLength) * 0.5f; // center text horizontally
            canvas.drawText(mWindDescription, textStart, mMaxY - padding, mTextPaint);
        }

    }


}
