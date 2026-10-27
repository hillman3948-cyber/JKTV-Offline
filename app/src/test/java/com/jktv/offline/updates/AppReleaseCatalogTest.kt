package com.jktv.offline.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseCatalogTest {
    private val hash = "a".repeat(64)
    private fun release(tag: String, code: Int, pkg: String = "com.jktv.offline", prerelease: Boolean = false,
                        draft: Boolean = false): Pair<JSONObject, JSONObject> {
        val name = tag.removePrefix("app-v").removePrefix("v")
        val json = JSONObject().put("packageName", pkg).put("versionName", name).put("versionCode", code)
            .put("apk", "JKTV-v$name.apk").put("sha256", hash)
        val assets = JSONArray().put(JSONObject().put("name", "app-version.json").put("browser_download_url", "https://example.test/$code.json"))
            .put(JSONObject().put("name", "JKTV-v$name.apk").put("browser_download_url", "https://example.test/$code.apk"))
        return JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", prerelease)
            .put("assets", assets).put("body", "Release notes") to json
    }
    private fun find(vararg fixtures: Pair<JSONObject, JSONObject>, current: String = "0.4.4", code: Int = 17): GitHubAppUpdate? {
        val releases = JSONArray(); val metadata = mutableMapOf<String, JSONObject>()
        fixtures.forEach { (r, m) -> releases.put(r); metadata["https://example.test/${m.getInt("versionCode")}.json"] = m }
        return AppReleaseCatalog.findUpdate(releases, "com.jktv.offline", current, code) { metadata.getValue(it) }
    }
    @Test fun sameVersionNewBuildIsAnUpdateAndSameBuildIsNot() {
        assertEquals(20, find(release("app-v0.4.4", 20))?.versionCode)
        assertNull(find(release("v0.4.4", 17)))
    }
    @Test fun androidBuildMustIncreaseEvenWhenVersionNameIncreases() {
        assertNull(find(release("app-v0.5.0", 16)))
    }
    @Test fun skipsDraftsPrereleasesAndDifferentPackages() {
        assertNull(find(release("app-v0.5.0", 30, prerelease = true), release("app-v0.6.0", 31, draft = true)))
        assertEquals(20, find(release("app-v0.5.0", 30, pkg = "com.jktv.offline.cast"), release("app-v0.4.4", 20))?.versionCode)
    }
    @Test fun comparesVersionsNumericallyAndFindsHighestBuildForDuplicateTags() {
        assertEquals("0.10.0", find(release("app-v0.9.0", 30), release("v0.10.0", 31))?.versionName)
        assertEquals(21, find(release("app-v0.4.4", 20), release("v0.4.4", 21))?.versionCode)
        assertEquals(0, AppReleaseCatalog.compareVersions("0.4", "0.4.0"))
    }
    @Test fun ignoresLibraryTagsAndUntaggedPrereleaseNames() {
        assertNull(find(release("library-v9.0.0", 90), release("app-v0.5.0-rc1", 30)))
    }
    @Test fun rejectsCorruptChecksumInsteadOfOfferingIt() {
        val fixture = release("app-v0.4.4", 20)
        fixture.second.put("sha256", "broken")
        assertThrows(IllegalArgumentException::class.java) { find(fixture) }
    }
}
