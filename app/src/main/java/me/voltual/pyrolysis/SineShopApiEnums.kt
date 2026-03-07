package me.voltual.pyrolysis

import java.util.ArrayList

/**
 * app_abi字段的位掩码解析逻辑
 */
enum class AbiFlag(val mask: Int, val label: String) {
    X86(1, "x86"),
    ARM32(2, "ARM32"),
    ARM64(4, "ARM64");

    companion object {
        fun parseAbi(abiValue: Int): String {
            // 特殊情况判断
            if (abiValue <= 0) return "无适配"
            if ((abiValue and 7) == 7) return "全兼容"

            val supportedAbis = ArrayList<String>(3)
            
            if ((abiValue and 4) != 0) {
                supportedAbis.add(ARM64.label)
            }
            if ((abiValue and 2) != 0) {
                supportedAbis.add(ARM32.label)
            }
            if ((abiValue and 1) != 0) {
                supportedAbis.add(X86.label)
            }

            // 使用 "/" 拼接字符串 
            return if (supportedAbis.isEmpty()) {
                "无适配"
            } else {
                supportedAbis.joinToString("/")
            }
        }
    }
}

/**
 * is_favourite字段的逻辑转换
 * 1 表示已收藏，0 表示未收藏
 */
enum class FavouriteState(val value: Int, val isFavourite: Boolean) {
    FAVOURITE(1, true),
    NOT_FAVOURITE(0, false);

    companion object {
        /**
         * 将 API 返回的整数值转换为 Boolean
         * 如果值不匹配（防御性编程），默认返回 false
         */
        fun isFavourite(value: Int): Boolean {
            return values().find { it.value == value }?.isFavourite ?: false
        }
    }
}