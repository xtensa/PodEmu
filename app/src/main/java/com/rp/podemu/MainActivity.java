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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;


public class MainActivity extends AppCompatActivity
{


    private TextView mainText=null;
    private TextView serialStatusText=null;
    private TextView iPodStatusText=null;
    private int iPodConnected=OAPMessenger.IPOD_MODE_DISCONNECTED;



    private String ctrlAppProcessName;
    private Intent serviceIntent;
    private SerialInterface serialInterface;
    private PodEmuIntentFilter iF = new PodEmuIntentFilter();
    private PodEmuService podEmuService;
    private boolean serviceBound = false;
    private PodEmuLog podEmuLog;
    private PodEmuMessage currentlyPlaying=new PodEmuMessage();

    //public static LooperThread looperThread;

    public void setCtrlAppProcessName(String processName)
    {
        ctrlAppProcessName = processName;
    }

    public String getCtrlAppProcessName()
    {
        return ctrlAppProcessName;
    }


    public void action_next(View v)
    {
        MediaControlLibrary.action_next();
    }

    static int tmp=0,tmp2=40;
    public void action_play_pause(View v)
    {
        MediaControlLibrary.action_play_pause();

        // reading from serial interface
        //String tmp_str;
        //mainText.setText((String) mainText.getText() + "\nREAD: " + serialInterface.readString());
/*        do
        {
            tmp_str = serialInterface.readString();
            tmp += tmp_str.length();
        }while(tmp_str.length()>0 && tmp<tmp2);
        tmp2=tmp+40;
        mainText.setText(mainText.getText() + "\nREAD: " + tmp + "bytes");
        serialInterface.write("TST".getBytes(),3);
*/


        /*
        wrong way

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent( KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        sendOrderedBroadcast(downIntent, null);
        */
    }

    public void action_prev(View v)
    {
        MediaControlLibrary.action_prev();
    }

    public void start_stop_service(View v)
    {
        if(serviceBound)
        {
            PodEmuLog.debug("Stop service initiated...");
            stop_service(v);
        }
        else
        {
            PodEmuLog.debug("Start service initiated...");
            start_service(v);
        }
    }

    public void start_service(View v)
    {
        // reconnect usb
        serialInterface.init((UsbManager) getSystemService(Context.USB_SERVICE));

        if(serialInterface.isConnected())
        {
            startService(serviceIntent);
            if (bindService(serviceIntent, serviceConnection, BIND_IMPORTANT))
            {
                PodEmuLog.log("Service succesfully bound");
                serviceBound = true;
            } else
            {
                PodEmuLog.log("Service NOT bound");
            }

            updateServiceButton();
        }
        updateSerialStatus();
    }

    public void stop_service(View v)
    {
        unbindService(serviceConnection);
        stopService(serviceIntent);
        serviceBound = false;
        updateSerialStatus();
        updateServiceButton();
    }


    private void updateServiceButton()
    {
        Button srvcButton=(Button) findViewById(R.id.button_start_stop_srvc);
        if(serviceBound)
            srvcButton.setText("STOP SRVC");
        else
            srvcButton.setText("START SRVC");

    }

/*
* 05.273  25767-26208/com.rp.podemu D/PodEmu﹕ Buffer thread started.
09-24 21:56:05.283  25767-26208/com.rp.podemu W/dalvikvm﹕ threadid=11: thread exiting with uncaught exception (group=0x2b4e71f8)
09-24 21:56:05.293  25767-25767/com.rp.podemu D/PodEmu﹕ Service started
09-24 21:56:05.293  25767-25767/com.rp.podemu D/PodEmu﹕ Service bound
09-24 21:56:05.293  25767-26208/com.rp.podemu E/AndroidRuntime﹕ FATAL EXCEPTION: Thread-271
    java.lang.NullPointerException
            at com.hoho.android.usbserial.driver.ProlificSerialDriver$ProlificSerialPort.read(ProlificSerialDriver.java:373)
            at com.rp.podemu.SerialInterface_USBSerial.read(SerialInterface_USBSerial.java:92)
            at com.rp.podemu.PodEmuService$1.run(PodEmuService.java:175)
            at java.lang.Thread.run(Thread.java:856)
09-24 21:56:05.303  25767-26212/com.rp.podemu D/PodEmu﹕ Background thread started.
09-24 21:56:05.353  25767-25767/com.rp.podemu D/RPP﹕ onPause done*/

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        podEmuLog=new PodEmuLog(this);


        // Make scroll view automatically scroll to the bottom
        final ScrollView sv=(ScrollView) this.findViewById(R.id.main_sv);
        sv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
           {
               @Override

               public void onGlobalLayout()
               {
                   sv.post(new Runnable()
                   {

                       public void run()
                       {
                           sv.fullScroll(ScrollView.FOCUS_DOWN);
                       }
                   });
               }
           });

        serialInterface=new SerialInterface_USBSerial();

//        LayoutInflater lif = getLayoutInflater();
//        ViewGroup layout = (ViewGroup)lif.inflate(R.layout.board, null);
        dockingLogoView = (DockingLogoView) findViewById(R.id.dockStationLogo);
//        layout.addView((View)dockingLogoView);

        //    loadPreferences();


        iF.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        iF.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");

        Log.d("RPP", "onCreate");
        registerReceiver(mReceiver, iF);
        this.mainText = (TextView) this.findViewById(R.id.main_text);
        this.serialStatusText = (TextView) this.findViewById(R.id.serial_status_text);
        this.iPodStatusText = (TextView) this.findViewById(R.id.ipod_status_text);

//        registerReceiver(mReceiver, iF);

        //AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        //if (!manager.isMusicActive())
        //{
        //    this.mainText.setText("Not playing...");
        //}

        // Start background service
        serviceIntent = new Intent(this, PodEmuService.class);

        updateSerialStatus();
        updateIPodStatus();

        //looperThread = new LooperThread();
        //looperThread.start();


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

            PodEmuLog.log("(A) Broadcast received: " + cmd + " - " + action);
            if(action.contains("USB_DEVICE_DETACHED"))
            {
                if(serviceBound) stop_service(null);
                serialInterface.close();
                updateSerialStatus();

                iPodConnected=OAPMessenger.IPOD_MODE_DISCONNECTED;
                updateIPodStatus();
            }
            else if(action.contains(BroadcastTypes.SPOTIFY_PACKAGE))
            {
                PodEmuLog.debug("(A) Detected SPOTIFY broadcast");

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

                if(!action.equals(BroadcastTypes.QUEUE_CHANGED))
                {
                    mainText.setText("MEDIA: \n" +
                            "     isPlaying:" + isPlaying + "\n" +
                            "     Event: " + action + "\n" +
                            "     Artist: " + artist + "\n" +
                            "     Album: " + album + "\n" +
                            "     Track: " + track + "\n" +
                            "     ID: " + id + "\n" +
                            "     Length: " + length + "\n" +
                            "     Position: " + position + "\n");
                }

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

                currentlyPlaying.bulk_update(podEmuMessage);

                // don't need to send - service has it's own broadcast receiver
                /*
                if(podEmuService!=null)
                {
                    podEmuService.registerMessage(podEmuMessage);
                }
                */
            }
            else
            {
                // not supported broadcast so exiting
                return;
            }

        }
    };


    private void loadPreferences()
    {
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS",Context.MODE_PRIVATE);
        ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "log loading");

    //    ((TextView) findViewById(R.id.main_text)).setText(ctrlAppProcessName);
    }


    @Override
    protected void onPause() {
        super.onPause();
    //    unregisterReceiver(mReceiver);

        Log.d("RPP", "onPause done");
    }


    @Override
    public void onResume()
    {
        super.onResume();
    //    loadPreferences();
        start_service(null);
        Log.d("RPP", "onResume done");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        PodEmuLog.debug("onDestroy");
        unregisterReceiver(mReceiver);
        //stopService(serviceIntent);

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        // this is main thread looper, so no need to quit()
        //mHandler.getLooper().quit();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_menu_settings) {

            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void updateSerialStatus()
    {
        if(serialInterface.isConnected())
        {
            this.serialStatusText.setText("Serial status: connected");
        }
        else
        {
            this.serialStatusText.setText("Serial status: NOT connected");
        }
    }

    private void updateIPodStatus()
    {
        if(iPodConnected==OAPMessenger.IPOD_MODE_AIR)
        {
            this.iPodStatusText.setText("iPod status: AiR mode");
        }
        else if(iPodConnected==OAPMessenger.IPOD_MODE_SIMPLE)
        {
            this.iPodStatusText.setText("iPod status: simple mode");
        }
        else
        {
            this.iPodStatusText.setText("iPod status: NOT connected");
        }
    }


    // Defines a Handler object that's attached to the UI thread
    //Handler mHandler = new Handler(Looper.getMainLooper());
    myHandler mHandler = new myHandler(this);

    private class myHandler extends Handler
    {
        private final WeakReference<MainActivity> mainActivityWeakReference;

        myHandler(MainActivity context)
        {
            mainActivityWeakReference=new WeakReference<MainActivity>((MainActivity) context);
            //this.Handler(looper);
        }

        /*
        * handleMessage() defines the operations to perform when
        * the Handler receives a new Message to process.
        */
        @Override
        public void handleMessage(Message inputMessage)
        {
            super.handleMessage(inputMessage);
            MainActivity target = mainActivityWeakReference.get();
            // Gets the image task from the incoming Message object.
            //        PhotoTask photoTask = (PhotoTask) inputMessage.obj;
            //mainText.setText(mainText.getText() + "Received MSG");
            switch(inputMessage.arg1)
            {
                case 1: // we received a picture block
                {
                    dockingLogoView.process_picture_block((OAPMessenger.PictureBlock) inputMessage.obj);
                } break;
                case 2: // iPod connection status changed
                {
                    iPodConnected=inputMessage.arg2;
                    updateIPodStatus();
                } break;
            }
        }
    }


    DockingLogoView dockingLogoView;


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection()
    {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            PodEmuService.LocalBinder binder = (PodEmuService.LocalBinder) service;
            podEmuService = binder.getService();
            serviceBound = true;
            podEmuService.setHandler(mHandler);

            podEmuService.registerMessage(currentlyPlaying);
            updateServiceButton();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
            updateServiceButton();
        }
    };


}
