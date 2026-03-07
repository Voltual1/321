//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.payment

// 支付类型枚举
enum class PaymentType {
    APP_PURCHASE,
    POST_REWARD
}

// 支付状态枚举
enum class PaymentStatus {
    INITIAL,   // 初始状态
    PROCESSING,  // 支付处理中
    SUCCESS,     // 支付成功
    FAILED,      // 支付失败
    SUBMITTED    // 支付请求已提交
}

// 支付信息数据类
data class PaymentInfo(
    val type: PaymentType,
    val appId: Long = 0L,
    val appName: String = "",
    val versionId: Long = 0L,
    val price: Int = 0,
    val postId: Long = 0L,
    val postTitle: String = "",
    val locked: Boolean = true,
    val iconUrl: String = "",
    val previewContent: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val postTime: String = ""
)