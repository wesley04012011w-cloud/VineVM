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
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class ChatMessage(val role: String, val content: String)

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
    val bg = Color(0xFF0B0D12)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9B8CFF),
            secondary = Color(0xFF71D7C2),
            background = bg,
            surface = Color(0xFF171A23)
        ),
        content = content
    )
}

@Composable
private fun VineChat(initialUrl: String, saveUrl: (String) -> Unit) {
    var workerUrl by remember { mutableStateOf(initialUrl) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Adicione a URL do seu Worker para começar.") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun sendMessage() {
        val text = draft.trim()
        val endpoint = workerUrl.trim()
        if (text.isEmpty() || busy) return
        if (!endpoint.startsWith("https://")) {
            status = "A URL precisa começar com https://"
            return
        }
        messages.add(ChatMessage("user", text))
        draft = ""
        busy = true
        status = "Conectando ao Worker…"
        scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { callWorker(endpoint, messages.toList()) }
                messages.add(ChatMessage("assistant", answer))
                status = "Conectado"
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
            Text("v0.1.0", color = Color(0xFF9298AA), fontSize = 11.sp)
        }

        Text("URL DO WORKER CLOUDFLARE", color = Color(0xFFAAAFC0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = workerUrl,
                onValueChange = { workerUrl = it; saveUrl(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("https://seu-worker.workers.dev") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF9B8CFF),
                    unfocusedBorderColor = Color(0xFF343847)
                )
            )
        }
        Text(status, color = if (status.startsWith("Erro")) Color(0xFFFF8585) else Color(0xFF9298AA), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))

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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF9B8CFF),
                    unfocusedBorderColor = Color(0xFF343847)
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

private fun callWorker(url: String, messages: List<ChatMessage>): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val history = JSONArray()
    messages.forEach { message ->
        history.put(JSONObject().put("role", message.role).put("content", message.content))
    }
    val payload = JSONObject().put("messages", history)
    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(url).post(body).build()

    client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${raw.take(180)}")
        val json = JSONObject(raw)
        return when {
            json.has("response") -> json.optString("response")
            json.has("output") -> json.optString("output")
            json.optJSONObject("result")?.has("response") == true -> json.getJSONObject("result").optString("response")
            json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.has("content") == true ->
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            else -> raw
        }.ifBlank { "O Worker respondeu, mas não enviou texto." }
    }
}
