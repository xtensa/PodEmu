/**

 OAPMessenger.class is class that implements "30 pin" serial protocol
 for iPod. It is based on the protocol description available here:
 http://www.adriangame.co.uk/ipod-acc-pro.html

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
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA

 */

package com.rp.podemu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Created by rp on 9/18/15.
 */

public class DockingLogoView extends View
{
    private Bitmap mBitmap, resizedBitmap;
    private Paint mPaint;
    private Canvas mCanvas;
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
        mCanvas = new Canvas(mBitmap);

    }

    public void setBitmap(Bitmap bitmap)
    {
        mBitmap=bitmap;
        if(mBitmap!=null)
        {
            resizedBitmap = Bitmap.createScaledBitmap(mBitmap, IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, true);
        }
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
        if(mBitmap!=null)
        {
            mPaint.setColor(Color.WHITE);
            mCanvas.drawRect(0, 0, mBitmap.getWidth(), mBitmap.getHeight(), mPaint);
            resizedBitmap = Bitmap.createScaledBitmap(mBitmap, IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, true);
            invalidate();
        }
    }


    @Override
    public void onDraw(Canvas canvas)
    {
        // TODO Auto-generated method stub
        super.onDraw(canvas);

        int x = getWidth();
        int y = getHeight();

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


        Log.d("onDraw", "img res: " + IMAGE_SCALED_RES_X + " " + IMAGE_SCALED_RES_Y);
        Log.d("onDraw", "Scaled to " + x + " " + y);


    }


    // override onSizeChanged
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        IMAGE_SCALED_RES_X=w;
        IMAGE_SCALED_RES_Y=h;
    }

    /*
    // TODO interpreting image data is better to move to OAPMessenger class
    public void process_picture_block(OAPMessenger.PictureBlock pictureBlock)
    {
        byte data[]=pictureBlock.data;

        if(data.length<10)
        {
            PodEmuLog.log("Wrong image block received");
            return;
        }

        int block_number=((data[0] & 0xff)<<8) | (data[1] & 0xff);
        int shift=2; // shift to start of image data

        if(block_number==0)
        {
            image_pos_x=0;
            image_pos_y=0;
            image_res_x=((data[3] & 0xff)<<8) | (data[4] & 0xff);
            image_res_y=((data[5] & 0xff)<<8) | (data[6] & 0xff);

            // calculate image position so that it is displayed centered
            image_start_x=(Math.max(image_res_x, image_res_y) - image_res_x)/2;
            image_start_y=(Math.max(image_res_x, image_res_y) - image_res_y)/2;

            image_bytes_per_line=((data[7] & 0xff)<<24) | ((data[8] & 0xff)<<16) | ((data[9] & 0xff)<<8) | (data[10] & 0xff);
            mBitmap = Bitmap.createBitmap(  Math.max(image_res_x, image_res_y),
                                            Math.max(image_res_x, image_res_y),
                                            Bitmap.Config.RGB_565);
            mCanvas = new Canvas(mBitmap);
            mPaint.setColor(Color.WHITE);
            mCanvas.drawRect(0, 0, Math.max(image_res_x, image_res_y), Math.max(image_res_x, image_res_y), mPaint);

            shift=11;
            PodEmuLog.debug("Received image starting block:");
            PodEmuLog.debug("              raw msg len=" + pictureBlock.len);
            PodEmuLog.debug("              image_res_x=" + image_res_x);
            PodEmuLog.debug("              image_res_y=" + image_res_y);
            PodEmuLog.debug("     image_bytes_per_line=" + image_bytes_per_line);
        }

        if(mCanvas==null)
        {
            return;
        }


        for(int i=shift;i<Math.min(pictureBlock.len,data.length)-1;i+=2)
        {
            // draw pixels only if we are inside declared resolution
            if(image_pos_x<image_res_x && image_pos_y<image_res_y)
            {
                int red = (data[i] & 0xff) >> 3; // take first 5 bits
                int green = ((((data[i] & 0xff) << 8) | (data[i+1] & 0xff)) >> 5) & 0x3f; // take next 6 bits
                int blue = data[i+1] & 0x1f; // take last 5 bits

                //expanding colors to 8 bit
                red <<= 3;
                green <<= 2;
                blue <<= 3;

                mPaint.setColor(Color.rgb(red, green, blue));
                mCanvas.drawPoint(image_start_x+image_pos_x, image_start_y+image_pos_y, mPaint);
            }

            image_pos_x++;
            if(image_pos_x==image_bytes_per_line/2)
            {
                if(image_pos_y<image_res_y) image_pos_y++;
                image_pos_x=0;
            }
        }

        // if last line was received
        //if(image_pos_y==image_res_y)
        if(mBitmap!=null)
        {
            resizedBitmap = Bitmap.createScaledBitmap(mBitmap, IMAGE_SCALED_RES_X, IMAGE_SCALED_RES_Y, true);
            invalidate();
        }

    }
*/
}
