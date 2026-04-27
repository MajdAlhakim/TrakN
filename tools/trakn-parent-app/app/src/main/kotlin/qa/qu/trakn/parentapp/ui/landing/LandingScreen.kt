package qa.qu.trakn.parentapp.ui.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.qu.trakn.parentapp.ui.theme.Blue
import qa.qu.trakn.parentapp.ui.theme.Orange

@Composable
fun LandingScreen(
    initialTagId: String,
    onStartTracking: (tagId: String) -> Unit,
) {
    var tagId by remember { mutableStateOf(initialTagId) }
    val valid  = tagId.trim().isNotBlank()

    fun submit() {
        if (!valid) return
        onStartTracking(tagId.trim().uppercase())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = "TRAKN",
            fontSize   = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = Orange,
        )
        Text(
            text     = "Indoor Child Localization",
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        Spacer(Modifier.height(48.dp))

        Text(
            text       = "Enter your child's Tag ID",
            fontWeight = FontWeight.SemiBold,
            fontSize   = 16.sp,
            modifier   = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value         = tagId,
            onValueChange = { tagId = it.uppercase() },
            placeholder   = { Text("e.g. TRAKN-A5BA", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction      = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick  = { submit() },
            enabled  = valid,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Blue),
        ) {
            Text("Start Tracking", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
