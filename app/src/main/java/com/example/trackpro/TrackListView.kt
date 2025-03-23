package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.ViewModels.TrackViewModel

class TrackListView : ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent{
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(navController: NavController,viewModel: TrackViewModel )
{
    val tracks by viewModel.tracks.collectAsState()

    Scaffold(
            topBar = { TopAppBar(title = { Text("Your recorded tracks") }) }
    ) {paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if(tracks.isEmpty())
            {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ){
                    Text("No tracks available", fontSize = 22.sp, color = Color.Gray)
                }
            }
            else
            {
                LazyColumn(modifier = Modifier.padding(16.dp))
                {
                    items(tracks) { track ->
                        TrackCard(track,navController)
                    }
                }

            }
        }
    }
}



@Composable
fun TrackCard(track:TrackMainData,navController: NavController)
{
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                navController.navigate("graph/${track.trackId}")
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    )
    {
        Column (modifier = Modifier.padding(16.dp)) {
            Text(
                text = track.trackName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text("Country: ${track.country}", fontSize = 16.sp, color = Color.DarkGray)
            Box(
                modifier = Modifier
                    .border(width = 4.dp, color = Color.Gray, shape = RoundedCornerShape(16.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd // Use contentAlignment for Box
            ) {
                Text(
                    text = "Length: ${track.totalLength}",
                    textAlign = TextAlign.Center, // Apply textAlign inside Text
                    modifier = Modifier.fillMaxWidth() // Ensures text alignment takes effect
                )
            }

        }

    }


}



