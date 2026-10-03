package com.focusgoal.app.focus

import android.view.accessibility.AccessibilityNodeInfo

/**
 * One blockable part of an app, e.g. YouTube Shorts. When it's on screen the blocker presses Back,
 * so the rest of the app (normal videos, chats, feed) keeps working.
 *
 * Apps don't publish how their screens are built, so detection uses a few independent signals and
 * any one of them is enough:
 *  - [viewIds]: Android view IDs that only exist on that screen (most reliable, but apps rename
 *    them in updates);
 *  - [selectedTabs]: the app's bottom tab with this label is the selected one;
 *  - [screenHints]: part of the Activity class name of a dedicated full-screen viewer.
 * If an app update breaks a rule, this file is the one place to fix it.
 */
data class FeatureRule(
    val id: String,
    val packageName: String,
    val appName: String,
    val featureName: String,
    val viewIds: List<String> = emptyList(),
    val selectedTabs: List<String> = emptyList(),
    val screenHints: List<String> = emptyList(),
    /** Detection is a best guess for this app; it may miss some screens. */
    val experimental: Boolean = false,
)

object FeatureRules {

    val ALL = listOf(
        FeatureRule(
            id = "youtube_shorts",
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            featureName = "Shorts",
            viewIds = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "reel_watch_fragment_root"),
            selectedTabs = listOf("Shorts"),
        ),
        FeatureRule(
            id = "instagram_reels",
            packageName = "com.instagram.android",
            appName = "Instagram",
            featureName = "Reels",
            viewIds = listOf("clips_viewer_view_pager", "clips_viewer_container", "root_clips_layout"),
            selectedTabs = listOf("Reels"),
        ),
        FeatureRule(
            id = "instagram_stories",
            packageName = "com.instagram.android",
            appName = "Instagram",
            featureName = "Stories",
            // Instagram calls stories "reels" internally.
            viewIds = listOf("reel_viewer_root", "reel_viewer_container", "reel_viewer_media_container"),
        ),
        FeatureRule(
            id = "facebook_reels",
            packageName = "com.facebook.katana",
            appName = "Facebook",
            featureName = "Reels",
            selectedTabs = listOf("Reels"),
            screenHints = listOf("Reels", "Clips"),
            experimental = true,
        ),
        FeatureRule(
            id = "snapchat_spotlight",
            packageName = "com.snapchat.android",
            appName = "Snapchat",
            featureName = "Spotlight",
            selectedTabs = listOf("Spotlight"),
            experimental = true,
        ),
        FeatureRule(
            id = "whatsapp_status",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            featureName = "Status",
            screenHints = listOf("StatusPlayback"),
        ),
        FeatureRule(
            id = "whatsapp_channels",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            featureName = "Channels",
            screenHints = listOf("Newsletter"),
            experimental = true,
        ),
    )

    private val byId = ALL.associateBy { it.id }
    val packages: Set<String> = ALL.map { it.packageName }.toSet()

    fun get(id: String): FeatureRule? = byId[id]

    fun forPackage(packageName: String, enabledIds: Set<String>): List<FeatureRule> =
        ALL.filter { it.packageName == packageName && it.id in enabledIds }

    /** True if [rule]'s screen is showing. [activityClass] is the app's current Activity class name. */
    fun isShowing(rule: FeatureRule, root: AccessibilityNodeInfo, activityClass: String?): Boolean {
        if (activityClass != null && rule.screenHints.any { activityClass.contains(it, ignoreCase = true) }) return true

        for (id in rule.viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("${rule.packageName}:id/$id")
            if (nodes.any { it.isVisibleToUser }) return true
        }

        for (label in rule.selectedTabs) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val onTab = nodes.any { node ->
                node.isSelected && node.isVisibleToUser && labelMatches(node, label)
            }
            if (onTab) return true
        }
        return false
    }

    /** Tab labels look like "Shorts" or "Reels, tab 2 of 5" — avoid matching random text that contains the word. */
    private fun labelMatches(node: AccessibilityNodeInfo, label: String): Boolean {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        return text.equals(label, ignoreCase = true) ||
            desc.equals(label, ignoreCase = true) ||
            desc?.startsWith("$label,", ignoreCase = true) == true ||
            desc?.startsWith("$label ", ignoreCase = true) == true
    }
}
