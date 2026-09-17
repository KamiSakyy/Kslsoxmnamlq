package com.tsuyu.line;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public final class TikTokActivity extends Activity {
    private FrameLayout root;
    private PlayerView playerView;
    private RecyclerView recycler;
    private ClipAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;
    private ExoPlayer player;
    private final ArrayList<ClipServer.Clip> clips = new ArrayList<>();
    private int currentPosition = -1;
    private boolean autoScroll = false; // By default disabled
    private boolean isMuted = false;
    private boolean isZoomMode = false; // By default NOT expanded (Fit mode)
    private boolean destroyed = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private ClipHolder currentHolder;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (destroyed || player == null || currentHolder == null) return;
            if (currentPosition >= 0 && currentPosition < clips.size()) {
                ClipServer.Clip clip = clips.get(currentPosition);
                long pos = player.getCurrentPosition();
                long dur = clip.durationSec * 1000L;
                if (dur > 0 && currentHolder.progressBar != null) {
                    int percent = (int) Math.min(100, Math.max(0, (pos * 100L) / dur));
                    currentHolder.progressBar.setProgress(percent);
                }
            }
            progressHandler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Ui.allow(this)) return;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        initPlayer();
        initRecycler();
        initTopBar();

        loadInitialClips();
    }

    private void initPlayer() {
        DefaultLoadControl control = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1500, 8000, 250, 600)
                .setBackBuffer(2000, false)
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(control)
                .build();

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(isZoomMode ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setPlayer(player);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (currentHolder != null) {
                    currentHolder.buffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                }
                if (state == Player.STATE_ENDED) {
                    if (autoScroll && currentPosition + 1 < clips.size()) {
                        recycler.smoothScrollToPosition(currentPosition + 1);
                    } else {
                        player.seekTo(0);
                        player.play();
                    }
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                if (currentHolder != null && currentHolder.poster != null) {
                    currentHolder.poster.animate()
                            .alpha(0f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                if (currentHolder != null && currentHolder.poster != null) {
                                    currentHolder.poster.setVisibility(View.INVISIBLE);
                                }
                            })
                            .start();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (currentHolder != null && currentHolder.playPauseCenter != null) {
                    if (!isPlaying) {
                        currentHolder.playPauseCenter.setVisibility(View.VISIBLE);
                        currentHolder.playPauseCenter.setAlpha(1f);
                    } else {
                        currentHolder.playPauseCenter.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    if (currentHolder != null && currentHolder.playPauseCenter != null) {
                                        currentHolder.playPauseCenter.setVisibility(View.GONE);
                                    }
                                })
                                .start();
                    }
                }
            }
        });
    }

    private void initRecycler() {
        recycler = new RecyclerView(this);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recycler.setLayoutManager(layoutManager);
        recycler.setItemViewCacheSize(0);

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        adapter = new ClipAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkActiveSnap();
                }
            }
        });

        root.addView(recycler, new FrameLayout.LayoutParams(-1, -1));
    }

    private void initTopBar() {
        LinearLayout topBar = Ui.row(this);
        topBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 36), Ui.dp(this, 14), Ui.dp(this, 10));

        View back = Ui.iconButton(this, "back", "Назад", this::finish);
        topBar.addView(back, Ui.lp(this, 40, 40));

        LinearLayout titleCol = Ui.column(this);
        titleCol.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 10), 0);
        TextView title = Ui.text(this, "Лента аниме", 16, Color.WHITE, true);
        titleCol.addView(title);

        TextView badge = Ui.text(this, "TikTok • 15–30 сек", 10, Ui.PURPLE, false);
        titleCol.addView(badge);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        Ui.gap(topBar, Ui.iconButton(this, "refresh", "Случайный", () -> {
            if (currentPosition + 1 < clips.size()) {
                recycler.smoothScrollToPosition(currentPosition + 1);
            } else {
                fetchMoreClips(true);
            }
        }), 38, 38, 8);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(topBar, lp);
    }

    private void loadInitialClips() {
        for (int i = 0; i < 3; i++) {
            ClipServer.get().nextClip(new ClipServer.ClipCallback() {
                @Override
                public void onClipReady(ClipServer.Clip clip) {
                    clips.add(clip);
                    adapter.notifyItemInserted(clips.size() - 1);
                    if (currentPosition == -1) {
                        recycler.post(() -> checkActiveSnap());
                    }
                }

                @Override
                public void onClipFailed() {
                }
            });
        }
    }

    private void fetchMoreClips(boolean playImmediately) {
        ClipServer.get().nextClip(new ClipServer.ClipCallback() {
            @Override
            public void onClipReady(ClipServer.Clip clip) {
                int index = clips.size();
                clips.add(clip);
                adapter.notifyItemInserted(index);
                if (playImmediately) {
                    recycler.smoothScrollToPosition(index);
                }
            }

            @Override
            public void onClipFailed() {
            }
        });
    }

    private void checkActiveSnap() {
        View snapView = snapHelper.findSnapView(layoutManager);
        if (snapView == null) return;
        int pos = layoutManager.getPosition(snapView);
        if (pos == currentPosition || pos < 0 || pos >= clips.size()) return;

        if (currentHolder != null && currentHolder.poster != null) {
            currentHolder.poster.setAlpha(1f);
            currentHolder.poster.setVisibility(View.VISIBLE);
        }

        currentPosition = pos;
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(pos);
        if (vh instanceof ClipHolder) {
            playClip((ClipHolder) vh, clips.get(pos));
        }

        if (pos >= clips.size() - 3) {
            fetchMoreClips(false);
            ClipServer.get().triggerPrefetch();
        }
    }

    private void playClip(ClipHolder holder, ClipServer.Clip clip) {
        if (player == null) return;
        progressHandler.removeCallbacks(progressTick);
        currentHolder = holder;

        holder.poster.setAlpha(1f);
        holder.poster.setVisibility(View.VISIBLE);
        holder.buffering.setVisibility(View.VISIBLE);
        holder.updateState();

        MediaItem.ClippingConfiguration clipping = new MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs)
                .setEndPositionMs(clip.endMs)
                .build();

        MediaItem mediaItem = DownloadHub.item(clip.streamUrl).buildUpon()
                .setClippingConfiguration(clipping)
                .build();

        player.setMediaSource(new DefaultMediaSourceFactory(YoruApp.app().mediaCache.onlineFactory()).createMediaSource(mediaItem));
        player.setRepeatMode(autoScroll ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
        player.setVolume(isMuted ? 0f : 1f);
        player.prepare();
        player.play();

        progressHandler.post(progressTick);
    }

    private final class ClipAdapter extends RecyclerView.Adapter<ClipHolder> {
        @NonNull
        @Override
        public ClipHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout item = new FrameLayout(TikTokActivity.this);
            item.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            return new ClipHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ClipHolder h, int position) {
            ClipServer.Clip clip = clips.get(position);
            h.bind(clip);
        }

        @Override
        public int getItemCount() {
            return clips.size();
        }
    }

    final class ClipHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        final ImageView poster;
        final ProgressBar progressBar;
        final ProgressBar buffering;
        final ImageView playPauseCenter;
        final ImageView avatar;
        final TextView title;
        final TextView timing;
        final TextView details;
        final Ui.Icon bookmarkIcon;
        final Ui.Icon autoSwipeIcon;
        final Ui.Icon resizeIcon;
        final Ui.Icon muteIcon;
        ClipServer.Clip clip;

        ClipHolder(@NonNull View itemView) {
            super(itemView);
            container = (FrameLayout) itemView;

            // Thumbnail / Poster background
            poster = new ImageView(TikTokActivity.this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            container.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            // Top gradient
            View topGrad = new View(TikTokActivity.this);
            topGrad.setBackground(Ui.gradient(0xa0000000, 0x00000000, 0, TikTokActivity.this));
            container.addView(topGrad, new FrameLayout.LayoutParams(-1, Ui.dp(TikTokActivity.this, 110), Gravity.TOP));

            // Bottom gradient
            View botGrad = new View(TikTokActivity.this);
            botGrad.setBackground(Ui.gradient(0x00000000, 0xdf000000, 0, TikTokActivity.this));
            container.addView(botGrad, new FrameLayout.LayoutParams(-1, Ui.dp(TikTokActivity.this, 300), Gravity.BOTTOM));

            // Buffering spinner
            buffering = new ProgressBar(TikTokActivity.this);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.dp(TikTokActivity.this, 48), Ui.dp(TikTokActivity.this, 48), Gravity.CENTER);
            container.addView(buffering, bp);

            // Play / Pause center indicator
            playPauseCenter = new ImageView(TikTokActivity.this);
            playPauseCenter.setImageResource(android.R.drawable.ic_media_play);
            playPauseCenter.setColorFilter(Color.WHITE);
            playPauseCenter.setVisibility(View.GONE);
            container.addView(playPauseCenter, new FrameLayout.LayoutParams(Ui.dp(TikTokActivity.this, 64), Ui.dp(TikTokActivity.this, 64), Gravity.CENTER));

            // Bottom Progress Bar
            progressBar = new ProgressBar(TikTokActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, Ui.dp(TikTokActivity.this, 4), Gravity.BOTTOM);
            container.addView(progressBar, pp);

            // Right Action Column
            LinearLayout actions = Ui.column(TikTokActivity.this);
            actions.setGravity(Gravity.CENTER_HORIZONTAL);
            actions.setPadding(0, 0, Ui.dp(TikTokActivity.this, 14), Ui.dp(TikTokActivity.this, 30));

            // 1. Avatar / Poster
            FrameLayout avFrame = new FrameLayout(TikTokActivity.this);
            avFrame.setBackground(Ui.stroke(Ui.PURPLE, 24, TikTokActivity.this));
            avFrame.setClipToOutline(true);
            avatar = new ImageView(TikTokActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avFrame.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
            avFrame.setOnClickListener(v -> {
                if (clip != null) Ui.openDetails(TikTokActivity.this, clip.anime);
            });
            actions.addView(avFrame, Ui.lp(TikTokActivity.this, 46, 46));

            Ui.space(actions, 22);

            // 2. Favorite / Collection Button (WITHOUT text, WITHOUT "В план")
            bookmarkIcon = new Ui.Icon(TikTokActivity.this, "bookmark");
            bookmarkIcon.color = Color.WHITE;
            bookmarkIcon.setContentDescription("В коллекцию");
            bookmarkIcon.setOnClickListener(v -> {
                if (clip != null) {
                    Ui.bucketDialog(TikTokActivity.this, clip.anime, this::updateFavoriteState);
                }
            });
            Ui.press(bookmarkIcon);
            actions.addView(bookmarkIcon, Ui.lp(TikTokActivity.this, 34, 34));

            Ui.space(actions, 20);

            // 3. Auto-swipe Button (Directly under favorite icon, WITHOUT text, WITHOUT emoji)
            autoSwipeIcon = new Ui.Icon(TikTokActivity.this, "autoswipe");
            autoSwipeIcon.color = autoScroll ? Ui.PURPLE : Color.WHITE;
            autoSwipeIcon.setContentDescription("Автосвайп");
            autoSwipeIcon.setOnClickListener(v -> {
                autoScroll = !autoScroll;
                if (player != null) {
                    player.setRepeatMode(autoScroll ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
                }
                updateAutoSwipeState();
                Ui.toast(TikTokActivity.this, autoScroll ? "Автосвайп включён" : "Автосвайп выключен");
            });
            Ui.press(autoSwipeIcon);
            actions.addView(autoSwipeIcon, Ui.lp(TikTokActivity.this, 32, 32));

            Ui.space(actions, 20);

            // 4. Expansion / Resize Button (Under auto-swipe icon, WITHOUT text, WITHOUT emoji)
            resizeIcon = new Ui.Icon(TikTokActivity.this, isZoomMode ? "shrink" : "expand");
            resizeIcon.color = Color.WHITE;
            resizeIcon.setContentDescription("Размер экрана");
            resizeIcon.setOnClickListener(v -> {
                isZoomMode = !isZoomMode;
                if (playerView != null) {
                    playerView.setResizeMode(isZoomMode ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
                }
                resizeIcon.setKind(isZoomMode ? "shrink" : "expand");
                Ui.toast(TikTokActivity.this, isZoomMode ? "Экран расширен" : "Исходный размер");
            });
            Ui.press(resizeIcon);
            actions.addView(resizeIcon, Ui.lp(TikTokActivity.this, 30, 30));

            Ui.space(actions, 20);

            // 5. Sound / Mute Toggle Button (WITHOUT text, WITHOUT emoji)
            muteIcon = new Ui.Icon(TikTokActivity.this, isMuted ? "mute" : "volume");
            muteIcon.color = Color.WHITE;
            muteIcon.setContentDescription("Звук");
            muteIcon.setOnClickListener(v -> {
                isMuted = !isMuted;
                if (player != null) player.setVolume(isMuted ? 0f : 1f);
                muteIcon.setKind(isMuted ? "mute" : "volume");
            });
            Ui.press(muteIcon);
            actions.addView(muteIcon, Ui.lp(TikTokActivity.this, 28, 28));

            Ui.space(actions, 20);

            // 6. Full Episode icon button
            Ui.Icon fullEpIcon = new Ui.Icon(TikTokActivity.this, "play");
            fullEpIcon.color = Ui.PURPLE;
            fullEpIcon.setContentDescription("Серия");
            fullEpIcon.setOnClickListener(v -> {
                if (clip != null) {
                    Ui.openPlayer(TikTokActivity.this, clip.anime, "yoru", clip.episode != null ? clip.episode.number : 1, clip.startMs);
                }
            });
            Ui.press(fullEpIcon);
            actions.addView(fullEpIcon, Ui.lp(TikTokActivity.this, 28, 28));

            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
            container.addView(actions, ap);

            // Bottom Info Column
            LinearLayout info = Ui.column(TikTokActivity.this);
            info.setPadding(Ui.dp(TikTokActivity.this, 16), 0, Ui.dp(TikTokActivity.this, 84), Ui.dp(TikTokActivity.this, 24));

            title = Ui.text(TikTokActivity.this, "", 18, Color.WHITE, true);
            title.setMaxLines(2);
            title.setShadowLayer(6, 0, 2, 0xcc000000);
            title.setOnClickListener(v -> {
                if (clip != null) Ui.openDetails(TikTokActivity.this, clip.anime);
            });
            Ui.press(title);
            info.addView(title);

            Ui.space(info, 5);

            timing = Ui.text(TikTokActivity.this, "", 12, Ui.AMBER, true);
            timing.setShadowLayer(4, 0, 1, 0xcc000000);
            info.addView(timing);

            Ui.space(info, 5);

            details = Ui.text(TikTokActivity.this, "", 12, Ui.ZINC, false);
            details.setShadowLayer(4, 0, 1, 0xcc000000);
            info.addView(details);

            Ui.space(info, 12);

            TextView fullBtn = Ui.button(TikTokActivity.this, "▶ Смотреть серию полностью", true, () -> {
                if (clip != null) {
                    Ui.openPlayer(TikTokActivity.this, clip.anime, "yoru", clip.episode != null ? clip.episode.number : 1, clip.startMs);
                }
            });
            info.addView(fullBtn, Ui.lp(TikTokActivity.this, -2, 42));

            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.START | Gravity.BOTTOM);
            container.addView(info, ip);

            // Single tap gesture to pause / resume
            GestureDetector gestureDetector = new GestureDetector(TikTokActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (player != null) {
                        if (player.isPlaying()) player.pause();
                        else player.play();
                    }
                    return true;
                }
            });

            container.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        }

        void bind(ClipServer.Clip c) {
            clip = c;
            title.setText(c.title);
            timing.setText("Серия " + (c.episode != null ? Ui.number(c.episode.number) : "1") + " • " + c.timingText);
            String genres = c.anime != null && c.anime.genres != null && !c.anime.genres.isEmpty()
                    ? String.join(", ", c.anime.genres.subList(0, Math.min(3, c.anime.genres.size())))
                    : "Аниме";
            String score = c.anime != null && c.anime.score > 0 ? String.format(java.util.Locale.US, "★ %.1f • ", c.anime.score) : "";
            details.setText(score + genres);
            updateState();
            if (c.anime != null) {
                YoruApp.app().images.load(avatar, c.anime);
                YoruApp.app().images.load(poster, c.anime);
            }
        }

        void updateState() {
            updateFavoriteState();
            updateAutoSwipeState();
            if (resizeIcon != null) {
                resizeIcon.setKind(isZoomMode ? "shrink" : "expand");
            }
            if (muteIcon != null) {
                muteIcon.setKind(isMuted ? "mute" : "volume");
            }
        }

        void updateFavoriteState() {
            if (clip == null || bookmarkIcon == null) return;
            boolean isFav = YoruApp.app().store.favorite(clip.anime);
            bookmarkIcon.color = isFav ? Ui.PURPLE : Color.WHITE;
            bookmarkIcon.filled = isFav;
            bookmarkIcon.invalidate();
        }

        void updateAutoSwipeState() {
            if (autoSwipeIcon == null) return;
            autoSwipeIcon.color = autoScroll ? Ui.PURPLE : Color.WHITE;
            autoSwipeIcon.setAlpha(autoScroll ? 1.0f : 0.8f);
            autoSwipeIcon.invalidate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !player.isPlaying()) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        progressHandler.removeCallbacks(progressTick);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
