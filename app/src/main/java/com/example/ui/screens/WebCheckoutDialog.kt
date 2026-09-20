package com.example.ui.screens

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig

@Composable
fun WebCheckoutDialog(
    showWebCheckout: Boolean,
    onDismiss: () -> Unit,
    sessionToken: String
) {
    if (!showWebCheckout) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webContext ->
                    WebView(webContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        val siteUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
                        cookies.setCookie(
                            siteUrl,
                            "session_user_id=${Uri.encode(sessionToken)}; Path=/; Secure"
                        )
                        cookies.flush()

                        webViewClient = object : WebViewClient() {
                            private var injected = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (view == null || sessionToken.isBlank()) return

                                val safe = sessionToken.replace("\\", "\\\\").replace("'", "\\'")
                                if (!injected) {
                                    injected = true
                                    view.evaluateJavascript(
                                        """
                                        (function(){
                                            try {
                                                localStorage.setItem('session_user_id','$safe');
                                                sessionStorage.setItem('session_user_id','$safe');
                                            } catch(e) {}
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }
                            }
                        }

                        loadUrl(siteUrl)
                    }
                }
            )
        }
    }
}
