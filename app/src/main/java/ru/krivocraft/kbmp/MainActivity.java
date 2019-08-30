package ru.krivocraft.kbmp;

import android.Manifest;
import android.animation.LayoutTransition;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import ru.krivocraft.kbmp.constants.Constants;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends BaseActivity {

    private SmallPlayerFragment smallPlayerFragment;
    private MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaControllerCompat;

    private int viewState = 0;
    private static final int STATE_EXPLORER = 1;
    private static final int STATE_TRACK_LIST = 2;

    private int PERMISSION_WRITE_EXTERNAL_STORAGE = 22892;

    private BroadcastReceiver showPlayerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constants.Actions.ACTION_SHOW_PLAYER.equals(intent.getAction())) {
                showSmallPlayerFragment();
            } else if (Constants.Actions.ACTION_HIDE_PLAYER.equals(intent.getAction())) {
                hideSmallPlayerFragment();
            }
        }
    };
    private TrackListFragment trackListFragment;
    private ExplorerFragment explorerFragment;

    @NonNull
    private TrackListFragment getTrackListFragment(TrackList trackList) {
        trackListFragment = TrackListFragment.newInstance(trackList, true, this);
        return trackListFragment;
    }

    private ExplorerFragment getExplorerFragment() {
        if (explorerFragment == null) {
            explorerFragment = ExplorerFragment.newInstance(this::showTrackListFragment); //ExplorerFragment is singleton, so we will reuse it, if it is possible
        }
        return explorerFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearOld();
        requestStoragePermission();
    }

    private void clearOld() {
        OldStuffCollector collector = new OldStuffCollector(this);
        collector.execute();
    }

    private void init() {
        setContentView(R.layout.activity_tortoise);
        configureLayoutTransition();

        startService();
        registerPlayerControlReceiver();

        initMediaBrowser();
        mediaBrowser.connect();

        showExplorerFragment();
    }

    private void initMediaBrowser() {
        mediaBrowser = new MediaBrowserCompat(
                MainActivity.this,
                new ComponentName(MainActivity.this, MediaPlaybackService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            MediaSessionCompat.Token token = mediaBrowser.getSessionToken();
                            MediaControllerCompat controller = new MediaControllerCompat(MainActivity.this, token);
                            MediaControllerCompat.setMediaController(MainActivity.this, controller);
                            MainActivity.this.mediaControllerCompat = controller;
                            showSmallPlayerFragment();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConnectionFailed() {
                        Log.e("TAG", "onConnectionFailed");
                        Toast.makeText(MainActivity.this, "Something is wrong", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onConnectionSuspended() {
                        Log.e("TAG", "onConnectionSuspended");
                    }
                },
                null);
    }

    private void configureLayoutTransition() {
        RelativeLayout layout = findViewById(R.id.main_layout);
        layout.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
    }

    private void startService() {
        if (!MediaPlaybackService.running) {
            startService(new Intent(this, MediaPlaybackService.class));
        }
    }

    private void registerPlayerControlReceiver() {
        IntentFilter showPlayerFilter = new IntentFilter();
        showPlayerFilter.addAction(Constants.Actions.ACTION_SHOW_PLAYER);
        showPlayerFilter.addAction(Constants.Actions.ACTION_HIDE_PLAYER);
        registerReceiver(showPlayerReceiver, showPlayerFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showExplorerFragment() {
        removeTrackListFragment();
        if (explorerFragment == null) {
            addFragment(R.anim.fadein, getExplorerFragment(), R.id.fragment_container);
        } else {
            showFragment(explorerFragment);
        }
        viewState = STATE_EXPLORER;
    }

    private void showTrackListFragment(TrackList trackList) {
        hideExplorerFragment();
        addFragment(R.anim.fadein, getTrackListFragment(trackList), R.id.fragment_container);
        viewState = STATE_TRACK_LIST;
    }

    @Override
    public void onBackPressed() {
        if (viewState == STATE_TRACK_LIST) {
            showExplorerFragment();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();

            unregisterReceiver(showPlayerReceiver);
            unregisterReceiver(positionReceiver);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                Toast.makeText(this, "App requires external storage permission to work", Toast.LENGTH_LONG).show();
                finishAffinity();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTheme();
        if (explorerFragment != null) {
            explorerFragment.invalidate();
        }
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
        } else {
            init();
        }
    }

    private void hideExplorerFragment() {
        hideFragment(explorerFragment);
    }

    private void removeTrackListFragment() {
        removeFragment(trackListFragment);
        trackListFragment = null;
    }

    private void showSmallPlayerFragment() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.Actions.ACTION_RESULT_DATA);
        registerReceiver(positionReceiver, filter);

        Intent intent = new Intent(Constants.Actions.ACTION_REQUEST_DATA);
        sendBroadcast(intent);
    }

    BroadcastReceiver positionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mediaControllerCompat != null) {
                MediaMetadataCompat metadata = intent.getParcelableExtra(Constants.Extras.EXTRA_METADATA);
                PlaybackStateCompat playbackState = intent.getParcelableExtra(Constants.Extras.EXTRA_PLAYBACK_STATE);
                int position = intent.getIntExtra(Constants.Extras.EXTRA_POSITION, 0);

                if (metadata != null && playbackState != null) {
                    if (smallPlayerFragment != null) {
                        smallPlayerFragment.init(MainActivity.this, metadata, playbackState, position);
                        smallPlayerFragment.invalidate();
                    } else {
                        SmallPlayerFragment smallPlayerFragment = new SmallPlayerFragment();
                        smallPlayerFragment.init(MainActivity.this, metadata, playbackState, position);
                        MainActivity.this.smallPlayerFragment = smallPlayerFragment;
                        addFragment(R.anim.fadeinshort, MainActivity.this.smallPlayerFragment, R.id.player_container);
                    }
                }
            }
        }
    };

    private void hideSmallPlayerFragment() {
        hideFragment(smallPlayerFragment);
        smallPlayerFragment = null;
    }


    private void addFragment(int animationIn, Fragment fragment, int container) {
        if (fragment != null && !fragment.isVisible()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(animationIn, R.anim.fadeoutshort)
                    .add(container, fragment)
                    .commitNowAllowingStateLoss();
        }
    }

    private void showFragment(Fragment fragment) {
        if (fragment != null && !fragment.isVisible()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .show(fragment)
                    .commitNowAllowingStateLoss();
        }
    }

    private void hideFragment(Fragment fragment) {
        if (fragment != null && fragment.isVisible()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .hide(fragment)
                    .commitNowAllowingStateLoss();
        }
    }

    private void removeFragment(Fragment fragment) {
        if (fragment != null && fragment.isVisible()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss();
        }
    }

}
