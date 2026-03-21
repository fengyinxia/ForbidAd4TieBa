package com.forbidad4tieba.hook.utils

import android.content.Context
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import de.robv.android.xposed.XposedHelpers

private const val AIF_EMPTY_VIEW = "AIF_EMPTY"
private const val AIF_TYPEADAPTER_AD_TYPES = "AIF_AD_TYPES"

fun isAdView(view: View, adClasses: Array<Class<*>>): Boolean {
    for (cls in adClasses) {
        if (cls.isInstance(view)) return true
    }
    return false
}

fun containsAdChild(parent: ViewGroup, adClasses: Array<Class<*>>): Boolean {
    val count = parent.childCount
    for (i in 0 until count) {
        val child = parent.getChildAt(i)
        for (cls in adClasses) {
            if (cls.isInstance(child)) return true
        }
    }
    return false
}

fun castToInt(obj: Any?): Int {
    return if (obj is Int) obj else -1
}

fun getOrCreateAdTypeCache(adapter: Any?): SparseBooleanArray? {
    if (adapter == null) return null
    return try {
        val existing = XposedHelpers.getAdditionalInstanceField(adapter, AIF_TYPEADAPTER_AD_TYPES)
        if (existing is SparseBooleanArray) {
            existing
        } else {
            val created = SparseBooleanArray()
            XposedHelpers.setAdditionalInstanceField(adapter, AIF_TYPEADAPTER_AD_TYPES, created)
            created
        }
    } catch (_: Throwable) {
        null
    }
}

fun safeGetItemViewType(adapter: Any, position: Int): Int {
    return try {
        val vt = XposedHelpers.callMethod(adapter, "getItemViewType", position)
        if (vt is Int) vt else -1
    } catch (_: Throwable) {
        -1
    }
}

fun resolveContext(args: Array<Any?>): Context? {
    if (args.size > 2 && args[2] is ViewGroup) return (args[2] as ViewGroup).context
    if (args.size > 1 && args[1] is View) return (args[1] as View).context
    return null
}

fun isOurEmptyView(view: View): Boolean {
    return try {
        java.lang.Boolean.TRUE == XposedHelpers.getAdditionalInstanceField(view, AIF_EMPTY_VIEW)
    } catch (_: Throwable) {
        false
    }
}

fun obtainEmptyView(context: Context?, reuseIfPossible: View?): View? {
    if (context == null) return reuseIfPossible
    if (reuseIfPossible != null && isOurEmptyView(reuseIfPossible)) return reuseIfPossible

    val emptyView = View(context)
    try {
        XposedHelpers.setAdditionalInstanceField(emptyView, AIF_EMPTY_VIEW, java.lang.Boolean.TRUE)
    } catch (_: Throwable) {}
    configureEmptyView(emptyView)
    return emptyView
}

private fun configureEmptyView(view: View) {
    try {
        view.visibility = View.GONE
        view.minimumHeight = 0
        view.minimumWidth = 0
        view.setPadding(0, 0, 0, 0)
        val lp = view.layoutParams
        if (lp !is AbsListView.LayoutParams || lp.width != 0 || lp.height != 0) {
            view.layoutParams = AbsListView.LayoutParams(0, 0)
        }
    } catch (_: Throwable) {}
}

fun filterOutTemplateKeys(list: List<*>?, keysToRemove: Set<String>): List<*>? {
    if (list == null || keysToRemove.isEmpty()) return list
    val size = list.size
    if (size == 0) return list

    var filtered: ArrayList<Any?>? = null
    for (i in 0 until size) {
        val item: Any? = try {
            list[i]
        } catch (_: Throwable) {
            null
        }
        val key = getTemplateKey(item)
        if (key != null && key in keysToRemove) {
            if (filtered == null) {
                filtered = ArrayList(size - 1)
                for (j in 0 until i) {
                    try {
                        filtered.add(list[j])
                    } catch (_: Throwable) {}
                }
            }
        } else if (filtered != null) {
            filtered.add(item)
        }
    }
    return filtered ?: list
}

private fun getTemplateKey(item: Any?): String? {
    if (item == null) return null
    return try {
        val key = XposedHelpers.callMethod(item, "b")
        if (key is String) key else null
    } catch (_: Throwable) {
        null
    }
}
