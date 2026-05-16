package com.example.courseproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.courseproject.presentation.App
import com.example.courseproject.ui.theme.CourseProjectTheme

/** Единственная Activity приложения — точка входа в Compose-интерфейс. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CourseProjectApp).container
        setContent {
            CourseProjectTheme {
                App(container)
            }
        }
    }
}
