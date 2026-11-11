package com.kaif.mastercaller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kaif.mastercaller.ui.theme.McColors

/** A fill-in part of the announcement: the stored code, and how it is shown to people. */
data class WordingToken(val code: String, val label: String, val example: String)

private val tokenRegex = Regex("\\{(title|name|number)\\}")

/**
 * Friendly editor for an announcement sentence. Shows the real sentence with gold "pills" for the parts
 * that change (your name, the caller, the number) instead of {code}. "Edit" opens a field with
 * one-tap insert buttons, so nobody has to type braces.
 */
@Composable
fun WordingEditor(
    label: String,
    template: String,
    tokens: List<WordingToken>,
    onSave: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(template)) }
    LaunchedEffect(template) { if (!editing) value = TextFieldValue(template) }
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(McColors.Surface2)
            .border(1.dp, if (editing) McColors.Accent.copy(alpha = 0.5f) else McColors.Outline, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = McColors.TextMuted, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        if (editing && value.text.isNotBlank()) onSave(value.text.trim())
                        editing = !editing
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, null, tint = McColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (editing) "Done" else "Edit", style = MaterialTheme.typography.labelLarge, color = McColors.Accent)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!editing) {
            Text(
                pretty(template, tokens),
                style = MaterialTheme.typography.bodyLarge,
                color = McColors.TextPrimary,
            )
        }

        AnimatedVisibility(visible = editing) {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    visualTransformation = TokenHighlight,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = McColors.TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McColors.Accent,
                        unfocusedBorderColor = McColors.Outline,
                        cursorColor = McColors.Accent,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text("Tap to insert", style = MaterialTheme.typography.labelMedium, color = McColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tokens.forEach { t ->
                        Text(
                            "+ ${t.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = McColors.AccentSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(McColors.AccentDark)
                                .border(1.dp, McColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .clickable {
                                    val insert = "{${t.code}}"
                                    val sel = value.selection
                                    val text = value.text.replaceRange(sel.min, sel.max, insert)
                                    value = TextFieldValue(text, TextRange(sel.min + insert.length))
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The sentence as people read it: gold pills where the caller / your name / number will go. */
private fun pretty(template: String, tokens: List<WordingToken>): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in tokenRegex.findAll(template)) {
        append(template.substring(last, m.range.first))
        val t = tokens.firstOrNull { it.code == m.groupValues[1] }
        withStyle(SpanStyle(color = McColors.Accent, background = McColors.AccentDark, fontWeight = FontWeight.SemiBold)) {
            append(" ${t?.example ?: m.value} ")
        }
        last = m.range.last + 1
    }
    append(template.substring(last))
}

/** Colours the {codes} inside the edit field (same characters, so the cursor maps 1:1). */
private object TokenHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            tokenRegex.findAll(text.text).forEach { m ->
                addStyle(SpanStyle(color = McColors.Accent, background = McColors.AccentDark), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
