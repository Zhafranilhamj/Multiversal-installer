package app.pwhs.universalinstaller.presentation.install

import app.pwhs.core.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Full provider result; no credentials are persisted with a pending installation. */
internal object ScanResultCodec {
    fun encode(v: VtResult) = JSONObject().apply {
        put("status", v.status.name); put("malicious", v.malicious); put("suspicious", v.suspicious)
        put("harmless", v.harmless); put("undetected", v.undetected); put("error", v.errorMessage)
        put("progress", v.uploadProgress); put("analysis", v.analysisId); put("provider", v.provider)
        put("engines", JSONArray(v.engineResults.map {
            JSONObject().put("name", it.engineName).put("category", it.category).put("result", it.result)
        }))
        put("report", v.report?.let { r -> JSONObject().apply {
            put("sha256", r.sha256); put("dexHashes", JSONArray(r.dexHashes)); put("package", r.packageName)
            put("app", r.appName); put("version", r.versionName); put("code", r.versionCode)
            put("cert", r.certSha256); put("verdict", r.verdict.name); put("risk", r.riskScore)
            put("scannerVersion", r.scannerVersion); put("created", r.createdAt); put("updated", r.updatedAt)
            put("findings", JSONArray(r.findings.map { f -> JSONObject().apply {
                put("scanner", f.scanner); put("rule", f.ruleId); put("severity", f.severity.name)
                put("impact", f.scoreImpact); put("title", f.title); put("description", f.description)
                put("evidence", f.evidence)
            } }))
        } })
    }

    fun decode(j: JSONObject): VtResult = VtResult(
        status = VtStatus.valueOf(j.getString("status")).let {
            if (it in setOf(VtStatus.SCANNING, VtStatus.UPLOADING, VtStatus.QUEUED, VtStatus.ANALYZING)) VtStatus.ERROR else it
        },
        malicious = j.optInt("malicious"), suspicious = j.optInt("suspicious"),
        harmless = j.optInt("harmless"), undetected = j.optInt("undetected"),
        errorMessage = j.optString("error"), uploadProgress = j.optInt("progress"),
        analysisId = j.optString("analysis"), provider = j.nullable("provider"),
        engineResults = j.optJSONArray("engines").objects().map {
            VtEngineResult(it.getString("name"), it.getString("category"), it.nullable("result"))
        },
        report = j.optJSONObject("report")?.let { r -> ScanReport(
            sha256 = r.getString("sha256"),
            dexHashes = r.getJSONArray("dexHashes").let { a -> (0 until a.length()).map { a.getString(it) } },
            packageName = r.getString("package"), appName = r.getString("app"),
            versionName = r.getString("version"), versionCode = r.getLong("code"),
            certSha256 = r.getString("cert"), verdict = Verdict.valueOf(r.getString("verdict")),
            riskScore = r.getInt("risk"), scannerVersion = r.getString("scannerVersion"),
            createdAt = r.getString("created"), updatedAt = r.getString("updated"),
            findings = r.getJSONArray("findings").objects().map { f -> Finding(
                scanner = f.getString("scanner"), ruleId = f.getString("rule"),
                severity = SeverityLevel.valueOf(f.getString("severity")), scoreImpact = f.getInt("impact"),
                title = f.getString("title"), description = f.getString("description"), evidence = f.nullable("evidence"),
            ) },
        ) },
    )
    private fun JSONObject.nullable(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList()
        else (0 until length()).map { getJSONObject(it) }
}
