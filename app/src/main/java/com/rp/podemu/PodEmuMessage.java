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
            position = positionMS + (int) (System.currentTimeMillis() - timeSent);
        }
        else
        {
            position=positionMS;
        }
        return position;
    }
}

