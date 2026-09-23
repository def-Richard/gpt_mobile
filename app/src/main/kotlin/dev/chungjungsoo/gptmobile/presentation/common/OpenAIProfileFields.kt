package dev.chungjungsoo.gptmobile.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.OPENAI_REASONING_EFFORTS
import dev.chungjungsoo.gptmobile.data.network.OpenAIModelOption
import dev.chungjungsoo.gptmobile.data.network.OpenAIProfileClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIProfileException
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class OpenAIProfileViewModel @Inject constructor(val client: OpenAIProfileClient, private val platforms: PlatformV2Dao) : ViewModel() {
    suspend fun loadCatalog(platform: PlatformV2): List<OpenAIModelOption> {
        if (platform.id == 0) return platform.modelCatalog
        val saved = platforms.getPlatform(platform.id) ?: return emptyList()
        return saved.modelCatalog.takeIf { saved.uid == platform.uid && saved.apiUrl == platform.apiUrl && saved.compatibleType == platform.compatibleType }.orEmpty()
    }

    suspend fun saveCatalog(platform: PlatformV2, models: List<OpenAIModelOption>) {
        if (platform.id > 0 && !platforms.replaceModelCatalog(platform, models)) {
            throw OpenAIProfileException(R.string.provider_settings_changed)
        }
    }
}

class OpenAIProfileState(
    private val client: OpenAIProfileClient,
    private val scope: CoroutineScope,
    private val loadCatalog: suspend (PlatformV2) -> List<OpenAIModelOption>,
    private val saveCatalog: suspend (PlatformV2, List<OpenAIModelOption>) -> Unit,
    private val errorMessage: (Exception) -> String
) {
    var models by mutableStateOf<List<OpenAIModelOption>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isTesting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var testedPlatform by mutableStateOf<PlatformV2?>(null)
        private set
    private var job: Job? = null

    fun load(platform: PlatformV2) {
        job = scope.launch {
            isLoading = true
            try {
                models = loadCatalog(platform)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                this@OpenAIProfileState.error = errorMessage(error)
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh(platform: PlatformV2) {
        if (isLoading || isTesting) return
        job = scope.launch {
            isLoading = true
            error = null
            try {
                val refreshed = client.models(platform)
                saveCatalog(platform, refreshed)
                models = refreshed
                if (refreshed.isEmpty()) error = errorMessage(OpenAIProfileException(R.string.provider_models_empty))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                this@OpenAIProfileState.error = errorMessage(error)
            } finally {
                isLoading = false
            }
        }
    }

    fun verify(platform: PlatformV2) {
        if (isLoading || isTesting) return
        job = scope.launch {
            isTesting = true
            testedPlatform = null
            error = null
            try {
                client.verify(platform)
                testedPlatform = platform
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                this@OpenAIProfileState.error = errorMessage(error)
            } finally {
                isTesting = false
            }
        }
    }

    fun cancel() = job?.cancel()
}

@Composable
fun rememberOpenAIProfileState(platform: PlatformV2, viewModel: OpenAIProfileViewModel = hiltViewModel()): OpenAIProfileState {
    val scope = rememberCoroutineScope()
    val messages = listOf(
        R.string.provider_models_empty,
        R.string.provider_select_model,
        R.string.provider_invalid_response,
        R.string.provider_invalid_base_url,
        R.string.provider_invalid_models_response,
        R.string.provider_request_timeout,
        R.string.provider_network_error,
        R.string.provider_settings_changed
    ).associateWith { stringResource(it) }
    val state = remember(platform.id, platform.uid, platform.apiUrl, platform.token, platform.compatibleType, messages) {
        OpenAIProfileState(viewModel.client, scope, viewModel::loadCatalog, viewModel::saveCatalog) { error ->
            val resource = when (error) {
                is OpenAIProfileException -> error.messageResource
                is io.ktor.client.plugins.HttpRequestTimeoutException -> R.string.provider_request_timeout
                is java.io.IOException -> R.string.provider_network_error
                is kotlinx.serialization.SerializationException -> R.string.provider_invalid_response
                else -> null
            }
            resource?.let(messages::getValue) ?: error.message ?: messages.getValue(R.string.provider_network_error)
        }
    }
    LaunchedEffect(state) { state.load(platform) }
    DisposableEffect(state) { onDispose { state.cancel() } }
    return state
}

@Composable
fun OpenAIModelField(platform: PlatformV2, state: OpenAIProfileState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val perform = rememberProviderAction(state)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChoiceField(
                value = platform.model,
                label = stringResource(R.string.model),
                options = state.models.map { it.id },
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { perform(platform.apiUrl) { state.refresh(platform) } }, enabled = !state.isLoading && !state.isTesting) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_models))
                }
            }
        }
        if (state.models.isEmpty()) {
            Text(stringResource(R.string.refresh_models_hint), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceField(value: String, label: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && options.isNotEmpty() }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun ReasoningEffortField(value: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    ChoiceField(value, stringResource(R.string.reasoning_effort), OPENAI_REASONING_EFFORTS, onSelect, modifier)
    Text(stringResource(R.string.reasoning_effort_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun OpenAIModelDialog(platform: PlatformV2, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var model by remember(platform.model) { mutableStateOf(platform.model) }
    val draft = platform.copy(model = model)
    val state = rememberOpenAIProfileState(platform)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model)) },
        text = {
            Column {
                OpenAIModelField(draft, state, { model = it })
                OpenAIConnectionTest(draft, state)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = state.models.any { it.id == model }, onClick = {
                onSelect(model)
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun OpenAIConnectionTest(platform: PlatformV2, state: OpenAIProfileState, modifier: Modifier = Modifier) {
    val perform = rememberProviderAction(state)
    OutlinedButton(
        onClick = { perform(platform.apiUrl) { state.verify(platform) } },
        enabled = platform.model.isNotBlank() && !state.isTesting && !state.isLoading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        if (state.isTesting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        val label = when {
            state.isTesting -> R.string.testing_connection
            state.testedPlatform == platform -> R.string.connection_test_success
            else -> R.string.test_connection
        }
        Text(stringResource(label))
    }
}

@Composable
fun rememberProviderAction(state: OpenAIProfileState): (String, () -> Unit) -> Unit {
    val context = LocalContext.current
    val permissionError = stringResource(R.string.local_network_check_failed)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else state.error = permissionError
        pending = null
    }
    return { url, action ->
        if (Build.VERSION.SDK_INT >= 37 &&
            requiresLocalNetworkAccess(url) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = action
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            action()
        }
    }
}
