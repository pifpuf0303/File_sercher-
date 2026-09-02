package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var etSearchInput: EditText
    private lateinit var btnScan: Button
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var cbSearchStorage: CheckBox
    private lateinit var cbSearchCache: CheckBox
    private lateinit var cbSearchTelegram: CheckBox
    
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private var checkedDirsCount = 0
    private val foundFilesList = mutableListOf<FileResult>()

    // Константы пакетов для прямого чтения
    private val targetGamePackage = "com.herogame.gplay.lastdayrulessurvival"
    private val targetTelegramPackage = "org.telegram.messenger"

    data class FileResult(val file: File, val matchName: String, val typeLabel: String)
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(32, 32, 32, 32)
        }

        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }
        mainLayout.addView(tvTitle)

        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла для поиска..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        mainLayout.addView(etSearchInput)

        val tagsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        
        val tags = listOf(".zip", ".so", ".apk", "Telegram")
        for (tag in tags) {
            val btnTag = Button(this).apply {
                text = tag
                textSize = 12f
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(4, 0, 4, 0) }
                layoutParams = params
                setOnClickListener { etSearchInput.setText(tag); etSearchInput.setSelection(tag.length) }
            }
            tagsLayout.addView(btnTag)
        }
        mainLayout.addView(tagsLayout)

        cbSearchStorage = CheckBox(this).apply { text = "Искать в общей памяти"; setTextColor(Color.WHITE); isChecked = true }
        mainLayout.addView(cbSearchStorage)

        cbSearchCache = CheckBox(this).apply { text = "Искать в кэше игры Last Island"; setTextColor(Color.WHITE); isChecked = true }
        mainLayout.addView(cbSearchCache)

        cbSearchTelegram = CheckBox(this).apply { text = "Искать в чатах Telegram"; setTextColor(Color.WHITE); isChecked = true }
        mainLayout.addView(cbSearchTelegram)

        btnScan = Button(this).apply { text = "ГЛУБОКИЙ ПОИСК" }
        mainLayout.addView(btnScan)

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        mainLayout.addView(progressBar)

        tvStatus = TextView(this).apply { text = "Приложение готово к работе"; textSize = 14f; setTextColor(Color.parseColor("#8E8E93")); setPadding(0, 16, 0, 16) }
        mainLayout.addView(tvStatus)

        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        btnScan.setOnClickListener {
            if (!cbSearchStorage.isChecked && !cbSearchCache.isChecked && !cbSearchTelegram.isChecked) {
                Toast.makeText(this, "Выберите область поиска!", Toast.LENGTH_SHORT).show()
            } else {
                if (hasAllFilesPermission()) { runSearch() } else { requestAllFilesPermission() }
            }
        }
    }

    private fun hasAllFilesPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    private fun requestAllFilesPermission() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION); startActivity(intent) } }
        private fun runSearch() {
        llResultsContainer.removeAllViews(); foundFilesList.clear(); checkedDirsCount = 0
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)
        btnScan.isEnabled = false; progressBar.visibility = View.VISIBLE
        
        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            
            // 1. Поиск в общей памяти
            if (cbSearchStorage.isChecked) { 
                collectFiles(rootDir, currentTargets, "[Память]") 
            }
            
            // 2. Прямой поиск в кэше игры Last Island
            if (cbSearchCache.isChecked) {
                val gameDataDir = File(rootDir, "Android/data/$targetGamePackage")
                if (gameDataDir.exists() && gameDataDir.isDirectory) { 
                    collectFiles(gameDataDir, currentTargets, "[Кэш игры]") 
                }
            }
            
            // 3. Сканирование папок Telegram
            if (cbSearchTelegram.isChecked) {
                // А. Проверяем сохраненные загрузки (Download/Telegram)
                val tgDownloadDir = File(rootDir, "Download/Telegram")
                if (tgDownloadDir.exists() && tgDownloadDir.isDirectory) {
                    collectFiles(tgDownloadDir, currentTargets, "[ТГ: Загрузки]")
                }
                // Б. Проверяем внутренний скрытый кэш чатов мессенджера
                val tgCacheDir = File(rootDir, "Android/data/$targetTelegramPackage")
                if (tgCacheDir.exists() && tgCacheDir.isDirectory) {
                    collectFiles(tgCacheDir, currentTargets, "[ТГ: Кэш чатов]")
                }
            }
            
            foundFilesList.sortByDescending { it.file.length() }
            
            runOnUiThread {
                for (item in foundFilesList) { displayFileItem(item.file, item.matchName, item.typeLabel) }
                btnScan.isEnabled = true; progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${foundFilesList.size}\nПроверено директорий: $checkedDirsCount"
                Toast.makeText(this@MainActivity, "Поиск завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun collectFiles(dir: File, targets: List<String>, label: String) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        if (checkedDirsCount % 100 == 0) { runOnUiThread { tvStatus.text = "⚡ Идёт сканирование... [$checkedDirsCount]" } }
        for (file in files) {
            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }
            if (matchByName != null) { foundFilesList.add(FileResult(file, matchByName, label)) }
            if (file.isDirectory && !file.name.startsWith(".")) {
                // При поиске по всей памяти пропускаем системную Android, чтобы не дублировать проверки
                if (label == "[Память]" && file.name.equals("Android", ignoreCase = true)) { continue }
                collectFiles(file, targets, label)
            }
        }
    }

    private fun displayFileItem(file: File, matchByName: String, typeLabel: String) {
        val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"
        
        val tvFileItem = TextView(this@MainActivity).apply {
            text = "$typeLabel Совпадение: ($matchByName)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
            textSize = 13f
            setTextColor(Color.parseColor("#00FF66"))
            setPadding(20, 20, 20, 20)
            val itemShape = GradientDrawable().apply { setColor(Color.parseColor("#1A1A1E")); cornerRadius = 12f; setStroke(1, Color.parseColor("#2C2C35")) }
            background = itemShape
            
            setOnClickListener {
                Toast.makeText(context, "Файл обнаружен в директории: $typeLabel", Toast.LENGTH_SHORT).show()
            }
        }
        
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        tvFileItem.layoutParams = params
        llResultsContainer.addView(tvFileItem)
    }
}
