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


public class PodEmuMessage
{
    private String artist;
    private String album;
    private String track;
    private String trackID;
    private String genre="Unknown Genre";
    private String composer="Unknown Composer";
    private int lengthSec;
    private int positionMS;
    private boolean isPlaying;
    private int action;
    private long timeSent;

    public final static int ACTION_METADATA_CHANGED=1;
    public final static int ACTION_PLAYBACK_STATE_CHANGED=2;
    public final static int ACTION_QUEUE_CHANGED=4;

    public String getArtist()  { return artist; }
    public String getAlbum()   { return album; }
    public String getTrackName()   { return track; }
    public String getTrackID() { return trackID; }
    public String getGenre() { return genre; }
    public String getComposer() { return composer; }
    public int getLength()     { return lengthSec; }
    public boolean isPlaying() { return isPlaying; }
    public int getAction()     { return action; }
    public long getTimeSent() { return timeSent; }

    public void setArtist(String artist)   { this.artist = artist; }
    public void setAlbum(String album)     { this.album = album;   }
    public void setTrackName(String track)     { this.track = track;    }
    public void setTrackID(String trackID) { this.trackID = trackID;  }
    public void setLength(int length)      { this.lengthSec = length;    }
    public void setPositionMS(int positionMS)    { this.positionMS = positionMS;  }
    public void setIsPlaying(boolean isPlaying)  { this.isPlaying = isPlaying;  }
    public void setAction(int action)            { this.action = action; }
    public void setTimeSent(long timeSent) { this.timeSent = timeSent; }

    /**
     * Calculating exact position of the played song in MS.
     * We don't need to bother if the song was paused as we receive updated
     * postion once it is paused or unpaused.
     * @return position in miliseconds
     */
    public int getPositionMS()
    {
        int position;

        // if playback is active then calculate real position, otherwise return last known position
        if(isPlaying)
        {
            position = positionMS + (int)(System.currentTimeMillis() - timeSent);
        }
        else
        {
            position=positionMS;
        }
        return position;
    }

    public void bulk_update(PodEmuMessage msg)
    {
        if(msg.getAction()!=PodEmuMessage.ACTION_QUEUE_CHANGED)
        {
            this.setIsPlaying(msg.isPlaying());
            this.setTimeSent(msg.getTimeSent());
            this.setAction(msg.getAction());
            this.setLength(msg.getLength());
            this.setTrackID(msg.getTrackID());
            this.setTrackName(msg.getTrackName());
            this.setAlbum(msg.getAlbum());
            this.setArtist(msg.getArtist());
            this.setPositionMS(msg.getPositionMS());
        }
    }

    public String getLengthHumanReadable()
    {
        int length=getLength();
        int hours=getLength()/1000/60/60;
        int mins=(length-hours*60*60*1000)/1000/60;
        int secs=(length-hours*60*60*1000-mins*60*1000)/1000;

        return String.format("%02d:%02d", mins, secs);
    }
}

