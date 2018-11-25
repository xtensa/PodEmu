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
 * Created by rp on 10/31/15.
 */
public class PodEmuMediaDB_Generic extends PodEmuMediaDB
{


    @Override
    public void rebuildDB(PodEmuMediaStore mediaStore)
    {
        rebuildDB(mediaStore, PodEmuMediaStore.getInstance().getPlaylistCountSize());
    }

    public synchronized void rebuildDB(PodEmuMediaStore mediaStore, int trackCount)
    {
        PodEmuLog.debug("PEDB: FUNCTION START rebuildDB, trackCount=" + trackCount);

        PodEmuMediaStore.Artist artist = mediaStore.new Artist();
        PodEmuMediaStore.Genre genre = mediaStore.new Genre();
        PodEmuMediaStore.Composer composer = mediaStore.new Composer();
        PodEmuMediaStore.Album album = mediaStore.new Album();
        PodEmuMediaStore.Playlist playlist = mediaStore.new Playlist();
        PodEmuMediaStore.Track track;
        PodEmuMediaStore.Track trackFirst=mediaStore.new Track();;

        // db cannot be empty
        if(trackCount<1) trackCount=1;

        mediaStore.clear();

        artist.name="Generic artist";
        trackFirst.artist_id = mediaStore.addArtist(artist);

        genre.name="Generic genre";
        trackFirst.genre_id = mediaStore.addGenre(genre);

        composer.name="Generic composer";
        trackFirst.composer_id = mediaStore.addComposer(composer);

        album.name="Generic album";
        album.artist_id=trackFirst.artist_id;
        trackFirst.album_id=mediaStore.addAlbum(album);

        trackFirst.name="Track 001";
        trackFirst.id=mediaStore.addTrack(trackFirst);
        playlist.add(trackFirst);

        // first element already added so we start with second
        for(int i=1; i<trackCount; i++)
        {
            track = mediaStore.new Track();
            track.copyFrom(trackFirst);
            track.name = String.format("Track %03d", i+1);
            track.id = mediaStore.addTrack(track);
            playlist.add(track);
        }

        playlist.name="All Tracks";
        mediaStore.addPlaylist(playlist);

        mediaStore.rebuildDbRequired = false;

        PodEmuLog.debug("PEDB: FUNCTION END rebuildDB");

    }

}
