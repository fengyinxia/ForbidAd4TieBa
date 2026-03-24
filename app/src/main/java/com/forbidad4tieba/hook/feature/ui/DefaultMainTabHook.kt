package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.Constants.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object DefaultMainTabHook {
    fun hook(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.tblauncher.MainTabActivity", cl
            ) ?: return

            XposedHelpers.findAndHookMethod(clazz, "onCreate", android.os.Bundle::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = try {
                        XposedHelpers.callMethod(param.thisObject, "getIntent") as? android.content.Intent
                    } catch (_: Throwable) {
                        null
                    } ?: return
                    if (!intent.hasExtra("locate_type")) {
                        intent.putExtra("locate_type", 1)
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook default main tab: ${t.message}")
        }
    }
}
