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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

import java.util.Vector;


public class PodEmuService extends Service
{
    private Thread bgThread=null;
    private Thread bufferThread=null;
    private Thread pollingThread=null;
    private final IBinder localBinder = new LocalBinder();
    private static Handler mHandler;
    private static PodEmuMediaStore podEmuMediaStore;
    private static MediaPlayback mediaPlayback;
    private Vector<PodEmuMessage> podEmuMessageVector=new Vector<>();
    private ByteFIFO inputBuffer=new ByteFIFO(2048); //assuming 2048 should be enough
    private SerialInterfaceBuilder serialInterfaceBuilder;
    private SerialInterface serialInterface;
    private OAPMessenger oapMessenger = new OAPMessenger();
    private PodEmuIntentFilter iF;
    private int failedReadCount=0;
    public Bitmap dockIconBitmap=null;
    public boolean isDockIconLoaded=false;
    public boolean isAppLaunched=false;
    private static String baudRate;
    private static int forceSimpleMode;
    private static boolean bluetoothEnabled;

    // timeout after which interface will be closed if not connected (ms)
    public final static int BT_CONNECT_TIMEOUT = 1000;
    private long markerTime;



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
        str="Track nr: " + MediaPlayback.getInstance().getCurrentPlaylist().getCurrentTrack() + "/" +
                MediaPlayback.getInstance().getCurrentPlaylist().getTrackCount() + "\n\r";
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

        if( serialInterface != null)
        {
            serialInterface.setHandler(handler);
        }
        else
        {
            PodEmuLog.error("PES: sth went wrong, probably random cable disconnection. Destroying service.");
            serialInterfaceBuilder.detach();
            stopSelf();
        }
    }

    void setMediaEngine()
    {
        podEmuMediaStore = PodEmuMediaStore.getInstance();
        oapMessenger.setMediaStore(podEmuMediaStore);

        mediaPlayback = MediaPlayback.getInstance();
        oapMessenger.setMediaPlayback(mediaPlayback);
    }

    void setForceSimpleMode(int forceSimpleMode)
    {
        if(oapMessenger!=null)
        {
            oapMessenger.setForceSimpleMode(forceSimpleMode);
        }
        else
        {
            PodEmuLog.error("PES: cannot set force simple mode to " + forceSimpleMode);
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        PodEmuLog.debug("PES: Service bound");
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        PodEmuLog.debug("PES: Service unbound");
        return true;
    }

    public void reloadSettings()
    {
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        baudRate = sharedPref.getString("BaudRate", "57600");
        forceSimpleMode = (sharedPref.getInt("ForceSimpleMode", 0));
        bluetoothEnabled = (sharedPref.getInt("bluetoothEnabled", 0)!=0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        try
        {
            if (bufferThread != null || bgThread != null)
            {
                PodEmuLog.debug("PES: Service already running...");
                return Service.START_STICKY;
            }

            reloadSettings();

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
                class SerialIFException extends Exception {
                    public SerialIFException() {
                        super();
                    }
                }

                final SerialIFException serialIFException=new SerialIFException();

                bufferThread = new Thread(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            serialInterface = serialInterfaceBuilder.getSerialInterface();
                            int numBytesRead;

                            if(serialInterface == null)
                            {
                                PodEmuLog.debug("PES: buffer thread started before serial interface initialization. Trying to reinitialize.");
                                serialInterface=serialInterfaceBuilder.getSerialInterface( getBaseContext() );
                                if(serialInterface != null)
                                {
                                    PodEmuLog.debug("PES: reinitialization successfull.");
                                }
                                else
                                {
                                    PodEmuLog.error("PES: reinitialization failed. Closing service!");
                                    serialInterfaceBuilder.detach();
                                    stopSelf();

                                    throw serialIFException;
                                }

                            }

                            // some devices have problem reading less then internal chip buffer
                            // size (due to android bug 28023), therefore we need to set
                            // expected buffer size equal to internal buffer size of the device
                            byte buffer[] = new byte[serialInterface.getReadBufferSize()];
                            PodEmuLog.debug("PES: buffer thread started.");

                            serialInterface.setBaudRate(Integer.parseInt(baudRate));
                            oapMessenger.setForceSimpleMode(forceSimpleMode);

                            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                            //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

                            numBytesRead=0;
                            while(true)
                            {
                                try
                                {
                                    if ( !(serialInterface instanceof SerialInterface_BT) && ( numBytesRead<0 || !serialInterface.isConnected() ) )
                                    {
                                        PodEmuLog.error("PES: Read attempt nr " + failedReadCount + " when interface is disconnected");
                                        Thread.sleep(100);
                                        failedReadCount++;
                                        if (failedReadCount > 50) // 5 seconds
                                        {
                                            PodEmuLog.error("PES: Something wrong happen. Reading from serial interface constantly failing. Terminating service.");
                                            stopSelf();
                                        }
                                    }


                                    // Reading incoming data
                                    while (true)
                                    {
                                        try
                                        {
                                            numBytesRead = serialInterface.read(buffer);
                                        }
                                        catch(Exception e)
                                        {
                                            PodEmuLog.error("PES: read() attempt while serial interface is not connected. Cable suddenly disconnected?");
                                            numBytesRead = -1;
                                        }

                                        if (numBytesRead <= 0) break;

                                        //PodEmuLog.debug("RECEIVED BYTES: " + numBytesRead);
                                        for (int j = 0; j < numBytesRead; j++)
                                        {
                                            inputBuffer.add(buffer[j]);
                                        }
                                    }

                                    if (numBytesRead == 0)
                                    {
                                        Thread.sleep(10);
                                        failedReadCount = 0;
                                    }

                                }
                                catch (Exception e)
                                {
                                    if(!(e instanceof InterruptedException))
                                    {
                                        // sth wrong happen, just log and throw it up
                                        PodEmuLog.printStackTrace(e);
                                    }
                                    throw e;
                                }
                            }

                        }
                        catch (InterruptedException e)
                        {
                            PodEmuLog.debug("PES: buffer thread interrupted!");
                        }
                        catch (SerialIFException e)
                        {
                            PodEmuLog.error("PES: buffer thread initialization failed.");
                            bufferThread.interrupt();
                        }
                        PodEmuLog.debug("PES: buffer thread finished.");
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
                            boolean stopCommandSent=false;
                            MediaPlayback mediaPlayback=MediaPlayback.getInstance();

                            while (true) // service main loop
                            {

                                if (oapMessenger.getPollingMode())
                                {
                                    if(mediaPlayback.getTrackStatusChanged())
                                    {
                                        mediaPlayback.setTrackStatusChanged(false);
                                        oapMessenger.oap_04_write_polling_playback_stopped();

                                        oapMessenger.oap_04_write_polling_track_status_changed(mediaPlayback.getCurrentPlaylist().getCurrentTrackPos());

                                        //oapMessenger.oap_04_write_polling_chapter_status_changed(0);

                                    }

                                    if(!mediaPlayback.isPlaying() && !stopCommandSent)
                                    {
                                        oapMessenger.oap_04_write_polling_playback_stopped();
                                        stopCommandSent=true;
                                    }
                                    if(mediaPlayback.isPlaying()) stopCommandSent=false;

                                    oapMessenger.oap_04_write_polling_elapsed_time();
                                }

                                // abort pending commands if waited too long
                                if (    oapMessenger.getPendingResponseStatus() &&
                                        System.currentTimeMillis() - oapMessenger.getPendingResponseSince() >= 2000)
                                {
                                    oapMessenger.abortPendingResponse("PES: time limit exceeded");
                                }

                                Thread.sleep(500);
                            }

                        } catch (InterruptedException e)
                        {
                            PodEmuLog.debug("PES: Polling thread interrupted!");
                        }
                    }
                });
                pollingThread.start();
            }

        /*
         * this is main thread that reads data from serial interface internal buffer
         * and processes it byte by byte
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
                            PodEmuLog.debug("PES: Background thread started.");

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
                                    MediaPlayback.getInstance().updateCurrentlyPlayingTrack(podEmuMessage);
                                    podEmuMessageVector.remove(0);
                                }
                                oapMessenger.flush();

                                if ( bluetoothEnabled && serialInterface instanceof SerialInterface_BT )
                                {
                                    long currTimeMillis = System.currentTimeMillis();
                                    SerialInterface_BT ifBT = (SerialInterface_BT) serialInterface;

                                    if (ifBT != null
                                            && !ifBT.isConnected()
                                            && currTimeMillis - markerTime > BT_CONNECT_TIMEOUT)
                                    {
                                        PodEmuLog.debug("PES: waited too long for BT interface to connect (" + BT_CONNECT_TIMEOUT + " ms.). Resetting BT interface.");
                                        markerTime = System.currentTimeMillis();
                                        ifBT.restart();
                                    }
                                }

                                if (numBytesRead == 0)
                                {
                                    Thread.sleep(10);
                                }
                            }
                        }
                        catch (InterruptedException e)
                        {
                            PodEmuLog.debug("PES: Background processing thread interrupted!");
                        }
                        catch (Exception e)
                        {
                            PodEmuLog.printStackTrace(e);
                            throw e;
                        }
                    }

                });
                bgThread.start();
            }

            PodEmuLog.debug("PES: Service started");
            return Service.START_STICKY;
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }
    }

    private void registerBroadcastReceiver(String ctrlAppProcessName)
    {

    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        try
        {
            markerTime=System.currentTimeMillis();
            serialInterfaceBuilder=new SerialInterfaceBuilder();

            //SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            //String ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "unknown app");
            iF = new PodEmuIntentFilter();
            iF.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            iF.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
            //iF.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            iF.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            registerReceiver(mReceiver, iF);
            reloadSettings();
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

            serialInterfaceBuilder.detach();
            serialInterface = null;

            PodEmuLog.debug("PES: Service destroyed");
        }
        catch (Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }
    }

    public PodEmuMessage getCurrentlyPlaying()
    {
        return MediaPlayback.getInstance().getCurrentPlaylist().getCurrentTrack().toPodEmuMessage();
    }

    private void closeServiceGracefully()
    {
        MediaPlayback mediaPlaybackInstance=MediaPlayback.getInstance();
        serialInterfaceBuilder.detach();

        Message message = mHandler.obtainMessage(0);
        message.arg1 = 2; // indicate ipod dock connection status changed
        message.arg2 = OAPMessenger.IPOD_MODE_DISCONNECTED;
        mHandler.sendMessage(message);

        message = mHandler.obtainMessage(0);
        message.arg1 = 3; // indicate serial connection status changed
        mHandler.sendMessage(message);

        mediaPlaybackInstance.action_stop();

        stopSelf();
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
                MediaPlayback mediaPlaybackInstance=MediaPlayback.getInstance();
                if(mediaPlaybackInstance==null)
                {
                    PodEmuLog.error("PES: MediaPlayback instance is not ready. Broadcast not processed.");
                    return;
                }

                PodEmuLog.debug("PES: (S) Broadcast received: " + cmd + " - " + action);
                if(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) instanceof  BluetoothDevice)
                {
                    PodEmuLog.debug("PES: BT device disconnected: " + ((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getName());
                }

                if (action.contains(UsbManager.ACTION_USB_DEVICE_DETACHED)
                        || action.contains(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                        || (action.contains(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                            && (((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getName().equals(SerialInterface_BT.getInstance().getName())) )
                   )
                {
                    PodEmuLog.debug("PES: PodEmu serial interface disconnected. Initiating closing service.");
                    closeServiceGracefully();
                }
                else
                {
                    PodEmuMessage podEmuMessage = PodEmuIntentFilter.processBroadcast(context, intent);
                    // if null is received then broadcast could be not from "our" app
                    if(podEmuMessage!=null)
                    {
                        oapMessenger.respondPendingResponse(OAPMessenger.IPOD_SUCCESS);
                        mediaPlaybackInstance.updateCurrentlyPlayingTrack(podEmuMessage);
                    }
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