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

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Created by rp on 9/1/15.
 */
public class MediaControlLibrary
{
    private static long lastPrevExecuted=System.currentTimeMillis();

    public static Context context;
    public static String ctrlAppProcessName;

    public static int playlistOffset = 500;
    public static int currentPlaylistPosition = playlistOffset;




    public static void execute_action(int keyCode)
    {
        Intent intent;
        KeyEvent keyEvent;

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

    public static void execute_action_long_press(int keyCode) {
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


    public static synchronized void action_next()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_NEXT);
        if(currentPlaylistPosition<playlistOffset*2) currentPlaylistPosition++;
    }

    public static synchronized void action_prev(int timeElapsed)
    {
        // most media players behave differently, depending on how much time elapsed
        // from the beginning of the song
        if(timeElapsed>2000)
        {
            execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        execute_action(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        if(currentPlaylistPosition>0) currentPlaylistPosition--;

        lastPrevExecuted=System.currentTimeMillis();
    }

    public static void action_play()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    public static void action_pause()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    public static void action_play_pause()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public static void action_stop()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_STOP);
    }

    public static void action_skip_forward()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
    }

    public static void action_skip_backward()
    {
        execute_action(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
    }


    public static void jump_to(int pos, int timeElapsed)
    {
        if(pos==0xffffffff)
        {
            //pos = playlistOffset;
            // don't want to process resetting playlist
            return;
        }

        // this should not happen - just in case fix the boundaries
        pos=Math.max(pos,0);
        pos=Math.min(pos,playlistOffset*2);

        while(pos>currentPlaylistPosition)
        {
            MediaControlLibrary.action_next();
        }

        while(pos<currentPlaylistPosition)
        {
            MediaControlLibrary.action_prev(timeElapsed);
            timeElapsed=0;
        }

        try
        {
            //get time for broadcasts to be processed
            Thread.sleep(200);
        }
        catch (InterruptedException e)
        {
            // do nothing
        }
    }

    /*
    Inserting KEYCODE_MEDIA_SKIP_FORWARD or KEYCODE_MEDIA_SKIP_BACKWARD will throw the following log

    09-12 23:42:56.013  27177-29132/com.rp.podemu W/dalvikvm﹕ threadid=13: thread exiting with uncaught exception (group=0x2b4e71f8)
    09-12 23:42:56.023  27177-29132/com.rp.podemu E/AndroidRuntime﹕ FATAL EXCEPTION: Thread-6329
        java.lang.SecurityException: Injecting to another application requires INJECT_EVENTS permission
                at android.os.Parcel.readException(Parcel.java:1327)
                at android.os.Parcel.readException(Parcel.java:1281)
                at android.view.IWindowManager$Stub$Proxy.injectKeyEvent(IWindowManager.java:1178)
                at android.app.Instrumentation.sendKeySync(Instrumentation.java:859)
                at android.app.Instrumentation.sendKeyDownUpSync(Instrumentation.java:871)
                at com.rp.podemu.MediaControlLibrary$6.run(MediaControlLibrary.java:93)
                at java.lang.Thread.run(Thread.java:856)

    could be fixed on rooted device
    SOLUTION: http://stackoverflow.com/questions/5383401/android-inject-events-permission?rq=1
 */

}
