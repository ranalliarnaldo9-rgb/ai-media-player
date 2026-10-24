package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.download.StreamInspector
import com.example.playback.PlaybackManager
import java.io.ByteArrayInputStream

class WebPlayerBridge(
    private val playbackManager: PlaybackManager,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    @JavascriptInterface
    fun onStateChanged(playing: Boolean, buffering: Boolean) {
        mainHandler.post {
            playbackManager.setWebPlaybackState(playing, buffering)
        }
    }

    @JavascriptInterface
    fun onProgress(currentSec: Float, durationSec: Float) {
        mainHandler.post {
            playbackManager.setWebProgress(currentSec, durationSec)
        }
    }

    @JavascriptInterface
    fun onError(errorMessage: String) {
        mainHandler.post {
            // Log or propagate error if required
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UniversalWebPlayerView(
    url: String,
    playbackManager: PlaybackManager,
    modifier: Modifier = Modifier
) {
    val ytId = remember(url) { StreamInspector.extractYouTubeVideoId(url) }
    val isYouTube = ytId != null

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = false
                    allowContentAccess = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()

                // Enhanced Ad-Blocking WebViewClient
                webViewClient = object : WebViewClient() {
                    private val adBlockKeywords = listOf(
                        "doubleclick.net",
                        "googleads",
                        "pagead",
                        "adservice.google",
                        "youtube.com/pagead",
                        "youtube.com/api/stats/ads",
                        "ad.doubleclick.net",
                        "adnxs.com",
                        "adsystem.com",
                        "scorecardresearch.com",
                        "popads",
                        "moatads",
                        "taboola.com",
                        "outbrain.com"
                    )

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()?.lowercase() ?: ""
                        if (adBlockKeywords.any { reqUrl.contains(it) }) {
                            // Block ad request completely
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                setBackgroundColor(android.graphics.Color.BLACK)

                // Attach two-way bridge to PlaybackManager
                val bridge = WebPlayerBridge(playbackManager)
                addJavascriptInterface(bridge, "AndroidBridge")

                playbackManager.webPlayerController = object : PlaybackManager.WebPlayerController {
                    override fun webPlay() {
                        post {
                            evaluateJavascript("if (window.jsPlay) { window.jsPlay(); }", null)
                        }
                    }

                    override fun webPause() {
                        post {
                            evaluateJavascript("if (window.jsPause) { window.jsPause(); }", null)
                        }
                    }

                    override fun webSeekTo(seconds: Float) {
                        post {
                            evaluateJavascript("if (window.jsSeekTo) { window.jsSeekTo($seconds); }", null)
                        }
                    }

                    override fun webSetPlaybackRate(rate: Float) {
                        post {
                            evaluateJavascript("if (window.jsSetSpeed) { window.jsSetSpeed($rate); }", null)
                        }
                    }
                }

                if (isYouTube) {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                          body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000; overflow:hidden; display:flex; align-items:center; justify-content:center; }
                          #player { width:100%; height:100%; border:none; }
                          /* Ad-Blocker CSS Styles */
                          .video-ads, .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-message-container,
                          .ytp-ad-preview-container, .ytp-ad-player-overlay, #player-ads, .ytp-suggested-action,
                          .annotation, .ytp-ce-element, .ytp-ad-text, .ytp-ad-action-interstitial,
                          .ytp-paid-content-overlay {
                            display: none !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                            pointer-events: none !important;
                          }
                        </style>
                        </head>
                        <body>
                        <div id="player"></div>
                        <script>
                          var tag = document.createElement('script');
                          tag.src = "https://www.youtube.com/iframe_api";
                          var firstScriptTag = document.getElementsByTagName('script')[0];
                          firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                          var player;
                          function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                              videoId: '$ytId',
                              playerVars: {
                                'autoplay': 1,
                                'controls': 0,
                                'disablekb': 1,
                                'fs': 0,
                                'modestbranding': 1,
                                'rel': 0,
                                'playsinline': 1,
                                'origin': 'https://www.youtube.com'
                              },
                              events: {
                                'onReady': onPlayerReady,
                                'onStateChange': onPlayerStateChange,
                                'onError': onPlayerError
                              }
                            });
                          }

                          function onPlayerReady(event) {
                            try { event.target.playVideo(); } catch(e){}
                            setInterval(pollProgress, 250);
                            setInterval(autoSkipAds, 200);
                          }

                          function onPlayerStateChange(event) {
                            var isPlaying = (event.data === 1);
                            var isBuffering = (event.data === 3);
                            if (window.AndroidBridge) {
                              window.AndroidBridge.onStateChanged(isPlaying, isBuffering);
                            }
                          }

                          function onPlayerError(event) {
                            if (window.AndroidBridge) {
                              window.AndroidBridge.onError("YouTube code " + event.data);
                            }
                          }

                          function pollProgress() {
                            if (player && typeof player.getCurrentTime === 'function' && typeof player.getDuration === 'function') {
                              var cur = player.getCurrentTime() || 0;
                              var dur = player.getDuration() || 0;
                              if (window.AndroidBridge) {
                                window.AndroidBridge.onProgress(cur, dur);
                              }
                            }
                          }

                          // Intelligent Real-time Ad Auto-Skipper
                          function autoSkipAds() {
                            try {
                              var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-preview-container');
                              if (skipBtn) {
                                skipBtn.click();
                              }
                              var overlayClose = document.querySelector('.ytp-ad-overlay-close-button');
                              if (overlayClose) {
                                overlayClose.click();
                              }
                              var vid = document.querySelector('video');
                              var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                              if (vid && adShowing && isFinite(vid.duration) && vid.duration > 0) {
                                vid.currentTime = vid.duration;
                              }
                            } catch(e){}
                          }

                          window.jsPlay = function() {
                            if (player && typeof player.playVideo === 'function') player.playVideo();
                          };
                          window.jsPause = function() {
                            if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
                          };
                          window.jsSeekTo = function(sec) {
                            if (player && typeof player.seekTo === 'function') player.seekTo(sec, true);
                          };
                          window.jsSetSpeed = function(rate) {
                            if (player && typeof player.setPlaybackRate === 'function') player.setPlaybackRate(rate);
                          };
                        </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                } else {
                    // Inject generic HTML5 video controller script with ad-blocker
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                          body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000; overflow:hidden; }
                          video { width:100%; height:100%; object-fit:contain; }
                          [class*="ad-"], [id*="ad-"], [class*="banner"], .video-ad { display:none !important; }
                        </style>
                        </head>
                        <body>
                        <video id="v" src="$url" autoplay playsinline></video>
                        <script>
                          var v = document.getElementById('v');
                          window.jsPlay = function() { if (v) v.play(); };
                          window.jsPause = function() { if (v) v.pause(); };
                          window.jsSeekTo = function(sec) { if (v) v.currentTime = sec; };
                          window.jsSetSpeed = function(rate) { if (v) v.playbackRate = rate; };

                          setInterval(function() {
                            if (v && window.AndroidBridge) {
                              window.AndroidBridge.onProgress(v.currentTime || 0, v.duration || 0);
                              window.AndroidBridge.onStateChanged(!v.paused, false);
                            }
                          }, 250);
                        </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
                }
            }
        },
        update = { /* Updates if required */ },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            playbackManager.webPlayerController = null
        }
    }
}
