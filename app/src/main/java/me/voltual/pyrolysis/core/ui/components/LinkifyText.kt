// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
@file:Suppress("DEPRECATION")
package me.voltual.pyrolysis.core.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import me.voltual.pyrolysis.ui.*
import java.util.regex.Pattern
import androidx.compose.runtime.DisposableEffect

private val INTERNAL_POST_LINK_PATTERN: Pattern = Pattern.compile(
    "http://apk\\.xiaoqu\\.online/post/(\\d+)\\.html"
)

private val BILI_VIDEO_LINK_PATTERN: Pattern = Pattern.compile(
    "【视频：([a-zA-Z0-9]+)】"
)

private val GENERAL_URL_PATTERN: Pattern = Pattern.compile(
    "(?:(?:https?|ftp)://|www\\.)[\\w\\-_]+(?:\\.[\\w\\-_]+)+(?:[\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&;/~+#])?"
)

private data class LinkMatch(
    val start: Int,
    val end: Int,
    val text: String,
    val type: LinkType
)

private enum class LinkType {
    POST,
    BILIVIDEO,
    URL
}

/**
 * 自动识别文本中的帖子链接、B站视频标记和普通URL，并使其可点击。
 * - 使用 Navigation 3 的 LocalNavigator 进行内部导航。
 * - 不再需要传递 NavController 或 Navigator 参数。
 */
@Composable
fun LinkifyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current          // Navigation 3 导航器
    val linkColor = MaterialTheme.colorScheme.primary

    val textStyle = if (style.color == Color.Unspecified) {
        style.copy(color = MaterialTheme.colorScheme.onSurface)
    } else {
        style
    }
        
    val annotatedString = remember(text, linkColor) {
        val processedText = text.replace("<br>", "\n")
        buildAnnotatedString {
            append(processedText)

            val matches = mutableListOf<LinkMatch>()

            val postMatcher = INTERNAL_POST_LINK_PATTERN.matcher(processedText)
            while (postMatcher.find()) {
                postMatcher.group(1)?.let { postId ->
                    matches.add(
                        LinkMatch(
                            start = postMatcher.start(),
                            end = postMatcher.end(),
                            text = postId,
                            type = LinkType.POST
                        )
                    )
                }
            }

            val biliVideoMatcher = BILI_VIDEO_LINK_PATTERN.matcher(processedText)
            while (biliVideoMatcher.find()) {
                biliVideoMatcher.group(1)?.let { bvid ->
                    matches.add(
                        LinkMatch(
                            start = biliVideoMatcher.start(),
                            end = biliVideoMatcher.end(),
                            text = bvid,
                            type = LinkType.BILIVIDEO
                        )
                    )
                }
            }

            val urlMatcher = GENERAL_URL_PATTERN.matcher(processedText)
            while (urlMatcher.find()) {
                val isAlreadyMatched = matches.any { it.start == urlMatcher.start() && it.end == urlMatcher.end() }
                if (!isAlreadyMatched) {
                    matches.add(
                        LinkMatch(
                            start = urlMatcher.start(),
                            end = urlMatcher.end(),
                            text = urlMatcher.group(),
                            type = LinkType.URL
                        )
                    )
                }
            }

            matches.forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = match.start,
                    end = match.end
                )
                addStringAnnotation(
                    tag = match.type.name,
                    annotation = match.text,
                    start = match.start,
                    end = match.end
                )
            }
        }
    }

    SelectionContainer(modifier = modifier) {
        ClickableText(
            text = annotatedString,
            style = textStyle,
            onClick = { offset ->
                // 处理内部帖子链接
                annotatedString.getStringAnnotations(tag = LinkType.POST.name, start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        annotation.item.toLongOrNull()?.let { postId ->
                            navigator.navigate(PostDetail(postId))   // 类型安全导航
                        }
                        return@ClickableText
                    }

                // 处理B站视频链接
                annotatedString.getStringAnnotations(tag = LinkType.BILIVIDEO.name, start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        val bvid = annotation.item
                        navigator.navigate(Player(bvid))            // 类型安全导航
                        return@ClickableText
                    }

                // 处理普通URL
                annotatedString.getStringAnnotations(tag = LinkType.URL.name, start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        var urlToOpen = annotation.item
                        if (!urlToOpen.startsWith("http://") && !urlToOpen.startsWith("https://")) {
                            urlToOpen = "http://$urlToOpen"
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("LinkifyText", "无法打开URL: $urlToOpen", e)
                        }
                    }
            }
        )
    }
}