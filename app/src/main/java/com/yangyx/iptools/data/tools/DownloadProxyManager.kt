package com.yangyx.iptools.data.tools

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class GitHubReleaseInfo(
    val tagName: String,
    val assets: List<GitHubAssetInfo> = emptyList()
)

data class GitHubAssetInfo(
    val name: String,
    val downloadUrl: String
)

object DownloadProxyManager {

    val PROXY_NODES = listOf(
        "RAW" to "GitHub 原始",
        "gh.dpik.top" to "gh.dpik.top",
        "gh-proxy.com" to "gh-proxy.com",
        "github.tbap.top" to "github.tbap.top",
        "github.dpik.top" to "github.dpik.top",
        "ghfile.geekertao.top" to "ghfile.geekertao.top",
        "ghproxy.net" to "ghproxy.net",
        "CUSTOM" to "自定义代理"
    )

    fun buildDownloadUrls(rawUrl: String, choice: String, customProxy: String): List<String> {
        val urls = mutableListOf<String>()

        when (choice) {
            "RAW" -> {
                urls.add(rawUrl)
            }
            "CUSTOM" -> {
                var prefix = customProxy.trim()
                if (prefix.isNotBlank()) {
                    if (!prefix.startsWith("http://") && !prefix.startsWith("https://")) {
                        prefix = "https://$prefix"
                    }
                    if (!prefix.endsWith("/")) {
                        prefix += "/"
                    }
                    urls.add("${prefix}$rawUrl")
                }
                urls.add(rawUrl)
            }
            else -> {
                val prefix = "https://$choice/"
                urls.add("${prefix}$rawUrl")
                urls.add(rawUrl)
            }
        }

        // Add backup fallback mirrors automatically
        PROXY_NODES.map { it.first }.filter { it != "RAW" && it != "CUSTOM" && it != choice }.forEach { host ->
            urls.add("https://$host/$rawUrl")
        }

        return urls.distinct()
    }

    /**
     * Fetch latest release tag and asset links dynamically from GitHub for a repository (e.g., "shadow1ng/fscan" or "fatedier/frp")
     */
    fun fetchLatestReleaseTag(
        repoPath: String, // e.g. "shadow1ng/fscan" or "fatedier/frp"
        choice: String,
        customProxy: String
    ): GitHubReleaseInfo? {
        val apiUrl = "https://api.github.com/repos/$repoPath/releases/latest"
        val candidateApiUrls = buildDownloadUrls(apiUrl, choice, customProxy)

        // Try API first
        for (urlStr in candidateApiUrls) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.instanceFollowRedirects = true

                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                    val tagMatcher = tagPattern.matcher(jsonStr)
                    if (tagMatcher.find()) {
                        val tagName = tagMatcher.group(1) ?: ""
                        if (tagName.isNotBlank()) {
                            val assets = mutableListOf<GitHubAssetInfo>()
                            val assetPattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
                            val assetMatcher = assetPattern.matcher(jsonStr)
                            while (assetMatcher.find()) {
                                val name = assetMatcher.group(1) ?: ""
                                val downloadUrl = assetMatcher.group(2) ?: ""
                                if (name.isNotBlank() && downloadUrl.isNotBlank()) {
                                    assets.add(GitHubAssetInfo(name, downloadUrl))
                                }
                            }
                            return GitHubReleaseInfo(tagName, assets)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: Check standard GitHub latest release redirect
        val redirectUrl = "https://github.com/$repoPath/releases/latest"
        val candidateRedirectUrls = buildDownloadUrls(redirectUrl, choice, customProxy)

        for (urlStr in candidateRedirectUrls) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")

                val responseCode = conn.responseCode
                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location") ?: ""
                    if (location.contains("/releases/tag/")) {
                        val tagName = location.substringAfter("/releases/tag/").trim('/')
                        if (tagName.isNotBlank()) {
                            return GitHubReleaseInfo(tagName)
                        }
                    }
                } else if (responseCode == 200) {
                    val finalUrl = conn.url.toString()
                    if (finalUrl.contains("/releases/tag/")) {
                        val tagName = finalUrl.substringAfter("/releases/tag/").trim('/')
                        if (tagName.isNotBlank()) {
                            return GitHubReleaseInfo(tagName)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }
}

