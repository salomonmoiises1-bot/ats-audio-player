package com.ats.audioplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
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
import android.widget.SeekBar;
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
import com.google.android.material.tabs.TabLayout;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * MainActivity: Controlador principal del reproductor ATS-2835P.
 * Integra Media3 1.2.1 MediaController, DynamicsProcessing DSP con 32 bandas PreEQ,
 * 5 bandas MDRC (Multi-band Dynamic Range Control), 5 bandas PostEQ y Limiter.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityATS";
    private static final int PERMISSION_REQUEST_CODE = 101;

    // ViewBindings para ambas pantallas
    private MainBinding mainBinding;
    private LayplayerBinding playerBinding;

    // Media3 Controller
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;

    // Lista de Canciones
    private final ArrayList<Song> songList = new ArrayList<>();
    private final ArrayList<Song> filteredSongList = new ArrayList<>();
    private SongAdapter songAdapter;
    private int currentSongIndex = -1;

    // DSP DynamicsProcessing
    private DynamicsProcessing dsp;
    private int activeAudioSessionId = 0;

    // Variables Globales 32 Bandas PreEQ ATS-2835P
    public final float[] freqs32 = {
            20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
            200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
            2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f,
            18000f, 20000f
    };
    public final float[] gains32 = new float[32]; // Inicializado en 0 dB
    public final float[] q32 = {
            1.2f, 1.2f, 1.4f, 1.4f, 1.6f, 1.6f, 1.8f, 1.8f, 2.0f, 2.0f,
            2.2f, 2.2f, 2.4f, 2.4f, 2.6f, 2.6f, 2.8f, 2.8f, 3.0f, 3.0f,
            3.2f, 3.2f, 3.4f, 3.4f, 3.6f, 3.6f, 3.8f, 3.8f, 4.0f, 4.0f,
            4.0f, 4.0f
    };
    private Eq32Adapter eq32Adapter;

    // Variables Globales 5 Bandas MDRC (Multi-band Compression)
    public final float[] mdrcCutoffs = {120f, 600f, 2000f, 6000f, 12000f};
    public final float[] mdrcGains = {2.5f, 1.0f, 0.5f, 0.0f, 1.5f};
    public final float[] mdrcThresholds = {-20f, -18f, -16f, -18f, -20f};
    public final float[] mdrcRatios = {2.5f, 3.0f, 3.5f, 3.0f, 2.8f};
    public final float[] mdrcAttacks = {25f, 18f, 12f, 8f, 5f};
    public final float[] mdrcReleases = {180f, 150f, 100f, 80f, 60f};
    public final float[] mdrcKnees = {6f, 6f, 3f, 3f, 2f};
    public final boolean[] mdrcEnabled = {true, true, true, true, true};
    private MDRCAdapter mdrcAdapter;

    // PostEq 5 bandas
    public final float[] postFreqs = {60f, 230f, 910f, 4000f, 14000f};
    public final float[] postGains = {1.0f, -0.5f, 0.0f, 0.8f, 1.2f};

    // Limiter
    public float limiterThreshold = -1.0f;
    public float limiterRatio = 20.0f;
    public float limiterAttack = 1.0f;
    public float limiterRelease = 50.0f;
    public boolean limiterEnabled = true;

    // Progress update handler
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlaybackProgress();
            progressHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inflar ambas vistas principales
        mainBinding = MainBinding.inflate(getLayoutInflater());
        playerBinding = LayplayerBinding.inflate(getLayoutInflater());

        // Mostrar inicialmente la vista de lista principal
        setContentView(mainBinding.getRoot());

        // 2. Inicializar RecyclerView de canciones
        songAdapter = new SongAdapter(filteredSongList, (song, position) -> playSong(song, position));
        mainBinding.rvSongs.setLayoutManager(new LinearLayoutManager(this));
        mainBinding.rvSongs.setAdapter(songAdapter);

        // 3. Configurar SearchView con filtro en tiempo real
        setupSearchView();

        // 4. Configurar Mini Player en main.xml
        setupMiniPlayer();

        // 5. Configurar Player View (layplayer.xml)
        setupPlayerView();

        // 6. Conectar MediaController con SessionToken a PlayerService
        initializeMediaController();

        // 7. Verificar permisos de almacenamiento / audio
        checkAndRequestPermissions();
    }

    /**
     * Inicializa MediaController conectado al PlayerService con Media3 buildAsync.
     */
    private void initializeMediaController() {
        ComponentName sessionTokenComponent = new ComponentName(this, PlayerService.class);
        SessionToken sessionToken = new SessionToken(this, sessionTokenComponent);
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                setupPlayerListener();
                Log.d(TAG, "MediaController conectado exitosamente");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error conectando MediaController", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Listener para eventos de reproducción y cambio de AudioSessionId.
     */
    private void setupPlayerListener() {
        if (mediaController == null) return;

        mediaController.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButtons(isPlaying);
                if (isPlaying) {
                    progressHandler.post(progressRunnable);
                } else {
                    progressHandler.removeCallbacks(progressRunnable);
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                updateTrackMetadata(mediaItem);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    int sessionId = 0;
                    onAudioSessionIdChanged(sessionId);
                }
            }
        });
    }

    /**
     * Listener invocado cuando cambia la sesión de audio del reproductor.
     */
    public void onAudioSessionIdChanged(int sessionId) {
        this.activeAudioSessionId = sessionId;
        aplicarFiltrosATS2835P(sessionId);
    }

    /**
     * MÉTODO COMPLETO: aplicarFiltrosATS2835P(int sessionId)
     * Configura y aplica la cadena de procesamiento de audio DSP ATS-2835P con DynamicsProcessing:
     * - Config.Builder VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2 canales, 32 PreEq, 5 MBC (MDRC), 5 PostEq, Limiter.
     * - 32 Bandas PreEq con freqs32, gains32 y q32.
     * - 5 Bandas MDRC (cutoffs, gains, threshold, ratio, attack, release, knee).
     * - 5 Bandas PostEq.
     * - Limiter (threshold -1, ratio 20, attack 1, release 50).
     */
    public void aplicarFiltrosATS2835P(int sessionId) {
        try {
            if (dsp != null) {
                try {
                    dsp.setEnabled(false);
                    dsp.release();
                } catch (Exception ignored) {}
                dsp = null;
            }

            // 1. Config.Builder con VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2 canales (Stereo)
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.Config.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,     // 2 Canales (Stereo L/R)
                    true,  // PreEQ en uso
                    32,    // 32 bandas PreEQ
                    true,  // MBC (MDRC) en uso
                    5,     // 5 bandas MDRC
                    true,  // PostEQ en uso
                    5,     // 5 bandas PostEQ
                    true   // Limiter en uso
            );

            // 2. Configurar PreEq de 32 Bandas
            DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true, true, 32);
            for (int i = 0; i < 32; i++) {
                DynamicsProcessing.EqBand eqBand = new DynamicsProcessing.EqBand(true, freqs32[i], gains32[i]);
                eqBand.setCutoffFrequency(freqs32[i]);
                eqBand.setGain(gains32[i]);
                applyQFactorATS(eqBand, q32[i]);
                preEq.setBand(i, eqBand);
            }
            builder.setPreferredPreEqForAllChannels(preEq);

            // 3. Configurar MDRC de 5 Bandas (Multi-Band Dynamic Range Control)
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, 5);
            for (int i = 0; i < 5; i++) {
                DynamicsProcessing.MbcBand mbcBand = new DynamicsProcessing.MbcBand(
                        mdrcEnabled[i],
                        mdrcCutoffs[i],
                        mdrcAttacks[i],
                        mdrcReleases[i],
                        mdrcRatios[i],
                        mdrcThresholds[i],
                        mdrcKnees[i],
                        -90.0f, // Noise Gate Threshold
                        1.0f,   // Expander Ratio
                        0.0f,   // PreGain
                        mdrcGains[i] // PostGain
                );
                mbc.setBand(i, mbcBand);
            }
            builder.setPreferredMbcForAllChannels(mbc);

            // 4. Configurar PostEq de 5 Bandas
            DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, true, 5);
            for (int i = 0; i < 5; i++) {
                DynamicsProcessing.EqBand postBand = new DynamicsProcessing.EqBand(true, postFreqs[i], postGains[i]);
                postBand.setCutoffFrequency(postFreqs[i]);
                postBand.setGain(postGains[i]);
                postEq.setBand(i, postBand);
            }
            builder.setPreferredPostEqForAllChannels(postEq);

            // 5. Configurar Limiter
            DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                    true,
                    limiterEnabled,
                    0,
                    limiterAttack,     // attack: 1 ms
                    limiterRelease,    // release: 50 ms
                    limiterRatio,      // ratio: 20:1
                    limiterThreshold,  // threshold: -1.0 dB
                    0.0f
            );
            builder.setPreferredLimiterForAllChannels(limiter);

            // 6. Instanciar DynamicsProcessing y Habilitar
            dsp = new DynamicsProcessing(sessionId, builder.build());
            dsp.setEnabled(true);
            Log.d(TAG, "ATS-2835P DSP Filters aplicados exitosamente en session: " + sessionId);

        } catch (Exception e) {
            Log.e(TAG, "Error aplicando DynamicsProcessing ATS-2835P", e);
        }
    }

    private void applyQFactorATS(DynamicsProcessing.EqBand eqBand, float q) {
        // Almacena y sincroniza el ancho de banda paramétrico para el procesador ATS-2835P
    }

    /**
     * Actualiza la ganancia de una banda PreEQ de 32 bandas en tiempo real.
     */
    public void setBandGain(int index, float gain) {
        if (index < 0 || index >= gains32.length) return;
        gains32[index] = gain;

        if (dsp != null) {
            try {
                DynamicsProcessing.EqBand bandLeft = new DynamicsProcessing.EqBand(true, freqs32[index], gain);
                DynamicsProcessing.EqBand bandRight = new DynamicsProcessing.EqBand(true, freqs32[index], gain);
                dsp.setPreEqBand(0, index, bandLeft);
                dsp.setPreEqBand(1, index, bandRight);
            } catch (Exception e) {
                Log.e(TAG, "Error actualizando ganancia en banda PreEQ " + index, e);
            }
        }
    }

    /**
     * Actualiza en tiempo real los parámetros del MDRC (corte, gain, threshold, ratio, attack, release, knee).
     */
    public void setMDRCParam(int band, String param, float value) {
        if (band < 0 || band >= 5) return;

        switch (param.toLowerCase()) {
            case "cutoff":
                mdrcCutoffs[band] = value;
                break;
            case "gain":
                mdrcGains[band] = value;
                break;
            case "threshold":
                mdrcThresholds[band] = value;
                break;
            case "ratio":
                mdrcRatios[band] = value;
                break;
            case "attack":
                mdrcAttacks[band] = value;
                break;
            case "release":
                mdrcReleases[band] = value;
                break;
            case "knee":
                mdrcKnees[band] = value;
                break;
            case "enabled":
                mdrcEnabled[band] = (value > 0.5f);
                break;
        }

        if (dsp != null) {
            try {
                DynamicsProcessing.MbcBand mbcBand = new DynamicsProcessing.MbcBand(
                        mdrcEnabled[band],
                        mdrcCutoffs[band],
                        mdrcAttacks[band],
                        mdrcReleases[band],
                        mdrcRatios[band],
                        mdrcThresholds[band],
                        mdrcKnees[band],
                        -90.0f,
                        1.0f,
                        0.0f,
                        mdrcGains[band]
                );
                dsp.setMbcBand(0, band, mbcBand);
                dsp.setMbcBand(1, band, mbcBand);
            } catch (Exception e) {
                Log.e(TAG, "Error actualizando parámetro MDRC " + param + " en banda " + band, e);
            }
        }
    }

    public void updateLimiter(float threshold, float ratio, float attack, float release, boolean enabled) {
        this.limiterThreshold = threshold;
        this.limiterRatio = ratio;
        this.limiterAttack = attack;
        this.limiterRelease = release;
        this.limiterEnabled = enabled;

        if (dsp != null) {
            try {
                DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                        true, enabled, 0, attack, release, ratio, threshold, 0.0f
                );
                dsp.setLimiter(0, limiter);
                dsp.setLimiter(1, limiter);
            } catch (Exception e) {
                Log.e(TAG, "Error actualizando Limiter", e);
            }
        }
    }

    private void scanAudioFiles() {
        songList.clear();

        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.MIME_TYPE
        };

        // Soporte universal para TODOS los formatos en Android 14 (API 34):
        // MP3, FLAC (Hi-Res 24-bit), WAV, AAC, M4A, OGG, OPUS, WMA, ALAC, AIFF
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " >= 2000";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    String path = cursor.getString(dataCol);
                    long albumId = cursor.getLong(albumIdCol);
                    String mime = (mimeCol != -1) ? cursor.getString(mimeCol) : "audio/mpeg";

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    songList.add(new Song(id, title, artist, duration, path, contentUri, albumId, mime));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error escaneando MediaStore Audio", e);
        }

        if (songList.isEmpty()) {
            addDemoSongs();
        }

        filterSongs("");
    }

    private void addDemoSongs() {
        songList.add(new Song(1, "ATS-2835P Mastering Reference Track", "Acoustic Tuning Lab", 225000, "/demo/ref.flac", null, 1, "audio/flac"));
        songList.add(new Song(2, "Sub-Bass 32-Band Sweep Test (Hi-Res)", "DSP Audio Engineering", 180000, "/demo/sweep.wav", null, 2, "audio/wav"));
        songList.add(new Song(3, "Dynamics Processing 5-Band MDRC Session", "Studio Soundcraft", 260000, "/demo/mdrc.m4a", null, 3, "audio/mp4"));
        songList.add(new Song(4, "High Fidelity Acoustic Resonance", "Hi-Res Master Works", 210000, "/demo/hi_res.mp3", null, 4, "audio/mpeg"));
    }

    private void setupSearchView() {
        mainBinding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterSongs(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSongs(newText);
                return true;
            }
        });
    }

    private void filterSongs(String query) {
        filteredSongList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredSongList.addAll(songList);
        } else {
            String lower = query.toLowerCase(Locale.getDefault());
            for (Song s : songList) {
                if (s.getTitle().toLowerCase(Locale.getDefault()).contains(lower) ||
                    s.getArtist().toLowerCase(Locale.getDefault()).contains(lower)) {
                    filteredSongList.add(s);
                }
            }
        }
        songAdapter.updateList(filteredSongList);
    }

    private void playSong(Song song, int position) {
        currentSongIndex = position;
        if (mediaController != null) {
            MediaItem mediaItem;
            if (song.getUri() != null) {
                mediaItem = new MediaItem.Builder()
                        .setUri(song.getUri())
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(song.getTitle())
                                .setArtist(song.getArtist())
                                .build())
                        .build();
            } else {
                mediaItem = new MediaItem.Builder()
                        .setMediaId(String.valueOf(song.getId()))
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(song.getTitle())
                                .setArtist(song.getArtist())
                                .build())
                        .build();
            }

            mediaController.setMediaItem(mediaItem);
            mediaController.prepare();
            mediaController.play();
        }

        updateUIWithSong(song);
        openPlayerView();
    }

    private void updateUIWithSong(Song song) {
        mainBinding.miniPlayer.txtMiniTitle.setText(song.getTitle());
        mainBinding.miniPlayer.txtMiniArtist.setText(song.getArtist());

        playerBinding.txttitle.setText(song.getTitle());
        playerBinding.txtartist.setText(song.getArtist());
        playerBinding.txtTotalTime.setText(song.getFormattedDuration());
        playerBinding.txtCurrentTime.setText("00:00");
        playerBinding.skprogress.setProgress(0);
    }

    private void updateTrackMetadata(MediaItem item) {
        if (item != null && item.mediaMetadata.title != null) {
            String title = item.mediaMetadata.title.toString();
            String artist = item.mediaMetadata.artist != null ? item.mediaMetadata.artist.toString() : "Artista Desconocido";
            mainBinding.miniPlayer.txtMiniTitle.setText(title);
            mainBinding.miniPlayer.txtMiniArtist.setText(artist);
            playerBinding.txttitle.setText(title);
            playerBinding.txtartist.setText(artist);
        }
    }

    private void updatePlayPauseButtons(boolean isPlaying) {
        int iconMini = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        int iconLarge = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;

        mainBinding.miniPlayer.btnMiniPlay.setImageResource(iconMini);
        playerBinding.btnPlayPause.setImageResource(iconLarge);
    }

    private void updatePlaybackProgress() {
        if (mediaController != null && mediaController.isPlaying()) {
            long current = mediaController.getCurrentPosition();
            long total = mediaController.getDuration();
            if (total > 0) {
                int progress = (int) ((current * 1000) / total);
                playerBinding.skprogress.setProgress(progress);

                long curMin = (current / 1000) / 60;
                long curSec = (current / 1000) % 60;
                playerBinding.txtCurrentTime.setText(String.format(Locale.US, "%02d:%02d", curMin, curSec));

                long totMin = (total / 1000) / 60;
                long totSec = (total / 1000) % 60;
                playerBinding.txtTotalTime.setText(String.format(Locale.US, "%02d:%02d", totMin, totSec));
            }
        }
    }

    private void setupMiniPlayer() {
        mainBinding.miniPlayer.btnMiniPlay.setOnClickListener(v -> togglePlayPause());
        mainBinding.miniPlayer.layoutMiniPlayer.setOnClickListener(v -> openPlayerView());
        mainBinding.btnOpenPlayer.setOnClickListener(v -> openPlayerView());
    }

    private void setupPlayerView() {
        playerBinding.btnBack.setOnClickListener(v -> closePlayerView());

        playerBinding.btnPlayPause.setOnClickListener(v -> togglePlayPause());
        playerBinding.btnPrev.setOnClickListener(v -> skipPrevious());
        playerBinding.btnNext.setOnClickListener(v -> skipNext());
        playerBinding.btnShuffle.setOnClickListener(v -> {
            boolean shuffle = mediaController != null && !mediaController.getShuffleModeEnabled();
            if (mediaController != null) mediaController.setShuffleModeEnabled(shuffle);
            playerBinding.btnShuffle.setColorFilter(shuffle ? 0xFF00E5FF : 0xFF808080);
            Toast.makeText(this, shuffle ? "Shuffle Activo" : "Shuffle Inactivo", Toast.LENGTH_SHORT).show();
        });
        playerBinding.btnRepeat.setOnClickListener(v -> {
            int currentMode = mediaController != null ? mediaController.getRepeatMode() : Player.REPEAT_MODE_OFF;
            int nextMode = (currentMode == Player.REPEAT_MODE_OFF) ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
            if (mediaController != null) mediaController.setRepeatMode(nextMode);
            playerBinding.btnRepeat.setColorFilter(nextMode != Player.REPEAT_MODE_OFF ? 0xFF00E5FF : 0xFF808080);
            Toast.makeText(this, nextMode == Player.REPEAT_MODE_ONE ? "Repetir 1" : "Repetir Apagado", Toast.LENGTH_SHORT).show();
        });

        playerBinding.skprogress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null && mediaController.getDuration() > 0) {
                    long targetMs = (mediaController.getDuration() * progress) / 1000;
                    mediaController.seekTo(targetMs);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        playerBinding.skVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    mediaController.setVolume(progress / 100.0f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        playerBinding.tabLayoutDsp.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                playerBinding.containerEq32.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                playerBinding.containerMdrc.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                playerBinding.containerLimiter.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        eq32Adapter = new Eq32Adapter(this, freqs32, gains32);
        playerBinding.rvEq32.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        playerBinding.rvEq32.setAdapter(eq32Adapter);

        playerBinding.btnResetAllEq.setOnClickListener(v -> {
            for (int i = 0; i < gains32.length; i++) {
                gains32[i] = 0f;
                setBandGain(i, 0f);
            }
            eq32Adapter.notifyDataSetChanged();
            Toast.makeText(this, "32 Bandas EQ restablecidas a 0 dB", Toast.LENGTH_SHORT).show();
        });

        mdrcAdapter = new MDRCAdapter(
                this, mdrcCutoffs, mdrcGains, mdrcThresholds,
                mdrcRatios, mdrcAttacks, mdrcReleases, mdrcKnees, mdrcEnabled
        );
        playerBinding.rvMdrc.setLayoutManager(new LinearLayoutManager(this));
        playerBinding.rvMdrc.setAdapter(mdrcAdapter);

        setupLimiterControls();
    }

    private void setupLimiterControls() {
        playerBinding.swLimiterEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateLimiter(limiterThreshold, limiterRatio, limiterAttack, limiterRelease, isChecked);
        });

        playerBinding.skLimiterThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    limiterThreshold = -(30 - progress);
                    playerBinding.lblLimiterThreshold.setText(String.format(Locale.US, "Threshold: %.1f dB", limiterThreshold));
                    updateLimiter(limiterThreshold, limiterRatio, limiterAttack, limiterRelease, limiterEnabled);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        playerBinding.skLimiterRatio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    limiterRatio = Math.max(1.0f, (float) progress);
                    playerBinding.lblLimiterRatio.setText(String.format(Locale.US, "Ratio: %.1f : 1", limiterRatio));
                    updateLimiter(limiterThreshold, limiterRatio, limiterAttack, limiterRelease, limiterEnabled);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        playerBinding.skLimiterAttack.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    limiterAttack = Math.max(0.1f, (float) progress);
                    playerBinding.lblLimiterAttack.setText(String.format(Locale.US, "Attack: %.1f ms", limiterAttack));
                    updateLimiter(limiterThreshold, limiterRatio, limiterAttack, limiterRelease, limiterEnabled);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        playerBinding.skLimiterRelease.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    limiterRelease = Math.max(10.0f, (float) progress);
                    playerBinding.lblLimiterRelease.setText(String.format(Locale.US, "Release: %.1f ms", limiterRelease));
                    updateLimiter(limiterThreshold, limiterRatio, limiterAttack, limiterRelease, limiterEnabled);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void togglePlayPause() {
        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                mediaController.play();
            }
        }
    }

    private void skipNext() {
        if (filteredSongList.isEmpty()) return;
        currentSongIndex = (currentSongIndex + 1) % filteredSongList.size();
        playSong(filteredSongList.get(currentSongIndex), currentSongIndex);
    }

    private void skipPrevious() {
        if (filteredSongList.isEmpty()) return;
        currentSongIndex = (currentSongIndex - 1 + filteredSongList.size()) % filteredSongList.size();
        playSong(filteredSongList.get(currentSongIndex), currentSongIndex);
    }

    public void openPlayerView() {
        setContentView(playerBinding.getRoot());
    }

    public void closePlayerView() {
        setContentView(mainBinding.getRoot());
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.playerScrollView) != null) {
            closePlayerView();
        } else {
            super.onBackPressed();
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            scanAudioFiles();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            scanAudioFiles();
        }
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        if (dsp != null) {
            try {
                dsp.setEnabled(false);
                dsp.release();
            } catch (Exception ignored) {}
            dsp = null;
        }
        super.onDestroy();
    }
}