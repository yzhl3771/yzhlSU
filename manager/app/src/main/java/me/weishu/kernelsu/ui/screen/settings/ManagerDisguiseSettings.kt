package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.util.ManagerDisguise

@Composable
fun ManagerDisguiseSettings() {
    var editing by rememberSaveable { mutableStateOf(false) }
    var enabling by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val count = input.toIntOrNull()?.takeIf { it in 2..99 }

    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        item {
            SegmentedSwitchItem(
                icon = Icons.Filled.VisibilityOff,
                title = stringResource(R.string.settings_manager_disguise),
                summary = stringResource(R.string.settings_manager_disguise_summary, ManagerDisguise.tapCount),
                checked = ManagerDisguise.enabled,
                onCheckedChange = { if (it) enabling = true else ManagerDisguise.updateEnabled(false) },
            )
        }
        item {
            SegmentedListItem(
                onClick = { input = ManagerDisguise.tapCount.toString(); editing = true },
                headlineContent = { Text(stringResource(R.string.settings_manager_disguise_taps)) },
                supportingContent = { Text(ManagerDisguise.tapCount.toString()) },
            )
        }
    }
    if (editing || enabling) {
        AlertDialog(
            onDismissRequest = { editing = false; enabling = false },
            title = { Text(stringResource(if (editing) R.string.settings_manager_disguise_taps else R.string.settings_manager_disguise)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_manager_disguise_summary, ManagerDisguise.tapCount))
                    if (editing) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { value -> if (value.length <= 2 && value.all { it in '0'..'9' }) input = value },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = count == null,
                            label = { Text("2-99") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editing || count != null,
                    onClick = {
                        if (editing) count?.let(ManagerDisguise::updateTapCount)
                        if (enabling) ManagerDisguise.updateEnabled(true)
                        editing = false
                        enabling = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false; enabling = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
