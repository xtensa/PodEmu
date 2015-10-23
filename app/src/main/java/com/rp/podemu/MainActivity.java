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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;


public class MainActivity extends AppCompatActivity
{


    private TextView serialStatusText=null;
    private TextView serialStatusHint=null;
    private TextView dockStatusText=null;
    private TextView ctrlAppStatusTitle=null;
    private TextView ctrlAppStatusText=null;
    private PodEmuLog podEmuLog;
    private int iPodConnected=OAPMessenger.IPOD_MODE_DISCONNECTED;


    private String ctrlAppProcessName;
    private Intent serviceIntent;
    private SerialInterface serialInterface;
    private PodEmuIntentFilter iF;
    private PodEmuService podEmuService;
    private boolean serviceBound = false;
    public PodEmuMessage currentlyPlaying=new PodEmuMessage();

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

    public void action_play_pause(View v)
    {
        MediaControlLibrary.action_play_pause();

    }

    public void action_prev(View v)
    {
        MediaControlLibrary.action_prev(0);
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

        try
        {
            // reconnect usb
            serialInterface.init((UsbManager) getSystemService(Context.USB_SERVICE));

            if (serialInterface.isConnected())
            {
                startService(serviceIntent);

                if (bindService(serviceIntent, serviceConnection, BIND_IMPORTANT))
                {
                    PodEmuLog.debug("Service succesfully bound");
                    serviceBound = true;
                } else
                {
                    PodEmuLog.debug("Service NOT bound");
                }

                updateServiceButton();
            }
            updateSerialStatus();
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }

    public void stop_service(View v)
    {
        try
        {
            iPodConnected = OAPMessenger.IPOD_MODE_DISCONNECTED;
            unbindService(serviceConnection);
            stopService(serviceIntent);
            serviceBound = false;
            updateSerialStatus();
            updateServiceButton();
            updateIPodStatus();
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }


    private void updateServiceButton()
    {
        Button srvcButton=(Button) findViewById(R.id.button_start_stop_srvc);
        if(serviceBound)
            srvcButton.setText("STOP SRVC");
        else
            srvcButton.setText("START SRVC");

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        try
        {
            PodEmuLog.debug("onCreate");
            setContentView(R.layout.activity_main);
            // required to create logdir
            podEmuLog = new PodEmuLog(this);
            podEmuLog.printSystemInfo();


            // Make scroll view automatically scroll to the bottom
/*        final ScrollView sv=(ScrollView) this.findViewById(R.id.main_sv);
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
*/
            serialInterface = new SerialInterface_USBSerial();

//        LayoutInflater lif = getLayoutInflater();
//        ViewGroup layout = (ViewGroup)lif.inflate(R.layout.board, null);
            dockingLogoView = (DockingLogoView) findViewById(R.id.dockStationLogo);
//        layout.addView((View)dockingLogoView);

//        loadPreferences();

            this.ctrlAppStatusTitle = (TextView) this.findViewById(R.id.CTRL_app_status_title);
            this.ctrlAppStatusText = (TextView) this.findViewById(R.id.CTRL_app_status_text);
            this.serialStatusText = (TextView) this.findViewById(R.id.SERIAL_status_text);
            this.serialStatusHint = (TextView) this.findViewById(R.id.SERIAL_status_hint);
            this.dockStatusText = (TextView) this.findViewById(R.id.DOCK_status_text);

            //AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            //if (!manager.isMusicActive())
            //{
            //    this.mainText.setText("Not playing...");
            //}

            // Start background service
            serviceIntent = new Intent(this, PodEmuService.class);

            updateSerialStatus();
            updateIPodStatus();
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;

            setTitle(getTitle() + " " + version);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            // do nothing
        }
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            try
            {
                PodEmuMessage podEmuMessage = PodEmuIntentFilter.processBroadcast(context, intent);
                if (podEmuMessage.getAction() != PodEmuMessage.ACTION_QUEUE_CHANGED)
                {
                    updateCurrentlyPlayingDisplay();
                }
                currentlyPlaying.bulk_update(podEmuMessage);
            }
            catch(Exception e)
            {
                PodEmuLog.printStackTrace(e);
                throw e;
            }

        }
    };


    private void loadPreferences()
    {
        try
        {
            ImageView appLogo = (ImageView) findViewById(R.id.CTRL_app_icon);
            SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "unknown app");
            String enableDebug = sharedPref.getString("enableDebug", "false");
            Boolean ctrlAppUpdated = sharedPref.getBoolean("ControlledAppUpdated", false);

            MediaControlLibrary.ctrlAppProcessName=ctrlAppProcessName;


            if (enableDebug.equals("true"))
                PodEmuLog.DEBUG_LEVEL = 2;
            else
                PodEmuLog.DEBUG_LEVEL = 0;

            if (podEmuService != null)
            {
                podEmuService.reloadBaudRate();
            }

            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo;

            try
            {
                appInfo = pm.getApplicationInfo(ctrlAppProcessName, PackageManager.GET_META_DATA);

                ctrlAppStatusTitle.setText("Controlled app: " + appInfo.loadLabel(pm));
                ctrlAppStatusTitle.setTextColor(Color.rgb(0xff, 0xff, 0xff));

                if(ctrlAppUpdated && currentlyPlaying.isPlaying())
                {
                    // invoke play_pause button to switch the app
                    MediaControlLibrary.action_play_pause();
                }
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("ControlledAppUpdated", false);
                editor.apply();

                appLogo.setImageDrawable(appInfo.loadIcon(pm));
            } catch (PackageManager.NameNotFoundException e)
            {
                ctrlAppStatusTitle.setText("Please go to the settings and setup controlled music application");
                ctrlAppStatusTitle.setTextColor(Color.rgb(0xff, 0x00, 0x00));

                appLogo.setImageDrawable(ContextCompat.getDrawable(this, (R.drawable.questionmark)));
            }
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }


    }


    @Override
    protected void onPause()
    {
        super.onPause();

        try
        {
            unregisterReceiver(mReceiver);

            PodEmuLog.debug("onPause done");
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }


    @Override
    public void onResume()
    {
        super.onResume();

        try
        {
            loadPreferences();
            start_service(null);

            iF = new PodEmuIntentFilter(ctrlAppProcessName);
            iF.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            iF.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
            registerReceiver(mReceiver, iF);

            MediaControlLibrary.context=this;

            PodEmuLog.debug("onResume done");
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

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

        try
        {
            PodEmuLog.debug("onDestroy");
            //unregisterReceiver(mReceiver);
            //stopService(serviceIntent);

            if (serviceBound)
            {
                unbindService(serviceConnection);
                serviceBound = false;
            }

            // this is main thread looper, so no need to quit()
            //mHandler.getLooper().quit();
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

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
            this.serialStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
            this.serialStatusText.setText("connected");

            this.serialStatusHint.setText(
                    String.format("VID: 0x%04X, ", serialInterface.getVID()) +
                            String.format("PID: 0x%04X\n", serialInterface.getPID()) +
                            "Cable: " + serialInterface.getName());
        }
        else
        {
            this.serialStatusText.setTextColor(Color.rgb(0xff,0x00,0x00));
            this.serialStatusText.setText("disconnected");

            this.serialStatusHint.setText(R.string.serial_status_hint);
        }
    }

    private void updateIPodStatus()
    {
        try
        {
            if (iPodConnected == OAPMessenger.IPOD_MODE_AIR)
            {
                this.dockStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
                this.dockStatusText.setText("AiR mode");
                if (podEmuService.isDockIconLoaded)
                {
                    dockingLogoView.setBitmap(podEmuService.dockIconBitmap);
                }
            } else if (iPodConnected == OAPMessenger.IPOD_MODE_SIMPLE)
            {
                this.dockStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
                this.dockStatusText.setText("simple mode");
            } else // docking station disconnected
            {
                this.dockStatusText.setTextColor(Color.rgb(0xff, 0x00, 0x00));
                this.dockStatusText.setText("disconnected");

                if (dockingLogoView != null)
                {
                    dockingLogoView.resetBitmap();
                    if (podEmuService != null)
                    {
                        podEmuService.dockIconBitmap = dockingLogoView.getResizedBitmap();
                    }
                }
            }
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }

    private void updateCurrentlyPlayingDisplay()
    {
        ctrlAppStatusText.setText(
                "Artist: " + currentlyPlaying.getArtist() + "\n" +
                " Album: " + currentlyPlaying.getAlbum() + "\n" +
                " Track: " + currentlyPlaying.getTrackName() + "\n" +
                "Length: " + currentlyPlaying.getLengthHumanReadable() + "\n"
        );

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

            try
            {
                MainActivity target = mainActivityWeakReference.get();
                // Gets the image task from the incoming Message object.
                //        PhotoTask photoTask = (PhotoTask) inputMessage.obj;
                //mainText.setText(mainText.getText() + "Received MSG");
                switch (inputMessage.arg1)
                {
                    case 1: // we received a picture block
                    {
                        dockingLogoView.setBitmap((Bitmap) inputMessage.obj);
                        podEmuService.dockIconBitmap = dockingLogoView.getResizedBitmap();
                        podEmuService.isDockIconLoaded = true;
                    }
                    break;
                    case 2: // dock station connection status changed
                    {
                        iPodConnected = inputMessage.arg2;
                        updateIPodStatus();
                    }
                    break;
                    case 3: // serial connection status changed
                    {
                        updateSerialStatus();
                    }
                    break;
                }
            }
            catch(Exception e)
            {
                PodEmuLog.printStackTrace(e);
                throw e;
            }

        }
    }


    DockingLogoView dockingLogoView;


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection()
    {


        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service)
        {
            try
            {
                // We've bound to LocalService, cast the IBinder and get LocalService instance
                PodEmuService.LocalBinder binder = (PodEmuService.LocalBinder) service;
                podEmuService = binder.getService();
                serviceBound = true;
                podEmuService.setHandler(mHandler);

                if (currentlyPlaying.getTrackName() != null)
                {
                    // update service only if we have this information
                    // otherwise we can overwrite information that service already has (eg. if we are rebinding)
                    podEmuService.registerMessage(currentlyPlaying);
                } else
                {
                    // otherwise update currently playing
                    currentlyPlaying.bulk_update(podEmuService.getCurrentlyPlaying());
                    updateCurrentlyPlayingDisplay();
                }
                updateServiceButton();

                if (podEmuService.dockIconBitmap != null)
                {
                    dockingLogoView.setResizedBitmap(podEmuService.dockIconBitmap);
                }


                // once service is bound we can launch controlled app
                if (!podEmuService.isAppLaunched)
                {
                    launchControlledApp(null);
                    podEmuService.isAppLaunched = true;
                }
            }
            catch(Exception e)
            {
                PodEmuLog.printStackTrace(e);
                throw e;
            }

        }


        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
            updateServiceButton();
        }
    };

    public void launchControlledApp(View v)
    {
        try
        {
            Intent intent = getPackageManager().getLaunchIntentForPackage(ctrlAppProcessName);
            if(intent!=null)
                startActivity(intent);
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }

}
