package com.vinevm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VineVmApp() {
    var roms by remember { mutableStateOf(listOf<String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) roms = roms + (uri.lastPathSegment ?: "ROM.vrom")
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("VineVM") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Android VM", style = MaterialTheme.typography.headlineMedium)
                Text("VineOS + QEMU + AVF")
            }
            item {
                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Importar .vrom")
                }
            }
            items(roms) { rom ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(rom, style = MaterialTheme.typography.titleMedium)
                        Text("Backend: AUTO • 2 GB RAM • 4 CPU")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { }) { Text("Iniciar") }
                    }
                }
            }
            if (roms.isEmpty()) {
                item {
                    Text("Nenhuma ROM importada. Selecione um arquivo .vrom para começar.")
                }
            }
        }
    }
}
