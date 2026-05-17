package com.novafame.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

public class MainActivity extends Activity {
    private WebView webView;
    private View loadingView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xFF141414);

        loadingView = new View(this);
        loadingView.setBackgroundColor(0xFF141414);
        layout.addView(loadingView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF141414);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebView.setWebContentsDebuggingEnabled(false);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(
                android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void playVideo(String url, String title, String subtitlesJson) {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", title != null ? title : "");
                intent.putExtra("subtitles", subtitlesJson != null ? subtitlesJson : "");
                startActivity(intent);
            }

            @JavascriptInterface
            public void playEpisodeList(String episodesJson) {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("episodes", episodesJson);
                startActivity(intent);
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loadingView.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("intent://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        if (intent != null) {
                            // Try to convert intent:// to native PlayerActivity
                            String type = intent.getType();
                            if (type != null && type.startsWith("video/")) {
                                String data = intent.getDataString();
                                if (data != null) {
                                    Intent pi = new Intent(MainActivity.this, PlayerActivity.class);
                                    pi.putExtra("url", "http://" + data);
                                    startActivity(pi);
                                    return true;
                                }
                            }
                            startActivity(intent);
                            return true;
                        }
                    } catch (Exception e) {
                        // fallback
                    }
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return loadingView;
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        layout.addView(webView, params);
        setContentView(layout);

        webView.loadUrl("http://192.168.100.23:3001?v=3");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            webView.dispatchKeyEvent(event);
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
