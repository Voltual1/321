// File: /app/src/main/java/cc/bbq/xq/ui/auth/LoginScreen.kt
//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.AppStore
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.voltual.pyrolysis.core.ui.theme.AppStoreDropdownMenu
import me.voltual.pyrolysis.ui.*
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.core.ui.theme.BBQSnackbarHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRegistering by remember { mutableStateOf(false) }
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    val navigator = LocalNavigator.current

    if (loginSuccess) {
        LaunchedEffect(Unit) {
            onLoginSuccess()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isRegistering) {
            RegisterContent(
                viewModel = viewModel,
                onBackToLogin = { isRegistering = false }
            )
        } else {
            LoginContent(
                viewModel = viewModel,
                onNavigateToRegister = { isRegistering = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginContent(
    viewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(snackbarHost = { BBQSnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(scrollState)  
        .padding(24.dp),             
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
            // 商店选择下拉菜单
            AppStoreDropdownMenu(
                selectedStore = selectedStore,
                onStoreChange = { viewModel.onStoreSelected(it) },
                modifier = Modifier.fillMaxWidth(),
                // 排除本地应用，包含 小趣空间、弦应用商店、弦-开放平台
                appStores = remember { AppStore.entries.filter { it != AppStore.LOCAL } }
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.login() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("登录")
            }
            
            // 仅在小趣空间模式下显示注册按钮
            if (selectedStore == AppStore.XIAOQU_SPACE) {
                TextButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
                    Text("注册新账号")
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂不支持应用内注册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterContent(
    viewModel: LoginViewModel,
    onBackToLogin: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val email by viewModel.email.collectAsState()
    val captcha by viewModel.captcha.collectAsState()
    val verificationCodeUrl by viewModel.verificationCodeUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadVerificationCode()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(snackbarHost = { BBQSnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                verificationCodeUrl?.let { url ->
                    Image(
                        painter = rememberAsyncImagePainter(url),
                        contentDescription = "Verification Code",
                        modifier = Modifier.size(120.dp, 40.dp),
                        contentScale = ContentScale.FillBounds
                    )
                } ?: Box(modifier = Modifier.size(120.dp, 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                Button(onClick = { viewModel.loadVerificationCode() }) {
                    Text("刷新")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = captcha,
                onValueChange = { viewModel.onCaptchaChange(it) },
                label = { Text("验证码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.register() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("注册")
            }
            TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("返回登录")
            }
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}