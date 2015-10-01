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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

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
    private PodEmuIntentFilter iF = new PodEmuIntentFilter();
    private int failedReadCount=0;
    public Bitmap dockIconBitmap=null;


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

    @TargetApi(16)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if(bufferThread!=null || bgThread!=null)
        {
            PodEmuLog.debug("Service already running...");
            return Service.START_STICKY;
        }



// Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);

        Notification notification =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle("PodEmu")
                        .setContentText("iPod emulation is running")
                        .setContentIntent(pendingIntent)
                        .build();
        startForeground(1, notification);

//      System.currentTimeMillis()

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your application to the Home screen.
/*        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
// Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
*/
//        NotificationManager mNotificationManager =
//                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
// mId allows you to update the notification later on.
        //mNotificationManager.notify(1, mBuilder.build());


        /*
         * Buffer thread is used only to read from serial interface and put bytes into internal buffer.
         * It is important for this thread to have the highest priority, because internal buffer
         * of serial devices usually has only 255 bytes and not reading data fast enough can cause
         * some bytes to be lost.
         */
        if(bufferThread==null)
        {
            bufferThread = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        serialInterface = new SerialInterface_USBSerial();
                        int numBytesRead;
                        byte buffer[] = new byte[258];
                        PodEmuLog.log("Buffer thread started.");

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
                                failedReadCount=0;
                            }
                            catch (NullPointerException e)
                            {
                                PodEmuLog.error("Read attempt when interface is disconnected");
                                Thread.sleep(100);
                                failedReadCount++;
//                                if(failedReadCount>15)
                                    stopSelf();
                            }
                        }
                    } catch (InterruptedException e)
                    {
                        PodEmuLog.log("Buffer thread interrupted!");
                    }
                }
            });
            bufferThread.start();
        }

        /*
         * This is polling thread that will only post message 0x0027 to the serial interface if
         * polling mode is enabled.
         */
        if(pollingThread==null)
        {
            pollingThread = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        while (true)
                        {
                            if(oapMessenger.getPollingMode()) oapMessenger.oap_write_elapsed_time();
                            Thread.sleep(500);
                        }

                    } catch (InterruptedException e)
                    {
                        PodEmuLog.log("Polling thread interrupted!");
                    }
                }
            });
            pollingThread.start();
        }

        /*
         * this is main thread that reads data from internal buffer abd processes it byte by byte
         */
        if(bgThread==null)
        {
            bgThread = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        int numBytesRead=0;
                        byte buffer[]=new byte[1];
                        PodEmuLog.log("Background thread started.");

                        while (true)
                        {
                            //serialInterface.write("Service is running...".getBytes(), 21);

                            numBytesRead=0;
                            // Reading incoming data
                            while(inputBuffer.remove(buffer)>0)
                            {
                                oapMessenger.oap_receive_byte(buffer[0]);
                                numBytesRead++;
                            }
                            /*
                            if (mHandler != null)
                            {
                                Message msg = mHandler.obtainMessage(0);
                                mHandler.sendMessage(msg);
                            }
                            */

                            // sending updates
                            while(podEmuMessageVector.size()>0)
                            {
                                PodEmuMessage podEmuMessage=podEmuMessageVector.get(0);
                                //podIfSend(podEmuMessage);
                                oapMessenger.update_currently_playing(podEmuMessage);
                                podEmuMessageVector.remove(0);
                            }
                            oapMessenger.flush();

                            if(numBytesRead==0)
                            {
                                Thread.sleep(10);
                            }
                        }
                    }
                    catch (InterruptedException e)
                    {
                        PodEmuLog.log("Background processing thread interrupted!");
                    }
                }

            });
            bgThread.start();
        }

        PodEmuLog.log("Service started");
        return Service.START_STICKY;
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        registerReceiver(mReceiver, iF);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

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

        PodEmuLog.log("Service destroyed");
    }



    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        final class BroadcastTypes {
            static final String SPOTIFY_PACKAGE = "com.spotify.music";
            static final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";
            static final String QUEUE_CHANGED = SPOTIFY_PACKAGE + ".queuechanged";
            static final String METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged";
        }

        @Override
        public void onReceive(Context context, Intent intent)
        {
            // will be used later to precisely determine position
            long timeSentInMs = intent.getLongExtra("timeSent", 0L);
            boolean isPlaying;
            String artist;
            String album;
            String track;
            String id;
            int length;
            int position;
            int action_code=0;

            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");

            PodEmuLog.log("(S) Broadcast received: " + cmd + " - " + action);

            if(action.contains(BroadcastTypes.SPOTIFY_PACKAGE))
            {
                PodEmuLog.debug("(S) Detected SPOTIFY broadcast");

                artist = intent.getStringExtra("artist");
                album = intent.getStringExtra("album");
                track = intent.getStringExtra("track");
                id = intent.getStringExtra("id");
                length = intent.getIntExtra("length", 0);
                position = intent.getIntExtra("playbackPosition", 0);
                isPlaying = intent.getBooleanExtra("playing", false);

                if (action.equals(BroadcastTypes.METADATA_CHANGED))
                {
                    action_code=PodEmuMessage.ACTION_METADATA_CHANGED;
                }
                if (action.equals(BroadcastTypes.PLAYBACK_STATE_CHANGED))
                {
                    action_code=PodEmuMessage.ACTION_PLAYBACK_STATE_CHANGED;
                }
                if (action.equals(BroadcastTypes.QUEUE_CHANGED))
                {
                    action_code=PodEmuMessage.ACTION_QUEUE_CHANGED;
                    // Sent only as a notification, your app may want to respond accordingly.
                }

                Log.d("RPP", isPlaying + " : " + artist + " : " + album + " : " + track + " : " + id + " : " + length);

                PodEmuMessage podEmuMessage = new PodEmuMessage();
                podEmuMessage.setAlbum(album);
                podEmuMessage.setArtist(artist);
                podEmuMessage.setTrackName(track);
                podEmuMessage.setTrackID(id);
                podEmuMessage.setLength(length);
                podEmuMessage.setIsPlaying(isPlaying);
                podEmuMessage.setPositionMS(position);
                podEmuMessage.setTimeSent(timeSentInMs);
                podEmuMessage.setAction(action_code);

                oapMessenger.update_currently_playing(podEmuMessage);
            }
            else
            {
                // not supported broadcast so exiting
                return;
            }

        }
    };


}