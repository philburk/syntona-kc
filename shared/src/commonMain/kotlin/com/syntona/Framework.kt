package com.syntona

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun Framework(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
    ) {
        Row(modifier = Modifier.weight(2f).fillMaxWidth()) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().border(1.dp, Color.Gray),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("Tree", modifier = Modifier.padding(8.dp))
            }
            Surface(
                modifier = Modifier.weight(3f).fillMaxSize().border(1.dp, Color.Gray),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text("Editor", modifier = Modifier.padding(8.dp))
            }
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Gray),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text("SubEditor", modifier = Modifier.padding(8.dp))
        }
    }
}
