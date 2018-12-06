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

/**
 * Created by rp on 9/1/15.
 */
public class MediaPlayback_Generic extends MediaPlayback
{
    private boolean trackStatusChanged=true;
    private PodEmuMediaStore.Playlist currentPlaylist;


    private boolean isPlaying=false;
    private long positionMS;
    private long timeSent;
    private final String TAG="PEMPGen";
    private static long prevTime=0;


    MediaPlayback_Generic()
    {
        if(currentPlaylist == null)
        {
            PodEmuMediaStore podEmuMediaStore=PodEmuMediaStore.getInstance();
            currentPlaylist = podEmuMediaStore.new Playlist();
        }
    }

    @Override
    public void setCurrentPlaylist(PodEmuMediaStore.Playlist playlist)
    {
        currentPlaylist=playlist;
    }

    public boolean getTrackStatusChanged()
    {
        return trackStatusChanged;
    }

    public synchronized void setTrackStatusChanged(boolean status)
    {
        trackStatusChanged = status;
    }

    public PodEmuMediaStore.Playlist getCurrentPlaylist()
    {
        return currentPlaylist;
    }


    /**
     * Calculating exact position of currently played song in MS.
     * We don't need to bother if the song was paused as we receive updated
     * postion once it is paused or unpaused.
     * @return position in miliseconds
     */
    public long getCurrentTrackPositionMS()
    {
        long position, currentTime = System.currentTimeMillis();

        PodEmuLog.debug(TAG + ": getCurrentTrackPositionMS, positionMS=" + positionMS + ", currentTime=" 
                + System.currentTimeMillis() + ", timeSent=" + timeSent);
        
        // if playback is active then calculate real position, otherwise return last known position
        if(isPlaying)
        {
            position = positionMS + (currentTime - timeSent);
        }
        else
        {
            position = positionMS;
        }
        long delta =  currentTime - prevTime;
        if(delta<POLLING_INTERVAL_TARGET*1.5)
        {
            pollingInterval -= (delta - POLLING_INTERVAL_TARGET)/2;
        }
        PodEmuLog.error(TAG + ": delta=" + delta + ", calculated pollingInterval=" + pollingInterval);

        prevTime = currentTime;
        return position;
    }

    public boolean isPlaying()
    {
        return isPlaying;
    }





    public synchronized void    updateCurrentlyPlayingTrack(PodEmuMessage msg)
    {
        PodEmuLog.debug(TAG + ": FUNCTION START updateCurrentlyPlayingTrack");
        PodEmuMediaStore.Track track = currentPlaylist.getCurrentTrack();
        int action = msg.getAction();

        if(        ((track.album  != null && track.album.equals(msg.getAlbum())) || track.album == null )
                && ((track.artist != null && track.artist.equals(msg.getArtist())) || track.artist == null )
                && ((track.name   != null && track.name.equals(msg.getTrackName())) || track.name == null )
                && ((track.id     != null && track.id == (msg.getListPosition() == -1 ? track.id : msg.getListPosition() ) ) || track.id == null )
        //        && (currentPlaylist.getTrackCount() == msg.getListSize())
                && track.duration == msg.getDurationMS()
        )
        {
            //PodEmuLog.error(TAG + ": isPlaying=" + isPlaying + ", newIsPlaying=" + isPlaying());

            if(isPlaying == msg.isPlaying())
            {
                if (positionMS >= 0)
                {
                    //PodEmuLog.error(TAG + ": updating time to " + positionMS);
                    timeSent = msg.getTimeSent();
                    positionMS = msg.getPositionMS();
                }
                PodEmuLog.debug(TAG + ": skipping track update. Tracks are the same, play status not changed.");
                return;
            }
            else
            {
                action = PodEmuMessage.ACTION_PLAYBACK_STATE_CHANGED;
            }
        }

        if(action!=PodEmuMessage.ACTION_QUEUE_CHANGED)
        {
            int listSize = msg.getListSize();
            boolean dummy_playlist=false;
            PodEmuMediaStore podEmuMediaStore=PodEmuMediaStore.getInstance();

            isPlaying = msg.isPlaying();
            timeSent = msg.getTimeSent();
            positionMS = msg.getPositionMS();

            PodEmuLog.debug(TAG + ": updateCurrentlyPlayingTrack: track " + msg.getListPosition() + "/" + listSize + ", prev_size=" + currentPlaylist.getTrackCount());
            if( listSize>0 )
            {
                PodEmuMediaStore.getInstance().setPlaylistCountMode(PodEmuMediaStore.MODE_PLAYLIST_SIZE_NORMAL, listSize);
            }
            // some media application does not provide listSize and listPosition information
            // if so, we should mimic it to fool the dock
            else
            {
                listSize = PodEmuMediaStore.getInstance().getPlaylistCountSize();
                PodEmuMediaStore.getInstance().setPlaylistCountMode(PodEmuMediaStore.MODE_PLAYLIST_SIZE_DEFAULT, listSize);
                dummy_playlist = true;
            }
            PodEmuLog.debug(TAG + ": updated listSize: " + listSize + ", prev track count: " + currentPlaylist.getTrackCount());


            // if total song count changed then we need to rebuild DB
            if (currentPlaylist.getTrackCount() != listSize)
            {
                // this is done by setPlayListCountMode
                //PodEmuMediaDB.getInstance().rebuildDB(PodEmuMediaStore.getInstance(), listSize);

                // update playback engine playlist and selection
                PodEmuMediaStore.getInstance().selectionReset();
                this.setCurrentPlaylist(PodEmuMediaStore.getInstance().selectionBuildPlaylist());

                podEmuMediaStore.selectionReset();
                podEmuMediaStore.selectionInitializeDB(1 /* 1=playlist */);
                MediaPlayback.getInstance().setCurrentPlaylist(PodEmuMediaStore.getInstance().selectionBuildPlaylist());

                if(dummy_playlist) currentPlaylist.setCurrentTrackPosToStart();
            }

            int trackNumber;
            if(dummy_playlist)
            {
                if(action == PodEmuMessage.ACTION_METADATA_CHANGED) currentPlaylist.positionIncrement();
                trackNumber = currentPlaylist.getCurrentTrackPos();
            }
            else
            {
                currentPlaylist.setCurrentTrack(msg.getListPosition()-1);
                trackNumber = msg.getListPosition();
            }
            // we need to set "pointer" to updated track number
            track = currentPlaylist.getCurrentTrack();

            track.track_number = trackNumber;
            track.id = track.track_number;
            track.duration = msg.getDurationMS();
            track.external_id = msg.getExternalId();
            track.name = msg.getTrackName();
            track.album = msg.getAlbum();
            track.genre = msg.getGenre();
            track.composer = msg.getComposer();
            track.artist = msg.getArtist();

            if(track.name==null)
            {
                track.name = "Generic track";
            }

            // finally update the title in the db and set track
            podEmuMediaStore.updateTrack(track);

            //currentPlaylist.getCurrentTrack().copyFrom(track1); <- no need for that, track is updated


            podEmuMediaStore.setCtrlAppProcessName(msg.getApplication());

        }
        PodEmuLog.debug(TAG + ": metadata updated, time = " + System.currentTimeMillis());
        setTrackStatusChanged(true);
    }

}
