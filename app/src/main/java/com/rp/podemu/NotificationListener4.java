package com.rp.podemu;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

@TargetApi(21)
/*
 * Due to bug with service caching this class should be renamed before each deployment in order
 * changes in this class to be applied.
 * Otherwise old service is cached and updated service is not loaded into memory
 */
public class NotificationListener4 extends NotificationListenerService
{

    static String TAG="PENL";
    private MediaSessionManager mediaSessionManager;
    private ComponentName componentName;


    MediaSessionManager.OnActiveSessionsChangedListener onActiveSessionChangedListener = new SessionChangedListener(this);
    //Runnable runnableSessionChangeThread = new SessionChangeThread(this);


    class SessionChangedListener implements MediaSessionManager.OnActiveSessionsChangedListener
    {
        /* renamed from: a */
        final /* synthetic */ NotificationListener4 notificationListenerTMP1;

        SessionChangedListener(NotificationListener4 notificationListener)
        {
            this.notificationListenerTMP1 = notificationListener;
        }

        public void onActiveSessionsChanged(List<MediaController> list)
        {
            PodEmuLog.debug(TAG + ": onActiveSessionsChanged");
            /*
            if (playerFragment.f13549e != null && playerFragment.f13549e.equals("NotificationListener"))
            {
                this.notificationListenerTMP1.handler.removeCallbacks(this.notificationListenerTMP1.runnableSessionChangeThread);
                this.notificationListenerTMP1.handler.postDelayed(this.notificationListenerTMP1.runnableSessionChangeThread, 2000);
            }
            */

            notificationListenerTMP1.parseActiveSessions(list);
        }
    }

    class SessionChangeThread implements Runnable
    {
        /* renamed from: a */
        final /* synthetic */ NotificationListener4 notificationListenerTMP2;

        SessionChangeThread(NotificationListener4 notificationListener)
        {
            this.notificationListenerTMP2 = notificationListener;
        }

        public void run()
        {
            PodEmuLog.debug(TAG + ": SessionChangeThread - execution");
            if (this.notificationListenerTMP2.mediaSessionManager != null)
            {
                this.notificationListenerTMP2.parseActiveSessions
                        (this.notificationListenerTMP2.mediaSessionManager.getActiveSessions(this.notificationListenerTMP2.componentName));
                /*
                this.notificationListenerTMP2.mediaController = this.notificationListenerTMP2.parseActiveSessions
                        (this.notificationListenerTMP2.mediaSessionManager.getActiveSessions(this.notificationListenerTMP2.componentName));
                if (this.notificationListenerTMP2.mediaController != null)
                {
                    this.notificationListenerTMP2.mediaController.registerCallback(this.notificationListenerTMP2.f3931c);
                    this.notificationListenerTMP2.mediaMetadata = this.notificationListenerTMP2.mediaController.getMetadata();
                    this.notificationListenerTMP2.m5032a();
                }
                */
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification)
    {
        PodEmuLog.debug(TAG + ": notification received " + statusBarNotification.toString());
        //PodEmuLog.log( TAG + ":    TICKER : " + statusBarNotification.getNotification().tickerText.toString());

        parseActiveSessions();
        super.onNotificationPosted(statusBarNotification);
    }

    private void parseActiveSessions()
    {
        parseActiveSessions(mediaSessionManager.getActiveSessions(getComponentName()));

    }

    public void parseActiveSessions(List<MediaController> mediaControllersList)
    {
        boolean sessionsFound = false;
        String application="";
        String trackTitle="";
        String trackArtist="";
        String trackAlbum="";
        Long   trackDuration = Long.valueOf(-1);
        Long   trackPosition = Long.valueOf(-1);
        int    trackNum = -1;
        int    trackCount = -1;
        Boolean isPlaying = false;
        Long   timeSent = System.currentTimeMillis();


        /*
        Bundle extras = statusBarNotification.getNotification().extras;
        trackTitle  = extras.getString("android.title");
        trackAlbum  = extras.getString("android.subText");
        trackArtist = extras.getString("android.text");
        */



        //=================================================

        int selectedID = 0;
        for(int i=0;i<mediaControllersList.size();i++)
        {
            MediaController mediaController = mediaControllersList.get(i);

            PodEmuLog.debug(TAG + ": === === SESSION " + i);
            if(mediaController.getMetadata()!=null)
            {
                application = mediaController.getPackageName();

                isPlaying = (mediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);

                trackTitle = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
                if (trackTitle == null)
                    trackTitle = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);

                trackAlbum = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM);
                if (trackAlbum == null)
                    trackAlbum = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_COMPILATION);

                trackArtist = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (trackArtist == null)
                    trackArtist = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);

                trackDuration = mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                trackPosition = mediaController.getPlaybackState().getPosition();

                trackNum = (int) mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                trackCount = (int) mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);
            }

            PodEmuLog.debug(TAG + ":    APPLICATION : " + application);
            PodEmuLog.debug(TAG + ":          TITLE : " + trackTitle);
            PodEmuLog.debug(TAG + ":          ALBUM : " + trackAlbum);
            PodEmuLog.debug(TAG + ":         ARTIST : " + trackArtist);
            PodEmuLog.debug(TAG + ":       DURATION : " + trackDuration);
            PodEmuLog.debug(TAG + ":       POSITION : " + trackPosition);
            PodEmuLog.debug(TAG + ":    PLAY STATUS : " + isPlaying);
            PodEmuLog.debug(TAG + ":    TRACK COUNT : " + trackCount);
            PodEmuLog.debug(TAG + ":   TRACK NUMBER : " + trackNum);

            if(!sessionsFound && application != null)
            {
                selectedID = i;
                sessionsFound = true;
            }

        }
        PodEmuLog.debug(TAG + ":   Selected session : " + selectedID);
        //=================================================




        if(mediaControllersList != null && mediaControllersList.size()>0)
        {
            MediaController mediaController = mediaControllersList.get(selectedID);
            String applicationTMP = mediaController.getPackageName();

            if(mediaController.getMetadata()!=null)
            {
                application = applicationTMP;

                isPlaying  = (mediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);

                trackTitle = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
                if (trackTitle == null)
                    trackTitle = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);

                trackAlbum = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM);
                if(trackAlbum==null)
                    trackAlbum = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_COMPILATION);

                trackArtist = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (trackArtist == null)
                    trackArtist = mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);

                trackDuration = mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                trackPosition = mediaController.getPlaybackState().getPosition();

                trackNum   = (int)mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                trackCount = (int)mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);

            }

        }

        if(!sessionsFound)
        {
            PodEmuLog.debug(TAG + ":  APPLICATION : " + application);
            PodEmuLog.debug(TAG + ": EMPTY OR NOT ACTIVE SESSION");
        }

        /*
                Bitmap id = sbn.getNotification().largeIcon;
          if(id != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            id.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            msgrcv.putExtra("icon",byteArray);
        }
         */
        if( sessionsFound )
        {
            if(trackCount==0)
            {
                trackCount = -1;
                trackNum   = -1;
            }

            /*
            PodEmuLog.debug(TAG + ":    APPLICATION : " + application);
            PodEmuLog.debug(TAG + ":          TITLE : " + trackTitle);
            PodEmuLog.debug(TAG + ":          ALBUM : " + trackAlbum);
            PodEmuLog.debug(TAG + ":         ARTIST : " + trackArtist);
            PodEmuLog.debug(TAG + ":       DURATION : " + trackDuration);
            PodEmuLog.debug(TAG + ":       POSITION : " + trackPosition);
            PodEmuLog.debug(TAG + ":    PLAY STATUS : " + isPlaying);
            PodEmuLog.debug(TAG + ":    TRACK COUNT : " + trackCount);
            PodEmuLog.debug(TAG + ":   TRACK NUMBER : " + trackNum);
            */

            PodEmuMessage podEmuMessage = new PodEmuMessage();
            podEmuMessage.setApplication(application);
            podEmuMessage.setTrackName(trackTitle);
            podEmuMessage.setAlbum(trackAlbum);
            podEmuMessage.setArtist(trackArtist);
            podEmuMessage.setDurationMS(trackDuration);
            podEmuMessage.setPositionMS(trackPosition);
            podEmuMessage.setIsPlaying(isPlaying);
            podEmuMessage.setListSize(trackCount);
            podEmuMessage.setListPosition(trackNum);
            podEmuMessage.setAction(PodEmuMessage.ACTION_METADATA_CHANGED);
            podEmuMessage.setTimeSent(timeSent);

            Intent intent = new Intent();
            intent.setAction(PodEmuIntentFilter.INTENT_ACTION_METADATA_CHANGED);
            intent.putExtra("PodEmuMessage", podEmuMessage);
            PodEmuLog.debug(TAG + ": sending internal broadcast with PodEmuMessage");
            sendBroadcast(intent);
        }
        else
        {
            PodEmuLog.log(TAG + ": No sessions found.");
        }
    }


    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification)
    {
        PodEmuLog.debug(TAG + ": notification removed" + statusBarNotification.toString());
        parseActiveSessions();
        super.onNotificationRemoved(statusBarNotification);
    }

    @Override public void onCreate()
    {


        PodEmuLog.debug(TAG + ": listener class created");

        mediaSessionManager = (MediaSessionManager)getSystemService(Context.MEDIA_SESSION_SERVICE);
        //mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener,componentName);
        /*
        List<MediaController> mediaControllerList = mediaSessionManager.getActiveSessions(componentName);
        mediaController = pickMediaController(mediaControllerList);
        if(mediaController != null) {
            mediaController.registerCallback(mediaControllerCallback);
            mediaMetadata = mediaController.getMetadata();
        }
*/
        super.onCreate();
    }

    @Override
    public void onListenerConnected()
    {

        PodEmuLog.debug(TAG + ": XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX listener connected for " + this.getClass() );

        mediaSessionManager.addOnActiveSessionsChangedListener(onActiveSessionChangedListener, getComponentName());

        parseActiveSessions();
        super.onListenerConnected();

    }
/*
    MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> list)
        {
            /*mediaController = pickMediaController(list);
            if(mediaController == null) {
                return;
            }
            mediaController.registerCallback(mediaControllerCallback);
            mediaMetadata = mediaController.getMetadata();
            *
            PodEmuLog.log(TAG + ": onActiveSessionsChanged" );
        }
    };
*/


    @Override
    public int onStartCommand(Intent i, int startId, int i2)
    {
        PodEmuLog.debug(TAG + ": onStartCommand");


        return START_STICKY;
    }

    private ComponentName getComponentName()
    {
        if(this.componentName==null)
        {
            this.componentName = new ComponentName(this.getApplicationContext(), NotificationListener4.class);
        }
        return this.componentName;
    }



}
