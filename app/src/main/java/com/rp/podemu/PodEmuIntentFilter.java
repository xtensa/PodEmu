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

import android.content.IntentFilter;

/**
 * Created by rp on 9/25/15.
 */
public class PodEmuIntentFilter extends IntentFilter
{
    
    public PodEmuIntentFilter()
    {
/*        this.addAction("com.android.music.musicservicecommand");
        this.addAction("com.android.music.metachanged");
        this.addAction("com.android.music.playstatechanged");
        this.addAction("com.android.music.updateprogress");
        this.addAction("com.android.music.playbackcomplete");
        this.addAction("com.android.music.queuechanged");

        this.addAction("com.htc.music.metachanged");
        this.addAction("com.htc.music.musicservicecommand");
        this.addAction("com.htc.music.metachanged");
        this.addAction("com.htc.music.playstatechanged");
        this.addAction("com.htc.music.updateprogress");
        this.addAction("com.htc.music.playbackcomplete");
        this.addAction("com.htc.music.queuechanged");

        this.addAction("fm.last.android.metachanged");
        this.addAction("com.sec.android.app.music.metachanged");
        this.addAction("com.nullsoft.winamp.metachanged");
        this.addAction("com.amazon.mp3.metachanged");
        this.addAction("com.miui.player.metachanged");
        this.addAction("com.real.IMP.metachanged");
        this.addAction("com.sonyericsson.music.metachanged");
        this.addAction("com.rdio.android.metachanged");
        this.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
        this.addAction("com.andrew.apollo.metachanged");
*/
        // for Spotthisy
        //this.addCategory("ComponentInfo");
        //this.addCategory("com.spotify.mobile.android.service.SpotifyIntentService");
        //this.addCategory("com.spotify.mobile.android.service.SpotifyService");
        //this.addAction("com.spotify.mobile.android.ui.widget.SpotifyWidget");
        //this.addAction("ComponentInfo");
        //this.addAction("com.spotify");
        //this.addAction("com.spotify.mobile.android.service.SpotthisyIntentService");
        //this.addAction("com.spotify.mobile.android.service.SpotthisyService");
        //this.addAction("com.spotify.mobile.android.ui");

        this.addAction("com.spotify.music.playbackstatechanged");
            this.addAction("com.spotify.music.metadatachanged");
            this.addAction("com.spotify.music.queuechanged");

    }
}
