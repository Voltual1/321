//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun IDMTransferDialog(onDismiss: () -> Unit) {
val noticeContent = """
        嘿孩子们，从 **Pyrolysis19.0** 版本开始，本项目的下载服务已经整体移交给 **1DM** 家族了。

所以你需要安装 1DM 系列的应用哦。

> **注意：** 是数字 **1**（一），不是字母 "I" 哦！

或者自己核对一下应用包名是不是这几个：

### **核对包名：**

* `idm.internet.download.manager.plus`
* `idm.internet.download.manager`
* `idm.internet.download.manager.adm.lite`

现在的逻辑很简单：**浊燃只负责获取到下载 URL 并通过 Intent 叫醒 1DM**。至于你设备上装的 1DM 是原版、Plus 版还是那种“懂的都懂”的修改版，我管不着，也没做校验。

---

### **关于获取途径：**

为了避嫌，你 V 哥在此处不提供安装包。如果你觉得免费版 1DM 广告多，我推荐你上 Plus 版本，也就是 **1DM+**。

至于 1DM+ 要付费？这个嘛……你可以去咨询 [Bing 大小姐](https://www.bing.com/search?q=1DM%2B)

### **为什么这是个好主意？**

1DM（原名 IDM+）是安卓端公认的下载神器。把下载任务全权交给它，是因为：

* **极致的速度优化**：它支持高达 32 线程的分段下载，能够压榨出服务器带宽的极限。
* **断点续传的稳定性**：对于大文件，1DM 的校验机制和重连能力非常成熟，比大多数应用内置的下载器要稳得多。
    """.trimIndent()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "下载服务变更",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 使用项目内置的 MarkDownText 处理正文
                // 嵌套在 Column 中并开启滚动，确保长文不溢出
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false) // 灵活高度，最高不超出屏幕
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkDownText(
                        content = noticeContent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("知道咯")
                }
            }
        }
    }
}