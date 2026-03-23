package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {
    private const val PREFS_NAME = "tiebahook_settings"
    private const val KEY_BLOCK_AD = "block_ad"
    private const val KEY_BLOCK_LIVE = "block_live"
    private const val KEY_BLOCK_MY_FORUM = "block_my_forum"
    private const val KEY_SIMPLIFY_HOME_TABS = "simplify_home_tabs"
    private const val KEY_SIMPLIFY_BOTTOM_TABS = "simplify_bottom_tabs"
    private const val KEY_PURIFY_MY_PAGE = "purify_my_page"
    private const val KEY_FILTER_ENTER_FORUM_WEB = "filter_enter_forum_web"
    private const val KEY_PURIFY_ENTER_FORUM_PAGE = "purify_enter_forum_page"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    private var blockAdEnabled: Boolean = true
    @Volatile
    private var blockLiveEnabled: Boolean = true
    @Volatile
    private var blockMyForumEnabled: Boolean = true
    @Volatile
    private var simplifyHomeTabsEnabled: Boolean = true
    @Volatile
    private var simplifyBottomTabsEnabled: Boolean = true
    @Volatile
    private var purifyMyPageEnabled: Boolean = true
    @Volatile
    private var purifyEnterForumPageEnabled: Boolean = true

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        syncKey(prefs, key)
    }

    fun init(context: Context) {
        if (cachedPrefs != null) return
        val appContext = context.applicationContext ?: context
        synchronized(this) {
            if (cachedPrefs == null) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
                syncAll(prefs)
                cachedPrefs = prefs
            }
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        init(context)
        return cachedPrefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun syncAll(prefs: SharedPreferences) {
        blockAdEnabled = prefs.getBoolean(KEY_BLOCK_AD, true)
        blockLiveEnabled = prefs.getBoolean(KEY_BLOCK_LIVE, true)
        blockMyForumEnabled = readPurifyEnterForumPage(prefs)
        simplifyHomeTabsEnabled = prefs.getBoolean(KEY_SIMPLIFY_HOME_TABS, true)
        simplifyBottomTabsEnabled = prefs.getBoolean(KEY_SIMPLIFY_BOTTOM_TABS, true)
        purifyMyPageEnabled = prefs.getBoolean(KEY_PURIFY_MY_PAGE, true)
        purifyEnterForumPageEnabled = readPurifyEnterForumPage(prefs)
    }

    private fun syncKey(prefs: SharedPreferences, key: String?) {
        when (key) {
            null -> syncAll(prefs)
            KEY_BLOCK_AD -> blockAdEnabled = prefs.getBoolean(KEY_BLOCK_AD, true)
            KEY_BLOCK_LIVE -> blockLiveEnabled = prefs.getBoolean(KEY_BLOCK_LIVE, true)
            KEY_BLOCK_MY_FORUM,
            KEY_FILTER_ENTER_FORUM_WEB,
            KEY_PURIFY_ENTER_FORUM_PAGE -> {
                val enabled = readPurifyEnterForumPage(prefs)
                blockMyForumEnabled = enabled
                purifyEnterForumPageEnabled = enabled
            }
            KEY_SIMPLIFY_HOME_TABS -> simplifyHomeTabsEnabled = prefs.getBoolean(KEY_SIMPLIFY_HOME_TABS, true)
            KEY_SIMPLIFY_BOTTOM_TABS -> simplifyBottomTabsEnabled = prefs.getBoolean(KEY_SIMPLIFY_BOTTOM_TABS, true)
            KEY_PURIFY_MY_PAGE -> purifyMyPageEnabled = prefs.getBoolean(KEY_PURIFY_MY_PAGE, true)
        }
    }

    private fun readPurifyEnterForumPage(prefs: SharedPreferences): Boolean {
        return when {
            prefs.contains(KEY_PURIFY_ENTER_FORUM_PAGE) -> prefs.getBoolean(KEY_PURIFY_ENTER_FORUM_PAGE, true)
            prefs.contains(KEY_BLOCK_MY_FORUM) -> prefs.getBoolean(KEY_BLOCK_MY_FORUM, true)
            prefs.contains(KEY_FILTER_ENTER_FORUM_WEB) -> prefs.getBoolean(KEY_FILTER_ENTER_FORUM_WEB, true)
            else -> true
        }
    }

    val isAdBlockEnabled: Boolean get() = blockAdEnabled
    val isLiveBlockEnabled: Boolean get() = blockLiveEnabled
    val isMyForumBlockEnabled: Boolean get() = blockMyForumEnabled
    val isHomeTabSimplifyEnabled: Boolean get() = simplifyHomeTabsEnabled
    val isBottomTabSimplifyEnabled: Boolean get() = simplifyBottomTabsEnabled
    val isMyPagePurifyEnabled: Boolean get() = purifyMyPageEnabled
    val isEnterForumPagePurifyEnabled: Boolean get() = purifyEnterForumPageEnabled
    val isEnterForumWebFilterEnabled: Boolean get() = purifyEnterForumPageEnabled
}
