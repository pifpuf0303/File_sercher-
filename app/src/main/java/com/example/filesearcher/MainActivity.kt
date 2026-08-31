package com.example.filesercher

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val searchButton = Button(this)
        searchButton.text = "Запустить поиск"
        searchButton.setOnClickListener {
            Toast.makeText(this, "Поиск запущен", Toast.LENGTH_SHORT).show()
        }
        
        setContentView(searchButton)
    }
}
