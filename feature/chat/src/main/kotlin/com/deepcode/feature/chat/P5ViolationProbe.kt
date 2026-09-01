package com.deepcode.feature.chat

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.json.JSONObject

/**
 * 临时违规样例：验证八条新 lint 规则可被 design-guard 命中，断言红后删除。
 * 结构性组件使用全限定名，避免 import 级 DirectMaterial3Usage 干扰，仅命中调用名规则本身。
 */
class P5ViolationProbe {
    val illegalColor: Color = Color(0xFF112233)                     // DirectColorLiteral
    val illegalStyle = TextStyle(fontSize = 14)                     // RawTextStyleConstruction
    val illegalJson = JSONObject("{}")                              // ForbiddenRawJsonRender

    fun toast(c: android.content.Context) {
        Toast.makeText(c, "x", Toast.LENGTH_SHORT).show()           // ForbiddenPlatformToast
    }
}

@Composable
fun probeComposables(onDismiss: () -> Unit) {
    androidx.compose.material3.Dialog(onDismissRequest = onDismiss) { }                       // ForbiddenWindowComponent
    androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = onDismiss) { } // ForbiddenRawDropdown
    androidx.compose.material3.OutlinedTextField(value = "", onValueChange = {})              // ForbiddenRawTextField
    androidx.compose.material3.Card { }                                                        // ForbiddenRawToolCard
}