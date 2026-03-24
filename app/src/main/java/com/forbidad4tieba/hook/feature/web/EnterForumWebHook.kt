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
    private const val DEBUG_DELAY_MS = 1500L
    private const val FILTER_JS = "javascript:(function(){if(window.__tbhook_injected)return;window.__tbhook_injected=true;var applyFilter=function(){var containers=document.getElementsByClassName('pull-con animation-top');for(var i=0;i<containers.length;i++){var container=containers[i];var keep=null;var children=Array.prototype.slice.call(container.children);for(var j=0;j<children.length;j++){var child=children[j];if(child.querySelector('.like-forum')!==null){keep=child;break;}}for(var k=0;k<children.length;k++){if(children[k]!==keep){children[k].style.display='none';}}}};applyFilter();var interval=setInterval(applyFilter,200);var observer=new MutationObserver(function(){applyFilter();});observer.observe(document.body,{childList:true,subtree:true});setTimeout(function(){clearInterval(interval);observer.disconnect();window.__tbhook_injected=false;},10000);})();"
    private const val DEBUG_JS = "(function(){try{var like=document.querySelector('.like-forum');var buttons=[];if(like){var nodes=like.querySelectorAll('*');for(var i=0;i<nodes.length;i++){var text=(nodes[i].textContent||'').trim();if(text==='展开'||text==='更多'||text==='全部'||text==='查看全部'){buttons.push(text);}}}var payload={title:document.title,href:location.href,hasLikeForum:!!like,expandButtons:buttons};return JSON.stringify(payload);}catch(e){return JSON.stringify({error:String(e)})}})();"
    private const val EXPAND_JS = "javascript:(function(){try{if(window.__tbhook_forumtab_expanded)return 'SKIPPED';var like=document.querySelector('.like-forum');if(!like)return 'NO_LIKE_FORUM';var nodes=like.querySelectorAll('*');var fallback=null;for(var i=0;i<nodes.length;i++){var node=nodes[i];var text=(node.textContent||'').trim();if(text==='展开'){window.__tbhook_forumtab_expanded=true;node.click();return 'CLICKED:展开';}if(fallback===null&&(text==='全部'||text==='查看全部')){fallback=node;}}if(fallback){window.__tbhook_forumtab_expanded=true;fallback.click();return 'CLICKED:'+((fallback.textContent||'').trim());}return 'NO_BUTTON';}catch(e){return 'ERROR:'+String(e);}})();"

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
                    XposedBridge.log("$TAG: EnterForum loadUrl=$normalizedUrl")
                    webView.post {
                        try {
                            if (shouldFilter(normalizedUrl)) {
                                webView.evaluateJavascript(FILTER_JS, null)
                            }
                            if (shouldExpand(normalizedUrl)) {
                                webView.postDelayed({
                                    try {
                                        webView.evaluateJavascript(EXPAND_JS) { result ->
                                            XposedBridge.log("$TAG: EnterForum expand=$result")
                                        }
                                    } catch (t: Throwable) {
                                        XposedBridge.log("$TAG: EnterForum expand failed: ${t.message}")
                                    }
                                }, DEBUG_DELAY_MS)
                            }
                            if (shouldDebug(normalizedUrl)) {
                                webView.postDelayed({
                                    try {
                                        webView.evaluateJavascript(DEBUG_JS) { result ->
                                            XposedBridge.log("$TAG: EnterForum debug=$result")
                                        }
                                    } catch (t: Throwable) {
                                        XposedBridge.log("$TAG: EnterForum debug failed: ${t.message}")
                                    }
                                }, DEBUG_DELAY_MS)
                            }
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

    private fun shouldFilter(url: String): Boolean {
        return url.contains("/hybrid-main-forumtab/mainPage/hybrid")
    }

    private fun shouldDebug(url: String): Boolean {
        return url.contains("/hybrid-main-forumtab/mainPage/hybrid")
    }

    private fun shouldExpand(url: String): Boolean {
        return url.contains("/hybrid-main-forumtab/mainPage/hybrid")
    }
}
