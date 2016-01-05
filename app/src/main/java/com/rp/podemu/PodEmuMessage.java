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
    private String external_id;
    private String genre="Unknown Genre";
    private String composer="Unknown Composer";
    private String uri="unknown uri";
    private int lengthSec;
    private int positionMS;
    private boolean isPlaying;
    private int action;
    private long timeSent;
    private int track_number;
    private int listSize;
    private int listPosition;

    public final static int ACTION_METADATA_CHANGED=1;
    public final static int ACTION_PLAYBACK_STATE_CHANGED=2;
    public final static int ACTION_QUEUE_CHANGED=4;

    public String getArtist()  { return artist; }
    public String getAlbum()   { return album; }
    public String getTrackName()   { return track; }
    public String getExternalId() { return external_id; }
    public String getGenre() { return genre; }
    public String getComposer() { return composer; }
    public int getLength()     { return lengthSec; }
    public boolean isPlaying() { return isPlaying; }
    public int getAction()     { return action; }
    public long getTimeSent() { return timeSent; }
    public int getPositionMS() { return positionMS; }
    public String getUri()    {        return uri;    }
    public int getTrackNumber()    {        return track_number;    }
    public int getListSize()    {   return listSize; }
    public int getListPosition()    {   return listPosition; }

    public void setArtist(String artist)   { this.artist = artist; }
    public void setAlbum(String album)     { this.album = album;   }
    public void setGenre(String genre)     { this.genre = genre;   }
    public void setComposer(String composer)     { this.composer = composer;   }
    public void setTrackName(String track)     { this.track = track;    }
    public void setExternalId(String external_id) { this.external_id = external_id;  }
    public void setLength(int length)      { this.lengthSec = length;    }
    public void setPositionMS(int positionMS)    { this.positionMS = positionMS;  }
    public void setIsPlaying(boolean isPlaying)  { this.isPlaying = isPlaying;  }
    public void setAction(int action)            { this.action = action; }
    public void setTimeSent(long timeSent) { this.timeSent = timeSent; }
    public void setUri(String uri)    {        this.uri = uri;    }
    public void setTrackNumber(int track_number)    {        this.track_number = track_number;    }
    public void setListSize(int listSize)   {   this.listSize = listSize; }
    public void setListPosition(int listPosition)   {   this.listPosition = listPosition; }

    public void bulk_update(PodEmuMessage msg)
    {
        if(msg.getAction()!=PodEmuMessage.ACTION_QUEUE_CHANGED)
        {
            this.setIsPlaying(msg.isPlaying());
            this.setTimeSent(msg.getTimeSent());
            this.setAction(msg.getAction());
            this.setLength(msg.getLength());
            this.setExternalId(msg.getExternalId());
            this.setTrackName(msg.getTrackName());
            this.setAlbum(msg.getAlbum());
            this.setGenre(msg.getGenre());
            this.setComposer(msg.getComposer());
            this.setArtist(msg.getArtist());
            this.setPositionMS(msg.getPositionMS());
            this.setUri(msg.getUri());
            this.setTrackNumber(msg.getTrackNumber());
            this.setListSize(msg.getListSize());
            this.setListPosition(msg.getListPosition());
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

