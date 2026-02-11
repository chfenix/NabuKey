package com.nabukey.ui.screens.settings.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

@Composable
fun TimeSetting(
    name: String,
    description: String = "",
    hour: Int?,
    minute: Int?,
    enabled: Boolean = true,
    validation: ((hour: Int, minute: Int) -> String?)? = null,
    onConfirmRequest: (hour: Int, minute: Int) -> Unit = { _, _ -> }
) {
    DialogSettingItem(
        name = name,
        description = description,
        value = formatTimeValue(hour, minute),
        enabled = enabled
    ) {
        TimeDialog(
            title = name,
            description = description,
            selectedHour = hour,
            selectedMinute = minute,
            validation = validation,
            onConfirmRequest = onConfirmRequest
        )
    }
}

@Composable
fun DialogScope.TimeDialog(
    title: String = "",
    description: String = "",
    selectedHour: Int?,
    selectedMinute: Int?,
    validation: ((hour: Int, minute: Int) -> String?)? = null,
    onConfirmRequest: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(selectedHour?.coerceIn(0, 23) ?: 0) }
    var minute by remember { mutableIntStateOf(selectedMinute?.coerceIn(0, 59) ?: 0) }
    val validationText = validation?.invoke(hour, minute)

    ActionDialog(
        title = title,
        description = description,
        confirmEnabled = validationText == null,
        onConfirmRequest = { onConfirmRequest(hour, minute) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberWheelPicker(
                value = hour,
                rangeStart = 0,
                rangeEnd = 23,
                formatter = { String.format(Locale.getDefault(), "%02d", it) },
                onValueChanged = { hour = it },
                modifier = Modifier.weight(1f)
            )
            Text(text = ":", style = MaterialTheme.typography.headlineMedium)
            NumberWheelPicker(
                value = minute,
                rangeStart = 0,
                rangeEnd = 59,
                formatter = { String.format(Locale.getDefault(), "%02d", it) },
                onValueChanged = { minute = it },
                modifier = Modifier.weight(1f)
            )
        }
        if (!validationText.isNullOrBlank()) {
            Text(
                text = validationText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NumberWheelPicker(
    value: Int,
    rangeStart: Int,
    rangeEnd: Int,
    formatter: (Int) -> String,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            NumberPicker(context).apply {
                minValue = rangeStart
                maxValue = rangeEnd
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                setOnValueChangedListener { _, _, newValue ->
                    onValueChanged(newValue)
                }
            }
        },
        update = { picker ->
            val displayed = (rangeStart..rangeEnd).map(formatter).toTypedArray()
            picker.displayedValues = null
            picker.minValue = rangeStart
            picker.maxValue = rangeEnd
            picker.displayedValues = displayed
            if (picker.value != value) {
                picker.value = value
            }
        }
    )
}

private fun formatTimeValue(hour: Int?, minute: Int?): String {
    if (hour == null || minute == null) return ""
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}
