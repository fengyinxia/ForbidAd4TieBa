package com.forbidad4tieba.hook.feature.web

import android.webkit.WebView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.core.Constants.TARGET_PACKAGE
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.Locale

object EnterForumWebHook {
    private const val TAG_LAST_FILTER_URL = 2113929233
    private const val FILTER_JS = "javascript:(function() {if (window.__tbhook_injected) return;window.__tbhook_injected = true;var interval = setInterval(function() {  var containers = document.getElementsByClassName('pull-con animation-top');  if (containers.length > 0) {    var foundLikeForum = false;    for (var i = 0; i < containers.length; i++) {      var container = containers[i];      var children = container.children;      for (var j = children.length - 1; j >= 0; j--) {        var child = children[j];        if (child.querySelector('.like-forum') !== null) {          foundLikeForum = true;        } else {          child.style.display = 'none';        }      }    }    if (foundLikeForum) { clearInterval(interval); }  }}, 200);setTimeout(function(){ clearInterval(interval); window.__tbhook_injected = false; }, 5000);})();"

    fun hook() {
        try {
            XposedBridge.hookAllMethods(WebView::class.java, "loadUrl", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isEnterForumWebFilterEnabled) return
                    val url = (param.args?.firstOrNull() as? String) ?: return
                    if (!shouldInject(url)) return

                    val webView = param.thisObject as? WebView ?: return
                    val context = webView.context ?: return
                    if (context.packageName != TARGET_PACKAGE) return

                    val normalizedUrl = url.substringBefore('#')
                    if (webView.getTag(TAG_LAST_FILTER_URL) == normalizedUrl) return

                    webView.setTag(TAG_LAST_FILTER_URL, normalizedUrl)
                    webView.post {
                        try {
                            webView.evaluateJavascript(FILTER_JS, null)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: WebView inject failed: ${t.message}")
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook WebView for EnterForum: ${t.message}")
        }
    }

    private fun shouldInject(url: String): Boolean {
        if (url.regionMatches(0, "javascript:", 0, 11, ignoreCase = true)) return false
        val normalized = url.lowercase(Locale.ROOT)
        return normalized.startsWith("https://tieba.baidu.com/") ||
            normalized.startsWith("http://tieba.baidu.com/") ||
            normalized.startsWith("https://tiebac.baidu.com/") ||
            normalized.startsWith("http://tiebac.baidu.com/")
    }
}
