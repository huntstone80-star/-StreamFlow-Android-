package com.streamflow.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    private TextView titleText, episodeCounter;
    private View loadingOverlay, controlsOverlay;
    private Handler controlsHandler = new Handler();
    private Runnable hideControlsRunnable;
    private List<EpisodeData> episodeList = new ArrayList<>();
    private int currentEpisodeIndex = 0;
    private boolean controlsVisible = true;
    private long lastControllerHideTime = 0;

    static class EpisodeData {
        String url;
        String title;
        String subtitlesJson;
        EpisodeData(String u, String t, String s) { url = u; title = t; subtitlesJson = s; }
    }

    @SuppressLint("SetJavaScriptEnabled")
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

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(4000);
        playerView.setControllerAutoShow(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        loadingOverlay = new View(this);
        loadingOverlay.setBackgroundColor(0xFF141414);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        controlsOverlay = getLayoutInflater().inflate(R.layout.player_overlay, null);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(controlsOverlay, overlayParams);

        titleText = controlsOverlay.findViewById(R.id.player_title);
        episodeCounter = controlsOverlay.findViewById(R.id.player_episode_counter);

        setContentView(root);

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

        initializePlayer();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNextEpisode();
                }
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }
        });

        hideControlsRunnable = () -> {
            controlsOverlay.setVisibility(View.GONE);
            playerView.setControllerShowTimeoutMs(0);
            controlsVisible = false;
        };

        loadCurrentEpisode();
    }

    private void loadCurrentEpisode() {
        if (currentEpisodeIndex >= episodeList.size()) {
            finish();
            return;
        }
        EpisodeData ep = episodeList.get(currentEpisodeIndex);
        loadingOverlay.setVisibility(View.VISIBLE);

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
                for (int i = 0; i < subs.length(); i++) {
                    JSONObject sub = subs.getJSONObject(i);
                    if (sub.has("src")) {
                        builder.setSubtitleConfigurations(java.util.Collections.singletonList(
                                new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.getString("src")))
                                        .setMimeType("text/srt")
                                        .setLanguage(sub.optString("lang", "en"))
                                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                        .build()));
                    }
                }
            } catch (Exception ignored) {}
        }

        player.setMediaItem(builder.build());
        player.prepare();
        showControls();
    }

    private void playNextEpisode() {
        if (currentEpisodeIndex + 1 < episodeList.size()) {
            currentEpisodeIndex++;
            loadCurrentEpisode();
        } else {
            finish();
        }
    }

    private void showControls() {
        controlsOverlay.setVisibility(View.VISIBLE);
        playerView.setControllerShowTimeoutMs(4000);
        controlsVisible = true;
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, 4000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player != null) {
                if (controlsVisible) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
                showControls();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (controlsVisible && player != null) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                showControls();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (controlsVisible && player != null) {
                player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                showControls();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (controlsVisible && player != null) {
                player.setVolume(Math.min(1, player.getVolume() + 0.1f));
                showControls();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (controlsVisible && player != null) {
                player.setVolume(Math.max(0, player.getVolume() - 0.1f));
                showControls();
                return true;
            }
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
            if (currentEpisodeIndex > 0) {
                currentEpisodeIndex--;
                loadCurrentEpisode();
            }
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
        controlsHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
