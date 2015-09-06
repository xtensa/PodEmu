package com.rp.podemu;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.renderscript.Script;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;


public class MainActivity extends Activity
{


    private TextView mainText=null;
    private TextView serialStatusText=null;
    private TextView iPodStatusText=null;
    private String ctrlAppProcessName;
    private Intent serviceIntent;
    private SerialInterface serialInterface;
    private IntentFilter iF;

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
        String tmp_str;
        //MediaControlLibrary.action_play_pause();

        //mainText.setText((String) mainText.getText() + "\nREAD: " + serialInterface.readString());
        do
        {
            tmp_str = serialInterface.readString();
            tmp += tmp_str.length();
        }while(tmp_str.length()>0 && tmp<tmp2);
        tmp2=tmp+40;
        mainText.setText(mainText.getText() + "\nREAD: " + tmp + "bytes");
        serialInterface.write("TST".getBytes(),3);

        /*
        String ACTION_USB_PERMISSION = "com.rp.podemu.USB_PERMISSION";
        FTDriver mSerial = new FTDriver((UsbManager) getSystemService(Context.USB_SERVICE));
        // [FTDriver] setPermissionIntent() before begin()
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mSerial.setPermissionIntent(permissionIntent);
        if(mSerial.begin(FTDriver.BAUD115200))
        {
            mainText.setText((String)mainText.getText() + "\nUSB device successfully opened");
            mSerial.write((String)mainText.getText() + "\nPodEmu started...");
            mSerial.end();
        }
        else
        {
            mainText.setText((String)mainText.getText() + "\nCannot open USB device");
        }
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

    public void start_service(View v)
    {
        startService(serviceIntent);
    }

    public void stop_service(View v)
    {
        stopService(serviceIntent);
    }




    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


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
        serialInterface.init((UsbManager) getSystemService(Context.USB_SERVICE));

        //    loadPreferences();

        Log.d("RPP", "onCreate");
        iF = new IntentFilter();
/*        iF.addAction("com.android.music.musicservicecommand");
        iF.addAction("com.android.music.metachanged");
        iF.addAction("com.android.music.playstatechanged");
        iF.addAction("com.android.music.updateprogress");
        iF.addAction("com.android.music.playbackcomplete");
        iF.addAction("com.android.music.queuechanged");

        iF.addAction("com.htc.music.metachanged");
        iF.addAction("com.htc.music.musicservicecommand");
        iF.addAction("com.htc.music.metachanged");
        iF.addAction("com.htc.music.playstatechanged");
        iF.addAction("com.htc.music.updateprogress");
        iF.addAction("com.htc.music.playbackcomplete");
        iF.addAction("com.htc.music.queuechanged");

        iF.addAction("fm.last.android.metachanged");
        iF.addAction("com.sec.android.app.music.metachanged");
        iF.addAction("com.nullsoft.winamp.metachanged");
        iF.addAction("com.amazon.mp3.metachanged");
        iF.addAction("com.miui.player.metachanged");
        iF.addAction("com.real.IMP.metachanged");
        iF.addAction("com.sonyericsson.music.metachanged");
        iF.addAction("com.rdio.android.metachanged");
        iF.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
        iF.addAction("com.andrew.apollo.metachanged");
*/
        // for Spotify
        //iF.addCategory("ComponentInfo");
        //iF.addCategory("com.spotify.mobile.android.service.SpotifyIntentService");
        //iF.addCategory("com.spotify.mobile.android.service.SpotifyService");
        //iF.addAction("com.spotify.mobile.android.ui.widget.SpotifyWidget");
        //iF.addAction("ComponentInfo");
        //iF.addAction("com.spotify");
        //iF.addAction("com.spotify.mobile.android.service.SpotifyIntentService");
        //iF.addAction("com.spotify.mobile.android.service.SpotifyService");
        //iF.addAction("com.spotify.mobile.android.ui");

        iF.addAction("com.spotify.music.playbackstatechanged");
        iF.addAction("com.spotify.music.metadatachanged");
        iF.addAction("com.spotify.music.queuechanged");

        registerReceiver(mReceiver, iF);
        this.mainText = (TextView) this.findViewById(R.id.main_text);
        this.serialStatusText = (TextView) this.findViewById(R.id.serial_status_text);
        this.iPodStatusText = (TextView) this.findViewById(R.id.ipod_status_text);

        registerReceiver(mReceiver, iF);

        //AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        //if (!manager.isMusicActive())
        //{
        //    this.mainText.setText("Not playing...");
        //}

        serviceIntent = new Intent(this, PodEmuService.class);
        updateSerialStatus();
        updateIPodStatus();

    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");

            Log.d("RPP", cmd + " : " + action);

            String artist = intent.getStringExtra("artist");
            String album = intent.getStringExtra("album");
            String track = intent.getStringExtra("track");
            String id = intent.getStringExtra("id");
            int length = intent.getIntExtra("length", 0);
            boolean playing = intent.getBooleanExtra("playing", false);

            Log.d("RPP", playing  + " : " + artist + " : " + album + " : " + track + " : " + id + " : " + length);

            if (!playing)
            {
                mainText.setText(mainText.getText() + "\nMEDIA: Stopped playing.");
            }
            else
            {
                mainText.setText(mainText.getText() + "MEDIA: \n" +
                                 "  Artist: " + artist + "\n" +
                                 "  Album: " + album + "\n" +
                                 "  Track: " + track + "\n" +
                                 "  ID: " + id + "\n" +
                                 "  Length: " + length + "\n");
            }
        }
    };


    private void loadPreferences() {
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS",Context.MODE_PRIVATE);
        ctrlAppProcessName = sharedPref.getString("ControlledAppProcessName", "error loading");

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
        Log.d("RPP", "onDestroy");
        unregisterReceiver(mReceiver);
        //stopService(serviceIntent);

        if(serialInterface!=null) serialInterface.close();
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
        {
            this.iPodStatusText.setText("iPod status: NOT connected");
        }
    }

}
