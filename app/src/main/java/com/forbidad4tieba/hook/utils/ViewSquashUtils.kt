package com.forbidad4tieba.hook.utils

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import de.robv.android.xposed.XposedHelpers

private const val AIF_SQUASH_STATE = "AIF_SQUASH"
private const val LP_UNSET = Int.MIN_VALUE
private val SQUASH_MARKER = Any()

fun findAncestorViewByClassName(view: View?, className: String?): View? {
    if (view == null || className == null) return null
    try {
        var parent: ViewParent? = view.parent
        while (parent is View) {
            if (className == parent.javaClass.name) return parent
            parent = parent.parent
        }
    } catch (_: Throwable) {}
    return null
}

fun squashView(view: View?) {
    if (view == null) return
    if (view.getTag(android.R.id.icon) === SQUASH_MARKER) return
    try {
        view.visibility = View.GONE
        view.minimumHeight = 0
        view.minimumWidth = 0
        view.setPadding(0, 0, 0, 0)
        val lp = view.layoutParams
        if (lp != null) {
            lp.width = 0
            lp.height = 0
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.setMargins(0, 0, 0, 0)
            }
            view.layoutParams = lp
        }
        view.setTag(android.R.id.icon, SQUASH_MARKER)
    } catch (_: Throwable) {}
}

fun squashAncestorFeedCard(v: View) {
    val feedCard = findAncestorViewByClassName(v, "com.baidu.tieba.feed.card.FeedCardView")
    if (feedCard != null) squashViewRemembering(feedCard)
}

fun squashViewRemembering(view: View?) {
    if (view == null) return
    try {
        val existing = XposedHelpers.getAdditionalInstanceField(view, AIF_SQUASH_STATE)
        var state = existing as? SquashState

        if (state == null || !state.squashed) {
            state = SquashState(view)
            XposedHelpers.setAdditionalInstanceField(view, AIF_SQUASH_STATE, state)
        }
        state.squashed = true
    } catch (_: Throwable) {}
    squashView(view)
}

fun restoreViewIfSquashed(view: View?) {
    if (view == null) return
    try {
        val obj = XposedHelpers.getAdditionalInstanceField(view, AIF_SQUASH_STATE)
        if (obj !is SquashState) return
        if (!obj.squashed) return
        obj.restore(view)
        obj.squashed = false
        view.setTag(android.R.id.icon, null)
    } catch (_: Throwable) {}
}

private class SquashState(view: View) {
    val visibility: Int = view.visibility
    val minWidth: Int = view.minimumWidth
    val minHeight: Int = view.minimumHeight
    val padL: Int = view.paddingLeft
    val padT: Int = view.paddingTop
    val padR: Int = view.paddingRight
    val padB: Int = view.paddingBottom
    val lpW: Int
    val lpH: Int
    val mL: Int
    val mT: Int
    val mR: Int
    val mB: Int
    var squashed: Boolean = false

    init {
        val lp = view.layoutParams
        if (lp != null) {
            lpW = lp.width
            lpH = lp.height
            if (lp is ViewGroup.MarginLayoutParams) {
                mL = lp.leftMargin
                mT = lp.topMargin
                mR = lp.rightMargin
                mB = lp.bottomMargin
            } else {
                mL = LP_UNSET
                mT = LP_UNSET
                mR = LP_UNSET
                mB = LP_UNSET
            }
        } else {
            lpW = LP_UNSET
            lpH = LP_UNSET
            mL = LP_UNSET
            mT = LP_UNSET
            mR = LP_UNSET
            mB = LP_UNSET
        }
    }

    fun restore(view: View) {
        try {
            view.visibility = visibility
            view.minimumWidth = minWidth
            view.minimumHeight = minHeight
            view.setPadding(padL, padT, padR, padB)

            val lp = view.layoutParams
            if (lp != null) {
                if (lpW != LP_UNSET) lp.width = lpW
                if (lpH != LP_UNSET) lp.height = lpH
                if (lp is ViewGroup.MarginLayoutParams && mL != LP_UNSET) {
                    lp.setMargins(mL, mT, mR, mB)
                }
                view.layoutParams = lp
            } else {
                view.requestLayout()
            }
        } catch (_: Throwable) {}
    }
}
