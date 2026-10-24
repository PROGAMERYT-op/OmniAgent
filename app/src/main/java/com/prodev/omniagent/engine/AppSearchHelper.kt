package com.prodev.omniagent.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Representation of an installed launchable application.
 */
data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

/**
 * Helper utility for querying, searching, and resolving installed applications on the device.
 * Used by the AI agent execution engine to locate exact package names and resolve app launch intents.
 */
object AppSearchHelper {

    /**
     * Queries all launchable apps installed on the device.
     */
    fun getInstalledLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        return resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = try {
                resolveInfo.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) {
                pkg
            }
            InstalledAppInfo(label = label, packageName = pkg)
        }.distinctBy { it.packageName }
    }

    /**
     * Searches installed launchable apps matching the query string.
     * Matches against app label and package name (case-insensitive),
     * sorted by relevance (exact label match > label prefix > label contains > package contains).
     */
    fun searchInstalledApps(context: Context, query: String): List<InstalledAppInfo> {
        val q = query.trim().lowercase()
        val allApps = getInstalledLaunchableApps(context)
        if (q.isBlank()) return allApps

        return allApps
            .filter { app ->
                app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            }
            .sortedWith(
                compareBy(
                    { !it.label.equals(q, ignoreCase = true) },
                    { !it.label.startsWith(q, ignoreCase = true) },
                    { !it.label.contains(q, ignoreCase = true) },
                    { !it.packageName.contains(q, ignoreCase = true) }
                )
            )
    }

    /**
     * Resolves a launch Intent and app information for a given app name or package identifier.
     * 1. Checks if [appNameOrPackage] is already a valid launchable package name.
     * 2. If not, performs a search across installed apps to find the closest match.
     * 3. Falls back to matching the trailing segment (e.g. "deskclock" from "com.google.clock").
     */
    fun resolveLaunchIntent(context: Context, appNameOrPackage: String): Pair<Intent, InstalledAppInfo>? {
        val pm = context.packageManager
        val trimmed = appNameOrPackage.trim()
        if (trimmed.isBlank()) return null

        // 1. Direct package lookup
        val directIntent = pm.getLaunchIntentForPackage(trimmed)
        if (directIntent != null) {
            val label = try {
                val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(trimmed, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    pm.getApplicationInfo(trimmed, 0)
                }
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                trimmed
            }
            return directIntent to InstalledAppInfo(label = label, packageName = trimmed)
        }

        // 2. Search installed apps by label or package substring
        val matches = searchInstalledApps(context, trimmed)
        for (match in matches) {
            val matchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (matchIntent != null) {
                return matchIntent to match
            }
        }

        // 3. Fallback: try stripping package segments (e.g., if query was "com.google.clock" try "clock")
        val simpleName = trimmed.substringAfterLast('.').lowercase()
        if (simpleName.length >= 3 && simpleName != trimmed.lowercase()) {
            val fallbackMatches = searchInstalledApps(context, simpleName)
            for (match in fallbackMatches) {
                val matchIntent = pm.getLaunchIntentForPackage(match.packageName)
                if (matchIntent != null) {
                    return matchIntent to match
                }
            }
        }

        return null
    }
}
