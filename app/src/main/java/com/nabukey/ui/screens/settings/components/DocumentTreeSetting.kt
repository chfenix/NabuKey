import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.nabukey.ui.screens.settings.components.SettingItem

@Composable
fun DocumentTreeSetting(
    name: String,
    description: String = "",
    value: Uri?,
    enabled: Boolean = true,
    onResult: (Uri?) -> Unit
) {
    val context = LocalContext.current
    val displayValue = remember(value) {
        if (value != null) DocumentFile.fromTreeUri(
            context,
            value
        )?.name else null
    }

    ActivityResultSetting(
        name = name,
        description = description,
        value = displayValue ?: "",
        enabled = enabled,
        contract = ActivityResultContracts.OpenDocumentTree(),
        input = value,
        onResult = onResult
    )
}

@Composable
fun <I, O> ActivityResultSetting(
    name: String,
    description: String = "",
    value: String,
    enabled: Boolean = true,
    contract: ActivityResultContract<I, O>,
    input: I,
    onResult: (O?) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = contract,
        onResult = onResult
    )
    val modifier =
        if (enabled) Modifier.clickable { launcher.launch(input) } else Modifier.alpha(0.5f)
    SettingItem(
        modifier = modifier,
        name = name,
        description = description,
        value = value
    )
}