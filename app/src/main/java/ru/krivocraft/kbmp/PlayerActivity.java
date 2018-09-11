package ru.krivocraft.kbmp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class PlayerActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, Track.OnTrackStateChangedListener {

    private SeekBar compositionProgressBar;

    private boolean mBounded = false;

    private Timer compositionProgressTimer;
    private TextView compositionProgressTextView;
    private TextView compositionDurationTextView;
    private TextView compositionNameTextView;
    private TextView compositionAuthorTextView;
    private ImageButton playPauseButton;

    private PlayerService mService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBounded = true;

            PlayerService.LocalBinder localBinder = (PlayerService.LocalBinder) service;
            mService = localBinder.getServerInstance();

            mService.addListener(PlayerActivity.this);

            initUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBounded = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playPauseButton = findViewById(R.id.play_pause);
        compositionNameTextView = findViewById(R.id.composition_name);
        compositionAuthorTextView = findViewById(R.id.composition_author);
        compositionProgressTextView = findViewById(R.id.composition_progress);
        compositionDurationTextView = findViewById(R.id.composition_duration);
        compositionProgressBar = findViewById(R.id.composition_progress_bar);

        bindService(new Intent(this, PlayerService.class), mConnection, BIND_ABOVE_CLIENT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBounded) {
            mService.removeListener(PlayerActivity.this);
            unbindService(mConnection);
            mBounded = false;
        }
    }

    private void initUI() {
        Track currentTrack = mService.getCurrentTrack();

        int progress = Utils.getSeconds(mService.getProgress());

        String compositionName = currentTrack.getName();
        String compositionComposer = currentTrack.getArtist();
        String compositionDuration = currentTrack.getDuration();

        compositionProgressTextView.setText(Utils.getFormattedTime(progress));
        compositionDurationTextView.setText(Utils.getFormattedTime((Integer.parseInt(compositionDuration) - progress) / 1000));

        compositionProgressBar.setProgress(progress);
        compositionProgressBar.setOnSeekBarChangeListener(this);

        compositionNameTextView.setText(compositionName);
        compositionAuthorTextView.setText(compositionComposer);

        compositionProgressBar.setMax(Integer.parseInt(compositionDuration) / 1000);

        if (mService.isPlaying()) {
            startUIPlaying();
        } else {
            stopUIPlaying();
        }
    }

    private void updateBar() {
        int duration = Integer.parseInt(mService.getCurrentTrack().getDuration());

        int progressMillis = mService.getProgress();
        int estimatedMillis = duration - progressMillis;

        int progress = Utils.getSeconds(progressMillis);
        int estimated = Utils.getSeconds(estimatedMillis) - 1;

        if (progress > compositionProgressBar.getMax()) {
            stopPlaying();
        } else {
            compositionProgressBar.setProgress(progress);
            compositionProgressTextView.setText(Utils.getFormattedTime(progress));
            compositionDurationTextView.setText(Utils.getFormattedTime(estimated));
        }
    }

    private void startPlaying() {
        if (mBounded) {
            mService.start();
        }
    }

    private void stopPlaying() {
        if (mBounded) {
            mService.stop();
        }
    }

    private void startUIPlaying() {
        if (compositionProgressTimer == null) {
            compositionProgressTimer = new Timer();
            compositionProgressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateBar();
                        }
                    });
                }
            }, Constants.ZERO, Constants.ONE_SECOND);

        }
        playPauseButton.setImageResource(R.drawable.ic_pause);

    }

    private void stopUIPlaying() {
        if (compositionProgressTimer != null) {
            compositionProgressTimer.cancel();
            compositionProgressTimer = null;
        }
        playPauseButton.setImageResource(R.drawable.ic_play);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.play_pause:
                if (!mService.isPlaying()) {
                    startPlaying();
                } else {
                    stopPlaying();
                }
                break;
            case R.id.previous:
                previousComposition();
                break;
            case R.id.next:
                nextComposition();
                break;
        }
    }

    private void previousComposition() {
        if (mBounded) {
            mService.previousComposition();
        }
    }

    private void nextComposition() {
        if (mBounded) {
            mService.nextComposition();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        stopPlaying();
        mService.setCurrentCompositionProgress(seekBar.getProgress() * 1000);
        startPlaying();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onNewTrackState() {
        initUI();
    }
}
