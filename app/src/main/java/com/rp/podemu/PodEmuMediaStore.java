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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by rp on 10/31/15.
 */
public class PodEmuMediaStore
{

    public static final int MODE_PLAYLIST_SIZE_NORMAL = 0;
    public static final int MODE_PLAYLIST_SIZE_SINGLE = 1;
    public static final int MODE_PLAYLIST_SIZE_TRIPLE = 2;
    public static final int MODE_PLAYLIST_SIZE_FIXED =  3;
    public static final int MODE_PLAYLIST_SIZE_DEFAULT = MODE_PLAYLIST_SIZE_FIXED;



    private static Context context;
    private String ctrlAppDbName =null;
    private String ctrlAppProcessName =null;
    public boolean rebuildDbRequired=true;
    public boolean rebuildSelectionQueryRequired=true;

    private SQLiteDatabase db=null;
    private DbHelper dbHelper=null;

    private int selectionPlaylist = 0xffffffff;
    private int selectionGenre = 0xffffffff;
    private int selectionArtist = 0xffffffff;
    private int selectionComposer = 0xffffffff;
    private int selectionAlbum = 0xffffffff;
    private int selectionTrack = 0xffffffff;

    public final static byte SORT_BY_PLAYLIST = 0x01;
    public final static byte SORT_BY_ARTIST   = 0x02;
    public final static byte SORT_BY_ALBUM    = 0x03;
    public final static byte SORT_BY_GENRE    = 0x04;
    public final static byte SORT_BY_SONG     = 0x05;
    public final static byte SORT_BY_COMPOSER = 0x06;


/*    private boolean selectionGenreIsSet=false;
    private boolean selectionArtistIsSet=false;
    private boolean selectionComposerIsSet=false;
    private boolean selectionAlbumIsSet=false;
*/

    private int nextGenreId =0;
    private int nextArtistId =0;
    private int nextAlbumId =0;
    private int nextComposerId =0;
    private int nextTrackId =0;
    private int nextPlaylistId =0;
    private int playlistCountMode = MODE_PLAYLIST_SIZE_DEFAULT;

    private Cursor selectionCursor=null;
    private byte selectionSortOrder=SORT_BY_SONG;

    // this class is a singletone
    private static PodEmuMediaStore podEmuMediaStoreInstance=null;
    public static void initialize(Context c)
    {
        if(context==null || context!=c)
        {
            context = c;
            PodEmuLog.debug("PEMS: context changed: " + context.toString());
        }
        if (podEmuMediaStoreInstance == null)
        {
            podEmuMediaStoreInstance = new PodEmuMediaStore();
            PodEmuLog.debug("PEMS: new PEMS instance initialized.");
        }

    }

    public void setPlaylistCountMode(int playlistCountMode)
    {
        PodEmuMediaDB instance=PodEmuMediaDB.getInstance();

        rebuildDbRequired = ( rebuildDbRequired || (this.playlistCountMode != playlistCountMode) );

        this.playlistCountMode = playlistCountMode;
        reinitializePlaylistAndDB();

        if(instance!=null)
        {
            instance.signalConfigurationUpdated(this);
        }
    }

    public int getPlaylistCountMode()
    {
        return this.playlistCountMode;
    }

    // helper function
    public int getPlaylistCountSize()
    {
        switch (this.playlistCountMode)
        {
            case MODE_PLAYLIST_SIZE_SINGLE:
                return 1;
            case MODE_PLAYLIST_SIZE_FIXED:
                return 11;
            case MODE_PLAYLIST_SIZE_NORMAL:
                return MediaPlayback.getInstance().getCurrentPlaylist().getTrackCount();
            case MODE_PLAYLIST_SIZE_TRIPLE:
            default:
                return 3;
        }
    }

    public void setSelectionSortOrder(byte sortOrder)
    {
        selectionSortOrder = sortOrder;

        if(selectionSortOrder<1 || selectionSortOrder>6) selectionSortOrder = SORT_BY_SONG;
    }

    public String getCtrlAppProcessName()
    {
        return ctrlAppProcessName;
    }

    public void setCtrlAppProcessName(String app)
    {
        String prevCtrlAppDbName = ctrlAppDbName;
        ctrlAppProcessName = app;

        PodEmuLog.debug("PEMS: setting CTRL APP process name to: " + ctrlAppProcessName);
        PodEmuLog.debug("PEMS: setting CTRL APP db name to: " + ctrlAppDbName);

        switch (app)
        {
            case "com....spotify":
                ctrlAppDbName = app;
                break;
            default:
                ctrlAppDbName = "generic";
        }
        PodEmuLog.debug("PEMS: CTRL APP updated db name to: " + ctrlAppDbName);

        // if controlled app changed, we probably need to rebuild the db
        if(prevCtrlAppDbName==null || !prevCtrlAppDbName.equals(ctrlAppDbName)) rebuildDbRequired = true;
        PodEmuLog.debug("PEMS: rebuild DB required: " + rebuildDbRequired);

        if(rebuildDbRequired)
        {
            // now we can initialize playback engine and DB engine
            MediaPlayback.initialize(context);

            reinitializePlaylistAndDB();
        }
        MediaPlayback.setCtrlAppProcessName(ctrlAppProcessName);
    }

    private void reinitializePlaylistAndDB()
    {
        updateNextIds();

        if(PodEmuMediaDB.getInstance()==null)
        {
            PodEmuMediaDB.initialize(ctrlAppDbName);
        }

        if(rebuildDbRequired)
        {
            PodEmuMediaDB.getInstance().rebuildDB(this);
        }

        //PodEmuMediaDB.getInstance().rebuildDB(PodEmuMediaStore.getInstance());
        // now we need to initialize currentlyPlayingPlaylist and set track start
        selectionSetTrack(0);
        MediaPlayback.getInstance().getCurrentPlaylist().setCurrentTrackPosToStart();
    }

    public void updateNextIds()
    {
        Cursor c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_ARTISTS, null);
        c.moveToFirst();
        nextArtistId = c.getInt(0);

        c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_ALBUMS, null);
        c.moveToFirst();
        nextAlbumId = c.getInt(0);

        c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_PLAYLISTS, null);
        c.moveToFirst();
        nextPlaylistId = c.getInt(0);

        c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_COMPOSERS, null);
        c.moveToFirst();
        nextComposerId = c.getInt(0);

        c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_GENRES, null);
        c.moveToFirst();
        nextGenreId = c.getInt(0);

        c=db.rawQuery("select count(1) as count from " + DbHelper.TABLE_TRACKS, null);
        c.moveToFirst();
        nextTrackId = c.getInt(0);

    }

    public static PodEmuMediaStore getInstance()
    {
        return podEmuMediaStoreInstance;
    }

    PodEmuMediaStore()
    {
        dbHelper = new DbHelper(context);
        db = dbHelper.getWritableDatabase();
        db.execSQL("PRAGMA foreign_keys = ON;");
    }

    public class Track
    {
        public Integer id=null;

        public String name=null;
        public String album=null;
        public String artist=null;
        public String composer=null;
        public String genre=null;
        public String uri=null;
        public String external_id=null;
        public long duration = 0;
        public int    track_number = 0;

        public Integer artist_id = null;
        public Integer album_id = null;
        public Integer composer_id = null;
        public Integer genre_id = null;

        public Track()
        {
        }

        public Track(Track t)
        {
            copyFrom(t);
        }

        public void copyFrom(Track t)
        {
            name=t.name;
            album=t.album;
            artist=t.artist;
            composer=t.composer;
            uri=t.uri;
            external_id=t.external_id;
            duration =t.duration;
            track_number=t.track_number;
            artist_id=t.artist_id;
            album_id=t.album_id;
            composer_id =t.composer_id;
            genre_id=t.genre_id;
        }

        public PodEmuMessage toPodEmuMessage()
        {
            PodEmuMessage msg = new PodEmuMessage();
            msg.setTrackName(name);
            msg.setAlbum(album);
            msg.setArtist(artist);
            msg.setComposer(composer);
            msg.setUri(uri);
            msg.setExternalId(external_id);
            msg.setDurationMS(duration);
            msg.setTrackNumber(track_number);
            msg.setListSize(-1);
            msg.setListPosition(-1);
            msg.setApplication(ctrlAppProcessName);
            msg.initialize();

            return msg;
        }

        public String getPrintableObject()
        {
            return "[" + this.getClass().getCanonicalName().replaceAll(".*PodEmuMediaStore","") + " {id="+id+", name="+name+", uri="+uri+", external_id="+external_id+", track_number="+track_number+"} ]";
        }

    }

    public class Playlist
    {
        public Integer id=null;
        public String name=null;
        public String uri=null;
        public String external_id = null;

        public boolean isShuffleOn = false;

        public static final int REPEAT_MODE_OFF = 0;
        public static final int REPEAT_MODE_track = 1;
        public static final int REPEAT_MODE_ALL = 2;
        public int repeat_mode = REPEAT_MODE_OFF;

        private int trackCount=0;
        private int currentTrack=0;
        private int increment=1;

        private Map<Integer, Track> trackList = new LinkedHashMap<>();

        public void clear()
        {
            trackList.clear();
        }

        public void setIncrement(int i)
        {
            if(i>=0)
            {
                increment = +1;
                PodEmuLog.debug("PEMS: increment set to POSITIVE");
            }
            else
            {
                increment = -1;
                PodEmuLog.debug("PEMS: increment set to NEGATIVE");
            }
        }

        public String getPrintableObject()
        {
            return "[" + this.getClass().getCanonicalName().replaceAll(".*PodEmuMediaStore","") + " {id="+id+", name="+name+", uri="+uri+", external_id="+external_id+"} ]";
        }

        public void add(Track track)
        {
            if(track!=null)
            {
                trackList.put(trackCount, track);
                trackCount++;
            }
            else
            {
                PodEmuLog.error("PEMS: trying to add null pointer track");
            }
        }

        public int getTrackCount()
        {
            return trackCount;
        }

        public int getCurrentTrackPos()
        {
            return currentTrack;
        }

        public Track getCurrentTrack()
        {
            return trackList.get(currentTrack);
        }

        public Track getTrack(int pos)
        {
            if(!trackList.containsKey(pos))
            {
                PodEmuLog.error("PEMS: playlist track request out of boundaries (playlist size: " + trackCount + ", requested track: " + pos);
                return null;
            }

            return trackList.get(pos);
        }

        public void setCurrentTrack(int pos)
        {
            PodEmuLog.debug("PEMS: setCurrentTrack: " + pos);

            if(pos>=trackCount || pos<0)
            {
                PodEmuLog.error("PEMS: playlist set request out of boundaries (playlist size: " + trackCount + ", jump request: " + pos);
            }
            else
            {
                currentTrack=pos;
            }
        }

        public void positionPlusPlus()
        {
            int pos = (currentTrack+1)%trackCount;
            setCurrentTrack(pos);
        }


        public void positionMinusMinus()
        {
            int pos = (currentTrack+trackCount-1)%trackCount;
            setCurrentTrack(pos);
        }

        public void positionIncrement()
        {
            if(increment>0) positionPlusPlus();
            else positionMinusMinus();
        }

        public void setCurrentTrackPosToStart()
        {
            currentTrack = 0;
            if ( PodEmuMediaStore.getInstance().getPlaylistCountMode() ==
                    PodEmuMediaStore.MODE_PLAYLIST_SIZE_TRIPLE ) setCurrentTrackToCenter();
        }

        private void setCurrentTrackToCenter()
        {
            setCurrentTrack( (getTrackCount() - 1) / 2);
        }

    }


    public class Genre extends Playlist
    {
/*        public Integer id=null;
        public String name=null;
        public String external_id=null;
        public String uri=null;
        */
    }

    public class Artist extends Genre
    {
        /*
        public Integer id=null;
        public String name=null;
        public String external_id=null;
        public String uri=null;
        */
    }

    public class Composer extends Genre
    {
        /*
        public Integer id=null;
        public String name=null;
        public String external_id=null;
        public String uri=null;
        */
    }

    public class Album extends Artist
    {
        /*
        public String name=null;
        public Integer id=null;
        public String external_id=null;
        public String uri=null;
        */
        public Integer artist_id=null;
        public int year=0;
    }


    //-----------------------------------------------------------------//
    //--------- DATABASE HELPER CLASSES -------------------------------//

    public class DbHelper extends SQLiteOpenHelper
    {
        protected static final String COLUMN_NAME="name";
        protected static final String COLUMN_ID="id";
        protected static final String COLUMN_EXTERNAL_ID="external_id";
        protected static final String COLUMN_URI="uri";
        protected static final String COLUMN_GENRE_ID="genre_id";
        protected static final String COLUMN_ARTIST_ID="artist_id";
        protected static final String COLUMN_COMPOSER_ID="composer_id";
        protected static final String COLUMN_TRACK_ID="track_id";
        protected static final String COLUMN_APP="app";
        protected static final String COLUMN_TRACK_NUMBER="track_number";
        protected static final String COLUMN_DURATION ="length";
        protected static final String COLUMN_PLAYLIST_ID="playlist_id";
        protected static final String COLUMN_YEAR="year";
        protected static final String COLUMN_ALBUM_ID="album_id";
        protected static final String COLUMN_LAST_UPDATED="last_updated";

        protected static final String TABLE_PLAYLISTS="playlists";
        protected static final String TABLE_ARTISTS="artists";
        protected static final String TABLE_ALBUMS="albums";
        protected static final String TABLE_COMPOSERS="composers";
        protected static final String TABLE_TRACKS="tracks";
        protected static final String TABLE_PLAYLIST_TRACKS="playlist_tracks";
        protected static final String TABLE_DB_STATUS="db_status";
        protected static final String TABLE_GENRES="genres";


        private String SQL_CREATE_TABLES[]={
                "CREATE TABLE " + TABLE_PLAYLISTS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_ARTISTS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_ALBUMS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_ARTIST_ID + " INTEGER NOT NULL,\n" + // main artist
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        COLUMN_YEAR + " INTEGER,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_ARTIST_ID + ") REFERENCES " + TABLE_ARTISTS + "(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_GENRES + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_COMPOSERS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_TRACKS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_URI + " TEXT,\n" +
                        COLUMN_EXTERNAL_ID + " TEXT,\n" + // external id, eg. Spotify ID of the object
                        COLUMN_TRACK_NUMBER + " INTEGER NOT NULL,\n" + // track number in the album
                        COLUMN_DURATION + " INTEGER NOT NULL,\n" +
                        COLUMN_NAME + " TEXT NOT NULL,\n" +
                        COLUMN_ALBUM_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_ARTIST_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_COMPOSER_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_GENRE_ID + " INTEGER NOT NULL,\n" +
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_ALBUM_ID + ") REFERENCES " + TABLE_ALBUMS + "(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_ARTIST_ID + ") REFERENCES " + TABLE_ARTISTS + "(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_COMPOSER_ID + ") REFERENCES " + TABLE_COMPOSERS + "(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_GENRE_ID + ") REFERENCES " + TABLE_GENRES + "(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_PLAYLIST_TRACKS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_PLAYLIST_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_TRACK_ID + " INTEGER NOT NULL,\n" +
                        COLUMN_TRACK_NUMBER + " INTEGER NOT NULL,\n" + // track number in the playlist
                        "PRIMARY KEY(" + COLUMN_APP + ", " + COLUMN_PLAYLIST_ID + ", " + COLUMN_TRACK_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_PLAYLIST_ID + ") REFERENCES " + TABLE_PLAYLISTS + "(" + COLUMN_APP + ", " + COLUMN_ID + "),\n" +
                        "FOREIGN KEY (" + COLUMN_APP + ", " + COLUMN_TRACK_ID + ") REFERENCES " + TABLE_TRACKS + "(" + COLUMN_APP + ", " + COLUMN_ID + ")\n" +
                        ");",
                "CREATE TABLE " + TABLE_DB_STATUS + " (\n" +
                        COLUMN_APP + " TEXT NOT NULL,\n" +
                        COLUMN_LAST_UPDATED + " INTEGER NOT NULL\n" +
                        ");"
        };

        private String SQL_DROP_TABLES[] = {
                "DROP TABLE IF EXISTS " + TABLE_PLAYLIST_TRACKS + ";",
                "DROP TABLE IF EXISTS " + TABLE_TRACKS + ";",
                "DROP TABLE IF EXISTS " + TABLE_ALBUMS + ";",
                "DROP TABLE IF EXISTS " + TABLE_PLAYLISTS + ";",
                "DROP TABLE IF EXISTS " + TABLE_ARTISTS + ";",
                "DROP TABLE IF EXISTS " + TABLE_GENRES + ";",
                "DROP TABLE IF EXISTS " + TABLE_COMPOSERS + ";",
                "DROP TABLE IF EXISTS " + TABLE_DB_STATUS + ";"
        };
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 16;
        public static final String DATABASE_NAME = "PodEmuMediaStore.db";

        public DbHelper(Context context)
        {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db)
        {
            for(int i=0; i<SQL_CREATE_TABLES.length; i++)
            {
                db.execSQL(SQL_CREATE_TABLES[i]);
            }
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            for(int i=0; i<SQL_DROP_TABLES.length; i++)
            {
                db.execSQL(SQL_DROP_TABLES[i]);
            }            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    //-----------------------------------------------------------------//

    public interface DBLoadInterface
    {
        void rebuildDB(PodEmuMediaStore mediaStore);
    }

    //-----------------------------------------------------------------//

    public Integer addTrack(Track track)
    {
        int id= nextTrackId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_TRACK_NUMBER, track.track_number);
        values.put(DbHelper.COLUMN_DURATION, track.duration);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, track.external_id);
        values.put(DbHelper.COLUMN_ALBUM_ID, track.album_id);
        values.put(DbHelper.COLUMN_ARTIST_ID, track.artist_id);
        values.put(DbHelper.COLUMN_GENRE_ID, track.genre_id);
        values.put(DbHelper.COLUMN_COMPOSER_ID, track.composer_id);
        values.put(DbHelper.COLUMN_NAME, track.name);
        values.put(DbHelper.COLUMN_URI, track.uri);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_TRACKS, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Artist data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Track not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added artist to DB.");

        track.id = id;
        nextTrackId++;
        PodEmuLog.debug("PEMS: addTrack: " + track.getPrintableObject());

        return id;
    }

    public synchronized void updateTrack(Track track)
    {
        ContentValues values = new ContentValues();

        //values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        //values.put(DbHelper.COLUMN_ID, track.id);
        values.put(DbHelper.COLUMN_NAME, track.name);

        int count;
        String where=DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "' and " + DbHelper.COLUMN_ID  + "='" + track.id + "'";

        try
        {
            count = db.update(DbHelper.TABLE_TRACKS, values, where, null);
        }
        catch(Exception e)
        {
            PodEmuLog.error("PEMS: update failed with values: " + values + ", where: " + where);
            PodEmuLog.printStackTrace(e);
            throw e;
        }


        if(count != 1)
        {
            PodEmuLog.error("PEMS: Sth went wrong during track name update (" + where + "). Rows updated: " + count);
        }

    }


    /**
     * Function addArtist adds artist information to the database.
     * @param artist - album object to be added to the database
     * @return - return record id. Null in case of error.
     */
    public synchronized Integer addArtist(Artist artist)
    {
        int id= nextArtistId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, artist.external_id);
        values.put(DbHelper.COLUMN_NAME, artist.name);
        values.put(DbHelper.COLUMN_URI, artist.uri);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_ARTISTS, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Artist data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Artist not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added artist to DB.");

        artist.id = id;
        nextArtistId++;
        PodEmuLog.debug("PEMS: addArtist: " + artist.getPrintableObject());

        return id;
    }

    /**
     * Function addAlbum adds album information to the database.
     * @param album - album object to be added to the database
     * @return - return record id. Null in case of error.
     */
    public synchronized Integer addAlbum(Album album)
    {
        int id= nextAlbumId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_ARTIST_ID, album.artist_id);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, album.external_id);
        values.put(DbHelper.COLUMN_NAME, album.name);
        values.put(DbHelper.COLUMN_URI, album.uri);
        values.put(DbHelper.COLUMN_YEAR, album.year);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_ALBUMS, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Album data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Album not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added album to DB.");

        album.id = id;
        nextAlbumId++;
        PodEmuLog.debug("PEMS: addAlbum: " + album.getPrintableObject());

        return id;
    }

    public synchronized Integer addGenre(Genre genre)
    {
        int id= nextGenreId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, genre.external_id);
        values.put(DbHelper.COLUMN_NAME, genre.name);
        values.put(DbHelper.COLUMN_URI, genre.uri);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_GENRES, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Genre data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Genre not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added genre to DB.");

        genre.id = id;
        nextGenreId++;
        PodEmuLog.debug("PEMS: addGenre: " + genre.getPrintableObject());

        return id;
    }

    public synchronized Integer addPlaylist(Playlist playlist)
    {
        int id= nextPlaylistId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, playlist.external_id);
        values.put(DbHelper.COLUMN_NAME, playlist.name);
        values.put(DbHelper.COLUMN_URI, playlist.uri);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_PLAYLISTS, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Playlist data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Playlist not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added playlist to DB.");

        playlist.id = id;

        // now we need to loop through tracks and add them to playlist
        // tracks should be already added to DB and have their ID set
        for(Map.Entry<Integer,Track> entry: playlist.trackList.entrySet())
        {
            values = new ContentValues();

            values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
            values.put(DbHelper.COLUMN_PLAYLIST_ID, playlist.id);
            values.put(DbHelper.COLUMN_TRACK_ID, entry.getValue().id);
            values.put(DbHelper.COLUMN_TRACK_NUMBER, entry.getKey());

            rowId=db.insert(DbHelper.TABLE_PLAYLIST_TRACKS, null, values);
            if(rowId == -1)
            {
                PodEmuLog.error("PEMS: Playlist track not added to DB.");
                return null;
            }

        }

        nextPlaylistId++;
        PodEmuLog.debug("PEMS: addPlaylist: " + playlist.getPrintableObject());

        return id;
    }

    public synchronized Integer addComposer(Composer composer)
    {
        int id= nextComposerId;
        ContentValues values = new ContentValues();

        values.put(DbHelper.COLUMN_APP, ctrlAppDbName);
        values.put(DbHelper.COLUMN_ID, id);
        values.put(DbHelper.COLUMN_EXTERNAL_ID, composer.external_id);
        values.put(DbHelper.COLUMN_NAME, composer.name);
        values.put(DbHelper.COLUMN_URI, composer.uri);

        long rowId;
        rowId=db.insert(DbHelper.TABLE_COMPOSERS, null, values);

        // TODO: override toString for all objects (albums, playlists, etc)
        // PodEmuLog.debug("Composer data: " + values.toString());
        if(rowId == -1)
        {
            PodEmuLog.error("PEMS: Composer not added to DB.");
            return null;
        }

        //PodEmuLog.debug("Successfully added composer to DB.");

        composer.id = id;
        nextComposerId++;
        PodEmuLog.debug("PEMS: addComposer: " + composer.getPrintableObject());

        return id;
    }

    public void clear()
    {
        PodEmuLog.debug("PEMS: clear");

        String queries[] = {
                "DELETE FROM " + DbHelper.TABLE_PLAYLIST_TRACKS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_TRACKS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_ALBUMS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_PLAYLISTS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_ARTISTS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_GENRES + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'",
                "DELETE FROM " + DbHelper.TABLE_COMPOSERS + " WHERE " + DbHelper.COLUMN_APP + "='" + ctrlAppDbName + "'"
        };

        for(int i=0; i<queries.length; i++)
        {
            db.execSQL(queries[i]);
        }

        updateNextIds();

        rebuildDbRequired = true;
    }

    //-----------------------------------------------------------------------//

    public void selectionReset()
    {
        PodEmuLog.debug("PEMS: selectionReset");

        selectionPlaylist = 0xffffffff;
        selectionGenre = 0xffffffff;
        selectionArtist = 0xffffffff;
        selectionComposer = 0xffffffff;
        selectionAlbum = 0xffffffff;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }

    /** function will map selection position to actual id in DB
     *
     * @param pos - position on selection
     * @return - id in DB, -1 in case of error
     */
    public int selectionMapPosToId(int pos)
    {
        int id;

        if(rebuildSelectionQueryRequired)
        {
            PodEmuLog.error("PEMS: selectionMapPosToId: trying to access cursor before initialization");
        }

        try
        {
            if(pos == 0xffffffff)
            {
                pos=0;
            }
            selectionCursor.moveToPosition(pos);
            id = selectionCursor.getInt(0);
        }
        catch(Exception e)
        {
            return -1;
        }
        return id;
    }

    public void selectionSetPlaylist(int playlist_id)
    {
        PodEmuLog.debug("PEMS: selectionSetPlaylist:" + playlist_id);

        selectionPlaylist = playlist_id;
        selectionGenre = 0xffffffff;
        selectionArtist = 0xffffffff;
        selectionComposer = 0xffffffff;
        selectionAlbum = 0xffffffff;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }

    public void selectionSetGenre(int genre_id)
    {
        PodEmuLog.debug("PEMS: selectionSetGenre:" + genre_id);

        selectionGenre = genre_id;
        selectionArtist = 0xffffffff;
        selectionComposer = 0xffffffff;
        selectionAlbum = 0xffffffff;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }

    public void selectionSetArtist(int artist_id)
    {
        PodEmuLog.debug("PEMS: selectionSetArtist:" + artist_id);

        selectionArtist = artist_id;
        selectionComposer = 0xffffffff;
        selectionAlbum = 0xffffffff;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }

    public void selectionSetComposer(int composer_id)
    {
        PodEmuLog.debug("PEMS: selectionSetComposer:" + composer_id);

        selectionComposer = composer_id;
        selectionAlbum = 0xffffffff;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }

    public void selectionSetAlbum(int album_id)
    {
        PodEmuLog.debug("PEMS: selectionSetAlbum:" + album_id);

        selectionAlbum = album_id;
        selectionTrack = 0xffffffff;

        rebuildSelectionQueryRequired = true;
    }


    public void selectionSetTrack(int track_id)
    {
        PodEmuLog.debug("PEMS: selectionSetTrack:" + track_id);

        selectionTrack = track_id;

        MediaPlayback.getInstance().setCurrentPlaylist(selectionBuildPlaylist());
        MediaPlayback.getInstance().getCurrentPlaylist().setCurrentTrack(track_id);
    }


    public Cursor getSelectionCursor()
    {
        return selectionCursor;
    }

    /**
     * Function will prepare where clause for selecting from TRACKS table
     * @return
     */
    private String selectionPrepareWhere()
    {
        String concat=" AND ";
        String where=" WHERE 1=1 ";

        if(selectionPlaylist != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_ID + " IN (SELECT " + DbHelper.COLUMN_TRACK_ID +
                    " FROM " + DbHelper.TABLE_PLAYLIST_TRACKS + " WHERE " + DbHelper.COLUMN_PLAYLIST_ID + "=" + selectionPlaylist + ") ";
        }

        if(selectionGenre != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_GENRE_ID + "=" + selectionGenre;
        }

        if(selectionArtist != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_ARTIST_ID + "=" + selectionArtist;
        }

        if(selectionComposer != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_COMPOSER_ID + "=" + selectionComposer;
        }

        if(selectionAlbum != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_ALBUM_ID + "=" + selectionAlbum;
        }

        if(selectionTrack != 0xffffffff)
        {
            where += concat + DbHelper.COLUMN_ID + "=" + selectionTrack;
        }

        return where;
    }

    /**
     * Function should be called whenever 0x0018 command is issued to reinitialize the playlist
     * for current selecion
     * @param type - category type to return records for
     *                  0x01 - Playlist
     *                  0x02 - Artist
     *                  0x03 - Album
     *                  0x04 - Genre
     *                  0x05 - Track
     *                  0x06 - Composer
     * @return - number of records if success, -1 if failed
     */
    public synchronized int selectionInitializeDB(int type)
    {
        PodEmuLog.debug("PEMS: selectionInitializeDB:" + type);

        selectionCursor=selectionExecute(type);
        rebuildSelectionQueryRequired = false;

        return selectionCursor.getCount();
    }

    /**
     * Function will prepare the query for given type, execute it and return the cursor.
     * @param type
     * @return
     */
    public synchronized Cursor selectionExecute(int type)
    {
        String where = selectionPrepareWhere();
        String query = "select " + DbHelper.COLUMN_ID + ", " + DbHelper.COLUMN_NAME;
        switch(type)
        {
            case 0x01: // playlist
                query += " FROM " + DbHelper.TABLE_PLAYLISTS;
                break;
            case 0x02: // artist
                query += " FROM " +
                        DbHelper.TABLE_ARTISTS + " where exists (select 1 from " +
                        DbHelper.TABLE_TRACKS + where + " AND " + DbHelper.COLUMN_ARTIST_ID + "=" +
                        DbHelper.TABLE_ARTISTS + "." + DbHelper.COLUMN_ID + ")";
                break;
            case 0x03: // album
                query += " FROM " +
                        DbHelper.TABLE_ALBUMS + " where exists (select 1 from " +
                        DbHelper.TABLE_TRACKS + where + " AND " + DbHelper.COLUMN_ALBUM_ID + "=" +
                        DbHelper.TABLE_ALBUMS + "." + DbHelper.COLUMN_ID + ")";
                break;
            case 0x04: // genre
                query += " FROM " +
                        DbHelper.TABLE_GENRES + " where exists (select 1 from " +
                        DbHelper.TABLE_TRACKS + where + " AND " + DbHelper.COLUMN_GENRE_ID + "=" +
                        DbHelper.TABLE_GENRES + "." + DbHelper.COLUMN_ID + ")";
                break;
            case 0x05: // track
                query += ", " + DbHelper.COLUMN_TRACK_NUMBER +
                        ", " + DbHelper.COLUMN_DURATION +
                        " FROM " +
                        DbHelper.TABLE_TRACKS + where;
                break;
            case 0x06: // composer
                query += " FROM " +
                        DbHelper.TABLE_COMPOSERS + " where exists (select 1 from " +
                        DbHelper.TABLE_TRACKS + where + " AND " + DbHelper.COLUMN_COMPOSER_ID + "=" +
                        DbHelper.TABLE_COMPOSERS + "." + DbHelper.COLUMN_ID + ")";
                break;
            case 0x07: // audiobook
            case 0x08: // podcast
                // this should always return empty cursor
                query += " FROM " +
                        DbHelper.TABLE_PLAYLISTS + " WHERE 1=0";
                break;
            default:
                return null;

        }
        PodEmuLog.debug("PEMS: DB queried: " + query);

        return db.rawQuery(query, null);
    }


    /**
     * Function returns the playlist basing on current selection
     * @return
     */
    public PodEmuMediaStore.Playlist selectionBuildPlaylist()
    {
        PodEmuLog.debug("PEMS: selectionBuildPlaylist");

        String where=selectionPrepareWhere();
        String query="SELECT " +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_ID + ", " +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_NAME + " as track_name," +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_URI + ", " +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_EXTERNAL_ID + ", " +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_TRACK_NUMBER + ", " +
                        DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_DURATION + ", " +
                        DbHelper.TABLE_ALBUMS + "." + DbHelper.COLUMN_NAME + " AS album_name," +
                        DbHelper.TABLE_ARTISTS + "." + DbHelper.COLUMN_NAME + " AS artist_name," +
                        DbHelper.TABLE_GENRES + "." + DbHelper.COLUMN_NAME + " AS genre_name," +
                        DbHelper.TABLE_COMPOSERS + "." + DbHelper.COLUMN_NAME + " AS composer_name" +
                " FROM " + DbHelper.TABLE_TRACKS +
                " LEFT JOIN " + DbHelper.TABLE_GENRES +
                        " ON " + DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_GENRE_ID +
                        "=" + DbHelper.TABLE_GENRES + "." + DbHelper.COLUMN_ID +
                " LEFT JOIN " + DbHelper.TABLE_ALBUMS +
                        " ON " + DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_ALBUM_ID +
                        "=" + DbHelper.TABLE_ALBUMS + "." + DbHelper.COLUMN_ID +
                " LEFT JOIN " + DbHelper.TABLE_ARTISTS +
                        " ON " + DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_ARTIST_ID +
                        "=" + DbHelper.TABLE_ARTISTS + "." + DbHelper.COLUMN_ID +
                " LEFT JOIN " + DbHelper.TABLE_COMPOSERS +
                        " ON " + DbHelper.TABLE_TRACKS + "." + DbHelper.COLUMN_COMPOSER_ID +
                        "=" + DbHelper.TABLE_COMPOSERS + "." + DbHelper.COLUMN_ID;

        PodEmuLog.debug("PEMS: build playlist: " + query);

        selectionCursor=db.rawQuery(query, null);

        Playlist newPlaylist=new Playlist();

        selectionCursor.moveToFirst();
        do
        {
            Track track=new Track();
            track.name         = selectionCursor.getString(selectionCursor.getColumnIndex("track_name"));
            track.track_number = selectionCursor.getInt(selectionCursor.getColumnIndex(DbHelper.COLUMN_TRACK_NUMBER));
            track.duration     = selectionCursor.getLong(selectionCursor.getColumnIndex(DbHelper.COLUMN_DURATION));
            track.album        = selectionCursor.getString(selectionCursor.getColumnIndex("album_name"));
            track.artist       = selectionCursor.getString(selectionCursor.getColumnIndex("artist_name"));
            track.genre        = selectionCursor.getString(selectionCursor.getColumnIndex("genre_name"));
            track.composer     = selectionCursor.getString(selectionCursor.getColumnIndex("composer_name"));
            track.uri          = selectionCursor.getString(selectionCursor.getColumnIndex(DbHelper.COLUMN_URI));
            track.external_id  = selectionCursor.getString(selectionCursor.getColumnIndex(DbHelper.COLUMN_EXTERNAL_ID));
            newPlaylist.add(track);

            if(selectionCursor.isLast()) break;
        } while(selectionCursor.moveToNext());

        return newPlaylist;
    }
}
