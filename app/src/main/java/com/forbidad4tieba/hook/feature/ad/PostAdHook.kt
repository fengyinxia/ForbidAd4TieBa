package com.forbidad4tieba.hook.feature.ad

import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.utils.castToInt
import com.forbidad4tieba.hook.utils.containsAdChild
import com.forbidad4tieba.hook.utils.getOrCreateAdTypeCache
import com.forbidad4tieba.hook.utils.isAdView
import com.forbidad4tieba.hook.utils.isOurEmptyView
import com.forbidad4tieba.hook.utils.obtainEmptyView
import com.forbidad4tieba.hook.utils.resolveContext
import com.forbidad4tieba.hook.utils.safeGetItemViewType
import com.forbidad4tieba.hook.utils.squashView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object PostAdHook {

    fun hook(cl: ClassLoader, adClasses: Array<Class<*>>) {
        try {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.baidu.adp.widget.ListView.TypeAdapter", cl),
                "getView",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (!ConfigManager.isAdBlockEnabled || args == null || args.size < 3) return

                        val cache = getOrCreateAdTypeCache(param.thisObject) ?: return
                        val position = castToInt(args[0])
                        if (position < 0) return

                        val viewType = safeGetItemViewType(param.thisObject, position)
                        if (viewType < 0 || !cache.get(viewType)) return

                        val ctx = resolveContext(args) ?: return
                        val convertView = args[1] as? View
                        param.result = obtainEmptyView(ctx, convertView)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        val result = param.result as? View ?: return
                        if (isOurEmptyView(result)) return

                        val args = param.args
                        val position = castToInt(args[0])
                        val viewType = if (position >= 0) safeGetItemViewType(param.thisObject, position) else -1
                        val cache = getOrCreateAdTypeCache(param.thisObject)

                        if (cache != null && viewType >= 0 && cache.get(viewType)) return

                        var isAd = isAdView(result, adClasses)
                        if (!isAd && result is ViewGroup) {
                            isAd = containsAdChild(result, adClasses)
                        }

                        if (isAd) {
                            if (cache != null && viewType >= 0) cache.put(viewType, true)
                            val convertView = if (args.size > 1 && args[1] is View) args[1] as View else null
                            param.result = obtainEmptyView(result.context, convertView)
                        }
                    }
                })

            try {
                XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(
                        "com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter", cl
                    ),
                    "onCreateViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!ConfigManager.isAdBlockEnabled) return
                            val holder = param.result ?: return
                            try {
                                val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View
                                if (itemView != null) squashView(itemView)
                            } catch (_: Throwable) {}
                        }
                    })
            } catch (_: Throwable) {}

            XposedBridge.log("$TAG: Post page ad view interception installed")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook post page ads: ${t.message}")
        }
    }
}
