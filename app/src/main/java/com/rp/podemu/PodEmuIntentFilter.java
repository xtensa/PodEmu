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
import android.content.IntentFilter;

import java.util.ArrayList;

/**
 * Created by rp on 9/25/15.
 */
public class PodEmuIntentFilter extends IntentFilter
{
    private static ArrayList<String> appList = new ArrayList<>(0);
    private static boolean appListInitialized=false;
    private static void initializeAppList()
    {
        if(!appListInitialized)
        {
            appList.add("com.aspiro.tidal");            // Tidal
            appList.add("com.maxmpz.audioplayer");      // PowerAmp
            appList.add("com.apple.android.music");     // Apple Music

            appListInitialized=true;
        }
    }

    public static ArrayList<String> getAppList()
    {
        initializeAppList();
        return appList;
    }

    public PodEmuIntentFilter()
    {
        initializeAppList();

        //if(processName.contains("com.spotify.music"))
        {
            this.addAction("com.spotify.music.playbackstatechanged");
            this.addAction("com.spotify.music.metadatachanged");
            this.addAction("com.spotify.music.queuechanged");
        //}
        //else
        //{
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

            /*

        MixZing:
            com.android.music.playstatechanged
            Bundle[{
                duration=0,
                playstate=false,
                artist=Chris Brown,
                preparing=false,
                playing=false,
                streaming=true,
                id=-1,
                album=HOT 108 JAMZ - #1 FOR HIP HOP - www.hot108.com,
                track=Back To Sleep,
                position=0,
                com.mixzing.basic.mediaSource=true,
                ListSize=1,
                ListPosition=0}]

        PowerAmp
            com.android.music.playstatechanged
            Bundle[{
                duration=68000,
                com.maxmpz.audioplayer.source=com.maxmpz.audioplayer,
                artist=Unknown artist,
                playing=false,
                id=117,
                album=null,
                track=Avalon}]
         */


    // defining as synchronized to avoid this exception:
    // android.os.BadParcelableException: ClassNotFoundException when unmarshalling
    public static synchronized PodEmuMessage processBroadcast(Context context, Intent intent)
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
        int listSize=-1;
        int listPosition=-1;

        String PLAYBACK_STATE_CHANGED, METADATA_CHANGED, QUEUE_CHANGED, PLAYBACK_COMPLETE, UPDATE_PROGRESS;

        String action = intent.getAction();
        String cmd = null;

        try
        {
            cmd = intent.getStringExtra("command");
        }
        catch (android.os.BadParcelableException e)
        {
            // do nothing as we don't bother
            // cmd is only used for informative purposes
        }

        MediaPlayback mediaPlayback = MediaPlayback.getInstance();
        if(mediaPlayback == null)
        {
            PodEmuLog.error("PEF: broadcast processing attempt before MediaPlayback engine initialized.");
            return null;
        }

        // unfortunately Android does not provide information about source process for broadcast, so we need
        // to check case by case when possible. Using XOR check only for "certain" applications

        boolean skip_broadcast=false;
        String skip_msg="";
        // MIXZING
        boolean isMixZingBroadcast = intent.getBooleanExtra("com.mixzing.basic.mediaSource", false);
        if (   ( mediaPlayback.getCtrlAppProcessName().equals("com.mixzing.basic") && !isMixZingBroadcast) ||
               (!mediaPlayback.getCtrlAppProcessName().equals("com.mixzing.basic") &&  isMixZingBroadcast)   )
        {
            skip_broadcast = true;
            skip_msg = "PEF: skipping not MixZing or MixZing outdated broadcast.";
        }

        // POWERAMP
        boolean isPowerAmpBroadcast=(intent.getStringExtra("com.maxmpz.audioplayer.source")!=null);
        if ( ( mediaPlayback.getCtrlAppProcessName().equals("com.maxmpz.audioplayer") && !isPowerAmpBroadcast) ||
             (!mediaPlayback.getCtrlAppProcessName().equals("com.maxmpz.audioplayer") &&  isPowerAmpBroadcast) )
        {
            skip_broadcast = true;
            skip_msg = "PEF: skipping not PowerAmp or PowerAmp outdated broadcast.";
        }

        // SPOTIFY
        boolean isSpotifyBroadcast=action.contains("spotify");
        if (   ( mediaPlayback.getCtrlAppProcessName().equals("com.spotify.music") && !isSpotifyBroadcast) ||
               (!mediaPlayback.getCtrlAppProcessName().equals("com.spotify.music") &&  isSpotifyBroadcast) )
        {
            skip_broadcast = true;
            skip_msg = "PEF: skipping not Spotify or Spotify outdated broadcast.";
        }

        // TIDAL
        // unfortunately TIDAL is not sending any marker so we can only guess...
        boolean isTidalBroadcast=(intent.getStringExtra("state")!=null);
        if (mediaPlayback.getCtrlAppProcessName().equals("com.aspiro.tidal") && !isTidalBroadcast)
        {
            skip_broadcast = true;
            skip_msg = "PEF: skipping not TIDAL broadcast.";
        }

        if( skip_broadcast )
        {
            PodEmuLog.debugVerbose("PEF: (" + context.getClass() + ") Broadcast received: " + cmd + " - " + action);
            PodEmuLog.debugVerbose("PEF: (" + context.getClass() + ") " + intent.getExtras());
            PodEmuLog.debugVerbose(skip_msg);
            return null;
        }
        else
        {
            PodEmuLog.debug("PEF: (" + context.getClass() + ") Broadcast received: " + cmd + " - " + action);
            PodEmuLog.debug("PEF: (" + context.getClass() + ") " + intent.getExtras());
        }



        if(action.contains(BroadcastTypes.SPOTIFY_PACKAGE))
        {
            PodEmuLog.debug("PEF: Detected SPOTIFY broadcast");

            length = intent.getIntExtra("length", 0);
            position = intent.getIntExtra("playbackPosition", 0);
            id = intent.getStringExtra("id");
            timeSentInMs = intent.getLongExtra("timeSent", 0L);

            // unfortunately spotify does not provide this information
            listSize = -1;
            listPosition = -1;

            METADATA_CHANGED=BroadcastTypes.SPOTIFY_METADATA_CHANGED;
            PLAYBACK_STATE_CHANGED=BroadcastTypes.SPOTIFY_PLAYBACK_STATE_CHANGED;
            QUEUE_CHANGED=BroadcastTypes.SPOTIFY_QUEUE_CHANGED;
            PLAYBACK_COMPLETE="pattern that will never match :)";
            UPDATE_PROGRESS="pattern that will never match :)";
        }
        else
        {
            if (mediaPlayback.getCtrlAppProcessName().equals("com.aspiro.tidal"))
            {
                // TIDAL sending this info differently then others
                length = intent.getIntExtra("duration", 0)*1000;
                position = intent.getIntExtra("position", 0);
            }
            else
            {
                length = (int) intent.getLongExtra("duration", 0);
                position = (int) intent.getLongExtra("position", 0);
            }

            if (mediaPlayback.getCtrlAppProcessName().equals("com.htc.music"))
            {
                id = String.valueOf(intent.getIntExtra("id", 0));
            }
            else
            {
                id = String.valueOf(intent.getLongExtra("id", 0));
            }

            timeSentInMs = System.currentTimeMillis();

            listSize = (int) intent.getLongExtra("ListSize", -1);

            try
            {
                // some applications provide Integer instead of Long and it causes exception
                listPosition = (int) intent.getLongExtra("ListPosition", -1);
            }
            catch(Exception e)
            {
                if(e instanceof java.lang.ClassCastException)
                {
                    listPosition = intent.getIntExtra("ListPosition", -1);
                }
                else
                {
                    throw e;
                }
            }

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
            // nothing to do here yet
            // playlist regeneration might be needed
        }

        artist = intent.getStringExtra("artist");
        album = intent.getStringExtra("album");
        track = intent.getStringExtra("track");

        isPlaying = intent.getBooleanExtra("playing", false);

        PodEmuLog.debug("PEF: received message - action:" + action_code + ", isPlaying:" + isPlaying + ", artist:" + artist + ", album:" + album +
                ", track:"+ track + ", id:" + id + ", length:" + length + ", track:" + listPosition + "/" + listSize);

        podEmuMessage.setAlbum(album);
        podEmuMessage.setArtist(artist);
        podEmuMessage.setTrackName(track);
        podEmuMessage.setExternalId(id);
        podEmuMessage.setLength(length);
        podEmuMessage.setIsPlaying(isPlaying);
        podEmuMessage.setPositionMS(position);
        podEmuMessage.setTimeSent(timeSentInMs);
        podEmuMessage.setAction(action_code);
        podEmuMessage.setListSize(listSize);
        podEmuMessage.setListPosition(listPosition);

        return podEmuMessage;
    }


}
