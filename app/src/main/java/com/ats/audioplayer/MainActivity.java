package com.ats.audioplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.audiofx.DynamicsProcessing;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.ats.audioplayer.databinding.LayplayerBinding;
import com.ats.audioplayer.databinding.MainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityATS";
    private static final int PERMISSION_REQUEST_CODE = 101;

    private MainBinding mainBinding;
    private LayplayerBinding playerBinding;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;
    private final ArrayList<Song> songList = new ArrayList<>();
    private final ArrayList<Song> filteredSongList = new ArrayList<>();
    private SongAdapter songAdapter;
    private int currentSongIndex = -1;
    private DynamicsProcessing dsp;
    private int activeAudioSessionId = 0;

    public final float[] freqs32 = {20f,25f,31.5f,40f,50f,63f,80f,100f,125f,160f,200f,250f,315f,400f,500f,630f,800f,1000f,1250f,1600f,2000f,2500f,3150f,4000f,5000f,6300f,8000f,10000f,12500f,16000f,18000f,20000f};
    public final float[] gains32 = new float[32];
    public final float[] q32 = {1.2f,1.2f,1.4f,1.4f,1.6f,1.6f,1.8f,1.8f,2.0f,2.0f,2.2f,2.2f,2.4f,2.4f,2.6f,2.6f,2.8f,2.8f,3.0f,3.0f,3.2f,3.2f,3.4f,3.4f,3.6f,3.6f,3.8f,3.8f,4.0f,4.0f,4.0f,4.0f};
    private Eq32Adapter eq32Adapter;

    public final float[] mdrcCutoffs = {120f,600f,2000f,6000f,12000f};
    public final float[] mdrcGains = {2.5f,1.0f,0.5f,0.0f,1.5f};
    public final float[] mdrcThresholds = {-20f,-18f,-16f,-18f,-20f};
    public final float[] mdrcRatios = {2.5f,3.0f,3.5f,3.0f,2.8f};
    public final float[] mdrcAttacks = {25f,18f,12f,8f,5f};
    public final float[] mdrcReleases = {180f,150f,100f,80f,60f};
    public final float[] mdrcKnees = {6f,6f,3f,3f,2f};
    public final boolean[] mdrcEnabled = {true,true,true,true,true};
    private MDRCAdapter mdrcAdapter;

    public final float[] postFreqs = {60f,230f,910f,4000f,14000f};
    public final float[] postGains = {1.0f,-0.5f,0.0f,0.8f,1.2f};

    public float limiterThreshold = -1.0f;
    public float limiterRatio = 20.0f;
    public float limiterAttack = 1.0f;
    public float limiterRelease = 50.0f;
    public boolean limiterEnabled = true;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() { updatePlaybackProgress(); progressHandler.postDelayed(this, 500); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainBinding = MainBinding.inflate(getLayoutInflater());
        playerBinding = LayplayerBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        songAdapter = new SongAdapter(filteredSongList, (song, position) -> playSong(song, position));
        mainBinding.rvSongs.setLayoutManager(new LinearLayoutManager(this));
        mainBinding.rvSongs.setAdapter(songAdapter);
        setupSearchView();
        setupMiniPlayer();
        setupPlayerView();
        initializeMediaController();
        checkAndRequestPermissions();
    }

    private void initializeMediaController() {
        ComponentName cn = new ComponentName(this, PlayerService.class);
        SessionToken token = new SessionToken(this, cn);
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                setupPlayerListener();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error MediaController", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupPlayerListener() {
        if (mediaController == null) return;
        mediaController.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButtons(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else progressHandler.removeCallbacks(progressRunnable);
            }
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { updateTrackMetadata(mediaItem); }
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) onAudioSessionIdChanged(mediaController.getAudioSessionId());
            }
        });
    }

    public void onAudioSessionIdChanged(int sessionId) {
        this.activeAudioSessionId = sessionId;
        aplicarFiltrosATS2835P(sessionId);
    }

    public void aplicarFiltrosATS2835P(int sessionId) {
        try {
            if (sessionId==0) return;
            if (dsp!= null) { try { dsp.setEnabled(false); dsp.release(); } catch(Exception ignored){} dsp=null; }
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.Config.VARIANT_FAVOR_FREQUENCY_RESOLUTION,2,true,32,true,5,true,5,true);

            DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true,true,32);
            for (int i=0;i<32;i++) {
                DynamicsProcessing.EqBand b = new DynamicsProcessing.EqBand(true, freqs32[i], gains32[i]);
                preEq.setBand(i,b);
            }
            builder.setPreferredPreEqForAllChannels(preEq);

            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true,true,5);
            for (int i=0;i<5;i++) {
                DynamicsProcessing.MbcBand mb = new DynamicsProcessing.MbcBand(mdrcEnabled[i],mdrcCutoffs[i],mdrcAttacks[i],mdrcReleases[i],mdrcRatios[i],mdrcThresholds[i],mdrcKnees[i],-90f,1f,0f,mdrcGains[i]);
                mbc.setBand(i,mb);
            }
            builder.setPreferredMbcForAllChannels(mbc);

            DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true,true,5);
            for (int i=0;i<5;i++) {
                DynamicsProcessing.EqBand b = new DynamicsProcessing.EqBand(true, postFreqs[i], postGains[i]);
                postEq.setBand(i,b);
            }
            builder.setPreferredPostEqForAllChannels(postEq);

            DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(true,limiterEnabled,0,limiterAttack,limiterRelease,limiterRatio,limiterThreshold,0f);
            builder.setPreferredLimiterForAllChannels(limiter);

            dsp = new DynamicsProcessing(sessionId, builder.build());
            dsp.setEnabled(true);
            Log.d(TAG,"ATS-2835P aplicado session:"+sessionId);
        } catch (Exception e) { Log.e(TAG,"Error DSP",e); }
    }

    public void setBandGain(int index, float gain) {
        if(index<0||index>=32) return;
        gains32[index]=gain;
        if(dsp!=null){ try{ DynamicsProcessing.EqBand b=new DynamicsProcessing.EqBand(true,freqs32[index],gain); dsp.setPreEqBand(0,index,b); dsp.setPreEqBand(1,index,b);}catch(Exception e){} }
    }

    public void setMDRCParam(int band, String param, float value) {
        if(band<0||band>=5) return;
        switch(param.toLowerCase()){
            case "cutoff": mdrcCutoffs[band]=value; break;
            case "gain": mdrcGains[band]=value; break;
            case "threshold": mdrcThresholds[band]=value; break;
            case "ratio": mdrcRatios[band]=value; break;
            case "attack": mdrcAttacks[band]=value; break;
            case "release": mdrcReleases[band]=value; break;
            case "knee": mdrcKnees[band]=value; break;
            case "enabled": mdrcEnabled[band]=value>0.5f; break;
        }
        if(dsp!=null){ try{ DynamicsProcessing.MbcBand mb=new DynamicsProcessing.MbcBand(mdrcEnabled[band],mdrcCutoffs[band],mdrcAttacks[band],mdrcReleases[band],mdrcRatios[band],mdrcThresholds[band],mdrcKnees[band],-90f,1f,0f,mdrcGains[band]); dsp.setMbcBand(0,band,mb); dsp.setMbcBand(1,band,mb);}catch(Exception e){} }
    }

    private void scanAudioFiles() {
        songList.clear();
        Uri collection = Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q?MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL):MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.MIME_TYPE};
        String selection = MediaStore.Audio.Media.IS_MUSIC+"!= 0 AND "+MediaStore.Audio.Media.DURATION+" >= 2000";
        String sortOrder = MediaStore.Audio.Media.TITLE+" ASC";
        try(Cursor cursor=getContentResolver().query(collection,projection,selection,null,sortOrder)){
            if(cursor!=null){
                int idCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int mimeCol=cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                while(cursor.moveToNext()){
                    long id=cursor.getLong(idCol); String title=cursor.getString(titleCol); String artist=cursor.getString(artistCol);
                    long duration=cursor.getLong(durationCol); String path=cursor.getString(dataCol); long albumId=cursor.getLong(albumIdCol);
                    String mime=mimeCol!=-1?cursor.getString(mimeCol):"audio/mpeg";
                    Uri contentUri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);
                    songList.add(new Song(id,title,artist,duration,path,contentUri,albumId,mime));
                }
            }
        }catch(Exception e){ Log.e(TAG,"scan error",e); }
        if(songList.isEmpty()) addDemoSongs();
        filterSongs("");
    }

    private void addDemoSongs(){
        songList.add(new Song(1,"ATS-2835P Reference","Acoustic Lab",225000,"/demo/ref.flac",null,1,"audio/flac"));
        songList.add(new Song(2,"Sub-Bass 32-Band Sweep","DSP Lab",180000,"/demo/sweep.wav",null,2,"audio/wav"));
    }

    private void setupSearchView(){
        mainBinding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            @Override public boolean onQueryTextSubmit(String q){ filterSongs(q); return true; }
            @Override public boolean onQueryTextChange(String s){ filterSongs(s); return true; }
        });
    }

    private void filterSongs(String query){
        filteredSongList.clear();
        if(query==null||query.trim().isEmpty()) filteredSongList.addAll(songList);
        else{
            String lower=query.toLowerCase(Locale.getDefault());
            for(Song s:songList) if(s.getTitle().toLowerCase().contains(lower)||s.getArtist().toLowerCase().contains(lower)) filteredSongList.add(s);
        }
        if(songAdapter!=null) songAdapter.notifyDataSetChanged();
    }

    private void setupMiniPlayer(){
        mainBinding.miniPlayerContainer.setOnClickListener(v->{ setContentView(playerBinding.getRoot()); });
        mainBinding.miniPlayPause.setOnClickListener(v->{ if(mediaController!=null){ if(mediaController.isPlaying()) mediaController.pause(); else mediaController.play(); }});
    }

    private void setupPlayerView(){
        playerBinding.btnBack.setOnClickListener(v->{ setContentView(mainBinding.getRoot()); });
        playerBinding.btnPlayPause.setOnClickListener(v->{ if(mediaController!=null){ if(mediaController.isPlaying()) mediaController.pause(); else mediaController.play(); }});
        playerBinding.btnNext.setOnClickListener(v->{ if(mediaController!=null) mediaController.seekToNext(); });
        playerBinding.btnPrev.setOnClickListener(v->{ if(mediaController!=null) mediaController.seekToPrevious(); });

        // Setup EQ 32 Recycler
        eq32Adapter = new Eq32Adapter(this, freqs32, gains32);
        playerBinding.rvEq32.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false));
        playerBinding.rvEq32.setAdapter(eq32Adapter);

        // Setup MDRC
        mdrcAdapter = new MDRCAdapter(this, mdrcCutoffs, mdrcGains, mdrcThresholds, mdrcRatios);
        playerBinding.rvMdrc.setLayoutManager(new LinearLayoutManager(this));
        playerBinding.rvMdrc.setAdapter(mdrcAdapter);

        playerBinding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){
            @Override public void onTabSelected(TabLayout.Tab tab){
                if(tab.getPosition()==0){ playerBinding.rvEq32.setVisibility(android.view.View.VISIBLE); playerBinding.rvMdrc.setVisibility(android.view.View.GONE); }
                else{ playerBinding.rvEq32.setVisibility(android.view.View.GONE); playerBinding.rvMdrc.setVisibility(android.view.View.VISIBLE); }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab){}
            @Override public void onTabReselected(TabLayout.Tab tab){}
        });
    }

    private void playSong(Song song, int position){
        currentSongIndex=position;
        if(mediaController==null) return;
        MediaMetadata metadata=new MediaMetadata.Builder().setTitle(song.getTitle()).setArtist(song.getArtist()).build();
        MediaItem item=new MediaItem.Builder().setUri(song.getContentUri()!=null?song.getContentUri():Uri.parse(song.getPath())).setMediaMetadata(metadata).build();
        mediaController.setMediaItem(item);
        mediaController.prepare(); mediaController.play();
        mainBinding.miniTitle.setText(song.getTitle()); mainBinding.miniArtist.setText(song.getArtist());
        playerBinding.txtTitle.setText(song.getTitle()); playerBinding.txtArtist.setText(song.getArtist());
        setContentView(playerBinding.getRoot());
    }

    private void updatePlayPauseButtons(boolean isPlaying){
        int icon = isPlaying? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        mainBinding.miniPlayPause.setImageResource(icon);
        playerBinding.btnPlayPause.setImageResource(icon);
    }
    private void updateTrackMetadata(MediaItem item){
        if(item==null||item.mediaMetadata==null) return;
        String t=item.mediaMetadata.title!=null?item.mediaMetadata.title.toString():""; String a=item.mediaMetadata.artist!=null?item.mediaMetadata.artist.toString():"";
        mainBinding.miniTitle.setText(t); mainBinding.miniArtist.setText(a);
        playerBinding.txtTitle.setText(t); playerBinding.txtArtist.setText(a);
    }
    private void updatePlaybackProgress(){
        if(mediaController==null||!mediaController.isPlaying()) return;
        long pos=mediaController.getCurrentPosition(); long dur=mediaController.getDuration();
        if(dur>0){ int prog=(int)(pos*100/dur); playerBinding.seekBar.setProgress(prog); playerBinding.txtCurrent.setText(formatTime(pos)); playerBinding.txtTotal.setText(formatTime(dur)); }
    }
    private String formatTime(long ms){ long s=ms/1000; return String.format(Locale.US,"%02d:%02d",s/60,s%60); }

    private void checkAndRequestPermissions(){
        if(Build.VERSION.SDK_INT>=33){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_MEDIA_AUDIO},PERMISSION_REQUEST_CODE);
            else scanAudioFiles();
        }else{
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION_REQUEST_CODE);
            else scanAudioFiles();
        }
    }
    @Override public void onRequestPermissionsResult(int rc,@NonNull String[] perms,@NonNull int[] res){
        super.onRequestPermissionsResult(rc,perms,res);
        if(rc==PERMISSION_REQUEST_CODE&&res.length>0&&res[0]==PackageManager.PERMISSION_GRANTED) scanAudioFiles();
        else Toast.makeText(this,"Permiso requerido para audio",Toast.LENGTH_SHORT).show();
    }
    @Override protected void onDestroy(){
        super.onDestroy(); progressHandler.removeCallbacks(progressRunnable);
        if(dsp!=null){ try{dsp.setEnabled(false);dsp.release();}catch(Exception ignored){} }
        if(controllerFuture!=null) { try{ ListenableFuture<MediaController> f=controllerFuture; f.cancel(false);}catch(Exception ignored){} }
    }
}
