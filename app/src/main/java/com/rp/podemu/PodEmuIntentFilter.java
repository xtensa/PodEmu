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
import android.content.IntentFilter;

/**
 * Created by rp on 9/25/15.
 */
public class PodEmuIntentFilter extends IntentFilter
{

    public PodEmuIntentFilter(String processName)
    {
        if(processName.contains("com.spotify.music"))
        {
            this.addAction("com.spotify.music.playbackstatechanged");
            this.addAction("com.spotify.music.metadatachanged");
            this.addAction("com.spotify.music.queuechanged");
        }
        else
        {
            this.addAction("com.android.music.musicservicecommand");
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
        }

    }

    final class BroadcastTypes {
        static final String SPOTIFY_PACKAGE = "com.spotify.music";
        static final String SPOTIFY_PLAYBACK_STATE_CHANGED = ".playbackstatechanged";
        static final String SPOTIFY_QUEUE_CHANGED = ".queuechanged";
        static final String SPOTIFY_METADATA_CHANGED = ".metadatachanged";


        static final String ANDROID_METADATA_CHANGED="metachanged";
        static final String ANDROID_PLAYBACK_STATE_CHANGED="playstatechanged";
        static final String ANDROID_UPDATE_PROGRESS="updateprogress";
        static final String ANDROID_PLAYBACK_COMPLETE="playbackcomplete";
        static final String ANDROID_QUEUE_CHANGED="queuechanged";
    }


    /*
    * Play.Music
    * Bundle[{
    *       duration=234000,
    *       playstate=true,
    *       woodstock=false,
    *       currentContainerName=In Utero (20th Anniversary Remaster),
    *       artist=Nirvana, domain=0, currentSongLoaded=true,
    *       preparing=false, rating=0, albumId=3653228523,
    *       currentContainerTypeValue=7,
    *       currentContainerId=3653228523,
    *       playing=true, streaming=true,
    *       inErrorState=false,
    *       albumArtFromService=false,
    *       id=67, currentContainerExtData=null,
    *       album=In Utero (20th Anniversary Remaster),
    *       local=true, track=Milk It, videoId=null,
    *       position=1123, currentContainerExtId=null,
    *       videoThumbnailUrl=null, supportsRating=true,
    *       ListSize=12,
    *       previewPlayType=-1,
    *       isSkipLimitReached=false,
    *       ListPosition=7}]
    *
    *
    * Spotify
    * Bundle[{
    *       timeSent=1444574487852,
    *       duration=212000,
    *       playstate=true,
    *       artist=Kelli O'Hara,
    *       length=212000,
    *       albumId=spotify:album:4MCsklkiBP9KTADPWmrhWB,
    *       playing=true,
    *       playbackPosition=68,
    *       id=spotify:track:3PRTcW2Rb4djpmuftTLqU0,
    *       album=The King And I (The 2015 Broadway Cast Recording),
    *       track=Getting To Know You,
    *       position=68}]
    *
    *
    * */


    public static PodEmuMessage processBroadcast(Context context, Intent intent)
    {
        PodEmuMessage podEmuMessage = new PodEmuMessage();
        // will be used later to precisely determine position
        long timeSentInMs;
        boolean isPlaying;
        String artist;
        String album;
        String track;
        String id;
        int length;
        int position;
        int action_code=0;

        String PLAYBACK_STATE_CHANGED, METADATA_CHANGED, QUEUE_CHANGED, PLAYBACK_COMPLETE, UPDATE_PROGRESS;

        String action = intent.getAction();
        String cmd = intent.getStringExtra("command");

        PodEmuLog.debug("(" + context.getClass() + ") Broadcast received: " + cmd + " - " + action);
        PodEmuLog.debug("(" + context.getClass() + ") " + intent.getExtras());


        if(action.contains(BroadcastTypes.SPOTIFY_PACKAGE))
        {
            PodEmuLog.debug("Detected SPOTIFY broadcast");

            length = intent.getIntExtra("length", 0);
            position = intent.getIntExtra("playbackPosition", 0);
            id = intent.getStringExtra("id");
            timeSentInMs = intent.getLongExtra("timeSent", 0L);

            METADATA_CHANGED=BroadcastTypes.SPOTIFY_METADATA_CHANGED;
            PLAYBACK_STATE_CHANGED=BroadcastTypes.SPOTIFY_PLAYBACK_STATE_CHANGED;
            QUEUE_CHANGED=BroadcastTypes.SPOTIFY_QUEUE_CHANGED;
            PLAYBACK_COMPLETE="pattern that will never match :)";
            UPDATE_PROGRESS="pattern that will never match :)";
        }
        else
        {
            length = (int) intent.getLongExtra("duration", 0);
            position = (int) intent.getLongExtra("position", 0);
            id = String.valueOf(intent.getLongExtra("id",0));
            timeSentInMs = System.currentTimeMillis();

            METADATA_CHANGED=BroadcastTypes.ANDROID_METADATA_CHANGED;
            PLAYBACK_STATE_CHANGED=BroadcastTypes.ANDROID_PLAYBACK_STATE_CHANGED;
            QUEUE_CHANGED=BroadcastTypes.ANDROID_QUEUE_CHANGED;
            PLAYBACK_COMPLETE=BroadcastTypes.ANDROID_PLAYBACK_COMPLETE;
            UPDATE_PROGRESS=BroadcastTypes.ANDROID_UPDATE_PROGRESS;
        }


        if (action.contains(METADATA_CHANGED))
        {
            action_code=PodEmuMessage.ACTION_METADATA_CHANGED;
        }
        if (action.contains(PLAYBACK_STATE_CHANGED)
                || action.contains(PLAYBACK_COMPLETE)
                || action.contains(UPDATE_PROGRESS))
        {
            action_code=PodEmuMessage.ACTION_PLAYBACK_STATE_CHANGED;
        }
        if (action.contains(QUEUE_CHANGED))
        {
            action_code=PodEmuMessage.ACTION_QUEUE_CHANGED;
            // Sent only as a notification, your app may want to respond accordingly.
        }

        artist = intent.getStringExtra("artist");
        album = intent.getStringExtra("album");
        track = intent.getStringExtra("track");

        isPlaying = intent.getBooleanExtra("playing", false);

        PodEmuLog.debug(isPlaying + " : " + artist + " : " + album + " : " + track + " : " + id + " : " + length);

        podEmuMessage.setAlbum(album);
        podEmuMessage.setArtist(artist);
        podEmuMessage.setTrackName(track);
        podEmuMessage.setTrackID(id);
        podEmuMessage.setLength(length);
        podEmuMessage.setIsPlaying(isPlaying);
        podEmuMessage.setPositionMS(position);
        podEmuMessage.setTimeSent(timeSentInMs);
        podEmuMessage.setAction(action_code);

        return podEmuMessage;
    }


}
