package app.k9mail.core.ui.compose.designsystem.atom.textfield

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.k9mail.core.ui.compose.designsystem.atom.inCanvasAwareDropdownMenuModifier
import kotlinx.collections.immutable.ImmutableList
import androidx.compose.material3.DropdownMenu as Material3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Material3DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox as Material3ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField as Material3OutlinedTextField
import androidx.compose.material3.Text as Material3Text

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
fun <T> TextFieldOutlinedSelect(
    options: ImmutableList<T>,
    selectedOption: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionToStringTransformation: (T) -> String = { it.toString() },
    label: String? = null,
    isEnabled: Boolean = true,
    isReadOnly: Boolean = false,
    isRequired: Boolean = false,
    hasError: Boolean = false,
) {
    var isExpanded by remember {
        mutableStateOf(false)
    }

    val isReadOnlyOrDisabled = isReadOnly || !isEnabled
    val inCanvasMenuAnchor = if (isReadOnlyOrDisabled) {
        Modifier
    } else {
        inCanvasAwareDropdownMenuModifier(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            SelectDropdownMenuContent(
                options = options,
                selectedOption = selectedOption,
                optionToStringTransformation = optionToStringTransformation,
                onValueChange = { option ->
                    onValueChange(option)
                    isExpanded = false
                },
            )
        }
    }

    Material3ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = {
            isExpanded = if (isReadOnlyOrDisabled) {
                false
            } else {
                isExpanded.not()
            }
        },
        modifier = inCanvasMenuAnchor,
    ) {
        Material3OutlinedTextField(
            value = optionToStringTransformation(selectedOption),
            onValueChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .then(modifier),
            enabled = isEnabled,
            readOnly = true,
            label = selectLabel(label, isRequired),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            isError = hasError,
            singleLine = true,
            interactionSource = remember {
                MutableInteractionSource()
            },
        )

        if (isReadOnlyOrDisabled.not() && inCanvasMenuAnchor == Modifier) {
            Material3DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier.exposedDropdownSize(),
            ) {
                SelectDropdownMenuContent(
                    options = options,
                    selectedOption = selectedOption,
                    optionToStringTransformation = optionToStringTransformation,
                    onValueChange = { option ->
                        onValueChange(option)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectDropdownMenuContent(
    options: ImmutableList<T>,
    selectedOption: T,
    optionToStringTransformation: (T) -> String,
    onValueChange: (T) -> Unit,
) {
    options.forEach { option ->
        Material3DropdownMenuItem(
            text = {
                Material3Text(
                    text = transformOptionWithSelectionHighlight(
                        option,
                        optionToStringTransformation(option),
                        selectedOption,
                    ),
                )
            },
            onClick = { onValueChange(option) },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
        )
    }
}

private fun <T> transformOptionWithSelectionHighlight(
    option: T,
    optionString: String,
    selectedOption: T,
): AnnotatedString {
    return buildAnnotatedString {
        if (option == selectedOption) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(optionString)
            }
        } else {
            append(optionString)
        }
    }
}
