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

        // T6: Check if any app can handle the intent before calling startActivity
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(view.getContext().getPackageManager()) != null) {
            view.getContext().startActivity(intent);
        }

        // Block all other navigations inside the WebView regardless
        return true;
    }
}
