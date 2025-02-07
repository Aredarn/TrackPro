package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.selects.SelectInstance


class DragTimesList() : ComponentActivity()
{
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ESPDatabase.getInstance(applicationContext)
    }
}

@Composable
fun DragTimesListView()
{

    LaunchedEffect(Unit) {
    }



}