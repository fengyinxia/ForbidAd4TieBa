package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

object StrategyAdHook {

    fun hook(cl: ClassLoader) {
        // 1. Strategy layer: fake VIP ad-free status
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdVipClose", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdVipClose", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdIsOpen", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdIsOpen", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberType", 2)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "G1", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "J1", 1)
        hookReturnConstant(cl, "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt", "a", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "i0", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "l", 0)

        // 2. SDK switches & crash prevention
        hookSwitchManager(cl)
        hookZga(cl)
    }

    private fun hookReturnConstant(cl: ClassLoader, className: String, methodName: String, value: Any) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isAdBlockEnabled) param.result = value
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        try {
            val blockedKeys = HashSet<String>(4).apply {
                add("ad_baichuan_open")
                add("bear_wxb_download")
                add("pref_key_fun_ad_sdk_enable")
            }

            XposedHelpers.findAndHookMethod(
                "com.baidu.adp.lib.featureSwitch.SwitchManager", cl, "findType", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        if (param.args[0] in blockedKeys) {
                            param.result = 0
                        }
                    }
                })
        } catch (_: Throwable) {}
    }

    private fun hookZga(cl: ClassLoader) {
        try {
            val safeStringHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == null) param.result = ""
                }
            }
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "d", String::class.java, safeStringHook)
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "f", String::class.java, safeStringHook)
        } catch (_: Throwable) {}
    }
}
