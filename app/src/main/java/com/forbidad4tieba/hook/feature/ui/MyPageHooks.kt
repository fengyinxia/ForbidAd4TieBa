package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.utils.squashView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object MyPageHooks {

    private enum class RootMode {
        FIELD_ONLY,
        SELF_OR_FIELD,
    }

    private data class HookSpec(
        val className: String,
        val methodName: String,
        val rootFieldName: String,
        val rootMode: RootMode,
        val label: String,
    )

    private val hookSpecs = listOf(
        HookSpec(
            className = "com.baidu.tieba.personCenter.view.PersonCenterMemberCardView",
            methodName = "o",
            rootFieldName = "a",
            rootMode = RootMode.FIELD_ONLY,
            label = "vip card",
        ),
        HookSpec(
            className = "com.baidu.tieba.personpage.view.PersonGameView",
            methodName = "i",
            rootFieldName = "b",
            rootMode = RootMode.SELF_OR_FIELD,
            label = "game zone",
        ),
        HookSpec(
            className = "com.baidu.tieba.personpage.view.PersonPageBannerView",
            methodName = "e",
            rootFieldName = "a",
            rootMode = RootMode.SELF_OR_FIELD,
            label = "banner",
        ),
        HookSpec(
            className = "com.baidu.tieba.personpage.view.PersonCommerceView",
            methodName = "a",
            rootFieldName = "a",
            rootMode = RootMode.SELF_OR_FIELD,
            label = "wallet",
        ),
    )

    fun hook(cl: ClassLoader) {
        hookSpecs.forEach { hookSimpleSquash(cl, it) }
    }

    private fun hookSimpleSquash(cl: ClassLoader, spec: HookSpec) {
        try {
            val clazz = XposedHelpers.findClassIfExists(spec.className, cl) ?: return

            val squashHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isMyPagePurifyEnabled) return
                    resolveRootView(param.thisObject, spec)?.let(::squashView)
                }
            }

            XposedBridge.hookAllConstructors(clazz, squashHook)
            XposedBridge.hookAllMethods(clazz, spec.methodName, squashHook)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook my-page ${spec.label}: ${t.message}")
        }
    }

    private fun resolveRootView(instance: Any?, spec: HookSpec): View? {
        try {
            return when (spec.rootMode) {
                RootMode.FIELD_ONLY -> XposedHelpers.getObjectField(instance, spec.rootFieldName) as? View
                RootMode.SELF_OR_FIELD -> when (instance) {
                    is View -> instance
                    else -> XposedHelpers.getObjectField(instance, spec.rootFieldName) as? View
                }
            }
        } catch (_: Throwable) {
            return null
        }
    }
}
