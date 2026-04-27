package qa.qu.trakn.parentapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.qu.trakn.parentapp.ui.theme.Blue
import qa.qu.trakn.parentapp.ui.theme.Green
import qa.qu.trakn.parentapp.ui.theme.Orange
import qa.qu.trakn.parentapp.ui.theme.Red

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onChangTag: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val s = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Tag", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Orange, modifier = Modifier.padding(bottom = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (s.tagId.isNotBlank()) s.tagId else "None",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onChangTag) {
                        Text("Change Tag")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Backend", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Orange, modifier = Modifier.padding(bottom = 8.dp))

                OutlinedTextField(
                    value = s.apiBaseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it); viewModel.save() },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.testConnection() },
                        enabled = !state.isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                        Text("Test Connection")
                    }
                }
                state.testResult?.let { result ->
                    val color = if (result.startsWith("✓")) Green else Red
                    Text(result, color = color, fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Orange, modifier = Modifier.padding(bottom = 4.dp))
                Text("TRAKN Parent — Indoor Localization", fontWeight = FontWeight.SemiBold)
                Text("Log-distance path loss + multilateration",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("Qatar University Senior Design — 2025",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}
