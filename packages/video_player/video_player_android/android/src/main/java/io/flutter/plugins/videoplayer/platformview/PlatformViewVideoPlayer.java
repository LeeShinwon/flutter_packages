// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
 * A subclass of {@link VideoPlayer} that adds functionality related to platform view as a way of
 * displaying the video in the app.
 */
public class PlatformViewVideoPlayer extends VideoPlayer {
  @VisibleForTesting
  public PlatformViewVideoPlayer(
      @NonNull VideoPlayerCallbacks events,
      @NonNull MediaItem mediaItem,
      @NonNull VideoPlayerOptions options,
      @NonNull ExoPlayerProvider exoPlayerProvider) {
    super(events, mediaItem, options, /* surfaceProducer */ null, exoPlayerProvider);
  }

  /**
   * Creates a platform view video player.
   *
   * @param context application context.
   * @param events event callbacks.
   * @param asset asset to play.
   * @param options options for playback.
   * @return a video player instance.
   */
  @NonNull
  public static PlatformViewVideoPlayer create(
      @NonNull Context context,
      @NonNull VideoPlayerCallbacks events,
      @NonNull VideoAsset asset,
      @NonNull VideoPlayerOptions options) {

    return new PlatformViewVideoPlayer(
    events,
    asset.getMediaItem(),
    options,
    () -> {
      LoadControl loadControl = new DefaultLoadControl.Builder()
          .setBufferDurationsMs(25_000, 80_000, 1_500, 3_000)
          .build();

      DefaultBandwidthMeter bandwidthMeter =
          new DefaultBandwidthMeter.Builder(context)
              .setInitialBitrateEstimate(8_000_000L)
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

      ExoPlayer player = new ExoPlayer.Builder(context)
          .setMediaSourceFactory(asset.getMediaSourceFactory(context))
          .setLoadControl(loadControl)
          .setBandwidthMeter(bandwidthMeter)
          .build();

      return player;
    });

    // return new PlatformViewViㄴdeoPlayer(
    //     events,
    //     asset.getMediaItem(),
    //     options,
    //     () -> {
    //       ExoPlayer.Builder builder =
    //           new ExoPlayer.Builder(context)
    //               .setMediaSourceFactory(asset.getMediaSourceFactory(context));
    //       return builder.build();
    //     });
  }

  @NonNull
  @Override
  protected ExoPlayerEventListener createExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @Nullable SurfaceProducer surfaceProducer) {
    return new PlatformViewExoPlayerEventListener(exoPlayer, videoPlayerEvents);
  }
}
