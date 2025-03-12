package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


class CarCreatorScreen : ComponentActivity()
{
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ESPDatabase.getInstance(applicationContext)

        setContent {
            CarCreationScreen(
             database = database,
             onBack = { finish()}
            )
        }

    }
}


@Composable
fun CarCreationScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {

    LaunchedEffect (Unit) {

    }


    Row (
        modifier = Modifier.padding(12.dp)
    ){
        Text("Give your race car's details here:")
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {



    }



}