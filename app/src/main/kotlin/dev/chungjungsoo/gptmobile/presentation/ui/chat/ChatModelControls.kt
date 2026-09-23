package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.OPENAI_REASONING_EFFORTS
import dev.chungjungsoo.gptmobile.data.model.openAIReasoningEffort
import dev.chungjungsoo.gptmobile.presentation.common.OpenAIProfileState
import dev.chungjungsoo.gptmobile.presentation.common.rememberOpenAIProfileState
import dev.chungjungsoo.gptmobile.presentation.common.rememberProviderAction
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun ChatModelControls(
    platform: PlatformV2,
    providers: List<PlatformV2>,
    usage: AgentRun?,
    enabled: Boolean,
    onProvider: (String) -> Unit,
    onModel: (String) -> Unit,
    onEffort: (String) -> Unit,
    onContextSettings: () -> Unit,
    fastMode: Boolean = false,
    onFastMode: (Boolean) -> Unit = {},
    modelState: OpenAIProfileState? = null
) {
    var showProviders by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showEffort by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }
    val contextLabel = stringResource(R.string.context_usage)
    val percent = contextUsagePercent(usage?.contextTokens, usage?.contextLimit)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                SuggestionChip(onClick = { showProviders = true }, enabled = enabled, label = { Text(platform.name) })
                DropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                    providers.forEach { provider ->
                        DropdownMenuItem(text = { Text(provider.name) }, onClick = {
                            onProvider(provider.uid)
                            showProviders = false
                        })
                    }
                }
            }
            Box {
                SuggestionChip(onClick = { showModels = true }, enabled = enabled, label = { Text(platform.model) })
                if (showModels) {
                    val state = modelState ?: rememberOpenAIProfileState(platform)
                    val perform = rememberProviderAction(state)
                    DropdownMenu(expanded = true, onDismissRequest = { showModels = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        if (platform.compatibleType == ClientType.OPENAI) {
                            DropdownMenuItem(
                                text = { Text("Fast") },
                                modifier = Modifier.semantics {
                                    role = Role.Checkbox
                                    toggleableState = ToggleableState(fastMode)
                                },
                                enabled = enabled,
                                trailingIcon = { Checkbox(checked = fastMode, onCheckedChange = null) },
                                onClick = { onFastMode(!fastMode) }
                            )
                            HorizontalDivider()
                        }
                        state.models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.id) },
                                enabled = enabled && !state.isLoading,
                                onClick = {
                                    onModel(model.id)
                                    showModels = false
                                }
                            )
                        }
                        if (state.models.isEmpty() && !state.isLoading) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.refresh_models_hint)) }, enabled = false, onClick = {})
                        }
                        state.error?.let { message ->
                            DropdownMenuItem(text = { Text(message, color = MaterialTheme.colorScheme.error) }, enabled = false, onClick = {})
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh_models)) },
                            trailingIcon = { if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                            enabled = enabled && !state.isLoading,
                            onClick = { perform(platform.apiUrl) { state.refresh(platform) } }
                        )
                    }
                }
            }
            Box {
                SuggestionChip(
                    onClick = { showEffort = true },
                    enabled = enabled,
                    label = { Text(platform.openAIReasoningEffort() ?: stringResource(R.string.reasoning_default_short)) }
                )
                DropdownMenu(expanded = showEffort, onDismissRequest = { showEffort = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.reasoning_default)) }, onClick = {
                        onEffort("")
                        showEffort = false
                    })
                    OPENAI_REASONING_EFFORTS.forEach { effort ->
                        DropdownMenuItem(text = { Text(effort) }, onClick = {
                            onEffort(effort)
                            showEffort = false
                        })
                    }
                }
            }
        }
        IconButton(onClick = { showContext = true }, modifier = Modifier.semantics { contentDescription = contextLabel }) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (percent ?: 0) / 100f }, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Text(percent?.let { "$it%" } ?: "?", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (showContext) {
        AlertDialog(
            onDismissRequest = { showContext = false },
            title = { Text(contextLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (percent != null) {
                        Text(stringResource(R.string.context_usage_percent, percent, 100 - percent))
                        Text(stringResource(R.string.context_usage_tokens, formatTokenSummary(usage?.contextTokens), formatTokenSummary(usage?.contextLimit?.toLong())))
                        Text(stringResource(R.string.context_usage_snapshot), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(stringResource(R.string.context_usage_unknown))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContext = false }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = {
                TextButton(enabled = enabled, onClick = {
                    showContext = false
                    onContextSettings()
                }) {
                    Text(stringResource(R.string.context_usage_settings))
                }
            }
        )
    }
}

internal fun contextUsagePercent(tokens: Long?, limit: Int?): Int? {
    if (tokens == null || tokens < 0 || limit == null || limit <= 0) return null
    return (tokens.toDouble() / limit * 100).roundToInt().coerceIn(0, 100)
}

internal fun formatTokens(tokens: Long?): String = tokens?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"

internal fun formatMessageTime(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun formatDetailedMessageTime(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

internal fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds.coerceAtLeast(0)}s"
}

internal fun formatTokenSummary(tokens: Long?): String = when {
    tokens == null -> "—"
    tokens >= 1_000_000 -> String.format(Locale.ROOT, "%.1f", tokens / 1_000_000.0).trimEnd('0').trimEnd('.') + "M"
    tokens >= 1000 -> "${(tokens / 1000.0).roundToLong()}k"
    else -> tokens.toString()
}

@Composable
fun AssistantMessageMetadata(run: AgentRun?, replyAt: Long?) {
    var expanded by remember(run?.runId) { mutableStateOf(false) }
    var showTime by remember(run?.runId, replyAt) { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = color)
    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (replyAt != null) {
            TextButton(onClick = { showTime = true }, colors = buttonColors, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)) {
                Text(formatMessageTime(replyAt), style = style)
            }
            Text("·", style = style, color = color)
        }
        if (run?.startedAt != null && run.completedAt != null) {
            Text(formatDuration(run.completedAt - run.startedAt), style = style, color = color)
            Text("·", style = style, color = color)
        }
        val total = if (run?.inputTokens != null && run.outputTokens != null) run.inputTokens + run.outputTokens else null
        val tokenDescription = if (total == null) stringResource(R.string.message_tokens_unknown) else stringResource(R.string.message_tokens, formatTokens(total))
        TextButton(
            onClick = { expanded = true },
            enabled = total != null,
            colors = buttonColors,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).semantics { contentDescription = tokenDescription }
        ) {
            Text(formatTokenSummary(total), style = style)
        }
    }
    if (showTime && replyAt != null) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.message_reply_time)) },
            text = { Text(formatDetailedMessageTime(replyAt)) },
            confirmButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.confirm)) } }
        )
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.token_usage_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.token_usage_input, formatTokens(run?.inputTokens)))
                    Text(stringResource(R.string.token_usage_output, formatTokens(run?.outputTokens)))
                    Text(stringResource(R.string.token_usage_cached, formatTokens(run?.cachedTokens)))
                    Text(stringResource(R.string.token_usage_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.confirm)) } }
        )
    }
}
