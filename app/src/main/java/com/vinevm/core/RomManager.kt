package com.vinevm.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class RomManager(private val context: Context) {
    fun importVrom(uri: Uri): Result<File> = runCatching {
        val source = DocumentFile.fromSingleUri(context, uri)
            ?: error("Arquivo ROM inválido")
        require(source.name?.endsWith(".vrom", ignoreCase = true) == true) {
            "VineVM aceita arquivos .vrom nesta versão"
        }

        val romDir = File(context.filesDir, "roms").apply { mkdirs() }
        val target = File(romDir, source.name ?: "imported.vrom")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir a ROM" }
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        target
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
