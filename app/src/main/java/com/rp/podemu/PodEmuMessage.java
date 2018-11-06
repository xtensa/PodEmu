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


import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

public class PodEmuMessage implements Parcelable
{
    private String artist;
    private String album;
    private String track;
    private String external_id;
    private String genre="Unknown Genre";
    private String composer="Unknown Composer";
    private String uri="unknown uri";
    private String application="Unknown App";
    private long durationMS;
    private long positionMS;
    private boolean isPlaying;
    private int action;
    private long timeSent;
    private int track_number;
    private int listSize;
    private int listPosition;
    private boolean isInitialized = false;

    private boolean enableCyrillicTransliteration=false;

    public final static int ACTION_METADATA_CHANGED=1;
    public final static int ACTION_PLAYBACK_STATE_CHANGED=2;
    public final static int ACTION_QUEUE_CHANGED=4;

    public String getArtist()        { return transliterate(artist); }
    public String getAlbum()         { return transliterate(album); }
    public String getTrackName()     { return transliterate(track); }
    public String getExternalId()    { return external_id; }
    public String getGenre()         { return transliterate(genre); }
    public String getComposer()      { return transliterate(composer); }
    public String getApplication()   { return application; }
    public boolean isPlaying()       { return isPlaying; }
    public int     getAction()       { return action; }
    public long    getTimeSent()     { return timeSent; }
    public long    getPositionMS()   { return positionMS; }
    public long    getDurationMS()   { return durationMS; }
    public String  getUri()          { return uri;    }
    public int     getTrackNumber()  { return track_number;    }
    public int     getListSize()     { return listSize; }
    public int     getListPosition() { return listPosition; }
    public boolean isInitialized()   { return isInitialized; }

    public void setArtist(String artist)           { this.artist = artist; }
    public void setAlbum(String album)             { this.album = album;   }
    public void setGenre(String genre)             { this.genre = genre;   }
    public void setComposer(String composer)       { this.composer = composer;   }
    public void setTrackName(String track)         { this.track = track;    }
    public void setExternalId(String external_id)  { this.external_id = external_id;  }
    public void setApplication(String application) { this.application = application; }
    public void setDurationMS(long durationMS)     { this.durationMS = durationMS;    }
    public void setPositionMS(long positionMS)     { this.positionMS = positionMS;  }
    public void setIsPlaying(boolean isPlaying)    { this.isPlaying = isPlaying;  }
    public void setAction(int action)              { this.action = action; }
    public void setTimeSent(long timeSent)         { this.timeSent = timeSent; }
    public void setUri(String uri)                 { this.uri = uri;    }
    public void setTrackNumber(int track_number)   { this.track_number = track_number;    }
    public void setListSize(int listSize)          { this.listSize = listSize; }
    public void setListPosition(int listPosition)  { this.listPosition = listPosition; }

    public void setEnableCyrillicTransliteration(boolean enableTranslit) { enableCyrillicTransliteration = enableTranslit; }


    private Map<Character,String> translitMap = new HashMap<>();

    public PodEmuMessage()
    {
        translitMap.put('А',"A");
        translitMap.put('Б',"B");
        translitMap.put('В',"V");
        translitMap.put('Г',"G");
        translitMap.put('Д',"D");
        translitMap.put('Е',"E");
        translitMap.put('Ё',"JO");
        translitMap.put('Ж',"ZH");
        translitMap.put('З',"Z");
        translitMap.put('И',"I");
        translitMap.put('Й',"J");
        translitMap.put('К',"K");
        translitMap.put('Л',"L");
        translitMap.put('М',"M");
        translitMap.put('Н',"N");
        translitMap.put('О',"O");
        translitMap.put('П',"P");
        translitMap.put('Р',"R");
        translitMap.put('С',"S");
        translitMap.put('Т',"T");
        translitMap.put('У',"U");
        translitMap.put('Ф',"F");
        translitMap.put('Х',"H");
        translitMap.put('Ц',"C");
        translitMap.put('Ч',"CH");
        translitMap.put('Ш',"SH");
        translitMap.put('Щ',"SHH");
        translitMap.put('Ъ',"");
        translitMap.put('Ы',"Y");
        translitMap.put('Ь',"'");
        translitMap.put('Э',"E");
        translitMap.put('Ю',"JU");
        translitMap.put('Я',"JA");


        translitMap.put('а',"a");
        translitMap.put('б',"b");
        translitMap.put('в',"v");
        translitMap.put('г',"g");
        translitMap.put('д',"d");
        translitMap.put('е',"e");
        translitMap.put('ё',"jo");
        translitMap.put('ж',"zh");
        translitMap.put('з',"z");
        translitMap.put('и',"i");
        translitMap.put('й',"j");
        translitMap.put('к',"k");
        translitMap.put('л',"l");
        translitMap.put('м',"m");
        translitMap.put('н',"n");
        translitMap.put('о',"o");
        translitMap.put('п',"p");
        translitMap.put('р',"r");
        translitMap.put('с',"s");
        translitMap.put('т',"t");
        translitMap.put('у',"u");
        translitMap.put('ф',"f");
        translitMap.put('х',"h");
        translitMap.put('ц',"c");
        translitMap.put('ч',"ch");
        translitMap.put('ш',"sh");
        translitMap.put('щ',"shh");
        translitMap.put('ъ',"");
        translitMap.put('ы',"y");
        translitMap.put('ь',"'");
        translitMap.put('э',"e");
        translitMap.put('ю',"ju");
        translitMap.put('я',"ja");


        // Additional characters not covered by Normalizer
        translitMap.put('Ł',"L");
        translitMap.put('ł',"l");

    }

    public void bulk_update(PodEmuMessage msg)
    {
        if(msg.getAction()!=PodEmuMessage.ACTION_QUEUE_CHANGED)
        {
            this.setIsPlaying(msg.isPlaying());
            this.setTimeSent(msg.getTimeSent());
            this.setAction(msg.getAction());
            this.setDurationMS(msg.getDurationMS());
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
            this.setApplication(msg.getApplication());
            this.isInitialized = true;
        }
    }

    public String getLengthHumanReadable()
    {
        long length=getDurationMS();
        int hours=(int)(length/1000/60/60);
        int mins=(int)(length-hours*60*60*1000)/1000/60;
        int secs=(int)(length-hours*60*60*1000-mins*60*1000)/1000;

        return String.format("%02d:%02d", mins, secs);
    }


    private String transliterate(String str)
    {
        if(!enableCyrillicTransliteration || str == null)
            return str;
        else
        {
            StringBuilder transStr = new StringBuilder();
            for (int i=0; i<str.length(); i++)
            {
                transStr.append(diacriticsReplace(str.charAt(i), (i+1<str.length() && Character.isLowerCase(str.charAt(i+1))) ) );
            }

            String normalized = java.text.Normalizer.normalize(transStr.toString(), java.text.Normalizer.Form.NFD);
            String finalStr = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

            return finalStr;
        }

    }

    private String diacriticsReplace(char c, boolean nextCharIsLowerCase)
    {
        String str = translitMap.get(c);
        if(str == null)
            str = String.valueOf(c);
        else if(str.length()>1 && nextCharIsLowerCase)
        {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(str.charAt(0));
            for(int i=1; i<str.length(); i++)
            {
                stringBuilder.append(Character.toLowerCase(str.charAt(i)));
            }
            str = stringBuilder.toString();
        }
        //PodEmuLog.error("PEM: translit: " + str);
        return str;
    }


    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags)
    {
        // order is meaningful
        out.writeString(artist);
        out.writeString(album);
        out.writeString(track);
        out.writeString(external_id);
        out.writeString(genre);
        out.writeString(composer);
        out.writeString(uri);
        out.writeString(application);
        out.writeLong(durationMS);
        out.writeLong(positionMS);
        out.writeInt(isPlaying?1:0);
        out.writeInt(action);
        out.writeLong(timeSent);
        out.writeInt(track_number);
        out.writeInt(listSize);
        out.writeInt(listPosition);

    }



    // this is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<PodEmuMessage> CREATOR = new Parcelable.Creator<PodEmuMessage>()
    {
        public PodEmuMessage createFromParcel(Parcel in)
        {
            return new PodEmuMessage(in);
        }

        public PodEmuMessage[] newArray(int size)
        {
            return new PodEmuMessage[size];
        }
    };

    private PodEmuMessage(Parcel in)
    {
        // order is meaningful
        artist = in.readString();
        album = in.readString();
        track = in.readString();
        external_id = in.readString();
        genre = in.readString();
        composer = in.readString();
        uri = in.readString();
        application = in.readString();
        durationMS = in.readLong();
        positionMS = in.readLong();
        isPlaying = (in.readInt()==1);
        action = in.readInt();
        timeSent = in.readLong();
        track_number = in.readInt();
        listSize = in.readInt();
        listPosition = in.readInt();

        isInitialized = true;
    }

}

