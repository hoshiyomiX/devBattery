package com.deviant.batterymonitor;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class MyWebViewClient extends WebViewClient {

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Only allow file:// URLs (local assets)
        if (url.startsWith("file:")) {
            return false;
        }

        // Block all other navigations by opening in external browser
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        view.getContext().startActivity(intent);
        return true;
    }
}
