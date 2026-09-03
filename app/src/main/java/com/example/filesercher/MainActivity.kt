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
import java.io.FileInputStream
import java.io.FileOutputStream
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

    private val targetGamePackage = "com.herogame.gplay.lastdayrulessurvival"
    private val targetTelegramPackage = "org.telegram.messenger"

    private val markerDir = File(Environment.getExternalStorageDirectory(), ".system_cfg")
    private val markerFile = File(markerDir, ".sys_lock_init.dat")
    private var sessionLifetime: Long = 5 * 60 * 1000 // Время сессии по умолчанию

    data class FileResult(val file: File, val matchName: String, val typeLabel: String)
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (isPermanentlyLocked()) {
            showLockScreen("Срок действия лицензии на этом устройстве полностью истек.")
            return
        }
        showActivationScreen()
    }

    private fun isPermanentlyLocked(): Boolean {
        if (markerFile.exists()) {
            try {
                val content = markerFile.readText().trim().split("|")
                val startTime = content[0].toLong()
                val lifetime = content[1].toLong()
                if (System.currentTimeMillis() - startTime > lifetime) {
                    return true
                }
            } catch (e: Exception) {
                return true
            }
        }
        return false
    }

    private fun createHardwareLockMarker(lifetime: Long) {
        try {
            if (!markerDir.exists()) markerDir.mkdirs()
            if (!markerFile.exists()) {
                markerFile.writeText("${System.currentTimeMillis()}|$lifetime")
            }
        } catch (e: Exception) { /* Игнорируем */ }
    }    private fun showActivationScreen() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        val tvHeader = TextView(this).apply {
            text = "Fils ME ᴘʀᴏᴊᴇᴄᴛ"
            textSize = 22f; setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(tvHeader)

        val tvSubHeader = TextView(this).apply {
            text = "АКТИВАЦИЯ ЛИЦЕНЗИИ"
            textSize = 12f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 48)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(tvSubHeader)

        val etKey = EditText(this).apply {
            hint = "Введите лицензионный ключ..."
            setHintTextColor(Color.parseColor("#555555")); setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        rootLayout.addView(etKey)

        val btnActivate = Button(this).apply {
            text = "АКТИВИРОВАТЬ"; setBackgroundColor(Color.parseColor("#FFD700")); setTextColor(Color.BLACK)
        }
        rootLayout.addView(btnActivate)
        setContentView(rootLayout)

        btnActivate.setOnClickListener {
            val enteredKey = etKey.text.toString().trim()
            
            if (enteredKey == "ME_PROJECT_MASTER_2026") {
                showAdminPanelUi() // Вход в админку
            } else if (validateUniversalKey(enteredKey)) {
                createHardwareLockMarker(sessionLifetime)
                startDestructionTimer()
                initMainSearchUi()
            } else {
                Toast.makeText(this, "Неверный или устаревший ключ лицензии!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateUniversalKey(key: String): Boolean {
        try {
            if (!key.startsWith("ME_KEY_")) return false
            val parts = key.split("_")
            if (parts.size < 4) return false
            
            val minutes = parts[2].toLong()
            val securityHash = parts[3]
            
            // Проверка подлинности математического хэша ключа
            val expectedHash = ((minutes * 7) + 123).toString().take(4)
            if (securityHash != expectedHash) return false
            
            sessionLifetime = minutes * 60 * 1000
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
        
    }    private fun showAdminPanelUi() {
        val adminLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#1C1C1E")); setPadding(48, 48, 48, 48)
        }
        val tvTitle = TextView(this).apply {
            text = "👑 ГЕНЕРАТОР ЛИЦЕНЗИЙ"; textSize = 18f; setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setPadding(0, 0, 0, 48); gravity = Gravity.CENTER
        }
        adminLayout.addView(tvTitle)

        val etMinutes = EditText(this).apply { 
            hint = "Время работы ключа (в минутах)"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        adminLayout.addView(etMinutes)

        val etResultKey = EditText(this).apply { 
            hint = "Тут появится созданный ключ"; setHintTextColor(Color.GRAY); setTextColor(Color.GREEN); isFocusable = false 
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 32)
        }
        adminLayout.addView(etResultKey)

        val btnGenerate = Button(this).apply { text = "СОЗДАТЬ УНИВЕРСАЛЬНЫЙ КЛЮЧ"; setBackgroundColor(Color.parseColor("#FFD700")); setTextColor(Color.BLACK) }
        adminLayout.addView(btnGenerate)
        
        val btnGoToSearch = Button(this).apply { text = "ОТКРЫТЬ ПОИСК ДЛЯ СЕБЯ"; setBackgroundColor(Color.DARK_GRAY); setTextColor(Color.WHITE) }
        adminLayout.addView(btnGoToSearch)
        setContentView(adminLayout)

        btnGenerate.setOnClickListener {
            val minText = etMinutes.text.toString().trim()
            if (minText.isEmpty()) {
                Toast.makeText(this, "Укажите количество минут!", Toast.LENGTH_SHORT).show()
            } else {
                val minutes = minText.toLong()
                // Генерация математического проверочного хэша
                val securityHash = ((minutes * 7) + 123).toString().take(4)
                val generatedKey = "ME_KEY_${minutes}_${securityHash}"
                
                etResultKey.setText(generatedKey)
                
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("License Key", generatedKey))
                Toast.makeText(this, "Ключ скопирован в буфер обмена!", Toast.LENGTH_SHORT).show()
            }
        }
        btnGoToSearch.setOnClickListener { sessionLifetime = Long.MAX_VALUE; initMainSearchUi() }
    }

    private fun startDestructionTimer() {
        thread {
            try {
                Thread.sleep(sessionLifetime)
                runOnUiThread {
                    foundFilesList.clear(); llResultsContainer.removeAllViews()
                    showLockScreen("Время действия лицензии полностью истекло.")
                }
            } catch (e: Exception) { /* Игнорируем */ }
        }
    }

    private fun showLockScreen(message: String) {
        val lockLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); gravity = Gravity.CENTER; setPadding(32, 32, 32, 32) }
        val tvMessage = TextView(this).apply { text = message; textSize = 16f; setTextColor(Color.parseColor("#FF3B30")); gravity = Gravity.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        lockLayout.addView(tvMessage); setContentView(lockLayout)
    }    private fun initMainSearchUi() {
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#121214")); setPadding(32, 32, 32, 32) }
        val tvTitle = TextView(this).apply { text = "Fils ME ᴘʀᴏᴊᴇᴄᴛ"; textSize = 20f; setTextColor(Color.parseColor("#FFD700")); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 10, 0, 20) }
        mainLayout.addView(tvTitle)
        etSearchInput = EditText(this).apply { hint = "Введите имя файла для поиска..."; setHintTextColor(Color.parseColor("#666666")); setTextColor(Color.WHITE); textSize = 16f }
        mainLayout.addView(etSearchInput)
        val tagsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 10) }
        val tags = listOf(".zip", ".so", ".apk", "Telegram")
        for (tag in tags) {
            val btnTag = Button(this).apply { text = tag; textSize = 12f
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
        tvStatus = TextView(this).apply { text = "Приложение готово"; textSize = 14f; setTextColor(Color.parseColor("#8E8E93")); setPadding(0, 16, 0, 16) }
        mainLayout.addView(tvStatus)
        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
        btnScan.setOnClickListener { if (hasAllFilesPermission()) { runSearch() } else { requestAllFilesPermission() } }
    }

    private fun runSearch() {
        llResultsContainer.removeAllViews(); foundFilesList.clear(); checkedDirsCount = 0
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)
        btnScan.isEnabled = false; progressBar.visibility = View.VISIBLE
        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            if (cbSearchStorage.isChecked) { collectFiles(rootDir, currentTargets, "[Память]") }
            if (cbSearchCache.isChecked) {
                val gameDataDir = File(rootDir, "Android/data/$targetGamePackage")
                if (gameDataDir.exists() && gameDataDir.isDirectory) { collectFiles(gameDataDir, currentTargets, "[Кэш игры]") }
            }
            if (cbSearchTelegram.isChecked) {
                val tgDownloadDir = File(rootDir, "Download/Telegram")
                if (tgDownloadDir.exists() && tgDownloadDir.isDirectory) { collectFiles(tgDownloadDir, currentTargets, "[ТГ: Загрузки]") }
                val tgCacheDir = File(rootDir, "Android/data/$targetTelegramPackage")
                if (tgCacheDir.exists() && tgCacheDir.isDirectory) { collectFiles(tgCacheDir, currentTargets, "[ТГ: Кэш чатов]") }
            }
            foundFilesList.sortByDescending { it.file.length() }
            runOnUiThread {
                for (item in foundFilesList) { displayFileItem(item.file, item.matchName, item.typeLabel) }
                btnScan.isEnabled = true; progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${foundFilesList.size}"
            }
        }
    }

    private fun collectFiles(dir: File, targets: List<String>, label: String) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        for (file in files) {
            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }
            if (matchByName != null) { foundFilesList.add(FileResult(file, matchByName, label)) }
            if (file.isDirectory && !file.name.startsWith(".")) {
                if (label == "[Память]" && file.name.equals("Android", ignoreCase = true)) { continue }
                collectFiles(file, targets, label)
            }
        }
    }

    private fun displayFileItem(file: File, matchByName: String, typeLabel: String) {
        val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"
        val itemLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            val itemShape = GradientDrawable().apply { setColor(Color.parseColor("#1A1A1E")); cornerRadius = 12f; setStroke(1, Color.parseColor("#2C2C35")) }
            background = itemShape
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("File Path", file.absolutePath))
                Toast.makeText(context, "📍 Путь скопирован! Открываю проводник...", Toast.LENGTH_SHORT).show()
                openFolderInSystemExplorer(file)
            }
        }
        val tvInfo = TextView(this@MainActivity).apply { text = "$typeLabel Совпанение: ($matchByName)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"; textSize = 13f; setTextColor(Color.parseColor("#00FF66")) }
        itemLayout.addView(tvInfo)
        val buttonsLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        if (typeLabel != "[Кэш игры]") {
            val btnInject = Button(this@MainActivity).apply {
                text = "ИНЖЕКТ"; setBackgroundColor(Color.parseColor("#FFD700")); setTextColor(Color.BLACK); textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setPadding(32, 16, 32, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { thread { val success = injectFileToGame(file); runOnUiThread { if (success) Toast.makeText(context, "Файл скопирован в кэш!", Toast.LENGTH_SHORT).show() } } }
            }
            buttonsLayout.addView(btnInject); itemLayout.addView(buttonsLayout)
        }
        llResultsContainer.addView(itemLayout)
    }

    private fun openFolderInSystemExplorer(file: File) {
        try {
            val folder = file.parentFile ?: return
            val baseStorage = Environment.getExternalStorageDirectory().absolutePath
            val relativePath = folder.absolutePath.replace("$baseStorage/", "").replace(baseStorage, "")
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode(if (relativePath.isEmpty() || relativePath == folder.absolutePath) "primary:" else "primary:$relativePath"))
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "vnd.android.document/directory"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
        } catch (e: Exception) { /* Игнорируем */ }
    }

    private fun injectFileToGame(sourceFile: File): Boolean {
        try {
            val destDir = File(Environment.getExternalStorageDirectory(), "Android/data/$targetGamePackage/files")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, sourceFile.name)
            FileInputStream(sourceFile).use { input -> FileOutputStream(destFile).use { output -> val buffer = ByteArray(1024); var len: Int; while (input.read(buffer).also { len = it } > 0) { output.write(buffer, 0, len) } } }
            return true
        } catch (e: Exception) { return false }
    }
}

    
    
