package com.rp.podemu;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.Vector;


public class PodEmuService extends Service
{
    private Thread bgThread=null;
    private Thread bufferThread=null;
    private final IBinder localBinder = new LocalBinder();
    private static Handler mHandler;
    private Vector<PodEmuMessage> podEmuMessageVector=new Vector<>();
    private ByteFIFO inputBuffer=new ByteFIFO(2048); //assuming 2048 should be enough
    SerialInterface serialInterface;
    OAPMessenger oapMessenger =new OAPMessenger();

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
        str="Track: " + podEmuMessage.getTrack() + "\n\r";
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
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return localBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d("RPPService", "Service started");


        if(bufferThread==null)
        {
            bufferThread = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        serialInterface = new SerialInterface_USBSerial();
                        int numBytesRead = 0;
                        byte buffer[] = new byte[258];
                        Log.d("RPPService", "Buffer thread started.");

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
                        Log.d("RPPService", "Buffer thread interrupted!");
                        return;
                    }
                }
            });
        }
        bufferThread.start();

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
                        Log.d("RPPService", "Background thread started.");

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
                                podIfSend(podEmuMessage);
                                podEmuMessageVector.remove(0);
                            }

                            if(numBytesRead==0)
                            {
                                Thread.sleep(10);
                            }
                        }
                    }
                    catch (InterruptedException e)
                    {
                        Log.d("RPPService", "Background processing thread interrupted!");
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
        super.onDestroy();
        if(bgThread!=null)
        {
            bgThread.interrupt();
        }
        if(bufferThread!=null)
        {
            bufferThread.interrupt();
        }
        Log.d("RPPService", "Service destroyed");
    }
}