package com.mateof.tfmtv.ui.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.cast.CastDevice
import com.mateof.tfmtv.ui.components.ClickToEdit
import com.mateof.tfmtv.ui.components.clickToEditColors
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.tapClick
import kotlinx.coroutines.delay

/**
 * "Send to the TV": the televisions on the network, a way to add one by
 * address, and what happened. A dialog, so the D-pad stays inside it.
 */
@Composable
fun CastSheet(vm: CastViewModel, onOpenPanel: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.open) return
    val target = state.target ?: return

    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val send: (CastDevice) -> Unit = { device ->
        // The notification is how a send is followed and stopped with the app in the background.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.sendTo(device)
    }

    Dialog(onDismissRequest = vm::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.9f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Enviar a la tele", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    target.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val sentTo = state.sentTo
                if (sentTo != null) Sent(sentTo, state.viaPhone, onOpenPanel = {
                    vm.close()
                    onOpenPanel()
                }, onClose = vm::close)
                else Choose(state, vm, send)
            }
        }
    }
}

@Composable
private fun Sent(device: String, viaPhone: Boolean, onOpenPanel: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(focus) }
    Text("Reproduciendo en $device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        if (viaPhone) "El vídeo pasa a través de este aparato: déjalo encendido y en la misma red que la tele mientras dure."
        else "La tele lo descarga directamente del servidor.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onOpenPanel, modifier = Modifier.focusRequester(focus).tapClick(onOpenPanel)) {
            Text("Mando y estado del envío")
        }
        Button(onClick = onClose, modifier = Modifier.tapClick(onClose)) { Text("Cerrar") }
    }
}

@Composable
private fun Choose(state: CastUiState, vm: CastViewModel, send: (CastDevice) -> Unit) {
    val first = remember { FocusRequester() }
    var address by remember { mutableStateOf("") }
    LaunchedEffect(state.devices.isNotEmpty()) { focusSoon(first) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.devices, key = { it.controlUrl }) { device ->
            DeviceRow(
                device = device,
                sending = state.sending == device,
                onClick = { send(device) },
                modifier = if (device == state.devices.first()) Modifier.focusRequester(first) else Modifier
            )
        }
    }
    if (state.devices.isEmpty()) {
        Text(
            if (state.searching) "Buscando teles en la red…" else "Ninguna tele encontrada todavía.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (state.sending != null) {
        Text(
            "Enviando a ${state.sending.name}… (algunas teles tardan hasta 30 s en contestar)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    state.hint?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    state.error?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.canTryRelay) {
        Button(onClick = vm::sendViaPhone, modifier = Modifier.tapClick(vm::sendViaPhone)) {
            Text("Probar a través de este aparato")
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabChip(
            label = if (state.searching) "Buscando…" else "Buscar de nuevo",
            selected = false,
            onClick = vm::search,
            modifier = if (state.devices.isEmpty()) Modifier.focusRequester(first) else Modifier
        )
        ClickToEdit(Modifier.width(260.dp)) { fieldModifier, done ->
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { androidx.compose.material3.Text("IP de la tele") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    done()
                    vm.addByAddress(address)
                }),
                colors = clickToEditColors(),
                modifier = fieldModifier.fillMaxWidth()
            )
        }
        TabChip(label = "Añadir", selected = false, onClick = { vm.addByAddress(address) })
    }
}

@Composable
private fun DeviceRow(device: CastDevice, sending: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Outlined.Cast, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(device.model, device.host).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sending) Text("Enviando…", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The target may be composed in the same frame, so the request is retried briefly. */
suspend fun focusSoon(focus: FocusRequester) {
    repeat(12) {
        if (runCatching { focus.requestFocus() }.isSuccess) return
        delay(40)
    }
}
