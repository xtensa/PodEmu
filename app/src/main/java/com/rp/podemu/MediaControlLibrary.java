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
}
