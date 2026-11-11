package com.kaif.mastercaller.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kaif.mastercaller.ui.theme.McColors

private val KEYS = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/** 12-key pad. Long-press 0 types "+". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KEYS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { (digit, letters) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.6f)
                            .clip(CircleShape)
                            .background(McColors.Surface2)
                            .combinedClickable(
                                onClick = { onKey(digit) },
                                onLongClick = if (digit == '0') ({ onKey('+') }) else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digit.toString(), style = MaterialTheme.typography.headlineSmall, color = McColors.TextPrimary)
                            if (letters.isNotEmpty()) {
                                Text(letters, style = MaterialTheme.typography.labelSmall, color = McColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
