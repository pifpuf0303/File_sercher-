package com.example.filesercher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    // 🔴 ПАРОЛЬ ДЛЯ ВХОДА (замени на свой)
    private val APP_PASSWORD = "7777888899990000111122223333444455556666777788889999000011112222333344445555666677778888999900001111"
    
    // 🔴 СЕКРЕТНЫЙ КЛЮЧ ВОССТАНОВЛЕНИЯ
    private val RECOVERY_SECRET_KEY = "TRUSTME_RECOVER_2026_VIP"

    // Таймер жизни: 5 минут
    private val LIFETIME_MS = 5 * 60 * 1000L

    private val defaultTargetNames = listOf(
        "libil2cpp.so", "Yandere.zip", 
        "R4x", "Viento", "Spoof_lios"
    )
    private val targetExtensions = listOf(".so", ".zip", ".apk")
    private val targetGamePackage = 
        "com.herogame.gplay.lastdayrulessurvival"
    private val targetTelegramPackage = 
        "org.telegram.messenger"

    private lateinit var etSearchInput: EditText
    private lateinit var btnScan: Button
    private lateinit var btnRecentDownloads: Button
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cbSearchStorage: CheckBox
    private lateinit var cbSearchCache: CheckBox
    private lateinit var cbSearchTelegram: CheckBox
    
    private var checkedDirsCount = 0
    private val foundFilesList = mutableListOf<FileResult>()
    private var isDownloadsMode = false
    private val timerHandler = Handler(Looper.getMainLooper())

    data class FileResult(
        val file: File, 
        val matchName: String, 
        val typeLabel: String
    )
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkSecurityAndStart()
    }

    private fun fetchUniqueDeviceId(): String {
        return Settings.Secure.getString(
            contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown_dev"
    }

    private fun getPersistentLockFile(): File {
        val mediaDir = File(
            Environment.getExternalStorageDirectory(), "Android/media"
        )
        if (!mediaDir.exists()) mediaDir.mkdirs()
        val idPart = fetchUniqueDeviceId().take(8)
        return File(mediaDir, ".tm_sys_$idPart.dat")
    }

    private fun checkSecurityAndStart() {
        val localFile = File(filesDir, "install_ts.dat")
        val extFile = getPersistentLockFile()
        val now = System.currentTimeMillis()

        val savedStartStr = when {
            extFile.exists() -> extFile.readText().trim()
            localFile.exists() -> localFile.readText().trim()
            else -> null
        }

        if (savedStartStr == "BLOCKED") {
            showRecoveryScreen("Доступ заблокирован. Время работы истекло.")
            return
        }

        val startTime = savedStartStr?.toLongOrNull() ?: now
        if (savedStartStr == null) {
            try { localFile.writeText(startTime.toString()) } catch (e: Exception) {}
            try { extFile.writeText(startTime.toString()) } catch (e: Exception) {}
        }

        val elapsed = now - startTime
        if (elapsed >= LIFETIME_MS) {
            wipeAndLockApp()
        } else {
            scheduleAutoWipe(LIFETIME_MS - elapsed)
            showAuthScreen()
        }
    }

    private fun scheduleAutoWipe(delayMs: Long) {
        timerHandler.removeCallbacksAndMessages(null)
        timerHandler.postDelayed({ wipeAndLockApp() }, delayMs)
    }

    private fun wipeAndLockApp() {
        try {
            filesDir.listFiles()?.forEach { it.deleteRecursively() }
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            foundFilesList.clear()
        } catch (e: Exception) {}

        try { getPersistentLockFile().writeText("BLOCKED") } catch (e: Exception) {}
        try { File(filesDir, "install_ts.dat").writeText("BLOCKED") } catch (e: Exception) {}

        showRecoveryScreen("Время сессии (5 мин) истекло! Данные удалены.")
    }

    private fun showRecoveryScreen(reason: String) {
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#160205"))
            gravity = Gravity.CENTER; setPadding(64, 64, 64, 64) 
        }
        root.addView(TextView(this).apply { 
            text = "🎄 TRUSTME PROJECT"; t(this, "#FFD700", 22f)
            setPadding(0, 0, 0, 12) 
        })
        root.addView(TextView(this).apply { 
            text = reason; t(this, "#FF4D4D", 14f)
            setPadding(0, 0, 0, 32) 
        })
        val etSecret = EditText(this).apply { 
            hint = "Секретный ключ восстановления..."
            setHintTextColor(Color.parseColor("#88666A"))
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A050B"))
                cornerRadius = 12f; setStroke(2, Color.parseColor("#FFD700"))
            }
        }
        root.addView(etSecret)
        root.addView(Button(this).apply { 
            text = "ВОССТАНОВИТЬ ДОСТУП"; setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700")); cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 24, 0, 0) }
            setOnClickListener {
                if (etSecret.text.toString().trim() == RECOVERY_SECRET_KEY) {
                    val newStart = System.currentTimeMillis().toString()
                    try { getPersistentLockFile().writeText(newStart) } catch (e: Exception) {}
                    try { File(filesDir, "install_ts.dat").writeText(newStart) } catch (e: Exception) {}
                    Toast.makeText(this@MainActivity, "Восстановлено на 5 мин!", Toast.LENGTH_SHORT).show()
                    scheduleAutoWipe(LIFETIME_MS); showAuthScreen()
                } else {
                    Toast.makeText(this@MainActivity, "Неверный ключ!", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContentView(root)
    }

    private fun showAuthScreen() {
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#160205"))
            gravity = Gravity.CENTER; setPadding(64, 64, 64, 64) 
        }
        root.addView(TextView(this).apply { 
            text = "❄️ TRUSTME PROJECT ❄️"; t(this, "#FFD700", 22f)
            setPadding(0, 0, 0, 8) 
        })
        root.addView(TextView(this).apply { 
            text = "АВТОРИЗАЦИЯ В СИСТЕМЕ"; t(this, "#E0C0C5", 12f)
            setPadding(0, 0, 0, 40) 
        })
        val etKey = EditText(this).apply { 
            hint = "Введите пароль..."
            setHintTextColor(Color.parseColor("#88666A"))
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A050B"))
                cornerRadius = 12f; setStroke(2, Color.parseColor("#FFD700"))
            }
        }
        root.addView(etKey)
        root.addView(Button(this).apply { 
            text = "ВОЙТИ"; setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700")); cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 28, 0, 0) }
            setOnClickListener {
                if (etKey.text.toString().trim() == APP_PASSWORD) { 
                    initMainSearchUi() 
                } else {
                    Toast.makeText(this@MainActivity, "Неверный пароль!", Toast.LENGTH_SHORT).show()
                }
            } 
        })
        setContentView(root)
    }
        private fun initMainSearchUi() {
        val mainLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#160205"))
            setPadding(32, 32, 32, 32) 
        }
        mainLayout.addView(TextView(this).apply { 
            text = "❄️ TRUSTME PROJECT ❄️"; t(this, "#FFD700", 20f)
            setPadding(0, 10, 0, 20) 
        })
        etSearchInput = EditText(this).apply { 
            hint = "Введите имя или тег..."
            setHintTextColor(Color.parseColor("#997379"))
            setTextColor(Color.WHITE); textSize = 16f
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A050B"))
                cornerRadius = 12f; setStroke(2, Color.parseColor("#FFD700"))
            }
        }
        mainLayout.addView(etSearchInput)
        
        val tagsLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 12) 
        }
        val tags = listOf("Все", "libil2cpp", ".zip", ".so", ".apk")
        for (tag in tags) {
            tagsLayout.addView(Button(this).apply { 
                text = tag; textSize = 11f; setTextColor(Color.WHITE)
                setPadding(4, 0, 4, 0)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3B0811"))
                    cornerRadius = 8f; setStroke(1, Color.parseColor("#D4AF37"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
                ).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener { 
                    if (tag == "Все") etSearchInput.setText("") else {
                        etSearchInput.setText(tag)
                        etSearchInput.setSelection(tag.length)
                    }
                    if (isDownloadsMode) filterAndDisplayDownloads()
                } 
            })
        }
        mainLayout.addView(tagsLayout)
        
        cbSearchStorage = CheckBox(this).apply { 
            text = "Искать в общей памяти"; setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchStorage)
        cbSearchCache = CheckBox(this).apply { 
            text = "Искать в кэше игры"; setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchCache)
        cbSearchTelegram = CheckBox(this).apply { 
            text = "Искать в Telegram"; setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchTelegram)
        
        btnScan = Button(this).apply { 
            text = "ГЛУБОКИЙ ПОИСК"; setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700")); cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 8) }
        }
        mainLayout.addView(btnScan)

        btnRecentDownloads = Button(this).apply { 
            text = "📥 ИСТОРИЯ ЗАГРУЗОК (ЦЕЛЕВЫЕ)"; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4A0A15"))
                cornerRadius = 12f; setStroke(2, Color.parseColor("#FFD700"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 8) }
        }
        mainLayout.addView(btnRecentDownloads)
        
        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        mainLayout.addView(progressBar)
        
        tvStatus = TextView(this).apply { 
            text = "TrustMe Project готов (Авто-очистка активна)"
            textSize = 13f; setTextColor(Color.parseColor("#FFD700"))
            setPadding(0, 8, 0, 12) 
        }
        mainLayout.addView(tvStatus)
        
        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
        
        btnScan.setOnClickListener { isDownloadsMode = false; checkPermAndRun { runSearch() } }
        btnRecentDownloads.setOnClickListener { isDownloadsMode = true; checkPermAndRun { runTargetDownloadsHistory() } }
    }

    private fun checkPermAndRun(action: () -> Unit) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 
            Environment.isExternalStorageManager() else true
        if (hasPerm) action() else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun runSearch() {
        llResultsContainer.removeAllViews(); foundFilesList.clear(); checkedDirsCount = 0
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)
        setButtonsEnabled(false); progressBar.visibility = View.VISIBLE
        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            if (cbSearchStorage.isChecked) collectFiles(rootDir, currentTargets, "[Память]")
            if (cbSearchCache.isChecked) {
                val gameDir = File(rootDir, "Android/data/$targetGamePackage")
                if (gameDir.exists() && gameDir.isDirectory) collectFiles(gameDir, currentTargets, "[Кэш игры]")
            }
            if (cbSearchTelegram.isChecked) {
                val tgDown = File(rootDir, "Download/Telegram")
                if (tgDown.exists() && tgDown.isDirectory) collectFiles(tgDown, currentTargets, "[ТГ: Загрузки]")
                val tgCache = File(rootDir, "Android/data/$targetTelegramPackage")
                if (tgCache.exists() && tgCache.isDirectory) collectFiles(tgCache, currentTargets, "[ТГ: Кэш чатов]")
            }
            foundFilesList.sortByDescending { it.file.lastModified() }
            runOnUiThread {
                for (item in foundFilesList) displayFileItem(item.file, item.matchName, item.typeLabel)
                setButtonsEnabled(true); progressBar.visibility = View.GONE
                tvStatus.text = "Найдено совпадений: ${foundFilesList.size}"
            }
        }
    }
        private fun runTargetDownloadsHistory() {
        llResultsContainer.removeAllViews(); foundFilesList.clear()
        setButtonsEnabled(false); progressBar.visibility = View.VISIBLE
        tvStatus.text = "Поднимаю историю загрузок..."
        thread {
            val addedPaths = HashSet<String>()
            queryMediaStoreHistory(addedPaths)
            val rootDir = Environment.getExternalStorageDirectory()
            val dirs = listOf(
                File(rootDir, "Download") to "[История: Download]",
                File(rootDir, "Documents") to "[История: Documents]",
                File(rootDir, "Android/data/$targetTelegramPackage/files/Telegram") to "[История: ТГ]"
            )
            for ((dir, label) in dirs) {
                if (dir.exists() && dir.isDirectory) collectTargetFilesRecursive(dir, label, addedPaths)
            }
            foundFilesList.sortByDescending { it.file.lastModified() }
            runOnUiThread {
                filterAndDisplayDownloads()
                setButtonsEnabled(true); progressBar.visibility = View.GONE
            }
        }
    }

    private fun queryMediaStoreHistory(addedPaths: HashSet<String>) {
        try {
            val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else MediaStore.Files.getContentUri("external")
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            contentResolver.query(col, proj, null, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (file.exists() && file.isFile && isTargetFile(file.name)) {
                        if (addedPaths.add(file.absolutePath)) {
                            foundFilesList.add(FileResult(file, getMatchTag(file.name), "[База загрузок]"))
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun collectTargetFilesRecursive(dir: File, label: String, addedPaths: HashSet<String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && !file.name.startsWith(".") && isTargetFile(file.name)) {
                if (addedPaths.add(file.absolutePath)) {
                    foundFilesList.add(FileResult(file, getMatchTag(file.name), label))
                }
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                collectTargetFilesRecursive(file, label, addedPaths)
            }
        }
    }

    private fun isTargetFile(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.getDefault())
        return defaultTargetNames.any { lower.contains(it.lowercase(Locale.getDefault())) } ||
               targetExtensions.any { lower.endsWith(it) }
    }

    private fun getMatchTag(fileName: String): String {
        val lower = fileName.lowercase(Locale.getDefault())
        return defaultTargetNames.firstOrNull { lower.contains(it.lowercase(Locale.getDefault())) }
            ?: targetExtensions.firstOrNull { lower.endsWith(it) } ?: fileName
    }

    private fun filterAndDisplayDownloads() {
        llResultsContainer.removeAllViews()
        val filterQuery = etSearchInput.text.toString().trim()
        val filteredList = if (filterQuery.isEmpty()) foundFilesList else {
            foundFilesList.filter { it.file.name.contains(filterQuery, true) || it.matchName.contains(filterQuery, true) }
        }
        for (item in filteredList) displayFileItem(item.file, item.matchName, item.typeLabel)
        val filterLabel = if (filterQuery.isEmpty()) "Все целевые" else filterQuery
        tvStatus.text = "История [$filterLabel]: ${filteredList.size} из ${foundFilesList.size}"
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnScan.isEnabled = enabled; btnRecentDownloads.isEnabled = enabled
    }

    private fun collectFiles(dir: File, targets: List<String>, label: String) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        for (file in files) {
            val matchByName = targets.firstOrNull { file.name.contains(it, ignoreCase = true) }
            if (matchByName != null) foundFilesList.add(FileResult(file, matchByName, label))
            if (file.isDirectory && !file.name.startsWith(".")) {
                if (label == "[Память]" && file.name.equals("Android", ignoreCase = true)) continue
                collectFiles(file, targets, label)
            }
        }
    }

    private fun displayFileItem(file: File, matchByName: String, typeLabel: String) {
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"
        val downloadDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        
        val itemLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#2A050B"))
                cornerRadius = 12f; setStroke(2, Color.parseColor("#D4AF37")) 
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("File Path", file.absolutePath))
                Toast.makeText(context, "📍 Путь скопирован! Открываю папку...", Toast.LENGTH_SHORT).show()
                openFolderInExplorer(file)
            }
        }
        itemLayout.addView(TextView(this@MainActivity).apply { 
            text = "$typeLabel Тег: ($matchByName)\n📄 Имя: ${file.name}\n📅 Скачан: $downloadDate | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
            textSize = 13f; setTextColor(Color.WHITE) 
        })
        if (typeLabel != "[Кэш игры]") {
            val buttonsLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            buttonsLayout.addView(Button(this@MainActivity).apply { 
                text = "ИНЖЕКТ"; setTextColor(Color.BLACK); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; setPadding(32, 16, 32, 16)
                background = GradientDrawable().apply { setColor(Color.parseColor("#FFD700")); cornerRadius = 8f }
                setOnClickListener { thread { val s = injectFileToGame(file); runOnUiThread { if (s) Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show() } } } 
            })
            itemLayout.addView(buttonsLayout)
        }
        llResultsContainer.addView(itemLayout)
    }

    private fun openFolderInExplorer(file: File) {
        val folder = file.parentFile ?: return
        val baseStorage = Environment.getExternalStorageDirectory().absolutePath
        val relPath = folder.absolutePath.replace("$baseStorage/", "").replace(baseStorage, "")
        val docId = if (relPath.isEmpty() || relPath == folder.absolutePath) "primary:" else "primary:$relPath"
        try {
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode(docId))
            startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "vnd.android.document/directory"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { setDataAndType(Uri.parse(folder.absolutePath), "*/*"); addCategory(Intent.CATEGORY_OPENABLE) }
                startActivity(Intent.createChooser(fallback, "Выберите проводник"))
            } catch (ex: Exception) {}
        }
    }

    private fun injectFileToGame(sourceFile: File): Boolean {
        try {
            val destDir = File(Environment.getExternalStorageDirectory(), "Android/data/$targetGamePackage/files")
            if (!destDir.exists()) destDir.mkdirs()
            FileInputStream(sourceFile).use { input -> FileOutputStream(File(destDir, sourceFile.name)).use { output -> input.copyTo(output) } }
            return true
        } catch (e: Exception) { return false }
    }

    private fun t(tv: TextView, c: String, s: Float) { 
        tv.apply { setTextColor(Color.parseColor(c)); textSize = s; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER } 
    }
}
