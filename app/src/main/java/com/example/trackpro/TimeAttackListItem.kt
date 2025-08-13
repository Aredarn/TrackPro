package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

class TimeAttackListItem :ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContent{
        }
    }
}

@Composable
fun TimeAttackListItemScreen(navController: NavController,database: ESPDatabase, sessionId : Long)
{


}