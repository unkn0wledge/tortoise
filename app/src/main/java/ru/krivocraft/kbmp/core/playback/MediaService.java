package ru.krivocraft.kbmp.core.playback;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.Objects;

import ru.krivocraft.kbmp.contexts.MainActivity;
import ru.krivocraft.kbmp.core.ColorManager;
import ru.krivocraft.kbmp.core.storage.TrackListsStorageManager;
import ru.krivocraft.kbmp.core.storage.TracksStorageManager;
import ru.krivocraft.kbmp.core.track.Track;
import ru.krivocraft.kbmp.core.track.TrackList;
import ru.krivocraft.kbmp.core.track.TrackReference;
import ru.krivocraft.kbmp.core.track.TracksProvider;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;

public class MediaService {

    private static final String ACTION_REQUEST_STOP = "stop";

    private static final String EXTRA_PLAYBACK_STATE = "playback_state";
    private static final String EXTRA_CURSOR = "cursor";
    private static final String EXTRA_METADATA = "metadata";

    public static final String EXTRA_POSITION = "position";
    public static final String ACTION_UPDATE_TRACK_LIST = "action_update_track_list";
    public static final String ACTION_REQUEST_TRACK_LIST = "action_request_track_list";

    public static final String ACTION_RESULT_TRACK_LIST = "result_track_list";
    public static final String ACTION_RESULT_DATA = "result_position";
    public static final String ACTION_REQUEST_DATA = "request_position";
    public static final String ACTION_EDIT_TRACK_LIST = "edit_track_list";
    public static final String ACTION_PLAY_FROM_LIST = "play_from_list";
    public static final String ACTION_EDIT_PLAYING_TRACK_LIST = "edit_current_track_list";
    public static final String ACTION_SHUFFLE = "shuffle";

    private static final int HEADSET_STATE_PLUG_IN = 1;
    private static final int HEADSET_STATE_PLUG_OUT = 0;

    private final MediaBrowserServiceCompat context;
    private final MediaSessionCompat mediaSession;
    private final MediaControllerCompat mediaController;
    private final NotificationBuilder notificationBuilder;
    private final TracksStorageManager tracksStorageManager;
    private final TrackListsStorageManager trackListsStorageManager;

    private final PlaybackManager playbackManager;

    public MediaService(MediaBrowserServiceCompat context) {
        this.context = context;
        mediaSession = new MediaSessionCompat(context, PlaybackManager.class.getSimpleName());
        context.setSessionToken(mediaSession.getSessionToken());

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        mediaSession.setActive(true);

        tracksStorageManager = new TracksStorageManager(context);
        trackListsStorageManager = new TrackListsStorageManager(context);

        mediaController = mediaSession.getController();

        notificationBuilder = new NotificationBuilder(context);

        playbackManager = new PlaybackManager(context, new PlaybackManager.PlayerStateCallback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat stateCompat) {
                mediaSession.setPlaybackState(stateCompat);
                showNotification();
            }

            @Override
            public void onTrackChanged(Track track) {
                mediaSession.setMetadata(track.getAsMediaMetadata());
                showNotification();
            }
        }, this::updateTrackList);

        mediaSession.setCallback(new MediaSessionCallback(playbackManager, this::stopPlayback));

        TracksProvider tracksProvider = new TracksProvider(context);
        tracksProvider.search();

        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(ACTION_AUDIO_BECOMING_NOISY);
        context.registerReceiver(headsetReceiver, headsetFilter);

        IntentFilter positionFilter = new IntentFilter();
        positionFilter.addAction(ACTION_REQUEST_DATA);
        positionFilter.addAction(ACTION_REQUEST_TRACK_LIST);
        context.registerReceiver(requestDataReceiver, positionFilter);

        IntentFilter playlistFilter = new IntentFilter();
        playlistFilter.addAction(ACTION_REQUEST_STOP);
        playlistFilter.addAction(ACTION_SHUFFLE);
        playlistFilter.addAction(ACTION_EDIT_PLAYING_TRACK_LIST);
        playlistFilter.addAction(ACTION_EDIT_TRACK_LIST);
        playlistFilter.addAction(ACTION_PLAY_FROM_LIST);
        context.registerReceiver(playlistReceiver, playlistFilter);

        IntentFilter colorFilter = new IntentFilter();
        colorFilter.addAction(ColorManager.ACTION_REQUEST_COLOR);
        context.registerReceiver(colorRequestReceiver, colorFilter);

    }


    private void showNotification() {
        Notification notification = notificationBuilder.getNotification(mediaSession);
        if (notification != null) {
            context.startForeground(NotificationBuilder.NOTIFY_ID, notification);
        }
    }

    private void hideNotification() {
        context.stopForeground(true);
    }

    private final BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                //Old method works only with wired headset and some bluetooth headphones.
                //The feature is replaying audio when user plugs headphones back
                switch (intent.getIntExtra("state", -1)) {
                    case HEADSET_STATE_PLUG_IN:
                        if (playbackManager.getSelectedTrackReference() != null) {
                            mediaSession.getController().getTransportControls().play();
                        }
                        break;
                    case HEADSET_STATE_PLUG_OUT:
                        mediaSession.getController().getTransportControls().pause();
                        break;
                    default:
                        //No idea what to do in this case
                        break;
                }
            } else {
                //Mute if some device unplugged
                mediaSession.getController().getTransportControls().pause();
            }
        }
    };


    private final BroadcastReceiver requestDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_REQUEST_DATA.equals(intent.getAction())) {
                Intent result = new Intent(ACTION_RESULT_DATA);
                result.putExtra(EXTRA_POSITION, playbackManager.getCurrentStreamPosition());
                result.putExtra(EXTRA_PLAYBACK_STATE, mediaSession.getController().getPlaybackState());
                result.putExtra(EXTRA_METADATA, mediaSession.getController().getMetadata());
                context.sendBroadcast(result);
            } else {
                Intent result = new Intent(ACTION_RESULT_TRACK_LIST);
                result.putExtra(TrackList.EXTRA_TRACK_LIST, playbackManager.getTrackList().toJson());
                result.putExtra(Track.EXTRA_TRACK, playbackManager.getSelectedTrackReference().toJson());
                result.putExtra(EXTRA_CURSOR, playbackManager.getCursor());
                context.sendBroadcast(result);
            }
        }
    };


    private final BroadcastReceiver playlistReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case ACTION_PLAY_FROM_LIST:
                    playFromList(intent);
                    break;
                case ACTION_REQUEST_STOP:
                    stopPlayback();
                    break;
                case ACTION_SHUFFLE:
                    shuffle();
                    break;
                case ACTION_EDIT_PLAYING_TRACK_LIST:
                    TrackList in = TrackList.fromJson(intent.getStringExtra(TrackList.EXTRA_TRACK_LIST));
                    notifyPlaybackManager(in);
                    break;
                case ACTION_EDIT_TRACK_LIST:
                    TrackList trackListEdited = TrackList.fromJson(intent.getStringExtra(TrackList.EXTRA_TRACK_LIST));
                    if (trackListEdited.equals(playbackManager.getTrackList())) {
                        notifyPlaybackManager(trackListEdited);
                    }
                    trackListsStorageManager.updateTrackListData(trackListEdited);
                    context.sendBroadcast(new Intent(TracksProvider.ACTION_UPDATE_STORAGE));
                    break;
                default:
                    //Do nothing
                    break;
            }
        }
    };

    private void shuffle() {
        playbackManager.shuffle();
    }

    private void stopPlayback() {
        System.out.println(System.currentTimeMillis());
        playbackManager.stop();
        System.out.println(System.currentTimeMillis());
        hideNotification();
        System.out.println(System.currentTimeMillis());
        context.sendBroadcast(new Intent(MainActivity.ACTION_HIDE_PLAYER));
        System.out.println(System.currentTimeMillis());
    }

    private void playFromList(Intent intent) {
        TrackList trackList = TrackList.fromJson(intent.getStringExtra(TrackList.EXTRA_TRACK_LIST));
        TrackReference reference = TrackReference.fromJson(intent.getStringExtra(Track.EXTRA_TRACK));

        if (!trackList.equals(playbackManager.getTrackList())) {
            playbackManager.setTrackList(trackList, true);
        }

        MediaMetadataCompat metadata = mediaController.getMetadata();
        if (metadata == null) {
            mediaController.getTransportControls().skipToQueueItem(playbackManager.getTrackList().indexOf(reference));
        } else {
            if (metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).equals(tracksStorageManager.getTrack(reference).getPath()) && trackList.equals(playbackManager.getTrackList())) {
                if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
                playbackManager.setCursor(playbackManager.getTrackList().indexOf(reference));
            } else {
                mediaController.getTransportControls().skipToQueueItem(playbackManager.getTrackList().indexOf(reference));
            }
        }
    }

    private BroadcastReceiver colorRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TrackReference currentTrack = playbackManager.getSelectedTrackReference();
            int color = -1;
            if (currentTrack != null) {
                Track track = tracksStorageManager.getTrack(currentTrack);
                color = track.getColor();
            }
            context.sendBroadcast(new Intent(ColorManager.ACTION_RESULT_COLOR)
                    .putExtra(ColorManager.EXTRA_COLOR, color));

        }
    };

    private void updateTrackList(TrackList list) {
        Intent intent = new Intent(ACTION_UPDATE_TRACK_LIST);
        intent.putExtra(TrackList.EXTRA_TRACK_LIST, list.toJson());
        context.sendBroadcast(intent);
    }

    private void notifyPlaybackManager(TrackList in) {
        TrackReference reference = playbackManager.getSelectedTrackReference();
        playbackManager.setTrackList(in, false);
        playbackManager.setCursor(in.indexOf(reference));
    }

    public void handleCommand(Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public void destroy() {

        context.unregisterReceiver(headsetReceiver);
        context.unregisterReceiver(playlistReceiver);
        context.unregisterReceiver(requestDataReceiver);
        context.unregisterReceiver(colorRequestReceiver);

        playbackManager.destroy();
    }
}