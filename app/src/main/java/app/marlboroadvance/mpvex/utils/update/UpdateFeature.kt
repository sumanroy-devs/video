package app.marlboroadvance.mpvex.utils.update
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// --- Data Models ---

@Serializable
data class Release(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String = "",
    @SerialName("published_at") val publishedAt: String,
    @SerialName("assets") val assets: List<Asset>,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class Asset(
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    companion object {
        const val RELEASE_PAGE_URL = "https://github.com/sumanroy-devs/video/releases/latest"

        /**
         * True only for the shipped release package: .debug installs carry a
         * different applicationId, so a release APK would land as a separate app instead
         * of updating this one.
         */
        val isUpdateActive: Boolean
            get() = BuildConfig.ENABLE_UPDATE_FEATURE &&
                BuildConfig.APPLICATION_ID == BuildConfig.RELEASE_APPLICATION_ID
    }

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        // GitHub may send null for empty release fields (e.g. "body")
        coerceInputValues = true
    }

    suspend fun checkForUpdate(forceShow: Boolean = false): Release? {
        // Disabled builds or packages that can't update in place never see a release
        if (!isUpdateActive) {
            return null
        }
        
        val release = getLatestRelease("https://api.github.com/repos/sumanroy-devs/video/releases/latest") ?: return null
        val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)
        val remoteVersion = normalizeVersion(release.tagName)
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)

        // If this version was ignored, don't show it unless forced (manual check)
        if (!forceShow && ignoredVersion == remoteVersion) {
            return null
        }

        return if (isNewerVersion(remoteVersion, currentVersion)) {
            release
        } else {
            null
        }
    }

    fun ignoreVersion(version: String) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ignored_version", normalizeVersion(version))
            .apply()
    }

    private suspend fun getLatestRelease(url: String): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        // 404 = no releases published yet (fresh repo) — not a connection problem
        if (response.code == 404) {
            response.close()
            return@withContext null
        }
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body.string()
        if (responseBody.isEmpty()) throw IOException("Empty body")
        json.decodeFromString<Release>(responseBody)
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = normalizeVersion(remote).split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = normalizeVersion(current).split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Strips flavor/prerelease suffixes so "1.3.0-playstore", "1.3.0-fdroid",
     * "1.3.0-preview.1" and "v1.3.0" all compare as plain "1.3.0" (ported from MyTube).
     */
    private fun normalizeVersion(version: String): String =
        version.trim().removePrefix("v").substringBefore('-')

    /** True when at least one release asset can be installed on this device. */
    fun hasCompatibleApk(release: Release): Boolean = selectBestApkAsset(release.assets) != null

    /**
     * Release page URL — fallback when the release has no compatible APK asset,
     * so the update flow never dead-ends (ported from MyTube).
     */
    fun getReleasePageUrl(release: Release): String =
        release.htmlUrl.ifBlank { RELEASE_PAGE_URL }

    fun downloadUpdate(release: Release): Flow<Float> {
        // Return completed flow immediately if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return flowOf(100f)
        }
        
        val asset = selectBestApkAsset(release.assets)
            ?: throw Exception("No compatible APK asset found")

        val destination = File(context.externalCacheDir, asset.name)
        return downloadApk(asset.downloadUrl, destination, asset.size)
    }

    private fun selectBestApkAsset(assets: List<Asset>): Asset? {
        val deviceArch = getDeviceArchitecture()
        
        // First, try to find architecture-specific APK
        val archSpecificApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk") && nameMatchesArch(asset.name, deviceArch)
        }

        if (archSpecificApk != null) {
            return archSpecificApk
        }

        // Fallback to universal APK
        val universalApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk") && nameMatchesArch(asset.name, "universal")
        }

        if (universalApk != null) {
            return universalApk
        }

        // Last resort: any APK
        return assets.firstOrNull { it.name.endsWith(".apk") }
    }

    /**
     * Matches an asset name against an ABI with token boundaries so "x86" never
     * matches an "x86_64" APK ("video-x86_64-v1.3.0.apk" must not be picked for x86).
     */
    private fun nameMatchesArch(assetName: String, arch: String): Boolean =
        Regex("(^|[-.])${Regex.escape(arch)}([-.]|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(assetName)

    private fun getDeviceArchitecture(): String {
        // Get the primary ABI (Application Binary Interface)
        val primaryAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS[0]
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
        
        // Map Android ABI names to your APK naming convention
        return when (primaryAbi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> "universal" // Fallback for unknown architectures
        }
    }

    private fun downloadApk(url: String, destination: File, expectedSize: Long): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Unexpected code $response")
        }

        val body = response.body
        val contentLength = body.contentLength()
        // Stream into a side file so an interrupted download never leaves a truncated
        // APK at the final path where getApkFile() would offer it as ready to install.
        val partFile = File(destination.parentFile, "${destination.name}.part")
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(partFile)
        var totalBytesRead: Long = 0

        try {
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength.toFloat()) * 100
                } else {
                    -1f 
                }
                emit(progress)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
        }

        if (expectedSize > 0 && totalBytesRead != expectedSize) {
            partFile.delete()
            throw IOException("Incomplete download: got $totalBytesRead of $expectedSize bytes")
        }
        if (!partFile.renameTo(destination)) {
            partFile.delete()
            throw IOException("Could not move downloaded file into place")
        }
        emit(100f) 
    }.flowOn(Dispatchers.IO)
    
    fun getApkFile(release: Release): File? {
        // Return null if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }
        
        val asset = selectBestApkAsset(release.assets) ?: return null
        val file = File(context.externalCacheDir, asset.name)
        if (!file.exists()) return null
        // Truncated leftovers (interrupted download, older versions) are not installable
        if (asset.size > 0 && file.length() != asset.size) {
            file.delete()
            return null
        }
        return file
    }

    fun clearCache() {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        context.externalCacheDir?.listFiles()?.forEach {
            if (it.name.endsWith(".apk") || it.name.endsWith(".apk.part")) it.delete()
        }
    }
}

// --- ViewModel ---

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float>(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val prefs = application.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
    private val _isAutoUpdateEnabled = MutableStateFlow(
        if (BuildConfig.ENABLE_UPDATE_FEATURE) prefs.getBoolean("auto_update", false) else false
    )
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    fun toggleAutoUpdate(enabled: Boolean) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        prefs.edit().putBoolean("auto_update", enabled).apply()
        _isAutoUpdateEnabled.value = enabled
        if (enabled) {
            checkForUpdate(manual = false)
        }
    }

    init {
        // Only initialize auto-update if feature is enabled
        if (BuildConfig.ENABLE_UPDATE_FEATURE && isAutoUpdateEnabled.value) {
            checkForUpdate(manual = false)
        }
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Available(val release: Release) : UpdateState()
        object NoUpdate : UpdateState()
        object Error : UpdateState()
        data class ReadyToInstall(val release: Release) : UpdateState()
    }

    fun dismissNoUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun checkForUpdate(manual: Boolean = false) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual)
                if (release != null) {
                    val existingFile = updateManager.getApkFile(release)
                     if (existingFile != null) {
                         _updateState.value = UpdateState.ReadyToInstall(release)
                     } else {
                         _updateState.value = UpdateState.Available(release)
                     }
                } else {
                    if (manual) _updateState.value = UpdateState.NoUpdate
                    else _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual) _updateState.value = UpdateState.Error
                else _updateState.value = UpdateState.Idle
            }
        }
    }

    fun downloadUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }

        // No APK asset matches this device → fall back to the release page in the browser
        if (!updateManager.hasCompatibleApk(release)) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(updateManager.getReleasePageUrl(release))
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            try {
                updateManager.downloadUpdate(release).collect { progress ->
                    _downloadProgress.value = progress
                }
                _isDownloading.value = false
                 _updateState.value = UpdateState.ReadyToInstall(release)
            } catch (e: Exception) {
                e.printStackTrace()
                _isDownloading.value = false
                _updateState.value = UpdateState.Error 
            }
        }
    }

    fun installUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        val file = updateManager.getApkFile(release) ?: return
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    fun dismiss() {
        // Clean up downloaded APK when user dismisses the dialog
        updateManager.clearCache()
        _updateState.value = UpdateState.Idle
    }

    fun ignoreVersion(version: String) {
        updateManager.ignoreVersion(version)
        _updateState.value = UpdateState.Idle
    }
}

// --- UI Components ---

@Composable
fun UpdateDialog(
    release: Release,
    isDownloading: Boolean,
    progress: Float,
    readyToInstall: Boolean,
    currentVersion: String,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onIgnore: () -> Unit
) {
    val downloadSize = release.assets.find { it.name.endsWith(".apk") }?.size ?: 0L
    val formattedDate = formatDate(release.publishedAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (readyToInstall) Icons.Filled.SystemUpdate else Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (readyToInstall) {
                        stringResource(R.string.update_dialog_title_install)
                    } else {
                        stringResource(R.string.update_dialog_title_available)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.tagName.removePrefix("v"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!readyToInstall) {
                    // Show version info for update available state
                    InfoRow(label = stringResource(R.string.update_dialog_current_version), value = currentVersion)
                    InfoRow(label = stringResource(R.string.update_dialog_latest_version), value = release.tagName.removePrefix("v"))
                    InfoRow(label = stringResource(R.string.update_dialog_release_date), value = formattedDate)
                    InfoRow(
                        label = stringResource(R.string.update_dialog_size),
                        value = if (downloadSize > 0) {
                            formatFileSize(downloadSize)
                        } else {
                            stringResource(R.string.update_dialog_unknown_size)
                        },
                    )
                }

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.update_dialog_whats_new),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.body.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.update_dialog_downloading), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (progress >= 0) "${progress.toInt()}%" else "—",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress >= 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        // Content length unknown → indeterminate bar instead of a frozen 0%
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onAction) {
                    Text(stringResource(if (readyToInstall) R.string.update_dialog_install else R.string.update_dialog_download))
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    if (!readyToInstall) {
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.update_dialog_ignore))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_cancel))
                    }
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(dateString: String): String {
    return try {
        // GitHub API returns ISO 8601 format: "2024-01-15T10:30:00Z"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return dateString

        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        dateString
    }
}
