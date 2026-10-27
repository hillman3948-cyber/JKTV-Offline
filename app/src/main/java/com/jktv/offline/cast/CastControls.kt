package com.jktv.offline.cast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun CastControls(controller: CastController) {
    val status by controller.status.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true; controller.search() }, modifier = Modifier.size(48.dp)) {
        Icon(if (status.phase == CastPhase.Connected) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = "Cast to TV: ${status.phase}", tint = MaterialTheme.colorScheme.primary)
    }
    if (!open) return
    val devices by controller.discovery.devices.collectAsState()
    val discoveryError by controller.discovery.error.collectAsState()
    var selected by remember { mutableStateOf<CastDevice?>(null) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    // Pairing secrets intentionally are not persisted in saved state or preferences.
    var key by remember { mutableStateOf("") }
    var compensation by remember { mutableFloatStateOf(controller.compensationMs.toFloat()) }
    var tvAudio by remember { mutableStateOf(status.audioOnTv) }
    LaunchedEffect(status.audioOnTv) { tvAudio = status.audioOnTv }
    val busy = status.phase in setOf(CastPhase.Connecting, CastPhase.Reconnecting, CastPhase.Connected)
    DisposableEffect(Unit) { onDispose { controller.stopSearch() } }
    Dialog(onDismissRequest = { open = false }) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cast to TV", style = MaterialTheme.typography.headlineSmall)
                Text("${status.phase}${status.device.takeIf { it.isNotEmpty() }?.let { " • $it" }.orEmpty()}")
                if (status.detail.isNotEmpty()) Text(status.detail, style = MaterialTheme.typography.bodyMedium)
                discoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Requires JKTV TV Stage on Android TV or Google TV, on the same Wi-Fi. Generic Chromecast, Samsung and LG receivers are not supported.",
                    style = MaterialTheme.typography.bodySmall)
                if (!busy) {
                    devices.forEach { device ->
                        OutlinedButton(onClick = { selected = device; host = device.host; port = device.port.toString() }, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (selected == device) "Selected: " else "Available: "}${device.name}")
                        }
                    }
                    if (devices.isEmpty()) Text("No JKTV receivers found yet. Open TV Stage on your TV, or enter its displayed address.")
                    OutlinedTextField(host, { host = it.trim(); selected = null }, label = { Text("TV address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("TV port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(key, { key = it }, label = { Text("Pairing key shown on TV") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        controller.connect(selected ?: CastDevice(host, host, port.toInt()), key)
                    }, enabled = host.isNotBlank() && port.toIntOrNull() in 1..65535 && runCatching { CastWire.secret(key) }.isSuccess,
                        modifier = Modifier.fillMaxWidth()) { Text("Connect") }
                }
                if (status.phase == CastPhase.Connected) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Play audio on TV", modifier = Modifier.weight(1f))
                        Switch(tvAudio, { tvAudio = it; controller.setTvAudio(it) })
                    }
                    Text(if (tvAudio) "Phone MIDI audio is muted." else "Audio plays on the phone; TV shows the Stage.", style = MaterialTheme.typography.bodySmall)
                    Text("Lyrics compensation: ${compensation.toInt()} ms")
                    Slider(compensation, { compensation = it; controller.setCompensation(it.toLong()) }, valueRange = -2000f..2000f, steps = 79)
                    Text("Round trip: ${status.rttMs} ms • TV clock difference: ${status.driftMs} ms", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) TextButton(onClick = { controller.disconnect() }) { Text("Disconnect") }
                    else TextButton(onClick = { controller.retry() }) { Text("Retry") }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
            }
        }
    }
}
