package com.rp.podemu;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by rp on 9/10/15.
 */
public class PodEmuLog
{
    /**
     * Debug levels:
     * 1 - error
     * 2 - debug
     * 3 - verbose debug
     */
    public final static int DEBUG_LEVEL=2;
    public static Context context;

    public final static String TAG="PodEmu";


    private static FileOutputStream logfileStream;
    private static File logfile;
    private String filename="PodEmu_" + System.currentTimeMillis() + ".log";

    public PodEmuLog(Context c)
    {
        context=c;


        String dirname="PodEmuLogs";
        // Get the directory for the user's public pictures directory.
        File logdir = new File(Environment.getExternalStorageDirectory(), dirname);
        PodEmuLog.error("Log dir: " + logdir.getPath());
        if (!logdir.mkdirs())
        {
            error("Directory not created");
        }
        logfile=new File(logdir,filename);

        try
        {
            logfileStream = new FileOutputStream(logfile);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public static void error(String str)
    {
        Log.d(TAG, str);
        if(logfileStream!=null)
        {
            byte msg[]=(System.currentTimeMillis() + ": " + TAG + " - " + str + "\n").getBytes();
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
    public static void debug(String str)
    {
        if(DEBUG_LEVEL<2) return;
        error(str);

    }
    public static void verbose(String str)
    {
        if(DEBUG_LEVEL<3) return;
        error(str);
    }


}
