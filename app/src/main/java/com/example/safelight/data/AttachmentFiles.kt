package com.example.safelight.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File

/** 사용자가 고른 파일 하나. 실제 바이트는 보낼 때 읽는다(고르기만 하고 취소할 수도 있어서). */
data class PickedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
)

/**
 * 첨부파일을 고르고 · 보내고 · 받아 저장하는 자리.
 *
 * 허용 규칙은 백엔드 FileStorageService 를 그대로 옮긴 것이다. 서버도 같은 검사를 하므로
 * 여기서 거르는 건 '올리고 나서 400 을 보는' 대신 고르는 자리에서 바로 알려주기 위한 것이다.
 * 서버는 확장자와 MIME 을 **둘 다** 보므로 한쪽만 맞아도 거절된다.
 */
object AttachmentFiles {

    val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "pdf", "txt")

    /** 파일 선택기에 넘길 MIME 목록. 백엔드 ALLOWED_CONTENT_TYPES 와 같다. */
    val ALLOWED_MIME_TYPES = arrayOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "application/pdf",
        "text/plain",
    )

    /** 백엔드 MAX_FILE_SIZE_BYTES · spring.servlet.multipart.max-file-size 와 같다. */
    const val MAX_BYTES = 10L * 1024 * 1024

    /** 사용자에게 보여줄 허용 목록 문구. */
    const val ALLOWED_HINT = "JPG, PNG, GIF, WEBP, PDF, TXT (최대 10MB)"

    fun read(context: Context, uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
        val filename = name ?: return null
        // 선택기가 MIME 을 못 주는 경우가 있어(일부 파일 앱) 확장자로 메운다.
        val mime = resolver.getType(uri) ?: mimeFromExtension(filename.extension()) ?: return null
        return PickedFile(uri, filename, mime.substringBefore(';').trim().lowercase(), size)
    }

    /** 거절 사유. 통과하면 null. 문구는 백엔드가 돌려주는 것과 같은 뜻으로 맞춰 뒀다. */
    fun reject(file: PickedFile): String? = when {
        file.size > MAX_BYTES -> "첨부파일은 최대 10MB까지 올릴 수 있습니다."
        file.name.length > 255 -> "파일명은 255자 이하여야 합니다."
        file.extension().isBlank() -> "파일 확장자가 필요합니다."
        file.extension() !in ALLOWED_EXTENSIONS ->
            "허용되지 않은 확장자입니다. 허용값: ${ALLOWED_EXTENSIONS.joinToString(", ")}"
        file.mimeType !in ALLOWED_MIME_TYPES -> "허용되지 않은 파일 형식입니다."
        else -> null
    }

    /** 보낼 때 바이트를 읽는다. 10MB 상한이 있어 통째로 메모리에 올려도 된다. */
    fun part(context: Context, file: PickedFile): MultipartBody.Part? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val body = bytes.toRequestBody(file.mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("files", file.name, body)
    }

    /**
     * 글자 조각. charset 을 붙이지 않으면 서버가 한글 제목을 깨진 채로 받는다
     * (multipart 의 글자 조각은 요청 전체의 인코딩과 따로 논다).
     */
    fun textPart(value: String): RequestBody =
        value.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

    /**
     * 받은 파일을 기기의 '다운로드' 폴더에 저장하고, 열 수 있는 주소를 돌려준다.
     * 웹이 브라우저 내려받기로 하던 것과 같은 자리에 떨어진다.
     *
     * Android 10(Q)부터는 MediaStore 로 넣으면 권한이 필요 없다. 그 아래에서는 공용 폴더에
     * 직접 쓰므로 호출하는 쪽이 WRITE_EXTERNAL_STORAGE 를 먼저 받아 둬야 한다([needsLegacyPermission]).
     */
    fun saveToDownloads(context: Context, filename: String, mimeType: String, body: ResponseBody): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            // 같은 이름이 있으면 MediaStore 가 알아서 '(1)' 을 붙인다.
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("다운로드 폴더에 쓸 수 없습니다.")
            resolver.openOutputStream(uri)?.use { out -> body.byteStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("다운로드 폴더에 쓸 수 없습니다.")
            return uri
        }

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueFile(dir, filename)
        target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        // 공용 폴더의 파일이라도 다른 앱에 넘길 땐 file:// 를 쓸 수 없다(FileUriExposedException).
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** `보고서.pdf` 가 있으면 `보고서 (1).pdf` 로. Q 아래에서만 쓴다(위에서는 MediaStore 가 한다). */
    private fun uniqueFile(dir: File, filename: String): File {
        val base = filename.substringBeforeLast('.', filename)
        val extension = filename.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var candidate = File(dir, filename)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$suffix")
            n++
        }
        return candidate
    }

    private fun mimeFromExtension(extension: String): String? = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> null
    }

    private fun String.extension(): String =
        substringAfterLast('.', "").lowercase()

    private fun PickedFile.extension(): String = name.extension()
}
