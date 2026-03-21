package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.utils.filterOutTemplateKeys
import com.forbidad4tieba.hook.utils.restoreViewIfSquashed
import com.forbidad4tieba.hook.utils.squashAncestorFeedCard
import com.forbidad4tieba.hook.utils.squashView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object FeedAdHook {

    fun hook(cl: ClassLoader, adClasses: Array<Class<*>>) {
        hookFeedAdViews(cl, adClasses)
        hookFeedLiveCards(cl)
        hookFeedMyForumCards(cl)
    }

    private fun hookFeedAdViews(cl: ClassLoader, adClasses: Array<Class<*>>) {
        hookFeedAppAdCard(cl)
        hookFeedAdViewHolder(cl)

        val squashOnAdBlock = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (ConfigManager.isAdBlockEnabled && param.thisObject is View) {
                    squashView(param.thisObject as View)
                }
            }
        }

        val forceGone = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isAdBlockEnabled) return
                val args = param.args
                if (args != null && args.isNotEmpty() && args[0] is Int) {
                    args[0] = View.GONE
                }
            }
        }

        for (clazz in adClasses) {
            try {
                XposedBridge.hookAllConstructors(clazz, squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "setVisibility", forceGone)
            } catch (_: Throwable) {}
        }
    }

    private fun hookFeedLiveCards(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.list.TemplateAdapter", cl, "setList", List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (args == null || args.isEmpty()) return
                        val arg0 = args[0]
                        if (arg0 !is List<*>) return

                        val blockLive = ConfigManager.isLiveBlockEnabled
                        val blockForum = ConfigManager.isMyForumBlockEnabled
                        if (!blockLive && !blockForum) return

                        val keysToRemove = HashSet<String>(2)
                        if (blockLive) keysToRemove.add("live_card")
                        if (blockForum) keysToRemove.add("card_feed_my_forum")

                        val filtered = filterOutTemplateKeys(arg0, keysToRemove)
                        if (filtered !== arg0) args[0] = filtered
                    }
                })
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.card.FeedCardView", cl, "w", "com.baidu.tieba.y89",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isLiveBlockEnabled) return
                        if (param.thisObject is View) {
                            restoreViewIfSquashed(param.thisObject as View)
                        }
                    }
                })
        } catch (_: Throwable) {}

        try {
            val cardLiveViewClass = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardLiveView", cl
            ) ?: return

            XposedBridge.hookAllConstructors(cardLiveViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isLiveBlockEnabled || param.thisObject !is View) return
                    val liveView = param.thisObject as View

                    liveView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            squashAncestorFeedCard(v)
                        }

                        override fun onViewDetachedFromWindow(v: View) {}
                    })

                    if (liveView.isAttachedToWindow) squashAncestorFeedCard(liveView)
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookFeedMyForumCards(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardFeedMyForumView", cl
            ) ?: return

            val squashHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isMyForumBlockEnabled && param.thisObject is View) {
                        squashView(param.thisObject as View)
                    }
                }
            }

            XposedBridge.hookAllConstructors(clazz, squashHook)
            XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashHook)
            XposedBridge.hookAllMethods(clazz, "onMeasure", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isMyForumBlockEnabled) return
                    if (param.thisObject is View) squashView(param.thisObject as View)
                    param.result = null
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookFeedAdViewHolder(cl: ClassLoader) {
        try {
            XposedBridge.hookAllConstructors(
                XposedHelpers.findClass("com.baidu.tieba.funad.adapter.FeedAdViewHolder", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        try {
                            val itemView = XposedHelpers.getObjectField(param.thisObject, "itemView") as? View
                            if (itemView != null) squashView(itemView)
                        } catch (_: Throwable) {}
                    }
                })
        } catch (_: Throwable) {}
    }

    private fun hookFeedAppAdCard(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.recapp.lego.view.AdCardBaseView", cl, "x",
                "com.baidu.tieba.recapp.lego.model.AdCard",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled || param.args[0] == null) return
                        try {
                            val appInfo = XposedHelpers.callMethod(param.args[0], "getAdvertAppInfo")
                            if (appInfo != null && XposedHelpers.callMethod(appInfo, "isAppAdvert") as Boolean) {
                                val self = param.thisObject as? View ?: return
                                squashView(self)
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (_: Throwable) {}
    }
}
