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
    private var checkedDirsCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Используем стандартный контейнер, но задаем ему темный цвет фона
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(32, 32, 32, 32)
        }

        // Красивый золотой заголовок ME PROJECT
        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        }
        mainLayout.addView(tvTitle)

        // Поле ввода с подсказкой
        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла для поиска..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        mainLayout.addView(etSearchInput)

        // Наша классическая, надежная кнопка Android — она появится 100%!
        btnScan = Button(this).apply {
            text = "ГЛУБОКИЙ ПОИСК ВЕЗДЕ"
        }
        mainLayout.addView(btnScan)

        // Стандартная крутилка прогресса
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        // Статус процесса
        tvStatus = TextView(this).apply {
            text = "Приложение готово к работе"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(tvStatus)

        // Область прокрутки результатов
        val scrollView = ScrollView(this)
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
        progressBar.visibility = View.VISIBLE

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets)

            val androidDataDir = File(rootDir, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                searchFiles(androidDataDir, currentTargets)
            }

            runOnUiThread {
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${llResultsContainer.childCount}"
                Toast.makeText(this@MainActivity, "Поиск завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File, targets: List<String>) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        
        if (checkedDirsCount % 100 == 0) {
            runOnUiThread {
                tvStatus.text = "⚡ Идёт сканирование папок... [$checkedDirsCount]"
            }
        }

        for (file in files) {
            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }

            if (matchByName != null) {
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data")) "[Кэш: $parentName]" else "[Память]"
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }

                runOnUiThread {
                    // Оставляем красивый неоново-зеленый дизайн карточек найденных файлов
                    val tvFileItem = TextView(this@MainActivity).apply {
                        text = "$prefix Найдено ($matchByName)\n📄 Тип: .$fileType\n📍 Путь: ${file.absolutePath}\n"
                        textSize = 13f
                        setTextColor(Color.parseColor("#00FF66")) // Неоново-зеленый текст
                        setPadding(20, 20, 20, 20)
                        
                        val itemShape = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A1A1E"))
                            cornerRadius = 12f
                            setStroke(1, Color.parseColor("#2C2C35"))
                        }
                        background = itemShape
                    }

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 16) }
                    tvFileItem.layoutParams = params

                    // ФУНКЦИЯ ПЕРЕХОДА: клик перенаправляет прямо в нужную папку проводника
                    tvFileItem.setOnClickListener { openFolderDirectly(file) }
                    llResultsContainer.addView(tvFileItem)
                }
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
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
                    file.parentFile.absolutePath.replace("${Environment.getExternalStorageDirectory().absolutePath}/", ""))
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fallbackUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Ошибка перехода в папку.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
