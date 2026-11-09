package app.pwhs.universalinstaller.presentation.install

import android.net.Uri
import app.pwhs.universalinstaller.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object PendingInstallCodec {
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun encode(entry: PendingInstallStore.Entry): String = JSONObject().apply {
        put("schema", 1)
        put("id", entry.id)
        put("uris", JSONArray(entry.apkUris.map { it.toString() }))
        put("original", entry.originalUri?.toString())
        put("fileName", entry.fileName)
        put("packageName", entry.packageName)
        put("appName", entry.appName)
        put("iconPath", entry.iconPath)
        put("downgrade", entry.isDowngrade)
        put("info", entry.apkInfo?.let(::encodeInfo))
        put("obbs", JSONArray(entry.obbEntries.map {
            JSONObject().put("path", it.entryPath).put("name", it.fileName).put("size", it.sizeBytes)
        }))
        put("attached", JSONArray(entry.attachedObbs.map {
            JSONObject().put("uri", it.uri.toString()).put("name", it.fileName).put("size", it.sizeBytes)
        }))
    }.toString()

    fun decode(text: String): PendingInstallStore.Entry {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1)
        fun optional(name: String) = if (json.isNull(name)) null else json.getString(name)
        return PendingInstallStore.Entry(
            id = json.getString("id"),
            apkUris = json.getJSONArray("uris").strings().map(Uri::parse).also { require(it.isNotEmpty()) },
            originalUri = optional("original")?.let(Uri::parse),
            fileName = json.getString("fileName"), packageName = json.getString("packageName"),
            appName = json.getString("appName"), iconPath = optional("iconPath"),
            isDowngrade = json.getBoolean("downgrade"),
            apkInfo = json.optJSONObject("info")?.let(::decodeInfo),
            obbEntries = json.getJSONArray("obbs").objects().map {
                ObbEntry(it.getString("path"), it.getString("name"), it.getLong("size"))
            },
            attachedObbs = json.getJSONArray("attached").objects().map {
                AttachedObb(Uri.parse(it.getString("uri")), it.getString("name"), it.getLong("size"))
            },
        )
    }
    private fun encodeInfo(info: ApkInfo) = JSONObject().apply {
        put("appName", info.appName)
        put("packageName", info.packageName)
        put("versionName", info.versionName)
        put("versionCode", info.versionCode)
        put("minSdkVersion", info.minSdkVersion)
        put("targetSdkVersion", info.targetSdkVersion)
        put("fileSizeBytes", info.fileSizeBytes)
        put("permissions", JSONArray(info.permissions))
        put("splitCount", info.splitCount)
        put("fileFormat", info.fileFormat)
        put("supportedAbis", JSONArray(info.supportedAbis))
        put("sha256", info.sha256)
        put("certSha256", info.certSha256)
        put("obbFileNames", JSONArray(info.obbFileNames))
        put("obbTotalBytes", info.obbTotalBytes)
        put("installedVersionName", info.installedVersionName)
        put("installedVersionCode", info.installedVersionCode)
        put("installedPermissions", JSONArray(info.installedPermissions))
        put("signatureMismatch", info.signatureMismatch)
        put("isBlocked", info.isBlocked)
        put("isAndroidAutoSupported", info.isAndroidAutoSupported)
        put("isWearOsSupported", info.isWearOsSupported)
        put("isScanningTrackers", info.isScanningTrackers)
        put("splits", JSONArray(info.splitEntries.map {
            JSONObject().put("name", it.name).put("type", it.type.name).put("uri", it.uri.toString())
                .put("size", it.sizeBytes).put("selected", it.selected)
        }))
        put("trackers", JSONArray(info.trackers.map {
            JSONObject().put("id", it.id).put("name", it.name).put("categories", JSONArray(it.categories))
                .put("website", it.website)
        }))
        put("vt", info.vtResult?.let {
            JSONObject().put("status", it.status.name).put("malicious", it.malicious)
                .put("suspicious", it.suspicious).put("harmless", it.harmless).put("undetected", it.undetected)
        })
    }
    private fun decodeInfo(json: JSONObject): ApkInfo = ApkInfo(
            appName = json.getString("appName"),
            packageName = json.getString("packageName"),
            versionName = json.getString("versionName"),
            versionCode = json.getLong("versionCode"),
            minSdkVersion = json.getInt("minSdkVersion"),
            targetSdkVersion = json.getInt("targetSdkVersion"),
            fileSizeBytes = json.getLong("fileSizeBytes"),
            permissions = json.getJSONArray("permissions").strings(),
            splitCount = json.getInt("splitCount"),
            fileFormat = json.getString("fileFormat"),
            supportedAbis = json.getJSONArray("supportedAbis").strings(),
            sha256 = json.getString("sha256"),
            certSha256 = json.getString("certSha256"),
            obbFileNames = json.getJSONArray("obbFileNames").strings(),
            obbTotalBytes = json.getLong("obbTotalBytes"),
            installedVersionName = if (json.isNull("installedVersionName")) null else json.getString("installedVersionName"),
            installedVersionCode = if (json.isNull("installedVersionCode")) null else json.getLong("installedVersionCode"),
            installedPermissions = json.getJSONArray("installedPermissions").strings(),
            signatureMismatch = if (json.isNull("signatureMismatch")) null else json.getBoolean("signatureMismatch"),
            isBlocked = json.getBoolean("isBlocked"),
            isAndroidAutoSupported = json.getBoolean("isAndroidAutoSupported"),
            isWearOsSupported = json.getBoolean("isWearOsSupported"),
            isScanningTrackers = false, // No scanner job survives process death.
            icon = null,
            splitEntries = json.getJSONArray("splits").objects().map {
                SplitEntry(it.getString("name"), SplitType.valueOf(it.getString("type")),
                    Uri.parse(it.getString("uri")), it.getLong("size"), it.getBoolean("selected"))
            },
            trackers = json.getJSONArray("trackers").objects().map {
                TrackerInfo(it.getInt("id"), it.getString("name"), it.getJSONArray("categories").strings(),
                    if (it.isNull("website")) null else it.getString("website"))
            },
            vtResult = json.optJSONObject("vt")?.let {
                VtResult(status = VtStatus.valueOf(it.getString("status")).let { status ->
                    if (status in setOf(VtStatus.SCANNING, VtStatus.UPLOADING, VtStatus.QUEUED, VtStatus.ANALYZING))
                        VtStatus.ERROR else status
                }, malicious = it.getInt("malicious"),
                    suspicious = it.getInt("suspicious"), harmless = it.getInt("harmless"), undetected = it.getInt("undetected"))
            },
        )
}
