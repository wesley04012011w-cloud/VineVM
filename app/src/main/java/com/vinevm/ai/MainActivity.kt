package com.vinevm.ai

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    val id: Long,
    val role: String,
    val content: String,
    val thinking: String = ""
)

private val Bg = Color(0xFF0B0D12)
private val Panel = Color(0xFF171A23)
private val Muted = Color(0xFF9298AA)
private val Accent = Color(0xFF9B8CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("vinevm", MODE_PRIVATE)
        setContent {
            VineTheme {
                VineApp(
                    initialUrl = prefs.getString("worker_url", "") ?: "",
                    initialModel = prefs.getString("model_id", "qwen3.5:2b") ?: "qwen3.5:2b",
                    saveSettings = { url, model ->
                        prefs.edit().putString("worker_url", url)
                            .putString("model_id", model).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun VineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Color(0xFF71D7C2),
            background = Bg,
            surface = Panel
        ),
        content = content
    )
}

@Composable
private fun VineApp(
    initialUrl: String,
    initialModel: String,
    saveSettings: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var model by remember { mutableStateOf(initialModel) }
    var thinkingEnabled by remember { mutableStateOf(true) }
    var showEngine by remember { mutableStateOf(initialUrl.isBlank()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Configure sua Engine para começar.") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun persist() {
        url = url.trim().trimEnd('/')
        model = model.trim()
        saveSettings(url, model)
    }

    fun testConnection() {
        persist()
        if (url.isBlank()) {
            status = "Informe a URL do túnel."
            return
        }
        busy = true
        status = "Testando conexão com Ollama…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { testOllama(url) }
                status = "Conectado • ${result}"
            } catch (e: Exception) {
                status = "Falha na conexão: ${e.message ?: "erro desconhecido"}"
            } finally {
                busy = false
            }
        }
    }

    fun sendMessage() {
        val prompt = draft.trim()
        if (prompt.isEmpty() || busy) return
        persist()
        if (url.isBlank() || model.isBlank()) {
            showEngine = true
            status = "Configure a URL e o ID do modelo."
            return
        }
        if (url.toHttpUrlOrNull()?.isHttps != true) {
            showEngine = true
            status = "A URL precisa ser HTTPS."
            return
        }

        messages.add(ChatMessage(System.nanoTime(), "user", prompt))
        val assistantId = System.nanoTime()
        messages.add(ChatMessage(assistantId, "assistant", ""))
        draft = ""
        busy = true
        status = "Conectando à Engine…"

        val history = messages.dropLast(1).toList()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    streamOllama(
                        baseUrl = url,
                        model = model,
                        messages = history,
                        thinkingEnabled = thinkingEnabled,
                        onChunk = { thinking, content ->
                            Handler(Looper.getMainLooper()).post {
                                val index = messages.indexOfFirst { it.id == assistantId }
                                if (index >= 0) {
                                    val old = messages[index]
                                    messages[index] = old.copy(
                                        thinking = old.thinking + thinking,
                                        content = old.content + content
                                    )
                                }
                            }
                        }
                    )
                }
                status = "Resposta concluída • ${model}"
            } catch (e: Exception) {
                status = "Erro: ${e.message ?: "não foi possível conectar"}"
                val index = messages.indexOfFirst { it.id == assistantId }
                if (index >= 0 && messages[index].content.isBlank() && messages[index].thinking.isBlank()) {
                    messages.removeAt(index)
                }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                Text("V", color = Bg, fontWeight = FontWeight.Black, fontSize = 23.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VineVM", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (showEngine) "ENGINE CONFIGURATION" else "AI • OLLAMA CLIENT", fontSize = 10.sp, color = Muted)
            }
            TextButton(onClick = { showEngine = !showEngine }) {
                Text(if (showEngine) "CHAT" else "ENGINE", color = Accent, fontWeight = FontWeight.Bold)
            }
        }

        if (showEngine) {
            EnginePanel(
                url = url,
                onUrlChange = { url = it },
                model = model,
                onModelChange = { model = it },
                onSave = {
                    persist()
                    status = "Configuração salva neste aparelho."
                    showEngine = false
                },
                onTest = { testConnection() },
                busy = busy,
                status = status
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(model.ifBlank { "Nenhum modelo selecionado" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(url.ifBlank { "Engine não configurada" }, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                Text("Thinking", color = Muted, fontSize = 11.sp)
                Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
            }
            Text(
                status,
                color = if (status.startsWith("Erro") || status.startsWith("Falha")) Color(0xFFFF8585) else Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
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
                    Text("Sua IA. Sua conexão. Seu espaço.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { showEngine = true }) { Text("Configurar Engine", color = Accent) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        if (message.role == "assistant" && message.thinking.isNotBlank()) {
                            ThinkingCard(message.thinking)
                        }
                        if (message.content.isNotBlank()) {
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
                                            if (message.role == "user") Color(0xFF393254) else Panel,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .padding(horizontal = 15.dp, vertical = 11.dp)
                                )
                            }
                        } else if (message.role == "assistant" && busy && message.thinking.isBlank()) {
                            Text("Aguardando resposta…", color = Muted, fontSize = 12.sp)
                        }
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
                        focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343847)
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
}

@Composable
private fun EnginePanel(
    url: String,
    onUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    busy: Boolean,
    status: String
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Sua Engine", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Conecte qualquer servidor compatível com a API do Ollama.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Text("URL DO SERVIDOR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://seu-tunel.trycloudflare.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343847)
            )
        )
        Spacer(Modifier.height(16.dp))
        Text("ID DO MODELO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ex.: qwen3.5:2b") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343847)
            )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Use o nome exato de um modelo instalado no Ollama. O app não baixa modelos automaticamente nesta versão.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(if (busy) "Testando…" else "TESTAR CONEXÃO")
        }
        OutlinedButton(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("SALVAR E VOLTAR AO CHAT")
        }
        Text(
            status,
            color = if (status.startsWith("Falha") || status.startsWith("Erro")) Color(0xFFFF8585) else Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ThinkingCard(text: String) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth().padding(end = 12.dp)
            .background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(if (expanded) "▾  🧠 THINKING" else "▸  🧠 THINKING", color = Color(0xFFB7AFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Text(text, color = Color(0xFFB7B9C8), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private fun apiUrl(baseUrl: String, path: String): okhttp3.HttpUrl {
    val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
        ?: throw IllegalArgumentException("URL inválida.")
    if (base.isHttps.not()) throw IllegalArgumentException("Use uma URL HTTPS.")
    return base.newBuilder().addPathSegments(path).build()
}

private fun newHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

private fun testOllama(baseUrl: String): String {
    val request = Request.Builder().url(apiUrl(baseUrl, "api/tags")).get().build()
    newHttpClient().newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${raw.take(300)}")
        val models = JSONObject(raw).optJSONArray("models") ?: JSONArray()
        return "${models.length()} modelo(s) encontrado(s)"
    }
}

private fun streamOllama(
    baseUrl: String,
    model: String,
    messages: List<ChatMessage>,
    thinkingEnabled: Boolean,
    onChunk: (String, String) -> Unit
) {
    val history = JSONArray()
    messages.forEach { message ->
        val item = JSONObject().put("role", message.role).put("content", message.content)
        if (message.role == "assistant" && message.thinking.isNotBlank()) {
            item.put("thinking", message.thinking)
        }
        history.put(item)
    }

    val payload = JSONObject()
        .put("model", model)
        .put("messages", history)
        .put("stream", true)
        .put("think", thinkingEnabled)

    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url(apiUrl(baseUrl, "api/chat"))
        .post(body)
        .header("Accept", "application/x-ndjson")
        .build()

    newHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val error = response.body?.string().orEmpty()
            throw IllegalStateException("HTTP ${response.code}: ${error.take(500)}")
        }
        val source = response.body?.source()
            ?: throw IllegalStateException("Resposta vazia do servidor.")
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            val chunk = try {
                JSONObject(line)
            } catch (_: Exception) {
                continue
            }
            if (chunk.has("error")) {
                throw IllegalStateException(chunk.optString("error"))
            }
            val message = chunk.optJSONObject("message") ?: continue
            val thinking = if (thinkingEnabled) message.optString("thinking").takeUnless { it == "null" }.orEmpty() else ""
            val content = message.optString("content").takeUnless { it == "null" }.orEmpty()
            if (thinking.isNotEmpty() || content.isNotEmpty()) onChunk(thinking, content)
        }
    }
}
