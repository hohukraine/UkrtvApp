package ua.ukrtv.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.domain.model.UpdateInfo
import ua.ukrtv.app.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val updateUrl = "https://raw.githubusercontent.com/alex/UkrtvApp/main/update.json"
    private val tag = "UpdateRepository"

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(updateUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    return@withContext json.decodeFromString<UpdateInfo>(body)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to check update", e)
        }
        null
    }

    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                val apkFile = File(context.cacheDir, "update.apk")
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalBytesRead.toFloat() / contentLength)
                            }
                        }
                    }
                }
                return@withContext apkFile
            }
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to download APK", e)
        }
        null
    }

    fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to launch installer", e)
        }
    }
}
