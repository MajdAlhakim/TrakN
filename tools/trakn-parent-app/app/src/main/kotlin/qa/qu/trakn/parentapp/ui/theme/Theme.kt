package qa.qu.trakn.parentapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFF97316)
val Blue   = Color(0xFF3B82F6)
val Green  = Color(0xFF22C55E)
val Yellow = Color(0xFFEAB308)
val Red    = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary          = Blue,
    secondary        = Orange,
    tertiary         = Green,
    background       = Color(0xFF080C12),
    surface          = Color(0xFF0E1420),
    surfaceVariant   = Color(0xFF141B28),
    onBackground     = Color(0xFFC8D3E0),
    onSurface        = Color(0xFFC8D3E0),
    onPrimary        = Color.White,
    outline          = Color(0xFF233047),
    errorContainer   = Color(0xFF2D1010),
    onErrorContainer = Red,
)

@Composable
fun TRAKNParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
