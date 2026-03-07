package me.voltual.pyrolysis.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer 
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import me.voltual.pyrolysis.core.ui.components.MarkDownText
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color // 必须导入
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.BuildConfig
import me.voltual.pyrolysis.R
import java.security.MessageDigest
import java.util.Calendar

/**
 * 获取当前应用签名的 SHA-256 指纹
 */
fun getAppSignatureSha256(context: Context): String {
    return try {
        val packageManager = context.packageManager
        val packageName = context.packageName

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                null
            }
        } else {
            // 针对旧版本，通过注解或局部抑制来处理
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        if (!signatures.isNullOrEmpty()) {
            val cert = signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } else {
            "无法获取签名"
        }
    } catch (e: Exception) {
        "提取失败: ${e.message}"
    }
}

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState 
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val scope = rememberCoroutineScope() 
    
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val copyrightText = if (currentYear > 2025) "2025 - $currentYear" else "2025"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.fire),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(20.dp)),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "版本 $versionName (Build $versionCode)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        LicenseCard(
            onLicenseClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/Voltula/bbq/blob/master/LICENSE"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "无法打开浏览器",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OpenSourceCard(
            onGiteeClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/Voltula/bbq"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(message = "无法打开浏览器")
                    }
                }
            },
            onReleasesClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/Voltula/bbq/releases/"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(message = "无法打开浏览器")
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SignatureVerificationCard(context)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        DeveloperCard()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        AcknowledgmentsCard()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "© $copyrightText Voltula. 保留所有权利。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LicenseCard(onLicenseClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "许可证",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本程序采用 GNU 通用公共许可证第3版 (GPLv3) 发布。您可以自由地使用、修改和分发本程序，但必须遵守其条款。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClickableTextItem(
                text = "查看项目使用的GPLv3许可证副本",
                onClick = onLicenseClick
            )
        }
    }
}

@Composable
fun OpenSourceCard(onGiteeClick: () -> Unit, onReleasesClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "开源与下载",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClickableTextItem(text = "项目源码 (Gitee)", onClick = onGiteeClick)
            Spacer(modifier = Modifier.height(8.dp))
            ClickableTextItem(text = "下载可安装的APK包 (Releases)", onClick = onReleasesClick)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "提示：如果您不了解如何编译代码，请点击上方\"下载可安装的APK包\"链接，下载后缀为 .apk 的文件进行安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本页面基于 Bilimiao 项目的代码修改而来，遵循 GPLv3 协议。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DeveloperCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "开发者信息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "开发者(owner): Voltula (Voltual)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "项目目前暂无其他贡献者",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickableTextItem(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = "打开链接",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SignatureVerificationCard(context: Context) {
    val OFFICIAL_SHA256 = "81:91:BD:71:15:7E:A7:04:1C:55:1D:1A:BA:C6:B7:DF:24:78:44:96:A7:3E:F3:46:0A:19:5F:1F:4B:B4:07:8C" 
    
    // 使用 remember 缓存签名获取结果，避免重组时重复计算性能损耗
    val currentSignature = remember(context) { getAppSignatureSha256(context) }
    val isOfficial = currentSignature.equals(OFFICIAL_SHA256, ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOfficial) {
                MaterialTheme.colorScheme.secondaryContainer 
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isOfficial) androidx.compose.material.icons.Icons.Default.CheckCircle 
                                 else androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOfficial) "官方签名认证" else "非官方签名提醒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOfficial) MaterialTheme.colorScheme.onSecondaryContainer 
                            else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "当前签名 SHA-256:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 使用 SelectionContainer 让用户可以长按复制签名
            SelectionContainer {
                Text(
                    text = currentSignature,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }

            if (!isOfficial) {
                Text(
                    text = "警告:签名与官方不一致意味着应用被非官方修改了（虽然本项目是开源项目）如果你是自己编译签名的话请忽略",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun AcknowledgmentsCard() {
    val acknowledgmentsMd = """
        本项目的上游与参考项目（或者说是它们的衍生作品）:
        
        * **哔哩终端 (GPLv3)**: [Gitee](https://gitee.com/RobinNotBad/BiliClient)
        * **RikkaHub (AGPLv3)**: [GitHub](https://github.com/rikkahub/rikkahub/)
        * **bilimiao (GPLv3)**: [GitHub](https://github.com/10miaomiao/bilimiao2)
        * **Droid-ify (GPLv3)**: [GitHub](https://github.com/Droid-ify/client)
        * **Neo Store (GPLv3)**: [GitHub](https://github.com/NeoApplications/Neo-Store)
    """.trimIndent()

    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "鸣谢名单",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            MarkDownText(
                content = acknowledgmentsMd,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClickCitation = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                    }
                }
            )
        }
    }
}