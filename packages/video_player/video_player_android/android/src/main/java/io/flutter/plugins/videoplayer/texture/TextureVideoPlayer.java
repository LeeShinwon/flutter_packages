// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.texture;

import android.content.Context;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import io.flutter.plugins.videoplayer.ExoPlayerEventListener;
import io.flutter.plugins.videoplayer.VideoAsset;
import io.flutter.plugins.videoplayer.VideoPlayer;
import io.flutter.plugins.videoplayer.VideoPlayerCallbacks;
import io.flutter.plugins.videoplayer.VideoPlayerOptions;
import io.flutter.view.TextureRegistry.SurfaceProducer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.common.TrackSelectionParameters;


/**
 * A subclass of {@link VideoPlayer} that adds functionality related to texture view as a way of
 * displaying the video in the app.
 *
 * <p>It manages the lifecycle of the texture and ensures that the video is properly displayed on
 * the texture.
 */
public final class TextureVideoPlayer extends VideoPlayer implements SurfaceProducer.Callback {
  // True when the ExoPlayer instance has a null surface.
  private boolean needsSurface = true;
  /**
   * Creates a texture video player.
   *
   * @param context application context.
   * @param events event callbacks.
   * @param surfaceProducer produces a texture to render to.
   * @param asset asset to play.
   * @param options options for playback.
   * @return a video player instance.
   */
  @NonNull
  public static TextureVideoPlayer create(
      @NonNull Context context,
      @NonNull VideoPlayerCallbacks events,
      @NonNull SurfaceProducer surfaceProducer,
      @NonNull VideoAsset asset,
      @NonNull VideoPlayerOptions options) {

        return new TextureVideoPlayer(
    events,
    surfaceProducer,
    asset.getMediaItem(),
    options,
    () -> {
      // ① 버퍼/대역폭 힌트
      LoadControl loadControl = new DefaultLoadControl.Builder()
          .setBufferDurationsMs(
              /*minBufferMs*/ 25_000,
              /*maxBufferMs*/ 80_000,
              /*bufferForPlaybackMs*/ 1_500,
              /*bufferForPlaybackAfterRebufferMs*/ 3_000
          ).build();

      DefaultBandwidthMeter bandwidthMeter =
          new DefaultBandwidthMeter.Builder(context)
              .setInitialBitrateEstimate(8_000_000L) // 8Mbps 시작 가정(6~12Mbps 범위 테스트)
              .build();

      // ② ExoPlayer 생성 시 주입
      ExoPlayer player = new ExoPlayer.Builder(context)
          .setMediaSourceFactory(asset.getMediaSourceFactory(context))
          .setLoadControl(loadControl)
          .setBandwidthMeter(bandwidthMeter)
          .build();

      
      // ⬇️⬇️ 여기부터 ‘바로 아래’에 리스너 추가 ⬇️⬇️
      player.addListener(new Player.Listener() {
        @Override public void onTracksChanged(Tracks tracks) {
          for (Tracks.Group g : tracks.getGroups()) if (g.getType() == C.TRACK_TYPE_VIDEO)
            for (int i = 0; i < g.length; i++) if (g.isTrackSelected(i)) {
              Format f = g.getTrackFormat(i);
              Log.d("VP", "SELECTED = " + f.width + "x" + f.height +
                          " @" + (f.bitrate > 0 ? (f.bitrate/1_000_000f + "Mbps") : "N/A"));
            }
        }
        @Override public void onRenderedFirstFrame() {
          Log.d("VP", "TTFF first frame rendered");
        }
      });

      player.addAnalyticsListener(new AnalyticsListener() {
        @Override public void onBandwidthEstimate(EventTime t, int loadMs, long bytes, long bps) {
          Log.d("VP", "BW=" + (bps/1_000_000f) + "Mbps");
        }
        @Override public void onDownstreamFormatChanged(EventTime t, MediaLoadData d) {
          if (d.trackType == C.TRACK_TYPE_VIDEO && d.trackFormat != null) {
            Format f = d.trackFormat;
            Log.d("VP", "DOWNSTREAM = " + f.width + "x" + f.height +
                        " @" + (f.bitrate > 0 ? (f.bitrate/1_000_000f + "Mbps") : "N/A"));
          }
        }
      });
      // ⬆️⬆️ 여기까지 추가 후 ⬆️⬆️

      return player;
    });

    // return new TextureVideoPlayer(
    //     events,
    //     surfaceProducer,
    //     asset.getMediaItem(),
    //     options,
    //     () -> {
    //       ExoPlayer.Builder builder =
    //           new ExoPlayer.Builder(context)
    //               .setMediaSourceFactory(asset.getMediaSourceFactory(context));
    //       return builder.build();
    //     });
  }

  @VisibleForTesting
  public TextureVideoPlayer(
      @NonNull VideoPlayerCallbacks events,
      @NonNull SurfaceProducer surfaceProducer,
      @NonNull MediaItem mediaItem,
      @NonNull VideoPlayerOptions options,
      @NonNull ExoPlayerProvider exoPlayerProvider) {
    super(events, mediaItem, options, surfaceProducer, exoPlayerProvider);

    surfaceProducer.setCallback(this);

    Surface surface = surfaceProducer.getSurface();
    this.exoPlayer.setVideoSurface(surface);
    needsSurface = surface == null;
  }

  @NonNull
  @Override
  protected ExoPlayerEventListener createExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @Nullable SurfaceProducer surfaceProducer) {
    if (surfaceProducer == null) {
      throw new IllegalArgumentException(
          "surfaceProducer cannot be null to create an ExoPlayerEventListener for TextureVideoPlayer.");
    }
    boolean surfaceProducerHandlesCropAndRotation = surfaceProducer.handlesCropAndRotation();
    return new TextureExoPlayerEventListener(
        exoPlayer, videoPlayerEvents, surfaceProducerHandlesCropAndRotation);
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public void onSurfaceAvailable() {
    if (needsSurface) {
      // TextureVideoPlayer must always set a surfaceProducer.
      assert surfaceProducer != null;
      exoPlayer.setVideoSurface(surfaceProducer.getSurface());
      needsSurface = false;
    }
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public void onSurfaceCleanup() {
    exoPlayer.setVideoSurface(null);
    needsSurface = true;
  }

  public void dispose() {
    // Super must be called first to ensure the player is released before the surface.
    super.dispose();

    // TextureVideoPlayer must always set a surfaceProducer.
    assert surfaceProducer != null;
    surfaceProducer.release();
  }
}
