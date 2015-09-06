package com.rp.podemu;

import android.app.Service;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;

public class PodEmuService extends Service
{
    private Thread bgThread=null;

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d("RPPService", "Service started");

        if(bgThread==null)
        {
            bgThread = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        while (true)
                        {
                            Log.d("RPPService", "Running...");
                            Thread.sleep(2000);
                        }
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }
                }

            });
            bgThread.start();
        }
        else
        {
            Log.d("RPPService","Service already running...");
        }
        return Service.START_STICKY;
    }


    @Override
    public void onDestroy()
    {
        Log.d("RPPService", "Service destroyed");
        super.onDestroy();
        if(bgThread!=null)
        {
            bgThread.interrupt();
        }
    }
}