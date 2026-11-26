package com.therefore.logic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import java.net.URLEncoder

/** A block of code (always shown in symbols, never plain-text notation), scrollable sideways, with Copy and optionally "Open in Lean". */
@Composable
fun CodeBox(code: String, modifier: Modifier = Modifier, lean: Boolean = false) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            code,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.sunk).horizontalScroll(rememberScrollState()).padding(12.dp),
            fontFamily = Mono, fontSize = 13.sp, color = p.ink, softWrap = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy") }
            if (lean) OutlinedButton(onClick = { uri.openUri(leanUrl(code)) }) { Text("Open in Lean's web editor") }
        }
    }
}

/** The Lean web editor opens code passed after #code= in its address. */
fun leanUrl(code: String) = "https://live.lean-lang.org/#code=" + URLEncoder.encode(code, "UTF-8").replace("+", "%20")
