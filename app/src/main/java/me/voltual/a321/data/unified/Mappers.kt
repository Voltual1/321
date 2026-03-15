package me.voltual.a321.data.unified

import me.voltual.a321.KtorClient

fun KtorClient.FileInfo.toUnifiedModel(): PanFile {
    return PanFile(
        id = this.FileId,
        name = this.FileName,
        size = this.Size,
        isDirectory = this.isDirectory,
        updateTime = this.UpdateAt,
        category = this.Category,
        isAbnormal = this.isAbnormal,
        etag = this.Etag,
        s3KeyFlag = this.S3KeyFlag,
        rawDownloadUrl = this.DownloadUrl // 映射列表自带的 URL
    )
}

fun List<KtorClient.FileInfo>.toUnifiedList(): List<PanFile> {
    return this.map { it.toUnifiedModel() }
}