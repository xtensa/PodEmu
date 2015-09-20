package com.rp.podemu;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.*;
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
    SerialInterface serialInterface;
    OAPMessenger oapMessenger = new OAPMessenger();

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
        return localBinder;
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
                        PodEmuLog.error("Buffer thread started.");

                        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                        //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

                        while (true)
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
                        }
                    } catch (InterruptedException e)
                    {
                        PodEmuLog.error("Buffer thread interrupted!");
                    }
                }
            });
            bufferThread.start();
        }


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
                        PodEmuLog.error("Buffer thread interrupted!");
                    }
                }
            });
            pollingThread.start();
        }

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
                        PodEmuLog.error("Background thread started.");

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
                        PodEmuLog.error("Background processing thread interrupted!");
                    }
                }

            });
            bgThread.start();
        }

        PodEmuLog.error("Service started");
        return Service.START_STICKY;
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if(bgThread!=null)
        {
            bgThread.interrupt();
        }
        if(bufferThread!=null)
        {
            bufferThread.interrupt();
        }

        PodEmuLog.error("Service destroyed");
    }
}