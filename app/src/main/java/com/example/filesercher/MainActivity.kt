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
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
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
    
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private val textExtensions = listOf("txt", "log", "cfg", "json", "xml", "ini", "yaml", "yml", "conf")
    
    private var checkedDirsCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(40, 50, 40, 40)
        }

        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainLayout.addView(tvTitle)

        val inputShape = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1E"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#33333C"))
        }
        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла или text внутри..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 15f
            background = inputShape
            setPadding(30, 30, 30, 30)
        }
        mainLayout.addView(etSearchInput)

        val spacer = View(this).apply { minimumHeight = 30 }
        mainLayout.addView(spacer)

        val buttonShape = GradientDrawable().apply {
            setColor(Color.parseColor("#FFD700"))
            cornerRadius = 25f
        }
        btnScan = Button(this).apply {
            text = "ГЛУБОКИЙ ПОИСК ВЕЗДЕ"
            setTextColor(Color.BLACK)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
            background = buttonShape
            setPadding(0, 25, 0, 25)
        }
        mainLayout.addView(btnScan)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            setPadding(0, 20, 0, 10)
        }
        mainLayout.addView(progressBar)

        tvStatus = TextView(this).apply {
            text = "Приложение готово к работе"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 20, 0, 20)
        }
        mainLayout.addView(tvStatus)

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        llResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        btnScan.setOnClickListener {
            if (hasAllFilesPermission()) {
                runSearch()
            } else {
                requestAllFilesPermission()
            }
        }
    }

    private fun hasAllFilesPermission(): Boolean = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun runSearch() {
        llResultsContainer.removeAllViews()
        checkedDirsCount = 0
        
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        btnScan.isEnabled = false
        btnScan.alpha = 0.5f
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets, query)

            val androidDataDir = File(rootDir, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                searchFiles(androidDataDir, currentTargets, query)
            }

            runOnUiThread {
                btnScan.isEnabled = true
                btnScan.alpha = 1.0f
                progressBar.visibility = View.GONE
                
                if (llResultsContainer.childCount == 0) {
                    tvStatus.text = "Ничего не найдено. Проверено папок: $checkedDirsCount"
                } else {
                    tvStatus.text = "Успешно! Найдено совпадений: ${llResultsContainer.childCount}\nПроверено папок в системе: $checkedDirsCount"
                }
                Toast.makeText(this, "Поиск успешно завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File, targets: List<String>, originalQuery: String) {
        val files = dir.listFiles() ?: return
        
        checkedDirsCount++
        if (checkedDirsCount % 50 == 0) {
            runOnUiThread {
                tvStatus.text = "⚡ Идёт сканирование... Проверено папок: $checkedDirsCount"
            }
        }

        for (file in files) {
            var isMatched = false
            var matchReason = ""

            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }

            if (matchByName != null) {
                isMatched = true
                matchReason = "Имя: $matchByName"
            } else if (originalQuery.isNotEmpty() && file.isFile && textExtensions.contains(file.extension.lowercase(Locale.getDefault()))) {
                if (file.length() < 5 * 1024 * 1024) {
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        if (content.contains(originalQuery, ignoreCase = true)) {
                            isMatched = true
                            matchReason = "Текст внутри"
                        }
                    } catch (e: Exception) {}
                }
            }

            if (isMatched) {
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data")) "[Кэш: $parentName]" else "[Память]"
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "БЕЗ РАСШИРЕНИЯ" }
                val fileSize = formatFileSize(file.length())

                runOnUiThread {
                    val tvFileItem = TextView(this@MainActivity).apply {
                        text = "$prefix Найдено ($matchReason)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
                        textSize = 13f
                        setTextColor(Color.parseColor("#00FF66"))
                        setPadding(25, 25, 25, 25)
                        
                        val itemShape = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A1A1E"))
                            cornerRadius = 15f
                            setStroke(1, Color.parseColor("#2C2C35"))
                        }
                        background = itemShape
                    }

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 20) }
                    tvFileItem.layoutParams = params

                    tvFileItem.setOnClickListener { openFolderDirectly(file) }
                    llResultsContainer.addView(tvFileItem)
                }
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets, originalQuery)
            }
        }
    }

    private fun openFolderDirectly(file: File) {
        try {
            val folder = file.parentFile ?: return
            val relativePath = folder.absolutePath
                .replace("${Environment.getExternalStorageDirectory().absolutePath}/", "")
                .replace(Environment.getExternalStorageDirectory().absolutePath, "")

            val authority = "com.android.externalstorage.documents"
            val documentId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            val uri = DocumentsContract.buildDocumentUri(authority, documentId)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + 
                    
