// AuthScreen.kt
package me.voltual.a321.ui.auth

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
  viewModel: AuthViewModel = koinViewModel(),
) {
  val isSuccess by viewModel.isLoginSuccess.collectAsStateWithLifecycle()
  val qrState by viewModel.qrLoginState.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()

  // 0: 网页登录, 1: 扫码登录
  var selectedTab by remember { mutableIntStateOf(0) }

  LaunchedEffect(isSuccess) {
    if (isSuccess) {
      onLoginSuccess()
    }
  }

  // 当用户切换到“扫码登录”时，自动开启扫码，切回时关闭轮询
  LaunchedEffect(selectedTab) {
    if (selectedTab == 1) {
      viewModel.startQrLoginFlow()
    } else {
      viewModel.stopQrLoginFlow()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      Column {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("网页登录", fontWeight = FontWeight.Bold) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("扫码登录", fontWeight = FontWeight.Bold) }
          )
        }
      }
    },
    floatingActionButton = {
      if (selectedTab == 0) {
        ExtendedFloatingActionButton(
          onClick = {
            val found = viewModel.checkAndExtractToken(manual = true)
            if (!found) {
              scope.launch { snackbarHostState.showSnackbar("未检测到登录状态，请先在网页内完成登录") }
            }
          },
          icon = { Icon(Icons.Default.Check, contentDescription = null) },
          text = { Text("我已完成登录") },
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
    ) {
      if (selectedTab == 0) {
        // ===== 网页登录通道 =====
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { context ->
            WebView(context).apply {
              layoutParams =
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )

              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              }

              webViewClient =
                object : WebViewClient() {
                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    viewModel.checkAndExtractToken(manual = false)
                  }
                }

              CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
              loadUrl(viewModel.getInitialUrl())
            }
          },
          update = { /* 状态由 WebView 自身管理 */ },
        )
      } else {
        // ===== 扫码登录通道 =====
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          when (val state = qrState) {
            is QrLoginState.Idle, QrLoginState.Loading -> {
              CircularProgressIndicator(modifier = Modifier.size(64.dp))
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "正在安全地获取登录二维码...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            is QrLoginState.QrReady -> {
              Surface(
                modifier = Modifier
                  .size(260.dp)
                  .padding(8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.fillMaxSize()
                ) {
                  Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Login QR Code",
                    modifier = Modifier
                      .size(240.dp)
                      .padding(4.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(24.dp))
              Text(
                text = "请使用 123云盘官方 APP 或 微信 扫码授权",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "扫码完成后网页会自动同步登录状态，无需额外操作",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
              )
              Spacer(modifier = Modifier.height(32.dp))
              OutlinedButton(
                onClick = { viewModel.startQrLoginFlow() },
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = MaterialTheme.colorScheme.primary
                )
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新二维码")
              }
            }
            is QrLoginState.Success -> {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "扫码登录成功！正在进入网盘...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }
            is QrLoginState.Error -> {
              Text(
                text = "登录失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
              )
              Spacer(modifier = Modifier.height(24.dp))
              Button(
                onClick = { viewModel.startQrLoginFlow() },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary
                )
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试")
              }
            }
          }
        }
      }
    }
  }
}