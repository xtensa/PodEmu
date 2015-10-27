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

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by rp on 9/10/15.
 */
public class PodEmuLog
{
    /**
     * Debug levels:
     * 1 - log
     * 2 - debug
     * 3 - verbose debug
     */
    public static int DEBUG_LEVEL=2;
    public static Context context;

    public final static String TAG="PodEmu";
    private static File logdir;
    private static SimpleDateFormat dateFormat;
    private static Calendar calendar;


    private static FileOutputStream logfileStream;
    private static File logfile;
    //private static String filename="PodEmu_" + System.currentTimeMillis() + ".log";
    private static String filename="PodEmu_debug.txt";

    public PodEmuLog(Context c)
    {
        context=c;

        String dirname="PodEmuLogs";
        // Get the directory for the user's public pictures directory.
        logdir = new File(Environment.getExternalStorageDirectory(), dirname);
        PodEmuLog.log("Log dir: " + logdir.getPath());
        if (!logdir.mkdirs())
        {
            log("Directory not created");
        }
        logfile=new File(logdir,filename);

        try
        {
            logfileStream = new FileOutputStream(logfile,true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
        calendar = Calendar.getInstance();
    }

    public static void eraseDebug()
    {
        logfile.delete();
        try
        {
            logfileStream = new FileOutputStream(logfile,true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public static void log(String str, boolean isError)
    {
        if(isError)
            Log.e(TAG, str);
        else
            Log.d(TAG, str);

        if(logfileStream!=null)
        {
            long currTimeMillis=System.currentTimeMillis();
            calendar.setTimeInMillis(currTimeMillis);
            int millis=(int)(currTimeMillis%1000);

            byte msg[]=(dateFormat.format(calendar.getTime()) + " (" + millis + ") " + ": " + TAG + " - " + str + "\n").getBytes();
            try
            {
                logfileStream.write(msg, 0, msg.length);
                logfileStream.flush();
            }
            catch (java.io.IOException e)
            {
                e.printStackTrace();
            }
        }

    }

    public static void log(String str)
    {
        log(str, false);
    }

    public static void debug(String str)
    {
        if(DEBUG_LEVEL<2) return;
        log(str);

    }
    public static void verbose(String str)
    {
        if(DEBUG_LEVEL<3) return;
        log(str);
    }

    public static void error(String str)
    {
        log("ERROR: " + str, true);
    }


    public static String getLogFileName()
    {
        return logdir.getPath() + "/" + filename;
    }

    public static void printSystemInfo()
    {
        String uname;
        try
        {
            Process process = Runtime.getRuntime().exec("uname -a");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            uname=bufferedReader.readLine();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            uname="uname failed";
        }



        log(
                "\nBoard: " + Build.BOARD +
                "\nBrand: " + Build.BRAND +
                "\nBoard: " + Build.BOARD +
                "\nDevice: " + Build.DEVICE +
                "\nDisplay: " + Build.DISPLAY +
                "\nHardware: " + Build.HARDWARE +
                "\nManufacturer: " + Build.MANUFACTURER +
                "\nModel: " + Build.MODEL +
                "\nProduct: " + Build.PRODUCT +
                "\nAPI level: " + Build.VERSION.SDK_INT +
                "\n" + uname
        );
    }


    public static void printStackTrace(Exception e)
    {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e.printStackTrace(printWriter);
        error(writer.toString());
    }
}
