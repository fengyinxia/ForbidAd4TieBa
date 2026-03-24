package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.utils.squashView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object HomeTabHook {
    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        hookHomeTopBar(cl)
        hookHomeSearchBar(cl)
        hookHomePagerScrollable(cl)

        // 保留 symbols 参数和设置页支持判断链路，不再沿旧的“仅保留推荐”逻辑处理 tab 列表
        if (symbols.homeTabClass == null && symbols.homeTabRebuildMethod == null && symbols.homeTabListField == null) {
            return
        }
    }

    private fun hookHomeTopBar(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.homepage.framework.indicator.ScrollTabBarLayout", cl
            ) ?: return

            val hideChildrenHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    try {
                        val host = param.thisObject ?: return
                        val leftSlot = XposedHelpers.getObjectField(host, "d") as? View
                        val rightSlot = XposedHelpers.getObjectField(host, "e") as? View
                        val tabBarView = XposedHelpers.getObjectField(host, "c") as? View
                        leftSlot?.let(::squashView)
                        rightSlot?.let(::squashView)
                        tabBarView?.let(::squashView)
                        preserveStatusBarInset(host)
                    } catch (_: Throwable) {
                    }
                }
            }

            XposedBridge.hookAllConstructors(clazz, hideChildrenHook)
        } catch (_: Throwable) {
        }
    }

    private fun preserveStatusBarInset(host: Any) {
        val hostView = host as? View ?: return
        val statusBarHeight = resolveStatusBarHeight(hostView)
        if (statusBarHeight <= 0) return

        try {
            hostView.minimumHeight = statusBarHeight
            hostView.layoutParams?.let {
                it.height = statusBarHeight
                hostView.layoutParams = it
            }
        } catch (_: Throwable) {
        }

        try {
            val rootContainer = XposedHelpers.getObjectField(host, "a") as? View ?: return
            rootContainer.minimumHeight = statusBarHeight
            rootContainer.layoutParams?.let {
                it.height = statusBarHeight
                rootContainer.layoutParams = it
            }
        } catch (_: Throwable) {
        }
    }

    private fun resolveStatusBarHeight(view: View): Int {
        return try {
            val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId != 0) view.resources.getDimensionPixelSize(resId) else 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun hookHomeSearchBar(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.homepage.personalize.PersonalizeHeaderViewController", cl
            ) ?: return

            val squashHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    try {
                        val container = XposedHelpers.getObjectField(param.thisObject, "d") as? View
                        if (container != null) {
                            squashView(container)
                            return
                        }
                        val searchBox = XposedHelpers.getObjectField(param.thisObject, "c") as? View
                        if (searchBox != null) {
                            squashView(searchBox)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }

            XposedBridge.hookAllConstructors(clazz, squashHook)
            XposedBridge.hookAllMethods(clazz, "k", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    squashView(param.result as? View ?: return)
                }
            })
            XposedBridge.hookAllMethods(clazz, "j", squashHook)
        } catch (_: Throwable) {
        }
    }

    private fun hookHomePagerScrollable(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.homepage.framework.indicator.ScrollFragmentTabHost", cl
            ) ?: return

            XposedHelpers.findAndHookMethod(clazz, "f0", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    try {
                        val pager = XposedHelpers.getObjectField(param.thisObject, "k") ?: return
                        XposedHelpers.callMethod(pager, "setScrollable", false)
                    } catch (_: Throwable) {
                    }
                }
            })
        } catch (_: Throwable) {
        }
    }
}
