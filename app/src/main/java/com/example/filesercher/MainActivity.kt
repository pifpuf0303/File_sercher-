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
    // Твои целевые файлы и ключевые слова для истории загрузок
    private val defaultTargetNames = listOf(
        "libil2cpp.so", "Yandere.zip", 
        "R4x", "Viento", "Spoof_lios"
    )
    // Расширения, которые считаем целевыми при сканировании истории
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

    data class FileResult(
        val file: File, 
        val matchName: String, 
        val typeLabel: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMainSearchUi()
    }
        private fun initMainSearchUi() {
        val mainLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#09090B"))
            setPadding(32, 32, 32, 32) 
        }
        
        mainLayout.addView(TextView(this).apply { 
            text = "👺 GOBLIN FILES SEARCHER"
            t(this, "#FF1E1E", 20f)
            setPadding(0, 10, 0, 20) 
        })
        
        etSearchInput = EditText(this).apply { 
            hint = "Введите имя или тег..."
            setHintTextColor(Color.parseColor("#777777"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141417"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#FF1E1E"))
            }
        }
        mainLayout.addView(etSearchInput)
        
        val tagsLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12) 
        }
        val tags = listOf("Все", "libil2cpp", ".zip", ".so", ".apk")
        for (tag in tags) {
            tagsLayout.addView(Button(this).apply { 
                text = tag; textSize = 11f; setTextColor(Color.WHITE)
                setPadding(4, 0, 4, 0)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C1C21"))
                    cornerRadius = 8f
                    setStroke(1, Color.parseColor("#8A0000"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
                ).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener { 
                    if (tag == "Все") {
                        etSearchInput.setText("")
                    } else {
                        etSearchInput.setText(tag)
                        etSearchInput.setSelection(tag.length)
                    }
                    if (isDownloadsMode) {
                        filterAndDisplayDownloads()
                    }
                } 
            })
        }
        mainLayout.addView(tagsLayout)
        
        cbSearchStorage = CheckBox(this).apply { 
            text = "Искать в общей памяти"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchStorage)
        
        cbSearchCache = CheckBox(this).apply { 
            text = "Искать в кэше игры"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchCache)
        
        cbSearchTelegram = CheckBox(this).apply { 
            text = "Искать в Telegram"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchTelegram)
        
        btnScan = Button(this).apply { 
            text = "ГЛУБОКИЙ ПОИСК"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D90429"))
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 8) }
        }
        mainLayout.addView(btnScan)

        btnRecentDownloads = Button(this).apply { 
            text = "📥 ИСТОРИЯ ЗАГРУЗОК (ЦЕЛЕВЫЕ)"
            setTextColor(Color.parseColor("#39FF14"))
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141417"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#39FF14"))
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
            text = "Система Goblin готова к поиску"
            textSize = 14f
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 8, 0, 12) 
        }
        mainLayout.addView(tvStatus)
        
        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL 
        }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
        
        btnScan.setOnClickListener { 
            isDownloadsMode = false
            checkPermAndRun { runSearch() } 
        }
        btnRecentDownloads.setOnClickListener { 
            isDownloadsMode = true
            checkPermAndRun { runTargetDownloadsHistory() } 
        }
    }

    private fun checkPermAndRun(action: () -> Unit) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 
            Environment.isExternalStorageManager() else true
        if (hasPerm) { 
            action() 
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
        private fun runSearch() {
        llResultsContainer.removeAllViews()
        foundFilesList.clear()
        checkedDirsCount = 0
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)
        setButtonsEnabled(false)
        progressBar.visibility = View.VISIBLE
        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            if (cbSearchStorage.isChecked) { 
                collectFiles(rootDir, currentTargets, "[Память]") 
            }
            if (cbSearchCache.isChecked) {
                val gameDataDir = File(rootDir, "Android/data/$targetGamePackage")
                if (gameDataDir.exists() && gameDataDir.isDirectory) { 
                    collectFiles(gameDataDir, currentTargets, "[Кэш игры]") 
                }
            }
            if (cbSearchTelegram.isChecked) {
                val tgDownloadDir = File(rootDir, "Download/Telegram")
                if (tgDownloadDir.exists() && tgDownloadDir.isDirectory) { 
                    collectFiles(tgDownloadDir, currentTargets, "[ТГ: Загрузки]") 
                }
                val tgCacheDir = File(rootDir, "Android/data/$targetTelegramPackage")
                if (tgCacheDir.exists() && tgCacheDir.isDirectory) { 
                    collectFiles(tgCacheDir, currentTargets, "[ТГ: Кэш чатов]") 
                }
            }
            foundFilesList.sortByDescending { it.file.lastModified() }
            runOnUiThread {
                for (item in foundFilesList) { 
                    displayFileItem(item.file, item.matchName, item.typeLabel) 
                }
                setButtonsEnabled(true)
                progressBar.visibility = View.GONE
                tvStatus.text = "Найдено совпадений: ${foundFilesList.size}"
            }
        }
    }

    // СБОР ВСЕХ ЦЕЛЕВЫХ ЗАГРУЗОК ЗА ВСЮ ИСТОРИЮ УСТРОЙСТВА
    private fun runTargetDownloadsHistory() {
        llResultsContainer.removeAllViews()
        foundFilesList.clear()
        setButtonsEnabled(false)
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Поднимаю всю историю загрузок..."
        
        thread {
            val addedPaths = HashSet<String>()
            
            // 1. Читаем системную базу MediaStore за всю историю телефона
            queryMediaStoreHistory(addedPaths)
            
            // 2. Дополнительно сканируем папки загрузок и Telegram
            val rootDir = Environment.getExternalStorageDirectory()
            val dirsToScan = listOf(
                File(rootDir, "Download") to "[История: Download]",
                File(rootDir, "Documents") to "[История: Documents]",
                File(rootDir, "Android/data/$targetTelegramPackage/files/Telegram") to "[История: ТГ]"
            )
            
            for ((dir, label) in dirsToScan) {
                if (dir.exists() && dir.isDirectory) {
                    collectTargetFilesRecursive(dir, label, addedPaths)
                }
            }
            
            // Сортируем от самых новых к самым старым за всё время
            foundFilesList.sortByDescending { it.file.lastModified() }
            
            runOnUiThread {
                filterAndDisplayDownloads()
                setButtonsEnabled(true)
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun queryMediaStoreHistory(addedPaths: HashSet<String>) {
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
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
        val matchesName = defaultTargetNames.any { lower.contains(it.lowercase(Locale.getDefault())) }
        val matchesExt = targetExtensions.any { lower.endsWith(it) }
        return matchesName || matchesExt
    }

    private fun getMatchTag(fileName: String): String {
        val lower = fileName.lowercase(Locale.getDefault())
        return defaultTargetNames.firstOrNull { lower.contains(it.lowercase(Locale.getDefault())) }
            ?: targetExtensions.firstOrNull { lower.endsWith(it) }
            ?: fileName
    }

    private fun filterAndDisplayDownloads() {
        llResultsContainer.removeAllViews()
        val filterQuery = etSearchInput.text.toString().trim()
        
        val filteredList = if (filterQuery.isEmpty()) {
            foundFilesList
        } else {
            foundFilesList.filter { item ->
                item.file.name.contains(filterQuery, ignoreCase = true) ||
                item.matchName.contains(filterQuery, ignoreCase = true)
            }
        }
        
        for (item in filteredList) {
            displayFileItem(item.file, item.matchName, item.typeLabel)
        }
        
        val filterLabel = if (filterQuery.isEmpty()) "Все целевые" else filterQuery
        tvStatus.text = "История [$filterLabel]: ${filteredList.size} из ${foundFilesList.size}"
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnScan.isEnabled = enabled
        btnRecentDownloads.isEnabled = enabled
    }

    private fun collectFiles(dir: File, targets: List<String>, label: String) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        for (file in files) {
            val matchByName = targets.firstOrNull { 
                file.name.contains(it, ignoreCase = true) 
            }
            if (matchByName != null) { 
                foundFilesList.add(FileResult(file, matchByName, label)) 
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                if (label == "[Память]" && file.name.equals("Android", ignoreCase = true)) { 
                    continue 
                }
                collectFiles(file, targets, label)
            }
        }
    }
        private fun displayFileItem(file: File, matchByName: String, typeLabel: String) {
        val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"
        
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val downloadDate = sdf.format(Date(file.lastModified()))
        
        val itemLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#141417"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#8A0000")) 
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            
            setOnClickListener {
                val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipManager.setPrimaryClip(android.content.ClipData.newPlainText("File Path", file.absolutePath))
                Toast.makeText(context, "📍 Путь скопирован! Открываю папку...", Toast.LENGTH_SHORT).show()
                openFolderInExplorer(file)
            }
        }
        
        itemLayout.addView(TextView(this@MainActivity).apply { 
            text = "$typeLabel Тег: ($matchByName)\n" +
                   "📄 Имя: ${file.name}\n" +
                   "📅 Скачан: $downloadDate | ⚖️ Вес: $fileSize\n" +
                   "📍 Путь: ${file.absolutePath}\n"
            textSize = 13f
            setTextColor(Color.parseColor("#39FF14")) 
        })
        
        if (typeLabel != "[Кэш игры]") {
            val buttonsLayout = LinearLayout(this@MainActivity).apply { 
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END 
            }
            buttonsLayout.addView(Button(this@MainActivity).apply { 
                text = "ИНЖЕКТ"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(32, 16, 32, 16)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#D90429"))
                    cornerRadius = 8f
                }
                setOnClickListener { 
                    thread { 
                        val success = injectFileToGame(file)
                        runOnUiThread { 
                            if (success) Toast.makeText(context, "Файл скопирован в кэш!", Toast.LENGTH_SHORT).show() 
                        } 
                    } 
                } 
            })
            itemLayout.addView(buttonsLayout)
        }
        llResultsContainer.addView(itemLayout)
    }

    private fun openFolderInExplorer(file: File) {
        val folder = file.parentFile ?: return
        val baseStorage = Environment.getExternalStorageDirectory().absolutePath
        val relativePath = folder.absolutePath.replace("$baseStorage/", "").replace(baseStorage, "")
        val docId = if (relativePath.isEmpty() || relativePath == folder.absolutePath) "primary:" else "primary:$relativePath"
        
        try {
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode(docId))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    setDataAndType(Uri.parse(folder.absolutePath), "*/*")
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivity(Intent.createChooser(fallbackIntent, "Выберите проводник"))
            } catch (ex: Exception) {
                Toast.makeText(this, "Не удалось открыть проводник автоматически", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun injectFileToGame(sourceFile: File): Boolean {
        try {
            val destDir = File(Environment.getExternalStorageDirectory(), "Android/data/$targetGamePackage/files")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, sourceFile.name)
            FileInputStream(sourceFile).use { input -> 
                FileOutputStream(destFile).use { output -> 
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) { 
                        output.write(buffer, 0, len) 
                    } 
                } 
            }
            return true
        } catch (e: Exception) { return false }
    }

    private fun t(tv: TextView, c: String, s: Float) { 
        tv.apply { 
            setTextColor(Color.parseColor(c))
            textSize = s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER 
        } 
    }
}
