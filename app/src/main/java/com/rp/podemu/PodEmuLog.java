/**

 Copyright (C) 2017, Roman P., dev.roman [at] gmail

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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
//import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
//import java.util.Locale;
//import java.util.concurrent.TimeUnit;

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
    public static final int LOGLEVEL_DISABLED=0;
    public static final int LOGLEVEL_LOG=1;
    public static final int LOGLEVEL_DEBUG=2;
    public static final int LOGLEVEL_DEBUG_VERBOSE=3;

    public static int LOGLEVEL_DEFAULT=LOGLEVEL_DEBUG;

    public static int debug_level=LOGLEVEL_DEFAULT;
    public static Context context;

    public final static String TAG="PodEmu";
    private static File logdir;
    private static SimpleDateFormat dateFormat;
    private static Calendar calendar;


    private static FileOutputStream logfileStream;
    private static File logfile;
    //private static String filename="PodEmu_" + System.currentTimeMillis() + ".log";
    private static String filename;

    public static boolean checkPermissions()
    {
        if(context==null)
        {
            Log.e(TAG, "context is null");
            return false;
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED)
        {
            // we dont have permissions. Need to disable debug and return false :(
            SharedPreferences sharedPref;
            sharedPref = context.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("enableDebug", "false");
            editor.apply();
            return false;
        }
        return true;
    }

    public static void initialize(Context c)
    {
        context=c;

        filename="PodEmu_debug.txt";

        initializeLogFile();


        dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
        calendar = Calendar.getInstance();
    }

    public static void initializeLogFile()
    {
        if(!checkPermissions()) return;

        String dirname="PodEmuLogs";
        // Get the directory for the user's public pictures directory.
        logdir = new File(Environment.getExternalStorageDirectory(), dirname);
        PodEmuLog.log("Log dir: " + logdir.getPath());
        if(!logdir.exists() && !logdir.mkdirs())
        {
            log("Directory not created");
        }
        logfile = new File(logdir, filename);

        try {
            logfileStream = new FileOutputStream(logfile, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void eraseDebugFile()
    {
        if( !checkPermissions() ) return;

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

        if(logfileStream!=null && checkPermissions())
        {
            long currTimeMillis=System.currentTimeMillis();
            calendar.setTimeInMillis(currTimeMillis);
            int millis=(int)(currTimeMillis%1000);

            byte msg[]=(dateFormat.format(calendar.getTime()) + " (" + String.format("%03d",millis) + ") " + ": " + TAG + " - " + str + "\n").getBytes();
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
        if(debug_level<LOGLEVEL_DEBUG) return;
        log(str);

    }
    public static void debugVerbose(String str)
    {
        if(debug_level<LOGLEVEL_DEBUG_VERBOSE) return;
        log(str);
    }

    public static void error(String str)
    {
        log("ERROR: " + str, true);
    }


    public static String getLogFileName()
    {
        if( checkPermissions() ) return logdir.getPath() + "/" + filename;
        else return "[missing]";
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
            //e.printStackTrace();
            uname="uname failed";
        }

        String version;
        try
        {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = pInfo.versionName;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            version="NA";
        }

        SharedPreferences sharedPref = context.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        //SharedPreferences.Editor editor = sharedPref.edit();
        String ctrlApp=sharedPref.getString("ControlledAppProcessName", "unknown app");
        String processName = sharedPref.getString("ControlledAppProcessName", "unknown application");
        int autoSwitchToApp=sharedPref.getInt("autoSwitchToApp", 0);

        int playlistCountMode=sharedPref.getInt("PlaylistCountMode", PodEmuMediaStore.MODE_PLAYLIST_SIZE_DEFAULT);
        int forceSimpleMode=sharedPref.getInt("ForceSimpleMode", 0);

        int bluetoothEnabled=sharedPref.getInt("bluetoothEnabled", 0);
        String bluetoothDevice=sharedPref.getString("bluetoothDeviceName", SerialInterface_BT.BTDEV_NAME_DEFAULT);
        String baudRate = sharedPref.getString("BaudRate", "unknown baud rate");
        String bluetoothDeviceAddress = sharedPref.getString("bluetoothDeviceAddress", "unknown");
        int logoDownloadBehaviour = sharedPref.getInt("LogoDownloadBehaviour", SettingsActivity.LOGO_DOWNLOAD_COLOR);
        boolean bluetoothIsBle = sharedPref.getBoolean("bluetoothIsBle", false);

        String enableDebug = sharedPref.getString("enableDebug", "false");

        log(
                "\nBoard        : " + Build.BOARD +
                "\nBrand        : " + Build.BRAND +
                "\nDevice       : " + Build.DEVICE +
                "\nDisplay      : " + Build.DISPLAY +
                "\nHardware     : " + Build.HARDWARE +
                "\nManufacturer : " + Build.MANUFACTURER +
                "\nModel        : " + Build.MODEL +
                "\nProduct      : " + Build.PRODUCT +
                "\nAPI level    : " + Build.VERSION.SDK_INT +
                "\n" + uname +
                "\nPodEmu Version: " + version +
                "\n\nSETTINGS DUMP: " +
                "\n     Controlled app          : " + ctrlApp +
                "\n     Controlled app process  : " + processName +
                "\n     Auto switch to app      : " + autoSwitchToApp +
                "\n     Playlist count mode     : " + playlistCountMode +
                "\n     Force simple mode       : " + forceSimpleMode +
                "\n     BT enabled              : " + bluetoothEnabled +
                "\n     BT device               : " + bluetoothDevice +
                "\n     BT device address       : " + bluetoothDeviceAddress +
                "\n     BT is LE device         : " + bluetoothIsBle +
                "\n     Baud rate               : " + baudRate +
                "\n     Logo download behaviour : " + logoDownloadBehaviour +
                "\n     Debug enabled           : " + enableDebug +
                "\n\n"
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
