package com.ats.audioplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvSongs;
    private SearchView searchView;
    private List<Song> songList = new ArrayList<>();
    private List<Song> filteredList = new ArrayList<>();
    private SongAdapter adapter;
    private TextView miniTitle, miniArtist;
    private ImageView miniPlayPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        rvSongs = findViewById(R.id.rvSongs);
        searchView = findViewById(R.id.searchView);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPause = findViewById(R.id.miniPlayPause);

        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SongAdapter(filteredList, song -> {
            Intent i = new Intent(this, PlayerService.class);
            i.putExtra("title", song.getTitle());
            i.putExtra("artist", song.getArtist());
            i.putExtra("path", song.getPath());
            startService(i);
            miniTitle.setText(song.getTitle());
            miniArtist.setText(song.getArtist());
        });
        rvSongs.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });

        checkPermission();
    }

    private void checkPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==100) loadSongs();
    }

    private void loadSongs(){
        songList.clear();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION};
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        try(Cursor c = getContentResolver().query(uri, proj, sel, null, null)){
            if(c!=null){
                while(c.moveToNext()){
                    long id = c.getLong(0);
                    String title = c.getString(1);
                    String artist = c.getString(2);
                    String path = c.getString(3);
                    long dur = c.getLong(4);
                    Uri contentUri = ContentUris.withAppendedId(uri, id);
                    Song s = new Song(id, title, artist, path, contentUri);
                    s.setDuration(dur);
                    songList.add(s);
                }
            }
        }
        filteredList.clear();
        filteredList.addAll(songList);
        adapter.notifyDataSetChanged();
    }

    private void filter(String text){
        filteredList.clear();
        if(text==null || text.isEmpty()){
            filteredList.addAll(songList);
        } else {
            String lower = text.toLowerCase();
            for(Song s: songList){
                if(s.getTitle().toLowerCase().contains(lower) || s.getArtist().toLowerCase().contains(lower)){
                    filteredList.add(s);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
}
