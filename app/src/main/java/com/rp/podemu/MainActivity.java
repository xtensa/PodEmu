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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.media.MediaMetadata;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
//import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.media.session.MediaController;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;


public class MainActivity extends AppCompatActivity
{


    private TextView serialStatusText=null;
    private TextView serialStatusHint=null;
    private TextView dockStatusText=null;
    private TextView ctrlAppStatusTitle=null;
    private TextView ctrlAppStatusText=null;
    private int iPodConnected=OAPMessenger.IPOD_MODE_DISCONNECTED;


    private boolean bluetoothEnabled;
    private boolean bluetoothIsBle;
    private int autoSwitchToApp;
    private Intent serviceIntent;
    private SerialInterfaceBuilder serialInterfaceBuilder;
    private PodEmuIntentFilter iF;
    private PodEmuService podEmuService;
    private boolean serviceBound = false;
    private int REQUEST_ENABLE_BT = 176;
    private boolean btRequestFailed = false;
    public PodEmuMessage currentlyPlaying=new PodEmuMessage();

    private boolean isBtOn = true;

    public boolean isBtOn() { return isBtOn; }



    public void action_next(View v)
    {
        MediaPlayback mediaPlayback=MediaPlayback.getInstance();
        mediaPlayback.action_next();
    }

    public void action_play_pause(View v)
    {
        MediaPlayback mediaPlayback=MediaPlayback.getInstance();
        mediaPlayback.action_play_pause();

    }

    public void action_prev(View v)
    {
        MediaPlayback mediaPlayback=MediaPlayback.getInstance();
        mediaPlayback.action_prev();
    }

    public void start_stop_service(View v)
    {


        if(serviceBound)
        {
            PodEmuLog.debug("MA: Stop service initiated...");
            stop_service(v);
        }
        else
        {
            PodEmuLog.debug("MA: Start service initiated...");
            start_service(v);
        }
    }

    public void start_service(View v)
    {

        try
        {
            SerialInterface serialInterface = serialInterfaceBuilder.getSerialInterface();
                // reconnect usb

            updateSerialStatus();
            if (serialInterface != null || isBtOn)
            {
                startService(serviceIntent);

                if (bindService(serviceIntent, serviceConnection, BIND_IMPORTANT))
                {
                    PodEmuLog.debug("MA: Service successfully bound");
                    serviceBound = true;
                }
                else
                {
                    PodEmuLog.debug("MA: Service NOT bound");
                }

                updateServiceButton();
            }

        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

    }

    public void stop_service(View v)
    {
    /*    try
        {
            iPodConnected = OAPMessenger.IPOD_MODE_DISCONNECTED;
            serialInterfaceBuilder.detach();
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
    */
        PodEmuService.stopService(this);
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
            PodEmuLog.debug("MA: onCreate");
            setContentView(R.layout.activity_main);
            // required to create logdir
            PodEmuLog.initialize(this);
            //PodEmuLog.printSystemInfo();


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
            serialInterfaceBuilder = SerialInterfaceBuilder.getInstance();

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
            serviceIntent  = new Intent(this, PodEmuService.class);
            //listenerIntent = new Intent(this, NotificationService.class);
            //startService(listenerIntent);


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
                String action = intent.getAction();


                if(action.contains(BluetoothDevice.ACTION_ACL_CONNECTED)
                        && (((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getName() == SerialInterface_BT.getInstance().getName()))
                {
                    PodEmuLog.debug("MA: Bluetooth device '" + SerialInterface_BT.getInstance().getName() + "' connected.");
                    start_service(null);
                }
                else
                {
                    PodEmuLog.debug("MA: broadcast processing requested");
                    PodEmuMessage podEmuMessage = PodEmuIntentFilter.processBroadcast(context, intent);
                    // if null is received then broadcast could be not from "our" app
                    if (podEmuMessage != null)
                    {
                        PodEmuLog.debug("MA: received PodEmuMessage");
                        currentlyPlaying.bulk_update(podEmuMessage);
                        if (podEmuMessage.getAction() != PodEmuMessage.ACTION_QUEUE_CHANGED)
                        {
                            updateCurrentlyPlayingDisplay();
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
    };


    private void loadPreferences()
    {
        try
        {

            SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
            autoSwitchToApp = sharedPref.getInt("autoSwitchToApp", 0);
            bluetoothEnabled=(sharedPref.getInt("bluetoothEnabled", 0)!=0);
            bluetoothIsBle = sharedPref.getBoolean("bluetoothIsBle", false);
            String enableDebug = sharedPref.getString("enableDebug", "false");
            Boolean playlistCountModeUpdated=sharedPref.getBoolean("PlaylistCountModeUpdated", false);
            boolean enableTranslit = sharedPref.getBoolean("CyrillicTransliteration", false);
            int forceSimpleMode = sharedPref.getInt("ForceSimpleMode", 0);
            int playlistCountMode=sharedPref.getInt("PlaylistCountMode", PodEmuMediaStore.MODE_PLAYLIST_SIZE_DEFAULT);
            SettingsActivity.logoDownloadBehaviour = sharedPref.getInt("LogoDownloadBehaviour", SettingsActivity.LOGO_DOWNLOAD_COLOR);

            if(PodEmuMediaStore.getInstance()==null)
            {
                PodEmuMediaStore.initialize(this);
            }


            if(playlistCountModeUpdated)
            {
                PodEmuMediaStore.getInstance().setPlaylistCountMode(playlistCountMode);
            }

            if (enableDebug.equals("true"))
                PodEmuLog.debug_level = PodEmuLog.LOGLEVEL_DEFAULT;
            else
                PodEmuLog.debug_level = PodEmuLog.LOGLEVEL_DISABLED;

            if (podEmuService != null)
            {
                podEmuService.reloadSettings();
                podEmuService.setForceSimpleMode(forceSimpleMode);
            }

            String ctrlApp = PodEmuMediaStore.getInstance().getCtrlAppProcessName();
            if(ctrlApp==null) ctrlApp = "unknown";
            // the next line is required to initialize MediaStore and DB
            PodEmuMediaStore.getInstance().setCtrlAppProcessName(ctrlApp);

            currentlyPlaying.bulk_update(MediaPlayback.getInstance().getCurrentPlaylist().getCurrentTrack().toPodEmuMessage());
            currentlyPlaying.setEnableCyrillicTransliteration(enableTranslit);

            updateCurrentlyPlayingDisplay();

        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }


    }

    private void updateAppInfo()
    {
        PackageManager pm = getPackageManager();
        ApplicationInfo appInfo;
        String appName = "unknown";
        MediaController mediaController = MediaPlayback.getActiveMediaController();
        Bitmap trackIcon = null;

        if(mediaController != null)
        {
            trackIcon = mediaController.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (trackIcon == null)
                trackIcon = mediaController.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (trackIcon == null)
                trackIcon = mediaController.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        else
        {
            PodEmuLog.debug("MA: cannot load icon because mediaController is not initialized");
        }

        if(currentlyPlaying==null) return;
        appName = currentlyPlaying.getApplication();

        PodEmuLog.debug("MA: setting AppInfo to " + appName);


            try
            {
                appInfo = pm.getApplicationInfo(appName, PackageManager.GET_META_DATA);
                ctrlAppStatusTitle.setText("App: " + appInfo.loadLabel(pm));
                ctrlAppStatusTitle.setTextColor(Color.rgb(0xff, 0xff, 0xff));

                if(trackIcon != null)
                    setAppLogo(new BitmapDrawable(getResources(), trackIcon) );
                else
                    setAppLogo(appInfo.loadIcon(pm));

            }
            catch (PackageManager.NameNotFoundException e)
            {
                ctrlAppStatusTitle.setText("Unknown app");
                ctrlAppStatusTitle.setTextColor(Color.rgb(0xff, 0x00, 0x00));

                setAppLogo(getDrawable(R.drawable.questionmark));
            }

    }

    private void setAppLogo(Drawable drawable)
    {
        ImageView appLogo = findViewById(R.id.CTRL_app_icon);
        appLogo.setImageDrawable( drawable);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        try
        {
            unregisterReceiver(mReceiver);

            PodEmuLog.debug("MA: onPause done");
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
            //if( bluetoothEnabled )
            {
                start_service(null);
            }

            iF = new PodEmuIntentFilter();
            iF.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            iF.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

            registerReceiver(mReceiver, iF);

            PodEmuLog.printSystemInfo();

            //Intent intent = new Intent(PodEmuIntentFilter.INTENT_ACTION_NOTIFY_MAIN_ACTIVITY_RESUMED,
            //                            null, getApplicationContext(), PodEmuService.class);
            //sendBroadcast(intent);

            PodEmuLog.debug("MA: onResume done");
        }
        catch(Exception e)
        {
            PodEmuLog.printStackTrace(e);
            throw e;
        }

        if ( bluetoothEnabled && !btRequestFailed)
        {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null)
            {
                //Does not support Bluetooth
                PodEmuLog.debug("MA: bluetooth is not supported.");
            }
            else
            {
                if (!bt.isEnabled())
                {

                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                SharedPreferences sharedPref = getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
                boolean firstTimeWarning = sharedPref.getBoolean("firstTimeMainActivityBleWarning", true);

                if(bluetoothEnabled && bluetoothIsBle && firstTimeWarning)
                {
                    // needed to set the context
                    SerialInterface_BLE.getInstance(getBaseContext());
                    SerialInterface_BLE.checkLocationPermissions(this);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean("firstTimeMainActivityBleWarning", false);
                    editor.apply();
                }
            }
        }

        ComponentName componentName = new ComponentName(getApplicationContext(), NotificationListener3.class);
        MediaSessionManager mediaSessionManager = (MediaSessionManager)getApplicationContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<MediaController> mediaControllerList = null;

        try
        {
            mediaControllerList = mediaSessionManager.getActiveSessions(componentName);
        }
        catch(Exception e)
        {
            PodEmuLog.error("MA: Notification Listener permissions not granted");
        }
        if(mediaControllerList == null)
        {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("To display information about currently playing track PodEmu need to have access to Notifications. Please enable it on the next screen.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        }
                    })

                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
        }

    }

    @Override
    protected void onPostResume()
    {
        super.onPostResume();

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_ENABLE_BT)
        {
            if (resultCode != RESULT_OK)
            {
                btRequestFailed = true;
            }
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
            PodEmuLog.debug("MA: onDestroy");
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
        // we don't want to initialize serial interface here - just get the handler
        SerialInterface serialInterface = serialInterfaceBuilder.getSerialInterface();

        ImageView serialInterfaceImage = (ImageView) this.findViewById(R.id.SERIAL_status_icon);

        if(serialInterface != null)
        {
            if(serialInterface.isConnected())
            {
                this.serialStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
                this.serialStatusText.setText("connected");

                if (serialInterface instanceof SerialInterface_BT || serialInterface instanceof SerialInterface_BLE)
                {
                    serialInterfaceImage.setImageDrawable(getDrawable(R.drawable.bluetooth));
                    this.serialStatusHint.setText(String.format("Bluetooth adapter\nName: " + serialInterface.getName() +
                            "\nMAC: " + serialInterface.getAddress()));
                } else
                {
                    this.serialStatusHint.setText(
                            String.format("VID: 0x%04X, ", serialInterface.getVID()) +
                                    String.format("PID: 0x%04X\n", serialInterface.getPID()) +
                                    "Cable: " + serialInterface.getName());
                    serialInterfaceImage.setImageDrawable(getDrawable(R.drawable.usb_serial_480x480));
                }

                // once service is bound we can launch controlled app
                if (!podEmuService.isAppLaunched && autoSwitchToApp==1)
                {
                    launchControlledApp(null);
                    podEmuService.isAppLaunched = true;
                }
            }
            else if(serialInterface.isConnecting())
            {
                this.serialStatusText.setTextColor(Color.rgb(0xff, 0x96, 0x00));
                this.serialStatusText.setText("connecting");
            }
        }
        else
        {
            podEmuService.isAppLaunched = false;
            this.serialStatusText.setTextColor(Color.rgb(0xff,0x00,0x00));
            this.serialStatusText.setText("disconnected");

            this.serialStatusHint.setText(R.string.serial_status_hint);
            serialInterfaceImage.setImageDrawable(getDrawable(R.drawable.usb_serial_480x480));
        }


    }

    private void updateIPodStatus()
    {
        try
        {
            if (iPodConnected == OAPMessenger.IPOD_MODE_AIR)
            {
                String dockStatus = "AiR Mode";
                if(SerialInterfaceBuilder.getSerialInterface() != null &&
                        SerialInterfaceBuilder.getSerialInterface().getAccessoryName() != null)
                    dockStatus += "\n" + SerialInterfaceBuilder.getSerialInterface().getAccessoryName();

                this.dockStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
                this.dockStatusText.setText(dockStatus);
                if (podEmuService.isDockIconLoaded)
                {
                    dockingLogoView.setBitmap(podEmuService.dockIconBitmap);
                }
            }
            else if (iPodConnected == OAPMessenger.IPOD_MODE_SIMPLE)
            {
                this.dockStatusText.setTextColor(Color.rgb(0x00, 0xff, 0x00));
                this.dockStatusText.setText("simple mode");
            }
            else // docking station disconnected
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
        if(!currentlyPlaying.isInitialized()) return;
        String text =
                "Artist: " + currentlyPlaying.getArtist() + "\n" +
                " Album: " + currentlyPlaying.getAlbum() + "\n" +
                " Track: " + currentlyPlaying.getTrackName() + "\n" +
                "Length: " + currentlyPlaying.getLengthHumanReadable() +
                        (currentlyPlaying.isPlaying()?" (playing)":" (paused)") + "\n" +
                //"Track NR: " + MediaPlayback.getInstance().getCurrentPlaylist().getCurrentTrackPos() + "/" +
                //        MediaPlayback.getInstance().getCurrentPlaylist().getTrackCount() + "\n" +
                "";
        PodEmuLog.debug("MA: currently playing\n" + text + "   App: " + currentlyPlaying.getApplication());
        ctrlAppStatusText.setText(text);
        PodEmuMediaStore.getInstance().setCtrlAppProcessName(currentlyPlaying.getApplication());

        updateAppInfo();

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
                podEmuService.setMediaEngine();

                if (currentlyPlaying.getTrackName() != null)
                {
                    // update service only if we have this information
                    // otherwise we can overwrite information that service already has (eg. if we are rebinding)
                    podEmuService.registerMessage(currentlyPlaying);
                }
                else
                {
                    PodEmuLog.error("UPDATE in MA on service connected Title: " + podEmuService.getCurrentlyPlaying().getTrackName());
                    // otherwise update currently playing
                    currentlyPlaying.bulk_update(podEmuService.getCurrentlyPlaying());
                    updateCurrentlyPlayingDisplay();
                }
                updateServiceButton();

                if (podEmuService.dockIconBitmap != null)
                {
                    dockingLogoView.setResizedBitmap(podEmuService.dockIconBitmap);
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
            Intent intent = getPackageManager().getLaunchIntentForPackage(currentlyPlaying.getApplication());
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
