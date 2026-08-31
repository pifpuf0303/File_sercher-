package com.example.filesercher

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
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
