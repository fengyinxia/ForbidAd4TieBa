package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object DisableUpdateHook {
    fun hook(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tbadk.coreExtra.data.VersionData", cl
            ) ?: return

            XposedHelpers.findAndHookMethod(clazz, "hasNewVer", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isUpdateDisabled) {
                        param.result = false
                    }
                }
            })

            XposedHelpers.findAndHookMethod(clazz, "forceUpdate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isUpdateDisabled) {
                        param.result = false
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook disable update: ${t.message}")
        }
    }
}
