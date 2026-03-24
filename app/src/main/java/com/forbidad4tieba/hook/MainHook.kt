package com.forbidad4tieba.hook

import android.app.Application
import android.content.Context
import android.os.Build
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.AD_CLASS_NAMES
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.core.Constants.TARGET_PACKAGE
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.ui.BottomTabHook
import com.forbidad4tieba.hook.feature.ui.DefaultMainTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.MyPageHooks
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.ui.SettingsMenuHook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var symbolHooksInstalled: Boolean = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE || lpparam.processName != TARGET_PACKAGE) return

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java, "attach",
                Context::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = (param.thisObject as? Application) ?: return
                        if (appContext == null) {
                            appContext = application
                            ConfigManager.init(application)
                            logModuleVersion(application)
                        }
                        val context = appContext ?: return
                        if (!markSymbolHooksInstalled()) return
                        try {
                            val symbols = HookSymbolResolver.loadCachedIfUsable(context, lpparam.classLoader)
                                ?: HookSymbols(source = "unsupported")
                            SettingsMenuHook.hook(lpparam.classLoader, symbols)
                            HomeTabHook.hook(lpparam.classLoader, symbols)
                            if (symbols.source == "unsupported") {
                                SettingsMenuHook.ensureInitialUnsupportedHint(lpparam.classLoader)
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: symbol hook init failed: ${t.message}")
                        }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Application.attach: ${t.message}")
        }

        val cl = lpparam.classLoader

        StrategyAdHook.hook(cl)

        // 3. Resolve ad classes once — shared by feed & post hooks
        val adClasses = resolveAdClasses(cl)

        FeedAdHook.hook(cl, adClasses)
        PostAdHook.hook(cl, adClasses)

        DefaultMainTabHook.hook(cl)
        BottomTabHook.hook(cl)
        MyPageHooks.hook(cl)
        EnterForumWebHook.hook()
    }

    // =============================================
    // Ad class resolution (done once at init)
    // =============================================

    private fun resolveAdClasses(cl: ClassLoader): Array<Class<*>> {
        val list = ArrayList<Class<*>>(AD_CLASS_NAMES.size)
        for (name in AD_CLASS_NAMES) {
            val c = XposedHelpers.findClassIfExists(name, cl)
            if (c != null) list.add(c)
        }
        return list.toTypedArray()
    }

    private fun markSymbolHooksInstalled(): Boolean {
        synchronized(this) {
            if (symbolHooksInstalled) return false
            symbolHooksInstalled = true
            return true
        }
    }

    private fun logModuleVersion(context: Context) {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()
            XposedBridge.log("$TAG: initialized. version=${pkg.versionName}($versionCode)")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: initialized. version=unknown (${t.message})")
        }
    }
}
