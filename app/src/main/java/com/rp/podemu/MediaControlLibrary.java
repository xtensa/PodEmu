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

import android.app.Instrumentation;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;

/**
 * Created by rp on 9/1/15.
 */
public class MediaControlLibrary
{

    public static void action_next()
    {

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
        }).start();

    }

    public static void action_prev()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        }).start();

    }

    public static void action_play_pause()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
        }).start();



    }

    public static void action_stop()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_STOP);
            }
        }).start();
    }

    public static void action_skip_backward()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
            }
        }).start();
    }

    public static void action_skip_forward()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
            }
        }).start();
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
