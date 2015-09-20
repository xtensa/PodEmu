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
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;


public class MainActivity extends AppCompatActivity
{


    private static TextView mainText=null;
    private static TextView serialStatusText=null;
    private static TextView iPodStatusText=null;



    private String ctrlAppProcessName;
    private Intent serviceIntent;
    private SerialInterface serialInterface;
    private IntentFilter iF;
    private PodEmuService podEmuService;
    boolean serviceBound = false;
    private PodEmuLog podEmuLog;


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

    public void start_service(View v)
    {
        startService(serviceIntent);
        if(bindService(serviceIntent, serviceConnection, 0))
        {
            Log.d("RPPService", "Service succesfully bound");
        }
        else
        {
            Log.d("RPPService", "Service NOT bound");

        }

    }

    public void stop_service(View v)
    {
        if (serviceBound)
        {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        stopService(serviceIntent);
    }




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
        serialInterface.init((UsbManager) getSystemService(Context.USB_SERVICE));

//        LayoutInflater lif = getLayoutInflater();
//        ViewGroup layout = (ViewGroup)lif.inflate(R.layout.board, null);
        dockingLogoView = (DockingLogoView) findViewById(R.id.dockStationLogo);
//        layout.addView((View)dockingLogoView);

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


            Log.d("RPP", cmd + " : " + action);
            if(action.contains(BroadcastTypes.SPOTIFY_PACKAGE))
            {
                Log.d("RPP", "Detected SPOTIFY broadcast");

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
            }
            else
            {
                // not supported broadcast so exiting
                return;
            }

            PodEmuMessage podEmuMessage = new PodEmuMessage();
            mainText.setText(mainText.getText() + "MEDIA: \n" +
                             "     isPlaying:" + isPlaying + "\n" +
                             "     Event: " + action + "\n" +
                             "     Artist: " + artist + "\n" +
                             "     Album: " + album + "\n" +
                             "     Track: " + track + "\n" +
                             "     ID: " + id + "\n" +
                             "     Length: " + length + "\n" +
                             "     Position: " + position + "\n");


            podEmuMessage.setAlbum(album);
            podEmuMessage.setArtist(artist);
            podEmuMessage.setTrackName(track);
            podEmuMessage.setTrackID(id);
            podEmuMessage.setLength(length);
            podEmuMessage.setIsPlaying(isPlaying);
            podEmuMessage.setPositionMS(position);
            podEmuMessage.setTimeSent(timeSentInMs);
            podEmuMessage.setAction(action_code);

            if(podEmuService!=null)
            {
                podEmuService.registerMessage(podEmuMessage);
            }
        }
    };


    private void loadPreferences()
    {
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

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        // this is main thread looper, so no need to quit()
        //mHandler.getLooper().quit();

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
            if(inputMessage.arg1 == 1) // we received a picture block
            {
               dockingLogoView.process_picture_block((OAPMessenger.PictureBlock) inputMessage.obj);
            }
        }
    }

    /*
    800-28978/com.rp.podemu D/PodEmu﹕ Line 2: ERROR: first byte is not 0xFF. Received 0x32
        09-18 22:41:54.614  28800-28978/com.rp.podemu D/PodEmu﹕ Line 2: Extended image message detected!!!
        09-18 22:41:54.774  28800-28978/com.rp.podemu W/dalvikvm﹕ threadid=13: thread exiting with uncaught exception (group=0x2b4e71f8)
    09-18 22:41:54.774  28800-28978/com.rp.podemu E/AndroidRuntime﹕ FATAL EXCEPTION: Thread-865
    java.lang.ArrayIndexOutOfBoundsException: length=300; index=300
    at com.rp.podemu.OAPMessenger.oap_receive_byte(OAPMessenger.java:176)
    at com.rp.podemu.PodEmuService$2.run(PodEmuService.java:194)
    at java.lang.Thread.run(Thread.java:856)
        09-18 22:41:54.784  28800-28800/com.rp.podemu D/RPP﹕ onPause done
    09-18 22:41:54.874  28800-28800/com.rp.podemu D/OpenGLRenderer﹕ Flushing caches (mode 0)
    09-18 22:41:55.404  28800-28800/com.rp.podemu D/OpenGLRenderer﹕ Flushing caches (mode 1)
    09-18 22:41:55.404  28800-28800/com.rp.podemu D/RPP﹕ onDestroy
    09-18 22:41:55.424  28800-28977/com.rp.podemu W/dalvikvm﹕ threadid=12: thread exi
*/

/*
    public class DockingLogoView extends View
    {
        private Bitmap mBitmap;
        private Canvas mCanvas;
        Context context;
//        private Path mPath;
        private Paint mPaint;

        public DockingLogoView(Context c, AttributeSet attrs)
        {
            super(c, attrs);
            context = c;
            // we set a new Path
//            mPath = new Path();
            // and we set a new Paint with the desired attributes
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.BLACK);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
//            mPaint.setStrokeWidth(4f);
            mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565);

        }

        public DockingLogoView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);

        }

        public DockingLogoView(Context context) {
            super(context);

        }

        @Override
        public void onDraw(Canvas canvas)
        {
            // TODO Auto-generated method stub
            super.onDraw(canvas);

            int x = getWidth();
            int y = getHeight();
            int radius;
            radius = 100;
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            mCanvas.drawPaint(paint);
            // Use Color.parseColor to define HTML colors
            paint.setColor(Color.parseColor("#CD5C5C"));
            mCanvas.drawCircle(x / 2, y / 2, radius, paint);
        }

        // TODO interpretting image data is better to move to OAPMessenger class
        private void process_picture_block(OAPMessenger.PictureBlock pictureBlock)
        {
            byte data[]=pictureBlock.data;
            int block_number=(data[0]<<8) | data[1];
            int shift=2; // shift to start of image data
            Bitmap mBitmap;
            Canvas mCanvas;
            //mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

            if(block_number==0)
            {
                image_pos_col=0;
                image_pos_row=0;
                image_res_x=(data[3]<<8) | data[4];
                image_res_y=(data[5]<<8) | data[6];
                image_bytes_per_line=(data[7]<<24) | (data[8]<<16) | (data[9]<<8) | data[10];
                shift=11;
            }

        }

    }
*/
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
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
        }
    };


}
