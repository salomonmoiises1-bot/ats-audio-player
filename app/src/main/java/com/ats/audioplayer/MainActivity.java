package com.ats.audioplayer;

import android.net.Uri;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String path;
    private Uri contentUri;

    public Song(long id, String title, String artist, String path, Uri contentUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.contentUri = contentUri;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
    public Uri getContentUri() { return contentUri; }
}
