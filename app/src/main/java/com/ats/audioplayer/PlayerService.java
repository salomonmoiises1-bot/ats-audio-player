package com.ats.audioplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.ui.PlayerNotificationManager;

/**
 * PlayerService: MediaSessionService con Media3 1.2.1.
 * Administra ExoPlayer, MediaSession y PlayerNotificationManager
 * para reproducción en segundo plano con foregroundServiceType mediaPlayback.
 */
public class PlayerService extends MediaSessionService {

    public static final String CHANNEL_ID = "ats_audio_playback_channel";
    public static final int NOTIFICATION_ID = 1001;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private PlayerNotificationManager notificationManager;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        // 1. Configurar AudioAttributes para reproducción multimedia musical
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        // 2. Construir ExoPlayer con soporte de audio attributes
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        // 3. Crear PendingIntent para abrir MainActivity al tocar la notificación
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivityPendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 4. Construir MediaSession
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build();

        // 5. Configurar PlayerNotificationManager con botones Anterior, Play/Pausa, Siguiente
        notificationManager = new PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @Override
                    public CharSequence getCurrentContentTitle(Player player) {
                        if (player.getCurrentMediaItem() != null && player.getCurrentMediaItem().mediaMetadata.title != null) {
                            return player.getCurrentMediaItem().mediaMetadata.title;
                        }
                        return getString(R.string.app_name);
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(Player player) {
                        return sessionActivityPendingIntent;
                    }

                    @Nullable
                    @Override
                    public CharSequence getCurrentContentText(Player player) {
                        if (player.getCurrentMediaItem() != null && player.getCurrentMediaItem().mediaMetadata.artist != null) {
                            return player.getCurrentMediaItem().mediaMetadata.artist;
                        }
                        return null;
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                        return null;
                    }
                })
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        stopForeground(true);
                        stopSelf();
                    }

                    @Override
                    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                        if (ongoing) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                            } else {
                                startForeground(notificationId, notification);
                            }
                        } else {
                            stopForeground(false);
                        }
                    }
                })
                .build();

        notificationManager.setPlayer(player);
        notificationManager.setUseNextAction(true);
        notificationManager.setUsePreviousAction(true);
        notificationManager.setUseFastForwardAction(false);
        notificationManager.setUseRewindAction(false);
        notificationManager.setSmallIcon(R.drawable.ic_music_note);
        notificationManager.setPriority(NotificationCompat.PRIORITY_LOW);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "ATS Audio Playback";
            String description = "Canal de notificaciones para reproducción ATS Audio Player";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onDestroy() {
        if (notificationManager != null) {
            notificationManager.setPlayer(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}