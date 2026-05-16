package com.streamflow.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class MainActivity extends Activity {
    private WebView webView;
    private View loadingView;
    private long lastDownloadId = -1;
    private BroadcastReceiver downloadReceiver;

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
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
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

            @JavascriptInterface
            public void downloadVideo(String url, String title) {
                try {
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    String filename = title != null && !title.isEmpty() ? title : "download_" + System.currentTimeMillis();
                    if (!filename.endsWith(".apk") && !filename.contains(".")) filename += ".mp4";
                    req.setTitle(filename);
                    req.setDescription("Downloading video");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    req.setAllowedOverMetered(true);
                    req.setAllowedOverRoaming(true);
                    lastDownloadId = dm.enqueue(req);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }, "AndroidBridge");

        // Handle APK downloads for in-app update
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (url.endsWith(".apk") || (mimetype != null && mimetype.contains("vnd.android.package-archive"))) {
                    try {
                        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                        String filename = "StreamFlow-Update.apk";
                        req.setTitle("StreamFlow APK");
                        req.setDescription("Downloading APK update");
                        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                        req.setAllowedOverMetered(true);
                        req.setAllowedOverRoaming(true);
                        lastDownloadId = dm.enqueue(req);
                        Toast.makeText(MainActivity.this, "APK downloading...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "APK download failed", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // Fallback: open in browser for non-APK files
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Cannot open link", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Broadcast receiver to trigger APK install when download completes
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == lastDownloadId) {
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(id);
                    android.database.Cursor c = dm.query(q);
                    if (c != null && c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        String localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                        c.close();
                        if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                            String path = localUri.replace("file://", "");
                            File apkFile = new File(path);
                            if (apkFile.exists() && apkFile.getName().endsWith(".apk")) {
                                Intent install = new Intent(Intent.ACTION_VIEW);
                                install.setDataAndType(Uri.fromFile(apkFile),
                                        "application/vnd.android.package-archive");
                                install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(install);
                            }
                        }
                    }
                }
            }
        };
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

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
                    }
                }
                if (url.endsWith(".apk")) {
                    webView.getDownloadListener().onDownloadStart(url, null, null, null, 0);
                    return true;
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

        webView.loadUrl("http://192.168.100.23:3001?v=5");
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
        if (downloadReceiver != null) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception e) {}
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
