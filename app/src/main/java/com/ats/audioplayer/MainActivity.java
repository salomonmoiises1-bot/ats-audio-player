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
import android.view.View;
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
    public final float[] mdrcCutoffs = {120f,600f,2000f,6000f,12000f};
    public final float[] mdrcGains = {2.5f,1.0f,0.5f,0.0f,1.5f};
    public final float[] mdrcThresholds = {-20f,-18f,-16f,-18f,-20f};
    public final float[] mdrcRatios = {2.5f,3.0f,3.5f,3.0f,2.8f};
    public final float[] mdrcAttacks = {25f,18f,12f,8f,5f};
    public final float[] mdrcReleases = {180f,150f,100f,80f,60f};
    public final float[] mdrcKnees = {6f,6f,3f,3f,2f};
    public final boolean[] mdrcEnabled = {true,true,true,true,true};
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

    @Override protected void onCreate(Bundle savedInstanceState) {
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
            try { mediaController = controllerFuture.get(); setupPlayerListener(); }
            catch (Exception e) { Log.e(TAG,"Error controller",e); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupPlayerListener() {
        if (mediaController==null) return;
        mediaController.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButtons(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else progressHandler.removeCallbacks(progressRunnable);
            }
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { updateTrackMetadata(mediaItem); }
        });
    }

    public void onAudioSessionIdChanged(int sessionId) { activeAudioSessionId=sessionId; aplicarFiltrosATS2835P(sessionId); }

    public void aplicarFiltrosATS2835P(int sessionId) {
        try {
            if (dsp!= null) { try { dsp.setEnabled(false); dsp.release(); } catch (Exception ignored) {} dsp=null; }
            if (sessionId<=0) return;
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.Config.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, 32, true, 5, true, 5, true);
            DynamicsProcessing.Config cfg = builder.build();
            for (int ch=0; ch<2; ch++) {
                DynamicsProcessing.Eq preEq = cfg.getPreEqByChannelIndex(ch);
                for (int i=0;i<32;i++) {
                    DynamicsProcessing.EqBand band = preEq.getBand(i);
                    if (band==null) band = new DynamicsProcessing.EqBand(true, freqs32[i], gains32[i]);
                    band.setEnabled(true); band.setCutoffFrequency(freqs32[i]); band.setGain(gains32[i]);
                    preEq.setBand(i, band);
                }
                cfg.setPreEqByChannelIndex(ch, preEq);
                DynamicsProcessing.Mbc mbc = cfg.getMbcByChannelIndex(ch);
                for (int i=0;i<5;i++) {
                    DynamicsProcessing.MbcBand mb = mbc.getBand(i);
                    if (mb==null) mb = new DynamicsProcessing.MbcBand(mdrcEnabled[i], mdrcCutoffs[i], mdrcAttacks[i], mdrcReleases[i], mdrcRatios[i], mdrcThresholds[i], mdrcKnees[i], -90f, 1f, 0f, mdrcGains[i]);
                    mb.setEnabled(mdrcEnabled[i]); mb.setCutoffFrequency(mdrcCutoffs[i]);
                    mbc.setBand(i, mb);
                }
                cfg.setMbcByChannelIndex(ch, mbc);
                DynamicsProcessing.Eq postEq = cfg.getPostEqByChannelIndex(ch);
                for (int i=0;i<5;i++) {
                    DynamicsProcessing.EqBand b = postEq.getBand(i);
                    if (b==null) b = new DynamicsProcessing.EqBand(true, postFreqs[i], postGains[i]);
                    b.setEnabled(true); b.setCutoffFrequency(postFreqs[i]); b.setGain(postGains[i]);
                    postEq.setBand(i, b);
                }
                cfg.setPostEqByChannelIndex(ch, postEq);
                DynamicsProcessing.Limiter limiter = cfg.getLimiterByChannelIndex(ch);
                limiter.setEnabled(limiterEnabled); limiter.setThreshold(limiterThreshold); limiter.setRatio(limiterRatio);
                limiter.setAttackTime(limiterAttack); limiter.setReleaseTime(limiterRelease);
                cfg.setLimiterByChannelIndex(ch, limiter);
            }
            dsp = new DynamicsProcessing(0, sessionId, cfg);
            dsp.setEnabled(true);
        } catch (Exception e) { Log.e(TAG,"Error DSP",e); }
    }

    public void setBandGain(int index, float gain) {
        if (index<0||index>=gains32.length) return;
        gains32[index]=gain;
        if (dsp!=null) { try { for(int ch=0;ch<2;ch++){ DynamicsProcessing.EqBand b=dsp.getPreEqBandByChannelIndex(ch,index); b.setGain(gain); dsp.setPreEqBandByChannelIndex(ch,index,b);} } catch(Exception e){} }
    }
    public void setMDRCParam(int band, String param, float value) {
        if(band<0||band>=5) return;
        switch(param.toLowerCase()){ case "cutoff": mdrcCutoffs[band]=value; break; case "gain": mdrcGains[band]=value; break; case "threshold": mdrcThresholds[band]=value; break; case "ratio": mdrcRatios[band]=value; break; case "attack": mdrcAttacks[band]=value; break; case "release": mdrcReleases[band]=value; break; case "knee": mdrcKnees[band]=value; break; case "enabled": mdrcEnabled[band]=value>0.5f; break; }
    }
    public void updateLimiter(float threshold,float ratio,float attack,float release,boolean enabled){ limiterThreshold=threshold; limiterRatio=ratio; limiterAttack=attack; limiterRelease=release; limiterEnabled=enabled; }

    private void scanAudioFiles() {
        songList.clear();
        Uri collection = Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.ALBUM_ID};
        String sel=MediaStore.Audio.Media.IS_MUSIC+"!= 0";
        try (Cursor c=getContentResolver().query(collection,proj,sel,null,MediaStore.Audio.Media.TITLE+" ASC")) {
            if(c!=null){ int idCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); int titleCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); int artistCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); int durCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION); int dataCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA); int albumCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while(c.moveToNext()){ long id=c.getLong(idCol); String title=c.getString(titleCol); String artist=c.getString(artistCol); long dur=c.getLong(durCol); String path=c.getString(dataCol); long album=c.getLong(albumCol); Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id); songList.add(new Song(id,title,artist,dur,path,uri,album,"audio/mpeg")); } }
        } catch(Exception e){ Log.e(TAG,"scan error",e); }
        filterSongs("");
    }

    private void setupSearchView(){ mainBinding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){ @Override public boolean onQueryTextSubmit(String q){ filterSongs(q); return true; } @Override public boolean onQueryTextChange(String s){ filterSongs(s); return true; } }); }
    private void filterSongs(String query){ filteredSongList.clear(); if(query==null||query.trim().isEmpty()) filteredSongList.addAll(songList); else{ String low=query.toLowerCase(Locale.getDefault()); for(Song s:songList) if(s.getTitle().toLowerCase().contains(low)||s.getArtist().toLowerCase().contains(low)) filteredSongList.add(s); } songAdapter.notifyDataSetChanged(); }
    private void setupMiniPlayer(){ mainBinding.miniPlayerContainer.setOnClickListener(v->{ setContentView(playerBinding.getRoot()); }); mainBinding.miniPlayPause.setOnClickListener(v->{ if(mediaController!=null){ if(mediaController.isPlaying()) mediaController.pause(); else mediaController.play(); } }); }
    private void setupPlayerView(){
        playerBinding.btnBack.setOnClickListener(v-> setContentView(mainBinding.getRoot()));
        playerBinding.btnPlayPause.setOnClickListener(v->{ if(mediaController!=null){ if(mediaController.isPlaying()) mediaController.pause(); else mediaController.play(); } });
        playerBinding.btnNext.setOnClickListener(v->{ if(mediaController!=null) mediaController.seekToNext(); });
        playerBinding.btnPrev.setOnClickListener(v->{ if(mediaController!=null) mediaController.seekToPrevious(); });
        playerBinding.seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){ @Override public void onProgressChanged(android.widget.SeekBar sb,int p,boolean fromUser){ if(fromUser&&mediaController!=null) mediaController.seekTo(p); } @Override public void onStartTrackingTouch(android.widget.SeekBar sb){} @Override public void onStopTrackingTouch(android.widget.SeekBar sb){} });
    }
    private void playSong(Song song,int position){
        currentSongIndex=position; if(mediaController==null) return;
        ArrayList<MediaItem> items=new ArrayList<>(); for(Song s:filteredSongList){ MediaItem it=new MediaItem.Builder().setUri(s.getContentUri()!=null?s.getContentUri():Uri.parse(s.getPath())).setMediaMetadata(new MediaMetadata.Builder().setTitle(s.getTitle()).setArtist(s.getArtist()).build()).build(); items.add(it); }
        mediaController.setMediaItems(items, position, 0); mediaController.prepare(); mediaController.play(); setContentView(playerBinding.getRoot()); updateTrackMetadata(items.get(position));
    }
    private void updatePlayPauseButtons(boolean isPlaying){ int icon=isPlaying?R.drawable.ic_pause:R.drawable.ic_play; try{ mainBinding.miniPlayPause.setImageResource(icon); playerBinding.btnPlayPause.setImageResource(icon); }catch(Exception e){} }
    private void updateTrackMetadata(MediaItem item){ if(item==null||item.mediaMetadata==null) return; String t=item.mediaMetadata.title!=null?item.mediaMetadata.title.toString():""; String a=item.mediaMetadata.artist!=null?item.mediaMetadata.artist.toString():""; mainBinding.miniTitle.setText(t); mainBinding.miniArtist.setText(a); playerBinding.tvTitle.setText(t); playerBinding.tvArtist.setText(a); }
    private void updatePlaybackProgress(){ if(mediaController==null) return; long pos=mediaController.getCurrentPosition(); long dur=mediaController.getDuration(); if(dur>0){ playerBinding.seekBar.setMax((int)dur); playerBinding.seekBar.setProgress((int)pos); playerBinding.tvCurrentTime.setText(formatTime(pos)); playerBinding.tvTotalTime.setText(formatTime(dur)); } }
    private String formatTime(long ms){ long s=ms/1000; return String.format(Locale.getDefault(),"%02d:%02d",s/60,s%60); }
    private void checkAndRequestPermissions(){ String perm=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE; if(ContextCompat.checkSelfPermission(this,perm)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{perm},PERMISSION_REQUEST_CODE); else scanAudioFiles(); }
    @Override public void onRequestPermissionsResult(int rc,@NonNull String[] perms,@NonNull int[] res){ super.onRequestPermissionsResult(rc,perms,res); if(rc==PERMISSION_REQUEST_CODE&&res.length>0&&res[0]==PackageManager.PERMISSION_GRANTED) scanAudioFiles(); else Toast.makeText(this,"Permiso necesario",Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy(){ super.onDestroy(); progressHandler.removeCallbacks(progressRunnable); if(dsp!=null){ try{dsp.release();}catch(Exception e){} } if(controllerFuture!=null) controllerFuture.cancel(true); }
}
