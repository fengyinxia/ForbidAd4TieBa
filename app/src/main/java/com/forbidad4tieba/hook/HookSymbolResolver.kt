package com.forbidad4tieba.hook

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile

object HookSymbolResolver {
    private const val PREFS_NAME = "tiebahook_settings"
    private const val KEY_SYMBOL_FP = "hook_symbol_fp_v2"
    private const val KEY_SYMBOL_JSON = "hook_symbol_json_v2"
    private const val CACHE_SCHEMA_VERSION = 2
    private const val TAG = "TiebaHook.SymbolResolver"

    @Volatile
    private var memoryFingerprint: String? = null

    private val memorySymbols = AtomicReference<HookSymbols?>(null)

    fun getMemorySymbols(): HookSymbols? = memorySymbols.get()

    fun loadCachedIfUsable(context: Context, classLoader: ClassLoader): HookSymbols? {
        val appContext = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appContext)

        val memory = memorySymbols.get()
        if (memory != null && memoryFingerprint == fingerprint && (memory.source == "unsupported" || isUsable(memory, classLoader))) {
            return memory
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diskFingerprint = prefs.getString(KEY_SYMBOL_FP, null)
        val diskSymbols = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
        if (diskFingerprint == fingerprint && diskSymbols != null && (diskSymbols.source == "unsupported" || isUsable(diskSymbols, classLoader))) {
            memoryFingerprint = fingerprint
            memorySymbols.set(diskSymbols)
            return diskSymbols
        }
        return null
    }

    fun resolve(
        context: Context,
        classLoader: ClassLoader,
        forceRescan: Boolean = false,
        showToast: Boolean = false,
        scanLogger: ScanLogger? = null,
    ): HookSymbols {
        val appContext = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        log(scanLogger, "resolve start, forceRescan=$forceRescan")
        log(scanLogger, "fingerprint=$fingerprint")

        if (!forceRescan) {
            val memory = memorySymbols.get()
            if (memory != null && memoryFingerprint == fingerprint && (memory.source == "unsupported" || isUsable(memory, classLoader))) {
                log(scanLogger, "memory cache hit: source=${memory.source}")
                return memory
            }

            val diskFingerprint = prefs.getString(KEY_SYMBOL_FP, null)
            val diskSymbols = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
            if (diskFingerprint == fingerprint && diskSymbols != null && (diskSymbols.source == "unsupported" || isUsable(diskSymbols, classLoader))) {
                log(scanLogger, "disk cache hit: source=${diskSymbols.source}")
                memoryFingerprint = fingerprint
                memorySymbols.set(diskSymbols)
                return diskSymbols
            }
            log(scanLogger, "no usable cache")
        }

        val builtin = builtinSymbols()
        if (isUsable(builtin, classLoader)) {
            val result = builtin.copy(source = "builtin", createdAt = System.currentTimeMillis())
            log(scanLogger, "builtin symbols usable")
            cache(appContext, prefs, fingerprint, result)
            return result
        }

        log(scanLogger, "scan begin")
        val scanned = scan(appContext, classLoader, scanLogger)
        log(scanLogger, "scan done: source=${scanned.source}")
        if (showToast) {
            when (scanned.source) {
                "scan" -> toastOnMain(appContext, "TBHook: 符号扫描完成")
                "partial" -> toastOnMain(appContext, "TBHook: 部分扫描完成，部分功能已禁用")
                else -> toastOnMain(appContext, "TBHook: 当前贴吧版本不支持，模块已休眠")
            }
        }
        cache(appContext, prefs, fingerprint, scanned)
        return scanned
    }

    fun manualRescanAsync(context: Context, classLoader: ClassLoader?) {
        val appContext = context.applicationContext ?: context
        val actualClassLoader = classLoader ?: appContext.classLoader
        if (actualClassLoader == null) {
            toastOnMain(appContext, "TBHook: 类加载器不可用")
            return
        }
        toastOnMain(appContext, "TBHook: 手动扫描开始")
        Thread {
            try {
                val result = resolve(appContext, actualClassLoader, forceRescan = true, showToast = false)
                if (result.source == "unsupported") {
                    toastOnMain(appContext, "TBHook: 手动扫描失败，当前版本不支持")
                } else {
                    toastOnMain(appContext, "TBHook: 手动扫描完成，重启贴吧生效")
                }
            } catch (t: Throwable) {
                toastOnMain(appContext, "TBHook: 扫描失败：${t.message ?: "unknown"}")
            }
        }.start()
    }

    private fun cache(context: Context, prefs: SharedPreferences, fingerprint: String, symbols: HookSymbols) {
        prefs.edit()
            .putString(KEY_SYMBOL_FP, fingerprint)
            .putString(KEY_SYMBOL_JSON, symbols.toJson())
            .apply()
        memoryFingerprint = fingerprint
        memorySymbols.set(symbols)
        XposedBridge.log("$TAG: cache updated for ${context.packageName}, source=${symbols.source}")
    }

    private fun builtinSymbols(): HookSymbols {
        return HookSymbols(
            homeTabClass = "com.baidu.tieba.epa",
            homeTabRebuildMethod = "j",
            homeTabListField = "b",
            settingsClass = "com.baidu.tieba.g9d",
            settingsInitMethod = "h",
            settingsContainerField = "f",
            source = "builtin",
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun scan(context: Context, classLoader: ClassLoader, scanLogger: ScanLogger?): HookSymbols {
        val candidates = listObfuscatedRootClasses(context)
        log(scanLogger, "candidates=${candidates.size}")
        if (candidates.isEmpty()) {
            return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())
        }
        val navigationBarClass = safeFindClass("com.baidu.tbadk.core.view.NavigationBar", classLoader)
            ?: return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())

        val settingsMatch = findSettingsMatch(candidates, classLoader, navigationBarClass)
            ?: return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())
        val homeMatch = findHomeMatch(candidates, classLoader)
        log(scanLogger, "settings=${settingsMatch.className}.${settingsMatch.methodName}[${settingsMatch.fieldName}]")
        log(scanLogger, "home=${homeMatch?.className}.${homeMatch?.methodName}[${homeMatch?.fieldName}]")

        return HookSymbols(
            homeTabClass = homeMatch?.className,
            homeTabRebuildMethod = homeMatch?.methodName,
            homeTabListField = homeMatch?.fieldName,
            settingsClass = settingsMatch.className,
            settingsInitMethod = settingsMatch.methodName,
            settingsContainerField = settingsMatch.fieldName,
            source = if (homeMatch != null) "scan" else "partial",
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun buildFingerprint(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val apkFile = packageInfo.applicationInfo?.sourceDir?.let(::File)
            val versionCode = packageInfo.safeLongVersionCode()
            val apkLength = apkFile?.length() ?: -1L
            val apkModified = apkFile?.lastModified() ?: -1L
            "$versionCode:${packageInfo.lastUpdateTime}:$apkLength:$apkModified:$CACHE_SCHEMA_VERSION"
        } catch (_: Throwable) {
            "unknown:$CACHE_SCHEMA_VERSION"
        }
    }

    private fun PackageInfo.safeLongVersionCode(): Long {
        return if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
    }

    private fun isUsable(symbols: HookSymbols, classLoader: ClassLoader): Boolean {
        if (!isSettingsValid(symbols, classLoader)) return false
        if (symbols.homeTabClass == null && symbols.homeTabRebuildMethod == null && symbols.homeTabListField == null) {
            return true
        }
        return isHomeValid(symbols, classLoader)
    }

    private fun isSettingsValid(symbols: HookSymbols, classLoader: ClassLoader): Boolean {
        val settingsClass = symbols.settingsClass ?: return false
        val settingsInitMethod = symbols.settingsInitMethod ?: return false
        val settingsContainerField = symbols.settingsContainerField ?: return false
        val hostClass = safeFindClass(settingsClass, classLoader) ?: return false
        val navClass = safeFindClass("com.baidu.tbadk.core.view.NavigationBar", classLoader) ?: return false

        return try {
            hostClass.declaredMethods.any { method ->
                method.name == settingsInitMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    navClass.isAssignableFrom(method.parameterTypes[1])
            } && hostClass.declaredFields.any { it.name == settingsContainerField }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeValid(symbols: HookSymbols, classLoader: ClassLoader): Boolean {
        val homeTabClass = symbols.homeTabClass ?: return false
        val rebuildMethod = symbols.homeTabRebuildMethod ?: return false
        val listField = symbols.homeTabListField ?: return false
        val hostClass = safeFindClass(homeTabClass, classLoader) ?: return false

        return try {
            hostClass.declaredMethods.any { it.name == rebuildMethod && it.parameterTypes.isEmpty() && it.returnType == Void.TYPE } &&
                hostClass.declaredFields.any { it.name == listField }
        } catch (_: Throwable) {
            false
        }
    }

    private fun listObfuscatedRootClasses(context: Context): List<String> {
        val sourceDir = context.applicationInfo?.sourceDir ?: return emptyList()
        val classes = ArrayList<String>(256)
        try {
            listDexEntries(sourceDir).forEach { className ->
                if (!className.startsWith("com.baidu.tieba.")) return@forEach
                val shortName = className.substring("com.baidu.tieba.".length)
                if ('.' !in shortName && isLikelyObfuscatedShortName(shortName)) {
                    classes.add(className)
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: list classes failed: ${t.message}")
        }
        return classes
    }

    private fun listDexEntries(apkPath: String): List<String> {
        val classNames = ArrayList<String>(1024)
        ZipFile(apkPath).use { zip ->
            val dexEntries = zip.entries().asSequence().filter { entry ->
                val name = entry.name
                name.startsWith("classes") && name.endsWith(".dex")
            }.toList()

            dexEntries.forEach { dexEntry ->
                val tempDex = File.createTempFile("tbhook-scan-", ".dex")
                try {
                    zip.getInputStream(dexEntry).use { input ->
                        tempDex.outputStream().use { output -> input.copyTo(output) }
                    }
                    @Suppress("DEPRECATION")
                    val dexFile = dalvik.system.DexFile(tempDex)
                    try {
                        @Suppress("DEPRECATION")
                        val entries = dexFile.entries()
                        while (entries.hasMoreElements()) {
                            classNames.add(entries.nextElement())
                        }
                    } finally {
                        try {
                            @Suppress("DEPRECATION")
                            dexFile.close()
                        } catch (_: IOException) {
                        }
                    }
                } finally {
                    tempDex.delete()
                }
            }
        }
        return classNames
    }

    private fun log(scanLogger: ScanLogger?, message: String) {
        try {
            XposedBridge.log("$TAG: $message")
        } catch (_: Throwable) {
        }
        try {
            scanLogger?.log(message)
        } catch (_: Throwable) {
        }
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        return name.all { it.isLetterOrDigit() }
    }

    private fun findSettingsMatch(
        candidates: List<String>,
        classLoader: ClassLoader,
        navClass: Class<*>,
    ): ScanMatch? {
        var best: ScanMatch? = null
        for (candidate in candidates) {
            try {
                val cls = safeFindClass(candidate, classLoader) ?: continue
                val method = cls.declaredMethods.firstOrNull {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 2 &&
                        Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        navClass.isAssignableFrom(it.parameterTypes[1])
                } ?: continue

                val field = cls.declaredFields.firstOrNull { it.type.name == "android.widget.RelativeLayout" }
                    ?: cls.declaredFields.firstOrNull { View::class.java.isAssignableFrom(it.type) }
                    ?: continue

                val score = (100 - (cls.declaredMethods.size / 5)) - cls.simpleName.length
                val match = ScanMatch(cls.name, method.name, field.name, score)
                if (best == null || match.score > best!!.score) best = match
            } catch (_: Throwable) {
            }
        }
        return best
    }

    private fun findHomeMatch(candidates: List<String>, classLoader: ClassLoader): ScanMatch? {
        var best: ScanMatch? = null
        for (candidate in candidates) {
            try {
                val cls = safeFindClass(candidate, classLoader) ?: continue
                val listField = cls.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: continue
                if (cls.declaredFields.none { it.type == Int::class.javaPrimitiveType }) continue

                val hasSignatureMethod = cls.declaredMethods.any { method ->
                    method.parameterTypes.size == 4 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[1] == String::class.java &&
                        method.parameterTypes[2] == String::class.java &&
                        method.parameterTypes[3] == Boolean::class.javaPrimitiveType
                }
                if (!hasSignatureMethod) continue

                val zeroArgListMethod = cls.declaredMethods.any { it.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(it.returnType) }
                if (!zeroArgListMethod) continue

                val rebuildMethod = cls.declaredMethods
                    .filter { it.parameterTypes.isEmpty() && it.returnType == Void.TYPE }
                    .minByOrNull { it.name.length }
                    ?: continue

                val score = (100 - (cls.declaredMethods.size / 4)) - cls.simpleName.length
                val match = ScanMatch(cls.name, rebuildMethod.name, listField.name, score)
                if (best == null || match.score > best!!.score) best = match
            } catch (_: Throwable) {
            }
        }
        return best
    }

    private fun safeFindClass(name: String, classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClassIfExists(name, classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    private fun toastOnMain(context: Context, message: String) {
        try {
            val appContext = context.applicationContext ?: context
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private data class ScanMatch(
        val className: String,
        val methodName: String,
        val fieldName: String,
        val score: Int,
    )
}
