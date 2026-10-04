package me.voltual.a321.data.unified

enum class PanPlatform(val id: String, val displayName: String) {
  PAN123("123pan", "123云盘"),
  CLOUD139("cloud139", "中国移动云盘");

  companion object {
    fun fromId(id: String): PanPlatform {
      return entries.find { it.id == id } ?: PAN123
    }
  }
}