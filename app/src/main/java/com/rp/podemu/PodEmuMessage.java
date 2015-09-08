package com.rp.podemu;

/**
 * Created by rp on 9/7/15.
 */
public class PodEmuMessage
{
    private String artist;
    private String album;
    private String track;
    private String trackID;
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
    public String getTrack()   { return track; }
    public String getTrackID() { return trackID; }
    public int getLength()     { return lengthSec; }
    public int getPositionMS() { return positionMS; }
    public boolean isPlaying() { return isPlaying; }
    public int getAction()     { return action; }
    public long getTimeSent() { return timeSent; }

    public void setArtist(String artist)   { this.artist = artist; }
    public void setAlbum(String album)     { this.album = album;   }
    public void setTrack(String track)     { this.track = track;    }
    public void setTrackID(String trackID) { this.trackID = trackID;  }
    public void setLength(int length)      { this.lengthSec = length;    }
    public void setPositionMS(int positionMS)    { this.positionMS = positionMS;  }
    public void setIsPlaying(boolean isPlaying)  { this.isPlaying = isPlaying;  }
    public void setAction(int action)            { this.action = action; }
    public void setTimeSent(long timeSent) { this.timeSent = timeSent; }
}

