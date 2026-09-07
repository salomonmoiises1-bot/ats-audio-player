package com.ats.audioplayer;

import android.net.Uri;

public class Song {
    private final long id;
    private final String title;
    private final String artist;
    private final long duration;
    private final String path;
    private final Uri uri;
    private final long albumId;
    private final String mimeType;

    public Song(long id, String title, String artist, long duration, String path, Uri uri, long albumId) {
        this(id, title, artist, duration, path, uri, albumId, "audio/mpeg");
    }

    public Song(long id, String title, String artist, long duration, String path, Uri uri, long albumId, String mimeType) {
        this.id = id;
        this.title = title != null ? title : "Unknown Title";
        this.artist = artist != null ? artist : "Unknown Artist";
        this.duration = duration;
        this.path = path != null ? path : "";
        this.uri = uri;
        this.albumId = albumId;
        this.mimeType = mimeType != null ? mimeType : "audio/mpeg";
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public long getDuration() {
        return duration;
    }

    public String getPath() {
        return path;
    }

    public Uri getUri() {
        return uri;
    }

    public long getAlbumId() {
        return albumId;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFormatBadge() {
        String lowerPath = path.toLowerCase();
        String lowerMime = mimeType.toLowerCase();
        if (lowerPath.endsWith(".flac") || lowerMime.contains("flac")) return "FLAC";
        if (lowerPath.endsWith(".wav") || lowerMime.contains("wav")) return "WAV";
        if (lowerPath.endsWith(".m4a") || lowerMime.contains("m4a") || lowerMime.contains("mp4")) return "M4A";
        if (lowerPath.endsWith(".aac") || lowerMime.contains("aac")) return "AAC";
        if (lowerPath.endsWith(".opus") || lowerMime.contains("opus")) return "OPUS";
        if (lowerPath.endsWith(".ogg") || lowerMime.contains("ogg")) return "OGG";
        if (lowerPath.endsWith(".alac")) return "ALAC";
        if (lowerPath.endsWith(".aiff") || lowerPath.endsWith(".aif")) return "AIFF";
        if (lowerPath.endsWith(".wma")) return "WMA";
        return "MP3";
    }

    public String getFormattedDuration() {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}