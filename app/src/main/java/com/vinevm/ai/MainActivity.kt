package com.vinevm.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class ChatMessage(
    val role: String,
    val content: String,
    val thinking: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("vinevm", MODE_PRIVATE)
        setContent {
            VineTheme {
                VineChat(
                    initialUrl = prefs.getString("worker_url", "") ?: "",
                    saveUrl = { prefs.edit().putString("worker_url", it).apply() }
                )
            }
        }
    }
}

@Composable
private fun VineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9B8CFF),
            secondary = Color(0xFF71D7C2),
            background = Color(0xFF0B0D12),
            surface = Color(0xFF171A23)
        ),
        content = content
    )
}

@Composable
private fun VineChat(initialUrl: String, saveUrl: (String) -> Unit) {
    var workerUrl by remember { mutableStateOf(initialUrl) }
    var model by remember { mutableStateOf("llama3.2") }
    var thinkingEnabled by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Cole a URL pública do Ollama (Cloudflare Tunnel).") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun sendMessage() {
        val text = draft.trim()
        val endpoint = workerUrl.trim()
        if (text.isEmpty() || busy) return
        if (!endpoint.startsWith("https://")) {
            status = "Erro: a URL precisa começar com https://"
            return
        }
        if (model.isBlank()) {
            status = "Erro: informe o nome do modelo Ollama."
            return
        }
        messages.add(ChatMessage("user", text))
        draft = ""
        busy = true
        status = "Conectando ao Ollama…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callOllama(endpoint, model.trim(), messages.toList(), thinkingEnabled)
                }
                messages.add(ChatMessage("assistant", result.second, result.first))
                status = "Conectado • Ollama"
            } catch (e: Exception) {
                status = "Erro: ${e.message ?: "não foi possível conectar"}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0D12)).imePadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).background(Color(0xFF9B8CFF), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("V", color = Color(0xFF0B0D12), fontWeight = FontWeight.Black, fontSize = 23.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VineVM", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("AI • Android", fontSize = 12.sp, color = Color(0xFF9298AA))
            }
            Text("v0.2.0", color = Color(0xFF9298AA), fontSize = 11.sp)
        }

        Text("URL PÚBLICA DO OLLAMA", color = Color(0xFFAAAFC0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = workerUrl,
            onValueChange = { workerUrl = it; saveUrl(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://seu-tunel.trycloudflare.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF9B8CFF), unfocusedBorderColor = Color(0xFF343847)
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.weight(1f),
                label = { Text("Modelo Ollama") },
                placeholder = { Text("ex.: llama3.2") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF9B8CFF), unfocusedBorderColor = Color(0xFF343847)
                )
            )
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Thinking", color = Color(0xFFAAAFC0), fontSize = 11.sp)
                Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
            }
        }
        Text(
            status,
            color = if (status.startsWith("Erro")) Color(0xFFFF8585) else Color(0xFF9298AA),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )

        HorizontalDivider(color = Color(0xFF252936))
        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Bora conversar.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Sua IA. Sua conexão. Seu espaço.", color = Color(0xFF9298AA), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    if (message.role == "assistant" && message.thinking.isNotBlank()) {
                        Column(
                            Modifier.fillMaxWidth().padding(end = 12.dp)
                                .background(Color(0xFF171A23), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text("🧠 Thinking", color = Color(0xFFB7AFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(message.thinking, color = Color(0xFFB7B9C8), fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
                    ) {
                        Text(
                            message.content,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.widthIn(max = 310.dp)
                                .background(
                                    if (message.role == "user") Color(0xFF393254) else Color(0xFF171A23),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 15.dp, vertical = 11.dp)
                        )
                    }
                }
                if (busy) item {
                    Text("VineVM está pensando…", color = Color(0xFF9298AA), fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Manda uma mensagem…") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF9B8CFF), unfocusedBorderColor = Color(0xFF343847)
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { sendMessage() },
                enabled = !busy && draft.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 17.dp)
            ) { Text("➤", fontSize = 20.sp) }
        }
    }
}

private fun callOllama(
    baseUrl: String,
    model: String,
    messages: List<ChatMessage>,
    thinkingEnabled: Boolean
): Pair<String, String> {
    val base = baseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("URL pública inválida.")
    val endpoint = if (base.encodedPath.endsWith("/api/chat") || base.encodedPath.endsWith("/v1/chat/completions")) {
        base
    } else {
        base.newBuilder().addPathSegments("api/chat").build()
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    val history = JSONArray()
    messages.forEach { message ->
        history.put(JSONObject().put("role", message.role).put("content", message.content))
    }
    val payload = JSONObject()
        .put("model", model)
        .put("messages", history)
        .put("stream", false)
        .put("think", thinkingEnabled)
    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(endpoint).post(body).build()

    client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val hint = if (response.code == 405) "O endereço respondeu 405. Confirme que o túnel aponta para o servidor Ollama (porta 11434), não para o painel/site." else raw.take(220)
            throw IllegalStateException("HTTP ${response.code}: $hint")
        }
        val json = JSONObject(raw)
        val message = json.optJSONObject("message")
        val thinking = message?.optString("thinking").orEmpty()
        val answer = message?.optString("content").orEmpty().ifBlank {
            json.optString("response").ifBlank { json.optString("output") }
        }
        if (answer.isBlank() && thinking.isBlank()) {
            throw IllegalStateException("Ollama respondeu, mas não encontrei message.content.")
        }
        return Pair(if (thinkingEnabled) thinking else "", answer.ifBlank { "(Sem resposta textual; veja o thinking.)" })
    }
}
