package com.ats.audioplayer;

import android.net.Uri;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String path;
    private Uri contentUri;
    private long duration;
    private String format;

    public Song(long id, String title, String artist, String path, Uri contentUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.contentUri = contentUri;
        this.duration = 0;
        this.format = "MP3";
        if (path != null && path.contains(".")) {
            this.format = path.substring(path.lastIndexOf(".")+1).toUpperCase();
        }
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
    public Uri getContentUri() { return contentUri; }
    public long getDuration() { return duration; }
    public void setDuration(long d) { this.duration = d; }

    public String getFormattedDuration() {
        long totalSec = duration / 1000;
        if (totalSec == 0 && path != null) totalSec = 0;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    public String getFormatBadge() {
        return format != null ? format : "MP3";
    }
}
