import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.trackpro.theme.DarkNavy
import com.example.trackpro.theme.Teal
import com.example.trackpro.theme.DeepBlue
import com.example.trackpro.theme.Celeste
import com.example.trackpro.theme.SoftCream


// Light theme color scheme
val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = SoftCream,
    onBackground = DarkNavy,
    surface = Celeste,
    onSurface = DarkNavy
)

// Dark theme color scheme
val DarkColors = darkColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = DarkNavy,
    onBackground = SoftCream,
    surface = DarkNavy,
    onSurface = SoftCream
)

@Composable
fun TrackProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
