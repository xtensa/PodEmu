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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;

/**
 * Created by rp on 10/31/15.
 */
public abstract class MediaPlayback
{
    private static MediaPlayback instance=null;

    protected static Context context=null;
    protected static String ctrlAppProcessName=null;
    //protected static String ctrlAppDbName=null;
    protected final static long POLLING_INTERVAL_TARGET = 500;
    protected static long pollingInterval = POLLING_INTERVAL_TARGET;

    private long lastPrevExecuted=System.currentTimeMillis();


    public static MediaController getActiveMediaController()
    {
        ComponentName componentName = new ComponentName(context, NotificationListener4.class);

        MediaSessionManager mediaSessionManager = (MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<MediaController> mediaControllerList = null;

        try
        {
            mediaControllerList = mediaSessionManager.getActiveSessions(componentName);

            for(MediaController mediaController: mediaControllerList)
            {
                if(mediaController.getPackageName().equals(ctrlAppProcessName)) return mediaController;
            }
        }
        catch(Exception e)
        {
            PodEmuLog.error("MPlayback: Notification Listener permissions not granted");
        }

        return null;
    }

    public static MediaPlayback getInstance()
    {
        if(instance==null) PodEmuLog.error("MPlayback: instance access before initialization");
        return instance;
    }

    public static void setCtrlAppProcessName(String app)
    {
        ctrlAppProcessName = app;
    }

    public static void initialize(Context c)
    {
        context=c;

        //ctrlAppDbName = appDbName;
        if(instance==null) instance = new MediaPlayback_Generic();

    }


    public abstract void setCurrentPlaylist(PodEmuMediaStore.Playlist playlist);
    public abstract PodEmuMediaStore.Playlist getCurrentPlaylist();

    public abstract boolean getTrackStatusChanged();

    public abstract void    updateCurrentlyPlayingTrack(PodEmuMessage podEmuMessage);

    public abstract boolean isPlaying();

    public abstract long getCurrentTrackPositionMS();

    public static long getPollingInteval()
    {
        return pollingInterval;
    }

    public void execute_action(int keyCode)
    {
        Intent intent;
        KeyEvent keyEvent;

        if(ctrlAppProcessName == null)
        {
            PodEmuLog.error("MPlayback: media control attempt before ctrlAppProcessName");
        }
        PodEmuLog.debug("MPlayback: executing action for " + ctrlAppProcessName);

        intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setPackage(ctrlAppProcessName);
        keyEvent = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);

        intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setPackage(ctrlAppProcessName);
        keyEvent = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);

    }

    public void execute_action_long_press(int keyCode) {
        Intent intent;
        KeyEvent keyEvent;

        intent  = new Intent(Intent.ACTION_MEDIA_BUTTON);
        keyEvent = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0);
        keyEvent = KeyEvent.changeFlags(keyEvent, KeyEvent.FLAG_LONG_PRESS);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);

        intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        keyEvent = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis()+1000, KeyEvent.ACTION_UP, keyCode, 0);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);
    }


    public synchronized void action_next()
    {
        //int currentTrack = getCurrentPlaylist().getCurrentTrackPos();
        //int trackCount   = getCurrentPlaylist().getTrackCount();
        //int newTrackPos  = currentTrack + 1;
        //if (newTrackPos == trackCount) newTrackPos = 0;
        //PodEmuLog.debug("PEMP: action NEXT requested. newTrackPos=" + newTrackPos);
        PodEmuLog.debug("PEMP: action NEXT requested");

        // TODO: implement repeat and shuffle
        //no loops? if(currentTrack == trackCount-1) return;

        if( shouldUpdatePosition() )
        {
            getCurrentPlaylist().setIncrement(+1);
            //getCurrentPlaylist().setCurrentTrack(newTrackPos);
        }

        PodEmuLog.error("PEMP: action NEXT, time = " + System.currentTimeMillis());

        MediaController mediaController=getActiveMediaController();
        if( mediaController != null) // && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) == PlaybackState.ACTION_SKIP_TO_NEXT)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_SKIP_TO_NEXT)");
            mediaController.getTransportControls().skipToNext();
        }
        else
        {
            execute_action(KeyEvent.KEYCODE_MEDIA_NEXT);
        }

    }

    public synchronized void action_prev(long timeElapsed)
    {
        action_prev(timeElapsed, false);
    }

    public synchronized void action_prev(long timeElapsed, boolean force)
    {
        //int currentTrack = getCurrentPlaylist().getCurrentTrackPos();
        //int trackCount   = getCurrentPlaylist().getTrackCount();
        //int newTrackPos  = currentTrack - 1;
        //if (newTrackPos == -1) newTrackPos = trackCount-1;

        //PodEmuLog.debug("PEMP: action PREV requested, force=" + force + ". newTrackPos=" + newTrackPos);
        PodEmuLog.debug("PEMP: action PREV requested, force=" + force);

        // TODO: implement repeat and shuffle
        //no loops? if(currentTrack == 0) return;

        if( shouldUpdatePosition() )
        {
            getCurrentPlaylist().setIncrement(-1);
            //getCurrentPlaylist().setCurrentTrack(newTrackPos);
        }

        MediaController mediaController=getActiveMediaController();
//        PlaybackState playbackState = mediaController.getPlaybackState();
        if(mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == PlaybackState.ACTION_SKIP_TO_PREVIOUS) )
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_SKIP_TO_PREVIOUS)");
            // media players behave differently, depending on how much time elapsed
            // from the beginning of the song. Usually pressing "back" button after
            // 2 second elapsed from the beginning of the song will only rewind to the beginning of the song.
            if (force && timeElapsed > 2000)
            {
                mediaController.getTransportControls().skipToPrevious();
            }
            mediaController.getTransportControls().skipToPrevious();


            //KeyEvent keyEvent = new KeyEvent(0,200,KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0);
            //mediaController.dispatchMediaButtonEvent(keyEvent);
        }

        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            // media players behave differently, depending on how much time elapsed
            // from the beginning of the song. Usually pressing "back" button after
            // 2 second elapsed from the beginning of the song will only rewind to the beginning of the song.
            if (force && timeElapsed > 2000)
            {
                execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
            execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        lastPrevExecuted=System.currentTimeMillis();
    }

    public void action_prev()
    {
        action_prev(0, false);
    }

    public void action_play()
    {
        PodEmuLog.debug("PEMP: action PLAY requested");
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_PLAY) == PlaybackState.ACTION_PLAY)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_PLAY)");
            mediaController.getTransportControls().play();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }

    public void action_pause()
    {
        PodEmuLog.debug("PEMP: action PAUSE requested");
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_PAUSE) == PlaybackState.ACTION_PAUSE)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_PAUSE)");
            mediaController.getTransportControls().pause();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    public void action_play_pause()
    {
        PodEmuLog.debug("PEMP: action PLAY_PAUSE requested");
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_PLAY_PAUSE) == PlaybackState.ACTION_PLAY_PAUSE)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_PLAY_PAUSE)");
            if(mediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
                mediaController.getTransportControls().pause();
            else
                mediaController.getTransportControls().play();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    public void action_stop()
    {
        PodEmuLog.debug("PEMP: action STOP requested");
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_STOP) == PlaybackState.ACTION_STOP)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_STOP)");
            mediaController.getTransportControls().stop();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_STOP);
        }
    }

    public void action_skip_forward()
    {
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_FAST_FORWARD) == PlaybackState.ACTION_FAST_FORWARD)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_FAST_FORWARD)");
            mediaController.getTransportControls().fastForward();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
            /*
            if(Build.VERSION.SDK_INT >= 23)
            {
                execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
            }
            */
        }
    }

    public void action_skip_backward()
    {
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_REWIND) == PlaybackState.ACTION_REWIND)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_REWIND)");
            mediaController.getTransportControls().fastForward();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_REWIND);
            /*
            if(Build.VERSION.SDK_INT >= 23)
            {
                execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
            }
            */
        }
    }

    public void action_stop_ff_rev()
    {
        if(!isPlaying()) return;

        PodEmuLog.debug("PEMP: action STOP_FF_REV requested");
        MediaController mediaController=getActiveMediaController();
        if( mediaController != null)// && (mediaController.getPlaybackState().getActions() & PlaybackState.ACTION_PLAY) == PlaybackState.ACTION_PLAY)
        {
            PodEmuLog.debug("PEMP: executing action through MediaController (ACTION_STOP_FF_REV)");
            mediaController.getTransportControls().play();
        }
        else
        {
            PodEmuLog.debug("PEMP: executing action through KeyEvent");
            execute_action(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }


    // this function will calculate the number of tracks to jump forward or backward depending on
    // track count distance
    public int calcTrackCountFromPosition(int pos)
    {
        int currentTrack = getCurrentPlaylist().getCurrentTrackPos();
        int trackCount = getCurrentPlaylist().getTrackCount();

        PodEmuLog.debug("PEMS.Playlist: jumpTo: " + pos + " while current pos is " + currentTrack);

        if (pos == 0xffffffff)
        {
            //pos = playlistOffset;
            // don't want to process resetting playlist
            pos = 0;
        }

        // this should not happen - just in case we fix the boundaries
        pos = Math.max(pos, 0);
        pos = Math.min(pos, trackCount);

        int count = pos - currentTrack;

        if (PodEmuMediaStore.getInstance().getPlaylistCountMode() == PodEmuMediaStore.MODE_PLAYLIST_SIZE_FIXED)
        {
            int threshold = trackCount / 2;
            if (count > threshold)
            {
                count = -(currentTrack + trackCount - pos);
            }
            if (count < -threshold)
            {
                count = trackCount - currentTrack + pos;
            }
        }

        PodEmuLog.debug("PEMS.Playlist: calculated track jump is: " + count);
        return count;
    }

    public boolean jumpTrackCount(int count)
    {
        long timeElapsed = MediaPlayback.getInstance().getCurrentTrackPositionMS();

        //------------

        if(count >= 0)
        {
            for(int i=0;i<count;i++)
            {
                action_next();
            }
        }
        else
        {
            for(int i=0;i<-count;i++)
            {
                action_prev(timeElapsed,true);
                timeElapsed = 0;
            }
        }

        return true;
    }

    private boolean shouldUpdatePosition()
    {
        switch (PodEmuMediaStore.getInstance().getPlaylistCountMode())
        {
            case PodEmuMediaStore.MODE_PLAYLIST_SIZE_SINGLE:
            case PodEmuMediaStore.MODE_PLAYLIST_SIZE_TRIPLE:
                return false;
            default:
                return true;
        }
    }

    //public abstract boolean jumpTo(int pos);
    public abstract void setTrackStatusChanged(boolean status);

    public String getCtrlAppProcessName()
    {
        return ctrlAppProcessName;
    }

}
