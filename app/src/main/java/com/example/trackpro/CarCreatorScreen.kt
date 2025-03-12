package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Database


class CarCreatorScreen : ComponentActivity()
{
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ESPDatabase.getInstance(applicationContext)

        setContent {

        }

    }
}


fun CarCreationScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {




}