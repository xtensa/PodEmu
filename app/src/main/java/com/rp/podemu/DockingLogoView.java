/**

 Copyright (C) 2015, Roman P., dev.roman [at] gmail

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see http://www.gnu.org/licenses/

 */

package com.rp.podemu;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by rp on 9/18/15.
 */

public class DockingLogoView extends View
{
    private Bitmap mBitmap, resizedBitmap;
    private Paint mPaint;
    private Context context;
    private String TAG="DockingLogoView";

    // two variables used to interpret picture block
    public final static int IMAGE_MAX_RES_X=176; // should be square
    public final static int IMAGE_MAX_RES_Y=176;

    public int IMAGE_SCALED_RES_X=IMAGE_MAX_RES_X; // should be square
    public int IMAGE_SCALED_RES_Y=IMAGE_MAX_RES_Y;



    public DockingLogoView(Context c, AttributeSet attrs)
    {
        super(c, attrs);
        context = c;

        // and we set a new Paint with the desired attributes
        mPaint = new Paint();
        //mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        //mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeWidth(0);
        bringToFront();
        setWillNotDraw(false);

        mBitmap=Bitmap.createBitmap(IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, Bitmap.Config.ARGB_8888 );

    }

    public void setBitmap(Bitmap bitmap)
    {
        mBitmap=bitmap;
        activateBitmap();
    }

    public void activateBitmap()
    {
        if(mBitmap==null) return;

        ImageView podemuLogo=(ImageView) ((Activity) context).findViewById(R.id.DOCK_status_icon);
        if(podemuLogo!=null)
        {
            podemuLogo.setImageDrawable(ContextCompat.getDrawable(context, (R.drawable.border)));
        }

        DockingLogoView dockingLogo=(DockingLogoView) findViewById(R.id.dockStationLogo);
        dockingLogo.setVisibility(ImageView.VISIBLE);

        resizedBitmap = Bitmap.createScaledBitmap(mBitmap, IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, true);
        invalidate();
    }

    public void setResizedBitmap(Bitmap bitmap)
    {
        resizedBitmap=bitmap;
        invalidate();
    }

    public Bitmap getResizedBitmap()
    {
        return resizedBitmap;
    }

    public void resetBitmap()
    {
        PodEmuLog.debug("Resetting logo");
        ImageView podemuLogo=(ImageView) ((Activity) context).findViewById(R.id.DOCK_status_icon);
        if(podemuLogo!=null)
        {
            podemuLogo.setImageDrawable(ContextCompat.getDrawable(context, (R.drawable.podemu_icon_with_text)));
        }

        if(mBitmap!=null)
        {
            mPaint.setColor(Color.WHITE);
            Canvas mCanvas = new Canvas(mBitmap);
            mCanvas.drawRect(0, 0, mBitmap.getWidth(), mBitmap.getHeight(), mPaint);
            resizedBitmap = Bitmap.createScaledBitmap(mBitmap, IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, true);
            invalidate();
        }

        DockingLogoView dockingLogo=(DockingLogoView) findViewById(R.id.dockStationLogo);
        if(dockingLogo!=null)
        {
            dockingLogo.setVisibility(ImageView.INVISIBLE);
        }
    }


    @Override
    public void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        //PodEmuLog.debug("onDraw");

        if(resizedBitmap!=null)
        {
            canvas.drawBitmap(resizedBitmap, 0, 0, mPaint);
        }

        else
        {
//            mPaint.setColor(Color.RED);
//            mCanvas.drawRect(0,0,IMAGE_SCALED_RES_X,IMAGE_SCALED_RES_Y,mPaint);

            canvas.drawBitmap(mBitmap, 1, 1, mPaint);
        }

    }


    // override onSizeChanged
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        IMAGE_SCALED_RES_X=w;
        IMAGE_SCALED_RES_Y=h;
    }

}
