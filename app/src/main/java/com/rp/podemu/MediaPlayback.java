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

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Created by rp on 10/31/15.
 */
public abstract class MediaPlayback
{
    private static MediaPlayback instance=null;

    protected static Context context=null;
    protected static String ctrlAppProcessName=null;
    protected static String ctrlAppDbName=null;
    private long lastPrevExecuted=System.currentTimeMillis();



    public static MediaPlayback getInstance()
    {
        if(instance==null) PodEmuLog.error("MPlayback: instance access before initialization");
        return instance;
    }

    public static void initialize(Context c, String app, String appDbName)
    {
        context=c;

        if(ctrlAppProcessName==null || !ctrlAppProcessName.equals(app) )
        {
            ctrlAppProcessName = app;
            ctrlAppDbName = appDbName;
            instance = new MediaPlayback_Generic();
        }

    }


    public abstract void setCurrentPlaylist(PodEmuMediaStore.Playlist playlist);
    public abstract PodEmuMediaStore.Playlist getCurrentPlaylist();

    public abstract boolean getTrackStatusChanged();

    public abstract void    updateCurrentlyPlayingTrack(PodEmuMessage podEmuMessage);

    public abstract boolean isPlaying();

    public abstract int getCurrentTrackPositionMS();

    public void execute_action(int keyCode)
    {
        Intent intent;
        KeyEvent keyEvent;

        if(ctrlAppProcessName == null)
        {
            PodEmuLog.error("MPlayback: media control attempt before ctrlAppProcessName");
        }

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
        int currentTrack = getCurrentPlaylist().getCurrentTrackPos();
        int trackCount   = getCurrentPlaylist().getTrackCount();

        PodEmuLog.debug("PEMP: action NEXT requested");

        // TODO: implement repeat and shuffle
        if(currentTrack == trackCount-1) return;

        if( shouldUpdatePosition() )
        {
            getCurrentPlaylist().setCurrentTrack(currentTrack+1);
        }

        execute_action(KeyEvent.KEYCODE_MEDIA_NEXT);

    }

    public synchronized void action_prev(int timeElapsed)
    {
        action_prev(timeElapsed, false);
    }

    public synchronized void action_prev(int timeElapsed, boolean force)
    {
        int currentTrack = getCurrentPlaylist().getCurrentTrackPos();

        PodEmuLog.debug("PEMP: action PREV requested, force=" + force);

        // TODO: implement repeat and shuffle
        if(currentTrack == 0) return;

        if( shouldUpdatePosition() )
        {
            getCurrentPlaylist().setCurrentTrack(currentTrack-1);
        }

        // media players behave differently, depending on how much time elapsed
        // from the beginning of the song. Usually pressing "back" button after
        // 2 second elapsed from the beginning of the song will only rewind to the beginning of the song.
        if(force && timeElapsed>2000)
        {
            execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
        execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        lastPrevExecuted=System.currentTimeMillis();
    }

    public void action_prev()
    {
        action_prev(0, false);
    }

    public void action_play()
    {
        PodEmuLog.debug("PEMP: action PLAY requested");
        execute_action(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    public void action_pause()
    {
        PodEmuLog.debug("PEMP: action PAUSE requested");
        execute_action(KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    public void action_play_pause()
    {
        PodEmuLog.debug("PEMP: action PLAY_PAUSE requested");
        execute_action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void action_stop()
    {
        PodEmuLog.debug("PEMP: action STOP requested");
        execute_action(KeyEvent.KEYCODE_MEDIA_STOP);
    }

    public void action_skip_forward()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
    }

    public void action_skip_backward()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
    }




    public boolean jumpTo(int pos)
    {
        int timeElapsed = MediaPlayback.getInstance().getCurrentTrackPositionMS();
        int currentTrack = getCurrentPlaylist().getCurrentTrackPos();
        int trackCount   = getCurrentPlaylist().getTrackCount();

        PodEmuLog.debug("PEMS.Playlist: jumTo: " + pos + " while current pos is " + currentTrack);

        if(pos==0xffffffff)
        {
            //pos = playlistOffset;
            // don't want to process resetting playlist
            pos=0;
        }

        // this should not happen - just in case we fix the boundaries
        pos=Math.max(pos,0);
        pos=Math.min(pos,trackCount);

        if(pos>currentTrack)
        {
            int count=pos-currentTrack;
            for(int i=0;i<count;i++)
            {
                action_next();
            }
        }
        else
        {
            int count=currentTrack-pos;
            for(int i=0;i<count;i++)
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
            case PlaylistCountDialogFragment.MODE_PLAYLIST_SIZE_SINGLE:
            case PlaylistCountDialogFragment.MODE_PLAYLIST_SIZE_TRIPLE:
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
