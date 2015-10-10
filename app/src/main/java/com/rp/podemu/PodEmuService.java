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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOError;
import java.io.IOException;
import java.util.Vector;


public class PodEmuService extends Service
{
    private Thread bgThread=null;
    private Thread bufferThread=null;
    private Thread pollingThread=null;
    private final IBinder localBinder = new LocalBinder();
    private static Handler mHandler;
    private Vector<PodEmuMessage> podEmuMessageVector=new Vector<>();
    private ByteFIFO inputBuffer=new ByteFIFO(2048); //assuming 2048 should be enough
    private SerialInterface serialInterface;
    private OAPMessenger oapMessenger = new OAPMessenger();
    private PodEmuIntentFilter iF;
    private int failedReadCount=0;
    public Bitmap dockIconBitmap=null;
    public boolean isDockIconLoaded=false;
    public boolean isAppLaunched=false;
    private static String baudRate;


    public class LocalBinder extends Binder
    {
        PodEmuService getService()
        {
            // Return this instance of LocalService so clients can call public methods
            return PodEmuService.this;
        }

    }

    void registerMessage(PodEmuMessage podEmuMessage)
    {
        podEmuMessageVector.add(podEmuMessage);
    }

    /**
     * Function used purely for debugging serial interface and internal calls
     * @param podEmuMessage - message to be posted to serial interface
     */
    void podIfSend(PodEmuMessage podEmuMessage)
    {
        String str;

        str = "Action: ";
        switch(podEmuMessage.getAction())
        {
            case PodEmuMessage.ACTION_METADATA_CHANGED: str+="METADATA_CHANGED"; break;
            case PodEmuMessage.ACTION_PLAYBACK_STATE_CHANGED: str+="PLAYBACK_STATE_CHANGED"; break;
            case PodEmuMessage.ACTION_QUEUE_CHANGED: str+="QUEUE_CHANGED"; break;
            default: str+="UNKNOWN";
        }
        str +="\n\r";
        podIfSend(str);
        str="Artist: " + podEmuMessage.getArtist() + "\n\r";
        podIfSend(str);
        str="Album: " + podEmuMessage.getAlbum() + "\n\r";
        podIfSend(str);
        str="Track: " + podEmuMessage.getTrackName() + "\n\r";
        podIfSend(str);
        str="Length: " + podEmuMessage.getLength() + "\n\r";
        podIfSend(str);
        str="Position: " + podEmuMessage.getPositionMS() + "\n\r";
        podIfSend(str);
        str="Is playing: " + podEmuMessage.isPlaying() + "\n\r";
        podIfSend(str);

    }

    void podIfSend(String str)
    {
        serialInterface.write(str.getBytes(), str.length());
    }

    void setHandler(Handler handler)
    {
        this.mHandler=handler;
        oapMessenger.setHandler(handler);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        PodEmuLog.debug("Service bound");
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        PodEmuLog.debug("Service unbound");
        return true;
    }

    public void reloadBaudRate()
    {
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        baudRate = sharedPref.getString("BaudRate", "57600");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        try
        {
            if (bufferThread != null || bgThread != null)
            {
                PodEmuLog.debug("Service already running...");
                return Service.START_STICKY;
            }

            reloadBaudRate();

// Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);

            Notification notification =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.drawable.notification_icon)
                            .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.podemu_icon))
                            .setContentTitle("PodEmu")
                            .setContentText("iPod emulation is running")
                            .setContentIntent(pendingIntent)
                            .build();
            startForeground(1, notification);

        /*
         * Buffer thread is used only to read from serial interface and put bytes into internal buffer.
         * It is important for this thread to have the highest priority, because internal buffer
         * of serial devices usually has only 255 bytes and not reading data fast enough can cause
         * some bytes to be lost.
         */
            if (bufferThread == null)
            {
                bufferThread = new Thread(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            serialInterface = new SerialInterface_USBSerial();
                            int numBytesRead;

                            // some devices have problem reading less then internal chip buffer
                            // size (due to android bug 28023), therefore we need to set
                            // expected buffer size equal to internal buffer size of the device
                            byte buffer[] = new byte[serialInterface.getReadBufferSize()];
                            PodEmuLog.debug("Buffer thread started.");

                            serialInterface.setBaudRate(Integer.parseInt(baudRate));

                            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                            //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

                            while (true)
                            {
                            /*
                            if(!serialInterface.isConnected())
                            {
                                PodEmuLog.error("Read attempt when interface is disconnected");
                                Thread.sleep(300);
                            }
                            else*/
                                try
                                {
                                    // Reading incoming data
                                    while ((numBytesRead = serialInterface.read(buffer)) > 0)
                                    {
                                        for (int j = 0; j < numBytesRead; j++)
                                        {
                                            inputBuffer.add(buffer[j]);
                                        }
                                    }
                                    if (numBytesRead == 0)
                                    {
                                        Thread.sleep(10);
                                    }
                                    failedReadCount = 0;
                                } catch (NullPointerException e)
                                {
                                    PodEmuLog.error("Read attempt when interface is disconnected");
                                    Thread.sleep(100);
                                    failedReadCount++;
                                    if (failedReadCount > 50) // 5 seconds
                                    {
                                        PodEmuLog.error("Something wrong happen. Reading from serial interface constantly failing. Terminating service.");
                                        stopSelf();
                                    }
                                }
                            }
                        } catch (InterruptedException e)
                        {
                            PodEmuLog.debug("Buffer thread interrupted!");
                        }
                    }
                });
                bufferThread.start();
            }

        /*
         * This is polling thread that will only post message 0x0027 to the serial interface if
         * polling mode is enabled.
         */
            if (pollingThread == null)
            {
                pollingThread = new Thread(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            while (true)
                            {
                                if (oapMessenger.getPollingMode())
                                    oapMessenger.oap_write_elapsed_time();
                                Thread.sleep(500);
                            }

                        } catch (InterruptedException e)
                        {
                            PodEmuLog.debug("Polling thread interrupted!");
                        }
                    }
                });
                pollingThread.start();
            }

        /*
         * this is main thread that reads data from internal buffer abd processes it byte by byte
         */
            if (bgThread == null)
            {
                bgThread = new Thread(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            int numBytesRead = 0;
                            byte buffer[] = new byte[1];
                            PodEmuLog.debug("Background thread started.");

                            while (true)
                            {
                                //serialInterface.write("Service is running...".getBytes(), 21);

                                numBytesRead = 0;
                                // Reading incoming data
                                while (inputBuffer.remove(buffer) > 0)
                                {
                                    oapMessenger.oap_receive_byte(buffer[0]);
                                    numBytesRead++;
                                }

                                // sending updates
                                while (podEmuMessageVector.size() > 0)
                                {
                                    PodEmuMessage podEmuMessage = podEmuMessageVector.get(0);
                                    oapMessenger.update_currently_playing(podEmuMessage);
                                    podEmuMessageVector.remove(0);
                                }
                                oapMessenger.flush();

                                if (numBytesRead == 0)
                                {
                                    Thread.sleep(10);
                                }
                            }
                        } catch (InterruptedException e)
                        {
                            PodEmuLog.debug("Background processing thread interrupted!");
                        }
                    }

                });
                bgThread.start();
            }

            PodEmuLog.debug("Service started");
            return Service.START_STICKY;
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }


    @Override
    public void onCreate()
    {
        super.onCreate();

        try
        {
            SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            String ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "unknown app");
            iF = new PodEmuIntentFilter(ctrlAppProcessName);
            iF.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
            registerReceiver(mReceiver, iF);
            reloadBaudRate();
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        try
        {
            unregisterReceiver(mReceiver);

            if(bgThread!=null)
            {
                bgThread.interrupt();
            }
            if(bufferThread!=null)
            {
                bufferThread.interrupt();
            }
            if(pollingThread!=null)
            {
                pollingThread.interrupt();
            }

            if(serialInterface!=null) serialInterface.close();

            PodEmuLog.debug("Service destroyed");
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }
    }

    public PodEmuMessage getCurrentlyPlaying()
    {
        return oapMessenger.get_currently_playing();
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            try
            {
                String action = intent.getAction();
                String cmd = intent.getStringExtra("command");

                PodEmuLog.debug("(S) Broadcast received: " + cmd + " - " + action);

                if (action.contains("USB_DEVICE_DETACHED"))
                {
                    serialInterface.close();

                    Message message = mHandler.obtainMessage(0);
                    message.arg1 = 2; // indicate ipod dock connection status changed
                    message.arg2 = OAPMessenger.IPOD_MODE_DISCONNECTED;
                    mHandler.sendMessage(message);

                    message = mHandler.obtainMessage(0);
                    message.arg1 = 3; // indicate serial connection status changed
                    mHandler.sendMessage(message);

                    MediaControlLibrary.action_stop();

                    stopSelf();
                } else
                {
                    PodEmuMessage podEmuMessage = PodEmuIntentFilter.processBroadcast(context, intent);
                    oapMessenger.update_currently_playing(podEmuMessage);
                }
            }
            catch(Exception e)
            {
                PodEmuLog.printStackTrace(e);
                throw e;
            }
        }
    };


}