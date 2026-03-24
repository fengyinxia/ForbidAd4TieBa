package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.ScanLogger
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants.TAG
import com.forbidad4tieba.hook.core.Constants.TARGET_PACKAGE
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object SettingsMenuHook {
    private val settingsFieldCache = ConcurrentHashMap<Class<*>, Field>()
    @Volatile
    private var initialScanResumeUnhooks: Array<XC_MethodHook.Unhook>? = null
    @Volatile
    private var initialUnsupportedHintInstalled = false
    @Volatile
    private var initialUnsupportedHintShown = false

    private data class SwitchItem(
        val label: String,
        val prefKey: String,
        val supported: Boolean,
    )

    private fun resolveSwitchChecked(prefs: SharedPreferences, prefKey: String): Boolean {
        return when (prefKey) {
            "purify_enter_forum_page" -> when {
                prefs.contains("purify_enter_forum_page") -> prefs.getBoolean("purify_enter_forum_page", true)
                prefs.contains("block_my_forum") -> prefs.getBoolean("block_my_forum", true)
                prefs.contains("filter_enter_forum_web") -> prefs.getBoolean("filter_enter_forum_web", true)
                else -> true
            }
            else -> prefs.getBoolean(prefKey, true)
        }
    }

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val settingsClass = symbols.settingsClass ?: return
        val settingsInitMethod = symbols.settingsInitMethod ?: return
        val settingsContainerField = symbols.settingsContainerField ?: return
        try {
            val navClass = XposedHelpers.findClassIfExists("com.baidu.tbadk.core.view.NavigationBar", cl) ?: return
            XposedHelpers.findAndHookMethod(
                settingsClass, cl, settingsInitMethod,
                Context::class.java,
                navClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as Context
                        try {
                            val settingsContainer = resolveSettingsContainer(param.thisObject, settingsContainerField)
                            settingsContainer?.setOnLongClickListener {
                                showModuleSettingsDialog(context)
                                true
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook settings menu($settingsClass.$settingsInitMethod): ${t.message}")
        }
    }

    private fun showModuleSettingsDialog(context: Context) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            val density = context.resources.displayMetrics.density
            val padding = (20 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
            }

            val switches = listOf(
                SwitchItem("屏蔽广告", "block_ad", true),
                SwitchItem("屏蔽直播内容", "block_live", true),
                SwitchItem("首页顶栏净化", "simplify_home_tabs", isHomeTabSupported()),
                SwitchItem("屏蔽小卖部Tab", "simplify_bottom_tabs", true),
                SwitchItem("个人页面净化", "purify_my_page", true),
                SwitchItem("进吧页面净化", "purify_enter_forum_page", true),
            )

            val hint = supportHint()
            if (hint != null) {
                root.addView(createHintText(context, hint, padding))
                root.addView(createDivider(context, padding))
            }

            for (i in switches.indices) {
                if (i > 0) root.addView(createDivider(context, padding))
                val item = switches[i]
                val label = if (item.supported) item.label else "${item.label} (当前版本不支持)"
                root.addView(createSwitchRow(context, prefs, label, item.prefKey, padding, item.supported))
            }

            root.addView(createDivider(context, padding))
            root.addView(createActionRow(context, "手动反混淆", padding) {
                startSymbolScanWithDialog(context, context.classLoader)
            })

            val builder = AlertDialog.Builder(
                context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            )
            builder.setTitle("模块设置")
            builder.setView(root)
            builder.setPositiveButton("保存") { _, _ ->
                Toast.makeText(context, "设置已保存，重启应用生效", Toast.LENGTH_SHORT).show()
            }

            val dialog = builder.create()
            dialog.show()

            dialog.window?.let { window ->
                val gd = GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = 16f * density
                }
                window.setBackgroundDrawable(InsetDrawable(gd, (16 * density).toInt()))
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to show settings dialog: ${t.message}")
        }
    }

    private fun createSwitchRow(
        context: Context,
        prefs: SharedPreferences,
        label: String,
        prefKey: String,
        padding: Int,
        supported: Boolean,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padding / 2, 0, padding / 2)
        }

        val tv = TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(if (supported) 0xFF222222.toInt() else 0xFF888888.toInt())
        }
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f))

        @Suppress("DEPRECATION")
        val sw = Switch(context).apply {
            isChecked = if (supported) resolveSwitchChecked(prefs, prefKey) else false
            isEnabled = supported
            setOnCheckedChangeListener { _, isChecked ->
                if (supported) {
                    val editor = prefs.edit().putBoolean(prefKey, isChecked)
                    if (prefKey == "purify_enter_forum_page") {
                        editor.putBoolean("block_my_forum", isChecked)
                        editor.putBoolean("filter_enter_forum_web", isChecked)
                    }
                    editor.apply()
                }
            }
        }
        row.addView(sw)

        return row
    }

    private fun createHintText(context: Context, text: String, padding: Int): View {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, padding / 2)
        }
    }

    private fun createActionRow(
        context: Context,
        label: String,
        padding: Int,
        action: () -> Unit,
    ): View {
        return TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFF1A73E8.toInt())
            setPadding(0, padding / 2, 0, padding / 2)
            setOnClickListener { action() }
        }
    }

    private fun createDivider(context: Context, padding: Int): View {
        val divider = View(context)
        divider.setBackgroundColor(0xFFEEEEEE.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        lp.setMargins(0, padding / 4, 0, padding / 4)
        divider.layoutParams = lp
        return divider
    }

    private fun isHomeTabSupported(): Boolean {
        val symbols = HookSymbolResolver.getMemorySymbols()
        return symbols?.homeTabClass != null &&
            symbols.homeTabRebuildMethod != null &&
            symbols.homeTabListField != null
    }

    private fun supportHint(): String? {
        return when (HookSymbolResolver.getMemorySymbols()?.source) {
            "partial" -> "当前贴吧版本只完成了部分符号解析，部分功能已禁用。"
            "unsupported" -> "当前贴吧版本尚未完成符号解析，部分功能不可用。"
            else -> null
        }
    }

    fun ensureInitialUnsupportedHint(classLoader: ClassLoader) {
        synchronized(this) {
            if (initialUnsupportedHintInstalled) return
            initialUnsupportedHintInstalled = true
        }
        try {
            val unhooks = XposedBridge.hookAllMethods(Activity::class.java, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (activity.packageName != TARGET_PACKAGE || activity.isFinishing) return
                    synchronized(this@SettingsMenuHook) {
                        if (initialUnsupportedHintShown) return
                        initialUnsupportedHintShown = true
                    }
                    unhookInitialUnsupportedHint()
                    startSymbolScanWithDialog(activity, classLoader)
                }
            })
            initialScanResumeUnhooks = unhooks.toTypedArray()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook initial unsupported hint: ${t.message}")
        }
    }

    private fun unhookInitialUnsupportedHint() {
        val unhooks = initialScanResumeUnhooks ?: return
        initialScanResumeUnhooks = null
        for (unhook in unhooks) {
            try {
                unhook.unhook()
            } catch (_: Throwable) {
            }
        }
    }

    private fun startSymbolScanWithDialog(context: Context, classLoader: ClassLoader?) {
        val activity = findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, "TBHook: 无法获取界面上下文，改为后台扫描", Toast.LENGTH_SHORT).show()
            HookSymbolResolver.manualRescanAsync(context, classLoader ?: context.classLoader)
            return
        }

        val actualClassLoader = classLoader ?: activity.classLoader
        if (actualClassLoader == null) {
            Toast.makeText(activity, "TBHook: 无法获取类加载器", Toast.LENGTH_SHORT).show()
            return
        }

        val density = activity.resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val handler = Handler(Looper.getMainLooper())
        val logs = StringBuilder(2048)
        var scanFinished = false
        var scanSource = "unsupported"

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val logView = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF203040.toInt())
            text = "准备扫描..."
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(activity).apply {
            addView(logView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (260 * density).toInt()))

        val buttonBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        val cancelButton = Button(activity).apply { text = "取消" }
        val copyButton = Button(activity).apply { text = "复制日志" }
        val restartButton = Button(activity).apply { text = "重启" }

        styleScanActionButton(cancelButton, density, 0xFF2C3E50.toInt())
        styleScanActionButton(copyButton, density, 0xFF1976D2.toInt())
        styleScanActionButton(restartButton, density, 0xFF1976D2.toInt())
        updateButtonEnabledState(restartButton, false)

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = (8 * density).toInt()
        }
        buttonBar.addView(cancelButton, lp)
        buttonBar.addView(copyButton, lp2)
        buttonBar.addView(restartButton, lp2)
        root.addView(buttonBar)

        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("反混淆扫描")
            .setView(root)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        copyButton.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = logs.toString()
            if (clipboard == null || text.isEmpty()) {
                Toast.makeText(activity, "TBHook: 暂无可复制日志", Toast.LENGTH_SHORT).show()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("tbhook_scan_log", text))
                Toast.makeText(activity, "TBHook: 日志已复制", Toast.LENGTH_SHORT).show()
            }
        }
        restartButton.setOnClickListener {
            if (scanFinished) {
                appendScanLog(handler, logs, logView, scrollView, "Restart requested")
                restartHostApp(activity)
            }
        }

        dialog.show()
        dialog.window?.let { window ->
            val gd = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = 16f * density
            }
            window.setBackgroundDrawable(InsetDrawable(gd, (16 * density).toInt()))
        }

        appendScanLog(handler, logs, logView, scrollView, "Scan started")
        appendScanLog(handler, logs, logView, scrollView, "activity=${activity::class.java.name}")
        appendScanLog(handler, logs, logView, scrollView, "process=${activity.packageName}")
        appendScanLog(handler, logs, logView, scrollView, "classLoader=${actualClassLoader::class.java.name}@${System.identityHashCode(actualClassLoader)}")

        Thread {
            try {
                val result = HookSymbolResolver.resolve(
                    context = activity,
                    classLoader = actualClassLoader,
                    forceRescan = true,
                    showToast = false,
                    scanLogger = ScanLogger { message ->
                        appendScanLog(handler, logs, logView, scrollView, message)
                    },
                )
                scanSource = result.source
            } catch (t: Throwable) {
                appendScanLog(handler, logs, logView, scrollView, "Exception: ${t.message ?: "unknown"}")
            }
            handler.post {
                scanFinished = true
                val completed = when (scanSource) {
                    "scan" -> "Scan completed"
                    "partial" -> "Scan partially completed"
                    else -> "Scan failed"
                }
                appendScanLog(handler, logs, logView, scrollView, completed)
                updateButtonEnabledState(restartButton, scanSource != "unsupported")
            }
        }.start()
    }

    private fun appendScanLog(
        handler: Handler,
        logs: StringBuilder,
        logView: TextView,
        scrollView: ScrollView,
        message: String,
    ) {
        handler.post {
            logs.append(message).append('\n')
            logView.text = logs.toString()
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun styleScanActionButton(button: Button, density: Float, color: Int) {
        button.textSize = 13.5f
        button.isAllCaps = false
        button.setTextColor(color)
        val hp = (6 * density).toInt()
        val vp = (4 * density).toInt()
        button.setPadding(hp, vp, hp, vp)
        button.minWidth = 0
        button.minHeight = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.setBackgroundColor(0)
    }

    private fun updateButtonEnabledState(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.45f
    }

    private fun restartHostApp(activity: Activity) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity.startActivity(launchIntent)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: restart launch failed: ${t.message}")
        }
        try {
            activity.finishAffinity()
        } catch (_: Throwable) {
        }
        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Process.killProcess(Process.myPid())
                } catch (_: Throwable) {
                }
                try {
                    kotlin.system.exitProcess(0)
                } catch (_: Throwable) {
                }
            }, 200L)
        } catch (_: Throwable) {
        }
    }

    private fun findActivityFromContext(context: Context): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
        }
        return null
    }

    private fun resolveSettingsContainer(target: Any?, preferredFieldName: String): View? {
        if (target == null) return null
        val clazz = target.javaClass
        settingsFieldCache[clazz]?.let {
            return try {
                it.get(target) as? View
            } catch (_: Throwable) {
                null
            }
        }

        try {
            val directField = clazz.getDeclaredField(preferredFieldName)
            if (View::class.java.isAssignableFrom(directField.type)) {
                directField.isAccessible = true
                settingsFieldCache[clazz] = directField
                return directField.get(target) as? View
            }
        } catch (_: Throwable) {
        }

        val fallback = clazz.declaredFields.firstOrNull { it.type.name == "android.widget.RelativeLayout" }
            ?: clazz.declaredFields.firstOrNull { View::class.java.isAssignableFrom(it.type) }
            ?: return null

        return try {
            fallback.isAccessible = true
            settingsFieldCache[clazz] = fallback
            fallback.get(target) as? View
        } catch (_: Throwable) {
            null
        }
    }
}
