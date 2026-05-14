package com.streamflow.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlayerActivity extends android.app.Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private Handler progressHandler = new Handler();
    private Handler controlsHandler = new Handler();
    private Runnable hideControlsRunnable;
    private Runnable progressRunnable;
    private List<EpisodeData> episodeList = new ArrayList<>();
    private int currentEpisodeIndex = 0;
    private boolean controlsVisible = true;
    private boolean isSeeking = false;

    // UI elements
    private View controlsOverlay, loadingOverlay, topBar, bottomBar;
    private TextView titleText, episodeCounter, seasonInfo;
    private TextView currentTime, remainingTime, totalTime;
    private TextView btnPlayPause, btnPrevious, btnNext, btnRewind, btnForward, btnBack;
    private ImageView playPauseCenter;
    private View progressPlayed, progressBuffered, progressThumb;
    private View volumeIndicator, volumeLevel, volumeText;
    private View episodeChangeOverlay;
    private TextView episodeChangeTitle, episodeChangeSubtitle;
    private ViewGroup progressContainer;

    static class EpisodeData {
        String url;
        String title;
        String subtitlesJson;
        EpisodeData(String u, String t, String s) { url = u; title = t; subtitlesJson = s; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // Player view (no default controller - we use our custom overlay)
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Loading overlay
        loadingOverlay = new View(this);
        loadingOverlay.setBackgroundColor(0xFF141414);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Custom controls overlay
        controlsOverlay = getLayoutInflater().inflate(R.layout.player_overlay, null);
        root.addView(controlsOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        bindViews();
        setupListeners();
        parseIntent();
        initializePlayer();
    }

    private void bindViews() {
        topBar = controlsOverlay.findViewById(R.id.top_bar);
        bottomBar = controlsOverlay.findViewById(R.id.bottom_bar);
        titleText = controlsOverlay.findViewById(R.id.player_title);
        episodeCounter = controlsOverlay.findViewById(R.id.player_episode_counter);
        seasonInfo = controlsOverlay.findViewById(R.id.player_season_info);
        currentTime = controlsOverlay.findViewById(R.id.current_time);
        remainingTime = controlsOverlay.findViewById(R.id.remaining_time);
        totalTime = controlsOverlay.findViewById(R.id.total_time);
        btnPlayPause = controlsOverlay.findViewById(R.id.btn_play_pause);
        btnPrevious = controlsOverlay.findViewById(R.id.btn_previous);
        btnNext = controlsOverlay.findViewById(R.id.btn_next);
        btnRewind = controlsOverlay.findViewById(R.id.btn_rewind);
        btnForward = controlsOverlay.findViewById(R.id.btn_forward);
        btnBack = controlsOverlay.findViewById(R.id.btn_back);
        playPauseCenter = controlsOverlay.findViewById(R.id.play_pause_center);
        progressPlayed = controlsOverlay.findViewById(R.id.progress_played);
        progressBuffered = controlsOverlay.findViewById(R.id.progress_buffered);
        progressThumb = controlsOverlay.findViewById(R.id.progress_thumb);
        progressContainer = controlsOverlay.findViewById(R.id.progress_container);
        volumeIndicator = controlsOverlay.findViewById(R.id.volume_indicator);
        volumeLevel = controlsOverlay.findViewById(R.id.volume_level);
        volumeText = controlsOverlay.findViewById(R.id.volume_text);
        episodeChangeOverlay = controlsOverlay.findViewById(R.id.episode_change_overlay);
        episodeChangeTitle = controlsOverlay.findViewById(R.id.episode_change_title);
        episodeChangeSubtitle = controlsOverlay.findViewById(R.id.episode_change_subtitle);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnBack.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnPlayPause.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        btnPrevious.setOnClickListener(v -> playPreviousEpisode());
        btnPrevious.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        btnNext.setOnClickListener(v -> playNextEpisode());
        btnNext.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        btnRewind.setOnClickListener(v -> seekRelative(-10000));
        btnRewind.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        btnForward.setOnClickListener(v -> seekRelative(30000));
        btnForward.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.15f : 1f);
            v.setScaleY(hasFocus ? 1.15f : 1f);
        });

        progressContainer.setOnClickListener(v -> {
            if (player == null) return;
            float x = progressContainer.getX() > 0 ? (v.getX() - progressContainer.getX()) : v.getX();
            float ratio = Math.max(0, Math.min(1, x / progressContainer.getWidth()));
            player.seekTo((long)(ratio * player.getDuration()));
            showControls();
        });
    }

    private void parseIntent() {
        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");
        String subtitlesJson = getIntent().getStringExtra("subtitles");
        String episodesJson = getIntent().getStringExtra("episodes");

        if (url != null) {
            episodeList.add(new EpisodeData(url, title, subtitlesJson));
        }
        if (episodesJson != null) {
            try {
                JSONArray arr = new JSONArray(episodesJson);
                episodeList.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    episodeList.add(new EpisodeData(
                        obj.getString("url"),
                        obj.optString("title", "Episode " + (i + 1)),
                        obj.optString("subtitles", null)
                    ));
                }
            } catch (Exception ignored) {}
        }
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    loadingOverlay.animate().alpha(0f).setDuration(300).withEndAction(() ->
                        loadingOverlay.setVisibility(View.GONE)).start();
                    updatePlayPauseIcon();
                    startProgressUpdates();
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNextEpisode();
                }
                if (playbackState == Player.STATE_BUFFERING) {
                    // Could show buffering indicator
                }
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon();
                if (isPlaying && loadingOverlay.getVisibility() == View.VISIBLE) {
                    loadingOverlay.animate().alpha(0f).setDuration(300).withEndAction(() ->
                        loadingOverlay.setVisibility(View.GONE)).start();
                }
            }
        });

        hideControlsRunnable = () -> {
            if (player != null && player.isPlaying()) {
                controlsOverlay.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                    controlsOverlay.setVisibility(View.INVISIBLE);
                    controlsVisible = false;
                }).start();
            }
        };

        startProgressUpdates();
        loadCurrentEpisode();
    }

    private void loadCurrentEpisode() {
        if (currentEpisodeIndex >= episodeList.size()) {
            finish();
            return;
        }
        EpisodeData ep = episodeList.get(currentEpisodeIndex);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(1f);

        if (titleText != null) {
            titleText.setText(ep.title != null ? ep.title : "Episode " + (currentEpisodeIndex + 1));
        }
        if (episodeCounter != null) {
            episodeCounter.setText((currentEpisodeIndex + 1) + " / " + episodeList.size());
        }

        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(ep.url)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(ep.title != null ? ep.title : "Episode " + (currentEpisodeIndex + 1))
                        .build());

        if (ep.subtitlesJson != null && !ep.subtitlesJson.isEmpty()) {
            try {
                JSONArray subs = new JSONArray(ep.subtitlesJson);
                List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
                for (int i = 0; i < subs.length(); i++) {
                    JSONObject sub = subs.getJSONObject(i);
                    if (sub.has("src")) {
                        configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.getString("src")))
                                .setMimeType("text/srt")
                                .setLanguage(sub.optString("lang", "en"))
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build());
                    }
                }
                if (!configs.isEmpty()) {
                    builder.setSubtitleConfigurations(configs);
                }
            } catch (Exception ignored) {}
        }

        player.setMediaItem(builder.build());
        player.prepare();
        showControls();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        showPlayPauseAnimation();
        showControls();
    }

    private void showPlayPauseAnimation() {
        if (player == null) return;
        playPauseCenter.setVisibility(View.VISIBLE);
        playPauseCenter.setAlpha(1f);
        playPauseCenter.setScaleX(1.5f);
        playPauseCenter.setScaleY(1.5f);

        playPauseCenter.setImageDrawable(getDrawable(
            player.isPlaying() ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause));

        playPauseCenter.animate()
                .alpha(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .withEndAction(() -> playPauseCenter.setVisibility(View.GONE))
                .start();
    }

    private void updatePlayPauseIcon() {
        if (player == null) return;
        if (player.isPlaying()) {
            btnPlayPause.setText("\u25B6");
        } else {
            btnPlayPause.setText("\u23F8");
        }
    }

    private void seekRelative(long ms) {
        if (player == null) return;
        long newPos = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + ms));
        player.seekTo(newPos);
        showControls();
    }

    private void playNextEpisode() {
        if (currentEpisodeIndex + 1 < episodeList.size()) {
            currentEpisodeIndex++;
            showEpisodeChangeOverlay(episodeList.get(currentEpisodeIndex).title, false);
            loadCurrentEpisode();
        } else {
            finish();
        }
    }

    private void playPreviousEpisode() {
        if (currentEpisodeIndex > 0) {
            currentEpisodeIndex--;
            showEpisodeChangeOverlay(episodeList.get(currentEpisodeIndex).title, true);
            loadCurrentEpisode();
        }
    }

    private void showEpisodeChangeOverlay(String title, boolean isPrevious) {
        episodeChangeOverlay.setVisibility(View.VISIBLE);
        episodeChangeOverlay.setAlpha(0f);
        episodeChangeTitle.setText(isPrevious ? "Previous Episode" : "Next Episode");
        episodeChangeSubtitle.setText(title != null ? title : "");
        episodeChangeOverlay.animate().alpha(1f).setDuration(300).start();
        controlsHandler.postDelayed(() -> {
            episodeChangeOverlay.animate().alpha(0f).setDuration(500)
                .withEndAction(() -> episodeChangeOverlay.setVisibility(View.GONE)).start();
        }, 1500);
    }

    private void showControls() {
        controlsOverlay.setVisibility(View.VISIBLE);
        controlsOverlay.setAlpha(1f);
        controlsVisible = true;
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, 4000);
        updatePlayPauseIcon();
    }

    private void startProgressUpdates() {
        if (progressRunnable != null) progressHandler.removeCallbacks(progressRunnable);
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                progressHandler.postDelayed(this, 250);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void updateProgress() {
        if (player == null || progressContainer == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration <= 0) return;

        float ratio = (float) position / duration;
        int width = progressContainer.getWidth();

        // Played progress
        ViewGroup.LayoutParams playedParams = progressPlayed.getLayoutParams();
        playedParams.width = (int)(ratio * width);
        progressPlayed.setLayoutParams(playedParams);

        // Thumb position
        int thumbX = (int)(ratio * width) - 7;
        progressThumb.setTranslationX(Math.max(0, Math.min(width - 14, thumbX)));

        // Time labels
        currentTime.setText(formatTime(position));
        totalTime.setText(formatTime(duration));
        remainingTime.setText("-" + formatTime(duration - position));
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    private void showVolumeIndicator(float volume) {
        volumeIndicator.setVisibility(View.VISIBLE);
        volumeIndicator.setAlpha(1f);
        int level = Math.round(volume * 100);
        ViewGroup.LayoutParams volParams = volumeLevel.getLayoutParams();
        volParams.height = (int)(volume * 120);
        volumeLevel.setLayoutParams(volParams);
        if (volumeText != null) {
            ((TextView)volumeText).setText(level + "%");
        }
        controlsHandler.removeCallbacks(hideVolumeRunnable);
        controlsHandler.postDelayed(hideVolumeRunnable, 1500);
    }

    private Runnable hideVolumeRunnable = () -> {
        volumeIndicator.animate().alpha(0f).setDuration(300)
            .withEndAction(() -> volumeIndicator.setVisibility(View.GONE)).start();
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player != null && controlsVisible) {
                togglePlayPause();
            } else {
                showControls();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (player != null) {
                if (!controlsVisible) showControls();
                seekRelative(-10000);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (player != null) {
                if (!controlsVisible) showControls();
                seekRelative(10000);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (player != null) {
                float vol = Math.min(1, player.getVolume() + 0.1f);
                player.setVolume(vol);
                showVolumeIndicator(vol);
                if (!controlsVisible) showControls();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (player != null) {
                float vol = Math.max(0, player.getVolume() - 0.1f);
                player.setVolume(vol);
                showVolumeIndicator(vol);
                if (!controlsVisible) showControls();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            playNextEpisode();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            playPreviousEpisode();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekRelative(-30000);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekRelative(30000);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        progressHandler.removeCallbacksAndMessages(null);
        controlsHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
