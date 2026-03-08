// AuthScreen.kt
package me.voltual.a321.ui.auth

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    snackbarHostState: SnackbarHostState, // 从导航传递过来
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel()
) {
    val isSuccess by viewModel.isLoginSuccess.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            onLoginSuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val found = viewModel.checkAndExtractToken(manual = true)
                    if (!found) {
                        scope.launch {
                            snackbarHostState.showSnackbar("未检测到登录状态，请先在页面内完成登录")
                        }
                    }
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("我已完成登录") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 依然保留自动尝试，但不再强求
                                viewModel.checkAndExtractToken(manual = false)
                            }
                        }

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(viewModel.getInitialUrl())
                    }
                },
                update = { /* 状态由 WebView 自身管理 */ }
            )
        }
    }
}