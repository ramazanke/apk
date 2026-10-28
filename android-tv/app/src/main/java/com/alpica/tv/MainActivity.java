package com.alpica.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

@UnstableApi
public final class MainActivity extends Activity {
    private static final String SITE = "https://ramazanke.github.io/Tvalp/";
    private static final String ORIGIN = "https://ramazanke.github.io";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private ExoPlayer player;
    private TextView loading;
    private String bridgeScript;
    private boolean bridgeReady, started, resumePlayback, dialogVisible;
    private long activeToken = -1;
    private int retries;
    private final Runnable watchdog = () -> {
        if (!bridgeReady && !isFinishing()) showProblem("Siteye bağlanılamadı veya oynatıcı arayüzü hazır değil. İnterneti ve Android System WebView güncellemesini kontrol edin.");
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        fullscreen();
        loading = findViewById(R.id.loading);
        web = findViewById(R.id.web_view);
        PlayerView playerView = findViewById(R.id.player_view);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("AlpicaTV/1.0 AndroidTV")
                .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) notifySite("buffering", "Yayın yükleniyor…");
                if (state == Player.STATE_READY) notifySite("ready", "CANLI");
                if (state == Player.STATE_ENDED) notifySite("error", "Yayın sona erdi. Sağ/sol ile başka kanal seçin.");
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                if (playing) { retries = 0; notifySite("playing", "CANLI"); }
            }
            @Override public void onPlayerError(PlaybackException error) {
                // Retry only transport errors. Decoder/format errors need another source or device codec.
                if (error.errorCode >= 2000 && error.errorCode < 3000 && retries++ < 2) {
                    final long token = activeToken;
                    notifySite("buffering", "Bağlantı yeniden deneniyor…");
                    handler.postDelayed(() -> {
                        if (started && token == activeToken && !isFinishing()) { player.prepare(); player.play(); }
                    }, 1500L * retries);
                    return;
                }
                notifySite("error", "Yayın açılamadı (" + error.getErrorCodeName() + "). Sağ/sol ile başka kanal deneyin.");
            }
        });
        configureWeb();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWeb() {
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setMediaPlaybackRequiresUserGesture(true); // Native player owns autoplay.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            showProblem("Android System WebView çok eski. Play Store üzerinden güncelleyip uygulamayı tekrar açın.");
            return;
        }
        try (InputStream input = getAssets().open("native-player.js"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            bridgeScript = new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception error) { showProblem("Uygulama arayüz dosyası okunamadı."); return; }
        // Only this HTTPS origin's main frame may send native playback messages.
        WebViewCompat.addWebMessageListener(web, "AlpicaNative", Collections.singleton(ORIGIN),
                (view, message, sourceOrigin, mainFrame, reply) -> {
                    if (!mainFrame || !ORIGIN.equals(sourceOrigin.toString()) || !isPlayerPage(web.getUrl())) return;
                    String payload = message.getData();
                    if (payload == null || payload.length() > 32768) return;
                    try { handleMessage(new JSONObject(payload)); } catch (Exception ignored) { }
                });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, bridgeScript, Collections.singleton(ORIGIN));
        }
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isPlayerPage(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !isPlayerPage(url); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                bridgeReady = false;
                activeToken = -1;
                player.stop();
                loading.setVisibility(View.VISIBLE);
                handler.removeCallbacks(watchdog);
                handler.postDelayed(watchdog, 25000);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (isPlayerPage(url)) web.evaluateJavascript(bridgeScript, null);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showProblem("Site açılamadı. İnternet bağlantısını kontrol edin.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showProblem("Site yanıtı: HTTP " + response.getStatusCode());
            }
        });
        web.requestFocus();
        web.loadUrl(SITE);
    }

    private boolean isPlayerPage(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return "https".equals(uri.getScheme()) && "ramazanke.github.io".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null
                && ("/Tvalp/".equals(uri.getPath()) || "/Tvalp/index.html".equals(uri.getPath()));
    }

    private void handleMessage(JSONObject message) {
        switch (message.optString("action")) {
            case "ready":
                bridgeReady = true;
                handler.removeCallbacks(watchdog);
                loading.setVisibility(View.GONE);
                web.requestFocus();
                break;
            case "play":
                if (!bridgeReady) return;
                String url = message.optString("url").trim();
                Uri uri = Uri.parse(url);
                if (url.length() > 16384 || uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return;
                activeToken = message.optLong("token", -1);
                retries = 0;
                MediaItem.Builder item = new MediaItem.Builder().setUri(uri);
                String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
                if (path.endsWith(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
                else if (path.endsWith(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
                player.setMediaItem(item.build());
                player.prepare();
                player.setPlayWhenReady(started);
                resumePlayback = true;
                break;
            case "stop": activeToken = -1; resumePlayback = false; player.stop(); break;
            case "resume": if (started && message.optLong("token", -1) == activeToken) player.play(); break;
        }
    }

    private void notifySite(String state, String message) {
        if (!bridgeReady || web == null) return;
        String script = "window.AlpicaAndroid && window.AlpicaAndroid.state(" + activeToken + "," + JSONObject.quote(state) + "," + JSONObject.quote(message) + ");";
        web.evaluateJavascript(script, null);
    }

    private void fullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) fullscreen(); }
    @Override protected void onStart() { super.onStart(); started = true; if (web != null) web.onResume(); if (player != null && resumePlayback) player.play(); }
    @Override protected void onStop() { started = false; if (player != null) { resumePlayback = player.getPlayWhenReady(); player.pause(); } if (web != null) web.onPause(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (player != null) player.release(); if (web != null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }

    private String remoteKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_CHANNEL_DOWN: return "ArrowLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_CHANNEL_UP: return "ArrowRight";
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_MENU: return "ArrowUp";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "ArrowDown";
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: return "Enter";
            default: return null;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (bridgeReady && !dialogVisible) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) onBackPressed();
                return true;
            }
            String key = remoteKey(code);
            if (key != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && !("Enter".equals(key) && event.getRepeatCount() > 0)) {
                    web.evaluateJavascript("window.AlpicaAndroid && window.AlpicaAndroid.key(" + JSONObject.quote(key) + ");", null);
                }
                return true;
            }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                    web.evaluateJavascript("window.AlpicaAndroid && window.AlpicaAndroid.digit(" + (code - KeyEvent.KEYCODE_0) + ");", null);
                return true;
            }
            if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    boolean pause = code == KeyEvent.KEYCODE_MEDIA_PAUSE || (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player.getPlayWhenReady());
                    if (pause) { player.pause(); notifySite("paused", "Duraklatıldı · Oynat tuşuyla devam edin"); }
                    else player.play();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event); // Volume, Home and system keys remain with Android.
    }
    @Override public void onBackPressed() {
        if (!bridgeReady) { confirmExit(); return; }
        web.evaluateJavascript("!!(window.AlpicaAndroid && window.AlpicaAndroid.back())", result -> {
            if (!"true".equals(result)) confirmExit();
        });
    }
    private void confirmExit() {
        if (dialogVisible || isFinishing()) return;
        dialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Alpica TV’den çıkılsın mı?")
                .setNegativeButton("İzlemeye devam et", (d, which) -> web.requestFocus())
                .setPositiveButton("Çıkış", (d, which) -> finish()).create();
        dialog.setOnDismissListener(d -> { dialogVisible = false; web.requestFocus(); });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void showProblem(String message) {
        handler.removeCallbacks(watchdog);
        if (dialogVisible || isFinishing()) return;
        dialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Alpica TV").setMessage(message)
                .setPositiveButton("Yeniden dene", (d, which) -> {
                    loading.setVisibility(View.VISIBLE);
                    web.loadUrl(SITE);
                }).setNegativeButton("Çıkış", (d, which) -> finish()).create();
        dialog.setOnDismissListener(d -> dialogVisible = false);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
    }
}
