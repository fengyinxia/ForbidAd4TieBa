package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object BottomTabHook {
    fun hook(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tbadk.abtest.UbsABTestHelper", cl,
                "retailStorePage", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (ConfigManager.isBottomTabSimplifyEnabled) param.result = false
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook bottom tabs: ${t.message}")
        }
    }
}
