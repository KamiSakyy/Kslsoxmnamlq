package com.tsuyu.line;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
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
    private RecyclerView recycler;
    private ClipAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;
    private ExoPlayer player;
    private final ArrayList<ClipServer.Clip> clips = new ArrayList<>();
    private int currentPosition = -1;
    private boolean autoScroll = true;
    private boolean isMuted = false;
    private boolean isZoomMode = true;
    private boolean destroyed = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private ProgressBar currentProgressBar;
    private ProgressBar currentBuffering;
    private ImageView currentPlayPauseIndicator;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (destroyed || player == null || currentProgressBar == null) return;
            if (currentPosition >= 0 && currentPosition < clips.size()) {
                ClipServer.Clip clip = clips.get(currentPosition);
                long pos = player.getCurrentPosition();
                long dur = clip.durationSec * 1000L;
                if (dur > 0) {
                    int percent = (int) Math.min(100, Math.max(0, (pos * 100L) / dur));
                    currentProgressBar.setProgress(percent);
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

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (currentBuffering != null) {
                    currentBuffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
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
            public void onIsPlayingChanged(boolean isPlaying) {
                if (currentPlayPauseIndicator != null) {
                    if (!isPlaying) {
                        currentPlayPauseIndicator.setVisibility(View.VISIBLE);
                        currentPlayPauseIndicator.setAlpha(1f);
                    } else {
                        currentPlayPauseIndicator.animate()
                                .alpha(0f)
                                .setDuration(250)
                                .withEndAction(() -> currentPlayPauseIndicator.setVisibility(View.GONE))
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
        titleCol.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        TextView title = Ui.text(this, "Лента аниме", 16, Color.WHITE, true);
        titleCol.addView(title);

        TextView badge = Ui.text(this, "TikTok • 15–30 сек", 10, Ui.PURPLE, false);
        titleCol.addView(badge);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView loopBtn = Ui.chip(this, autoScroll ? "▶ Автосвайп" : "🔁 Зациклить", false, () -> {
            autoScroll = !autoScroll;
            initTopBar();
        });
        topBar.addView(loopBtn, Ui.lp(this, -2, -2));

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
                        checkActiveSnap();
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

        currentProgressBar = holder.progressBar;
        currentBuffering = holder.buffering;
        currentPlayPauseIndicator = holder.playPauseCenter;

        holder.playerView.setPlayer(player);
        holder.playerView.setResizeMode(isZoomMode ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        holder.buffering.setVisibility(View.VISIBLE);

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

    private void showHeartBurst(float x, float y) {
        Ui.Icon heart = new Ui.Icon(this, "heart");
        heart.color = 0xfff43f5e;
        heart.filled = true;

        int size = Ui.dp(this, 88);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = (int) (x - size / 2f);
        lp.topMargin = (int) (y - size / 2f);
        root.addView(heart, lp);

        heart.setScaleX(0.2f);
        heart.setScaleY(0.2f);
        heart.setAlpha(0.9f);
        heart.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .alpha(0f)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> root.removeView(heart))
                .start();
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

    private final class ClipHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        final PlayerView playerView;
        final ProgressBar progressBar;
        final ProgressBar buffering;
        final ImageView playPauseCenter;
        final ImageView avatar;
        final TextView title;
        final TextView timing;
        final TextView details;
        final TextView likeCount;
        final Ui.Icon likeIcon;
        final Ui.Icon muteIcon;
        final Ui.Icon resizeIcon;
        ClipServer.Clip clip;

        ClipHolder(@NonNull View itemView) {
            super(itemView);
            container = (FrameLayout) itemView;

            playerView = new PlayerView(TikTokActivity.this);
            playerView.setUseController(false);
            container.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

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
            actions.setPadding(0, 0, Ui.dp(TikTokActivity.this, 12), Ui.dp(TikTokActivity.this, 30));

            // Avatar / Poster
            FrameLayout avFrame = new FrameLayout(TikTokActivity.this);
            avFrame.setBackground(Ui.stroke(Ui.PURPLE, 24, TikTokActivity.this));
            avFrame.setClipToOutline(true);
            avatar = new ImageView(TikTokActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avFrame.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
            avFrame.setOnClickListener(v -> {
                if (clip != null) Ui.openDetails(TikTokActivity.this, clip.anime);
            });
            actions.addView(avFrame, Ui.lp(TikTokActivity.this, 48, 48));

            Ui.space(actions, 18);

            // Like Button
            likeIcon = new Ui.Icon(TikTokActivity.this, "heart");
            likeIcon.color = Color.WHITE;
            actions.addView(likeIcon, Ui.lp(TikTokActivity.this, 34, 34));
            likeCount = Ui.text(TikTokActivity.this, "0", 11, Color.WHITE, true);
            actions.addView(likeCount);
            View.OnClickListener onLike = v -> {
                if (clip == null) return;
                clip.liked = !clip.liked;
                clip.likes += clip.liked ? 1 : -1;
                updateLikeState();
                likeIcon.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction(() ->
                        likeIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            };
            likeIcon.setOnClickListener(onLike);

            Ui.space(actions, 18);

            // Collection / Bookmark Button
            Ui.Icon bookmark = new Ui.Icon(TikTokActivity.this, "bookmark");
            bookmark.color = Color.WHITE;
            actions.addView(bookmark, Ui.lp(TikTokActivity.this, 30, 30));
            TextView bookmarkTxt = Ui.text(TikTokActivity.this, "В план", 10, Color.WHITE, false);
            actions.addView(bookmarkTxt);
            bookmark.setOnClickListener(v -> {
                if (clip != null) Ui.bucketDialog(TikTokActivity.this, clip.anime, null);
            });

            Ui.space(actions, 18);

            // Share Button
            Ui.Icon share = new Ui.Icon(TikTokActivity.this, "share");
            share.color = Color.WHITE;
            actions.addView(share, Ui.lp(TikTokActivity.this, 30, 30));
            TextView shareTxt = Ui.text(TikTokActivity.this, "Ссылка", 10, Color.WHITE, false);
            actions.addView(shareTxt);
            share.setOnClickListener(v -> {
                if (clip == null) return;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Смотрите аниме \"" + clip.title + "\" (" + clip.timingText + ") в приложении Tsuyu!");
                TikTokActivity.this.startActivity(Intent.createChooser(shareIntent, "Поделиться клипом"));
            });

            Ui.space(actions, 18);

            // Mute Button
            muteIcon = new Ui.Icon(TikTokActivity.this, isMuted ? "mute" : "volume");
            muteIcon.color = Color.WHITE;
            actions.addView(muteIcon, Ui.lp(TikTokActivity.this, 28, 28));
            muteIcon.setOnClickListener(v -> {
                isMuted = !isMuted;
                if (player != null) player.setVolume(isMuted ? 0f : 1f);
                muteIcon.setKind(isMuted ? "mute" : "volume");
            });

            Ui.space(actions, 18);

            // Resize Button
            resizeIcon = new Ui.Icon(TikTokActivity.this, isZoomMode ? "smartfill" : "fit");
            resizeIcon.color = Color.WHITE;
            actions.addView(resizeIcon, Ui.lp(TikTokActivity.this, 28, 28));
            resizeIcon.setOnClickListener(v -> {
                isZoomMode = !isZoomMode;
                playerView.setResizeMode(isZoomMode ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
                resizeIcon.setKind(isZoomMode ? "smartfill" : "fit");
            });

            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
            container.addView(actions, ap);

            // Bottom Info Column
            LinearLayout info = Ui.column(TikTokActivity.this);
            info.setPadding(Ui.dp(TikTokActivity.this, 16), 0, Ui.dp(TikTokActivity.this, 90), Ui.dp(TikTokActivity.this, 24));

            title = Ui.text(TikTokActivity.this, "", 18, Color.WHITE, true);
            title.setMaxLines(2);
            title.setShadowLayer(6, 0, 2, 0xcc000000);
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

            // Double tap & single tap gestures
            GestureDetector gestureDetector = new GestureDetector(TikTokActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (player != null) {
                        if (player.isPlaying()) player.pause();
                        else player.play();
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (clip != null && !clip.liked) {
                        clip.liked = true;
                        clip.likes++;
                        updateLikeState();
                    }
                    showHeartBurst(e.getX(), e.getY());
                    return true;
                }
            });

            playerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
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
            updateLikeState();
            if (c.anime != null) YoruApp.app().images.load(avatar, c.anime);
        }

        void updateLikeState() {
            if (clip == null) return;
            likeCount.setText(clip.likes > 999 ? String.format(java.util.Locale.US, "%.1fk", clip.likes / 1000f) : String.valueOf(clip.likes));
            likeIcon.color = clip.liked ? 0xfff43f5e : Color.WHITE;
            likeIcon.filled = clip.liked;
            likeIcon.invalidate();
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
