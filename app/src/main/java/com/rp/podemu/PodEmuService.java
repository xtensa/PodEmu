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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;

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
    //public static boolean isAppLaunched=false;
    private static String baudRate;
    private static int forceSimpleMode;
    private static boolean bluetoothEnabled;
    private static boolean isBTConnected=false;
    private static boolean enableCyrillicTransliteration=false;
    private static boolean enableMimicAlwaysPlay=false;
    private static int intentId=0;

    public static String INTENT_ACTION_CLOSE_SERVICE="com.podemu.rp.ACTION_CLOSE_SERVICE";

    private long markerTime;



    public class LocalBinder extends Binder
    {
        PodEmuService getService()
        {
            // Return this instance of LocalService so clients can call public methods
            return PodEmuService.this;
        }

    }

    public static void stopService(Context context)
    {
        // closing Service
        Intent intent=new Intent(PodEmuService.INTENT_ACTION_CLOSE_SERVICE);
        context.sendBroadcast(intent);
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
        str="Length: " + podEmuMessage.getDurationMS() + "\n\r";
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

        /* should be set after serialInterface actualy initialized
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
        */
    }


    /**
     * Inform Main Activity that serial connection status changed
     */
    public static void communicateSerialStatusChange()
    {
        PodEmuLog.debug("PES: communicateSerialStatusChange() - " +
                (SerialInterfaceBuilder.getSerialInterface()!=null?SerialInterfaceBuilder.getSerialInterface().getAccessoryName():"unknown accessory"));
        // if mHandler is not set then service is not ready. This method will be called again once service is ready.
        if (mHandler != null)
        {
            Message message = mHandler.obtainMessage(0);
            message.arg1 = 3; // indicate serial status change message
            mHandler.sendMessage(message);
            PodEmuLog.debugVerbose("PES: communicateSerialStatusChange() - sent");
        }
        else
        {
            PodEmuLog.debugVerbose("SIBT: communicateSerialStatusChange() - not sent");
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
        enableCyrillicTransliteration = (sharedPref.getBoolean("CyrillicTransliteration", false));
        enableMimicAlwaysPlay = (sharedPref.getBoolean("MimicAlwaysPlay", false));

        OAPMessenger.setMimicAlwaysPlay(enableMimicAlwaysPlay);
    }

    private void runBufferThread()
    {
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

                        PodEmuLog.debug("PES: NEW bufferThread started. (Thread ID = " + bufferThread.getId() + ")");

                        while(serialInterface == null)
                        {

                            PodEmuLog.debug("PES: Starting serial interface initialization. (Thread ID = " + bufferThread.getId() + ")");
                            serialInterface = serialInterfaceBuilder.getSerialInterface(getBaseContext(), mHandler);

                            if(serialInterface != null)
                            {
                                /*
                                markerTime = System.currentTimeMillis();
                                long currTimeMillis = markerTime;


                                while ( !serialInterface.isConnected()
                                        && (currTimeMillis - markerTime < SerialInterface_BT.BT_CONNECT_TIMEOUT)
                                        //|| serialInterface instanceof SerialInterface_BLE
                                        )
                                {
                                    Thread.sleep(50);
                                    currTimeMillis = System.currentTimeMillis();
                                }
                                */

                                //Thread.sleep(SerialInterface_BT.BT_CONNECT_TIMEOUT);

                                if(serialInterface.isConnected())
                                {
                                    isBTConnected=true;
                                    PodEmuLog.debug("PES: reinitialization successfull.");
                                }
                                else
                                {
                                    PodEmuLog.error("PES: serial interface initialized but not connected. Restarting...");
                                    if(serialInterface!=null) // could be null if just disconnected
                                    {
                                        serialInterface.close();
                                        // Allow everything to close
                                        Thread.sleep(200);
                                        serialInterfaceBuilder.detach();
                                        serialInterface = null;

                                    }
                                }
                            }
                            else
                            {

                                // if we got here then probably BT is disabled. Aborting service.
                                PodEmuLog.error("PES: reinitialization failed. Closing service!");
                                serialInterfaceBuilder.detach();
                                closeServiceGracefully();
                                throw serialIFException;

                                //PodEmuLog.error("PES: serial interface initialization failed. Restarting...");
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
                                if ( numBytesRead<0 || ( !(serialInterface instanceof SerialInterface_BT) && !serialInterface.isConnected() ) )
                                {
                                    //PodEmuLog.error("PES: Read attempt nr " + failedReadCount + " when interface is disconnected");
                                    Thread.sleep(100);
                                    failedReadCount++;
                                    if (failedReadCount > 100) // 10 seconds
                                    {
                                        PodEmuLog.error("PES: Something wrong happen. Reading from serial interface constantly failing. Terminating service.");
                                        //serialInterface.close();
                                        //bufferThread.interrupt();
                                        //pollingThread.interrupt();
                                        closeServiceGracefully();
                                    }
                                }


                                // Reading incoming data
                                // TODO: maybe some day will rewrite to read using callback mechanism
                                while (true)
                                {
                                    try
                                    {
                                        numBytesRead = serialInterface.read(buffer);
                                    }
                                    catch(Exception e)
                                    {
                                        PodEmuLog.error("PES: read() attempt while serial interface is not connected. Cable suddenly disconnected?");
                                        PodEmuLog.printStackTrace(e);
                                        numBytesRead = -1;
                                    }

                                    if (numBytesRead <= 0) break;

                                    inputBuffer.add(buffer, numBytesRead);

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
                        if(bufferThread==null)
                            PodEmuLog.error("PES: buffer thread interrupted! bufferThread is null");
                        else
                            PodEmuLog.error("PES: buffer thread interrupted! (Thread ID = " + bufferThread.getId() + ")");

                        if(serialInterface!=null) serialInterface.close();
                    }

                    catch (SerialIFException e)
                    {
                        PodEmuLog.error("PES: buffer thread initialization failed.");
                        synchronized (this)
                        {
                            bufferThread.interrupt();
                        }
                    }

                    PodEmuLog.debug("PES: buffer thread finished.");
                }
            });
            bufferThread.start();
        }

    }

    @NonNull
    @TargetApi(26)
    private synchronized String createChannel() {
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        String name = "PodEmu notification channel";
        String channel = "PodEmu notification ID";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel(channel, name, importance);

        mChannel.enableLights(true);
        mChannel.setLightColor(Color.BLUE);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        } else {
            stopSelf();
        }
        return channel;
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
            PendingIntent pendingIntent = PendingIntent.getActivity(this, ++intentId, resultIntent, 0);

            // Intent to handle Close button
            Intent closeServiceIntent = new Intent(INTENT_ACTION_CLOSE_SERVICE);
            PendingIntent closeIntent = PendingIntent.getBroadcast(this, ++intentId, closeServiceIntent, 0);

            Notification.Action closeAction = new Notification.Action.Builder(R.drawable.ic_action_trash,
                                                            "Close PodEmu", closeIntent)
                    .build();

            Notification notification;
            String channel;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) // >= 8.0
            {
                channel = createChannel();
                notification = new Notification.Builder(this, channel).setSmallIcon(R.drawable.notification_icon)
                        .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.podemu_icon))
                        .setContentTitle("PodEmu")
                        .setContentText("iPod emulation is running")
                        .setContentIntent(pendingIntent)
                        .addAction(closeAction)
                        .build();
            }
            else
            {
                notification = new Notification.Builder(this).setSmallIcon(R.drawable.notification_icon)
                        .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.podemu_icon))
                        .setContentTitle("PodEmu")
                        .setContentText("iPod emulation is running")
                        .setContentIntent(pendingIntent)
                        .addAction(closeAction)
                        .build();
            }

            startForeground(1, notification);


            runBufferThread();

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
                                        //oapMessenger.oap_04_write_polling_playback_stopped();
                                        oapMessenger.oap_04_write_polling_track_status_changed(mediaPlayback.getCurrentPlaylist().getCurrentTrackPos());
                                    }

                                    oapMessenger.oap_04_write_polling_elapsed_time();

                                    // stop polling message should be sent only once, when playback is really stopped
                                    if(!mediaPlayback.isPlaying() && !stopCommandSent)
                                    {
                                        oapMessenger.oap_04_write_polling_playback_stopped();
                                        stopCommandSent=true;
                                    }
                                    if(mediaPlayback.isPlaying()) stopCommandSent=false;

                                }

                                // abort pending commands if waited too long
                                if (    oapMessenger.getPendingResponseStatus() &&
                                        System.currentTimeMillis() - oapMessenger.getPendingResponseSince() >= 1500)
                                {
                                    oapMessenger.abortPendingResponse("PES: time limit exceeded");
                                }

                                Thread.sleep(MediaPlayback.getPollingInteval());
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
                                    PodEmuLog.debugVerbose(String.format("PES: reading byte: 0x%02X", buffer[0]));
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

                                /*
                                if ( bluetoothEnabled && serialInterface instanceof SerialInterface_BT )
                                {
                                    long currTimeMillis = System.currentTimeMillis();
                                    SerialInterface_BT ifBT = (SerialInterface_BT) serialInterface;

                                    if (ifBT != null
                                            && !ifBT.isConnected()
                                            && currTimeMillis - markerTime > SerialInterface_BT.BT_CONNECT_TIMEOUT)
                                    {
                                        PodEmuLog.debug("PES: waited too long for BT interface to connect (" + SerialInterface_BT.BT_CONNECT_TIMEOUT + " ms.). Resetting BT interface.");
                                        markerTime = System.currentTimeMillis();
                                        ifBT.restart();
                                    }
                                }
*/
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

        //Intent listenerIntent = new Intent(this, NotificationListener3.class);
        //startService(listenerIntent);

        try
        {
            markerTime=System.currentTimeMillis();
            serialInterfaceBuilder=SerialInterfaceBuilder.getInstance();

            //SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            //String ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "unknown app");
            iF = new PodEmuIntentFilter();
            iF.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            iF.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
            //iF.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            iF.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            iF.addAction(INTENT_ACTION_CLOSE_SERVICE);
            registerReceiver(mReceiver, iF);
            reloadSettings();
            setMediaEngine();
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
            isBTConnected = false;

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

        if(serialInterface!=null) serialInterface.close();
        if(bufferThread!=null)    bufferThread.interrupt();
        if(pollingThread!=null)   pollingThread.interrupt();

        if (mHandler != null)
        {
            Message message = mHandler.obtainMessage(0);
            message.arg1 = 2; // indicate ipod dock connection status changed
            message.arg2 = OAPMessenger.IPOD_MODE_DISCONNECTED;
            mHandler.sendMessage(message);

            message = mHandler.obtainMessage(0);
            message.arg1 = 3; // indicate serial connection status changed
            mHandler.sendMessage(message);
        }
        mediaPlaybackInstance.action_stop();
        isBTConnected = false;

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
                BluetoothDevice btDev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if(action.equals(INTENT_ACTION_CLOSE_SERVICE))
                {
                    PodEmuLog.debug("PES: closing service in broadcast request");
                    closeServiceGracefully();
                }
                else if(action.equals(PodEmuIntentFilter.INTENT_ACTION_NOTIFY_MAIN_ACTIVITY_RESUMED))
                {
                    PodEmuLog.error("PES: received MA resume notification");
                    Intent maIntent = new Intent(PodEmuIntentFilter.INTENT_ACTION_METADATA_CHANGED, null, getApplicationContext(), MainActivity.class);
                    maIntent.putExtra("PodEmuMessage", getCurrentlyPlaying());
                    PodEmuLog.error("SENT Title: " + getCurrentlyPlaying().getTrackName());
                    sendBroadcast(maIntent);
                }
                else if (       action.contains(UsbManager.ACTION_USB_DEVICE_DETACHED)
                        || action.contains(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                        || (  action.contains(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                            && (                 isBTConnected &&     btDev != null &&
                                                    SerialInterface_BT.getInstance(getBaseContext()) != null &&
                                    (btDev.getName().equals(SerialInterface_BT.getInstance().getName())) )
                            )
                   )
                {
                    PodEmuLog.debug("PES: PodEmu serial interface disconnected. Initiating closing service.");
                    if(        !action.contains(UsbManager.ACTION_USB_DEVICE_DETACHED)
                            && !action.contains(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                            && bluetoothEnabled && SerialInterface_BT.getInstance()!=null && btDev != null)
                    {
                        PodEmuLog.debug("PES: Connected BT device: " + SerialInterface_BT.getInstance().getName());
                        PodEmuLog.debug("PES: Dropped BT device: " + ((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getName());
                        bufferThread.interrupt();
                        serialInterfaceBuilder.detach();
                        /*
                        WARNING: this is not required because detach() will nullify serialInterface
                        serialInterface.close();
                        serialInterface=null;
                        */
                        bufferThread=null;
                        runBufferThread();
                    }
                    else
                        closeServiceGracefully();
                }
                else
                {
                    PodEmuMessage podEmuMessage = PodEmuIntentFilter.processBroadcast(context, intent);
                    // if null is received then broadcast could be not from "our" app
                    if(podEmuMessage!=null)
                    {
                        podEmuMessage.setEnableCyrillicTransliteration(enableCyrillicTransliteration);
                        oapMessenger.respondPendingResponse("notification received", OAPMessenger.IPOD_SUCCESS);
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