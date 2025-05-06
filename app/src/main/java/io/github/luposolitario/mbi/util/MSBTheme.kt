package io.github.luposolitario.mbi.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MBITheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
