package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object HomeTabHook {
    private val listFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val typeFieldCache = ConcurrentHashMap<Class<*>, Field>()

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val homeClass = symbols.homeTabClass ?: return
        val rebuildMethod = symbols.homeTabRebuildMethod ?: return
        val listField = symbols.homeTabListField ?: return
        try {
            XposedHelpers.findAndHookMethod(homeClass, cl, rebuildMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    val list = resolveMutableListField(param.thisObject, listField) ?: return
                    val it = list.iterator()
                    while (it.hasNext()) {
                        val pm6 = it.next()
                        if (pm6 != null) {
                            val type = readItemType(pm6)
                            if (type != 0 && type != 1) it.remove()
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook home tabs($homeClass.$rebuildMethod): ${t.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveMutableListField(target: Any?, preferredFieldName: String): MutableList<Any?>? {
        if (target == null) return null
        val clazz = target.javaClass
        listFieldCache[clazz]?.let {
            return try {
                it.get(target) as? MutableList<Any?>
            } catch (_: Throwable) {
                null
            }
        }

        try {
            val directField = clazz.getDeclaredField(preferredFieldName)
            directField.isAccessible = true
            listFieldCache[clazz] = directField
            return directField.get(target) as? MutableList<Any?>
        } catch (_: Throwable) {
        }

        val fallback = clazz.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        return try {
            fallback.isAccessible = true
            listFieldCache[clazz] = fallback
            fallback.get(target) as? MutableList<Any?>
        } catch (_: Throwable) {
            null
        }
    }

    private fun readItemType(item: Any): Int {
        val clazz = item.javaClass
        typeFieldCache[clazz]?.let {
            return try {
                it.getInt(item)
            } catch (_: Throwable) {
                -1
            }
        }

        try {
            val directField = clazz.getDeclaredField("a")
            directField.isAccessible = true
            typeFieldCache[clazz] = directField
            return directField.getInt(item)
        } catch (_: Throwable) {
        }

        val fallback = clazz.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType } ?: return -1
        return try {
            fallback.isAccessible = true
            typeFieldCache[clazz] = fallback
            fallback.getInt(item)
        } catch (_: Throwable) {
            -1
        }
    }
}
