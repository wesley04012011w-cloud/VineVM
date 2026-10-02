package com.vinevm.ai

import android.content.Intent
import android.net.Uri
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

private data class ChatMessage(val id: Long, val role: String, val content: String, val thinking: String = "")
private data class ModelInfo(val name: String, val size: Long, val vram: Long)
private data class SavedChat(val id: String, val title: String, val messages: List<ChatMessage>)
private data class ModelOption(val id: String, val description: String)
private val suggestions = listOf(ModelOption("qwen3.5:2b","Compacto"),ModelOption("qwen3.5:4b","Mais capacidade"),ModelOption("llama3.2:1b","Leve"),ModelOption("llama3.2:3b","Geral"),ModelOption("gemma3:1b","Compacto"),ModelOption("gemma3:4b","Multimodal"),ModelOption("deepseek-r1:1.5b","Raciocínio"),ModelOption("phi4-mini","Compacto"))
private val Bg=Color(0xFF0B0D12); private val Panel=Color(0xFF171A23); private val Muted=Color(0xFF9298AA); private val Accent=Color(0xFF9B8CFF); private val Green=Color(0xFF71D7C2)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val p=getSharedPreferences("vinevm",MODE_PRIVATE)
 setContent { MaterialTheme(colorScheme=darkColorScheme(primary=Accent,secondary=Green,background=Bg,surface=Panel)){
 VineApp(p.getString("worker_url","")?:"",p.getString("model_id","qwen3.5:2b")?:"qwen3.5:2b",p.getString("saved_chats","[]")?:"[]",
 {u,m->p.edit().putString("worker_url",u).putString("model_id",m).apply()},{s->p.edit().putString("saved_chats",s).apply()},
 {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://ollama.com/library")))})
 } }
 }
}

@Composable private fun VineApp(initialUrl:String,initialModel:String,savedRaw:String,saveSettings:(String,String)->Unit,saveChats:(String)->Unit,openCatalog:()->Unit){
 var url by remember{mutableStateOf(initialUrl)}; var model by remember{mutableStateOf(initialModel)}; var modelId by remember{mutableStateOf(initialModel)}
 var draft by remember{mutableStateOf("")}; var thinking by remember{mutableStateOf(true)}
 var engine by remember{mutableStateOf(initialUrl.isBlank())}; var modelsScreen by remember{mutableStateOf(false)}; var savedScreen by remember{mutableStateOf(false)}
 var tab by remember{mutableStateOf(0)}; var busy by remember{mutableStateOf(false)}; var downloading by remember{mutableStateOf(false)}
 var progress by remember{mutableStateOf(0f)}; var downloadInfo by remember{mutableStateOf("")}; var status by remember{mutableStateOf("")}
 var installed by remember{mutableStateOf<List<String>>(emptyList())}; var running by remember{mutableStateOf<List<ModelInfo>>(emptyList())}; var menu by remember{mutableStateOf(false)}
 val messages=remember{mutableStateListOf<ChatMessage>()}; var saved by remember{mutableStateOf(parseChats(savedRaw))}; var active by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 fun persist(){url=url.trim().trimEnd('/');model=model.trim();saveSettings(url,model)}
 fun refresh(){if(url.isBlank()){engine=true;return};scope.launch{try{installed=withContext(Dispatchers.IO){getInstalled(url)};running=withContext(Dispatchers.IO){getRunning(url)};status="Listas atualizadas."}catch(e:Exception){status="Erro: "+e.message}}}
 fun connect(){persist();if(url.isBlank()){status="Cole a URL do túnel.";return};busy=true;status="Testando conexão…";scope.launch{try{installed=withContext(Dispatchers.IO){getInstalled(url)};running=withContext(Dispatchers.IO){getRunning(url)};engine=false;modelsScreen=true;tab=1;status="Conexão confirmada!"}catch(e:Exception){status="Falha na conexão: "+e.message}finally{busy=false}}}
 fun pull(id:String){val name=id.trim();if(name.isBlank()||downloading||busy)return;persist();if(url.isBlank()){engine=true;return};downloading=true;progress=0f;downloadInfo="Iniciando…";scope.launch{try{withContext(Dispatchers.IO){pullModel(url,name){p,s->Handler(Looper.getMainLooper()).post{progress=p;downloadInfo=s}}};installed=withContext(Dispatchers.IO){getInstalled(url)};model=name;modelId=name;saveSettings(url,model);downloadInfo="Download concluído!"}catch(e:Exception){downloadInfo="Falha: "+e.message}finally{downloading=false}}}
 fun load(name:String){if(busy||downloading)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){setLoaded(url,name,true)};model=name;saveSettings(url,model);running=withContext(Dispatchers.IO){getRunning(url)};status=name+" carregado na RAM."}catch(e:Exception){status="Falha: "+e.message}finally{busy=false}}}
 fun unload(name:String){if(busy||downloading)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){setLoaded(url,name,false)};running=withContext(Dispatchers.IO){getRunning(url)};status=name+" descarregado."}catch(e:Exception){status="Falha: "+e.message}finally{busy=false}}}
 fun saveChat(){if(messages.isEmpty()){status="Nenhuma mensagem para salvar.";return};val id=active.ifBlank{System.currentTimeMillis().toString()};val title=messages.firstOrNull{it.role=="user"}?.content?.take(35)?:"Conversa";saved=listOf(SavedChat(id,title,messages.toList()))+saved.filterNot{it.id==id};saveChats(encodeChats(saved));active=id;status="Conversa salva no aparelho."}
 fun send(){val prompt=draft.trim();if(prompt.isBlank()||busy||downloading)return;persist();if(url.isBlank()){engine=true;return};messages.add(ChatMessage(System.nanoTime(),"user",prompt));val aid=System.nanoTime();messages.add(ChatMessage(aid,"assistant",""));draft="";busy=true;val history=messages.dropLast(1).toList();scope.launch{try{withContext(Dispatchers.IO){streamChat(url,model,history,thinking){t,c->Handler(Looper.getMainLooper()).post{val i=messages.indexOfFirst{it.id==aid};if(i>=0)messages[i]=messages[i].copy(thinking=messages[i].thinking+t,content=messages[i].content+c)}}};status="Resposta concluída."}catch(e:Exception){status="Erro: "+e.message}finally{busy=false}}}
 Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(horizontal=16.dp)){
 Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
 Box(Modifier.size(38.dp).background(Accent,CircleShape),contentAlignment=Alignment.Center){Text("V",color=Bg,fontWeight=FontWeight.Black,fontSize=22.sp)}
 Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("VineVM",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(if(engine)"ENGINE" else if(modelsScreen)"MODELS" else model,color=Muted,fontSize=10.sp)}
 Box{IconButton(onClick={menu=true}){Text("⋮",color=Color.White,fontSize=24.sp)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
 DropdownMenuItem(text={Text("Nova conversa")},onClick={messages.clear();active="";menu=false})
 DropdownMenuItem(text={Text("Salvar conversa")},onClick={menu=false;saveChat()})
 DropdownMenuItem(text={Text("Conversas salvas")},onClick={menu=false;savedScreen=true;engine=false;modelsScreen=false})
 DropdownMenuItem(text={Text("Engine")},onClick={menu=false;engine=true;modelsScreen=false;savedScreen=false})
 DropdownMenuItem(text={Text("Models")},onClick={menu=false;engine=false;modelsScreen=true;savedScreen=false;refresh()})
 }}
 }
 when{
 engine->{Text("Conectar Engine",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Cole o endereço público do túnel Ollama.",color=Muted,fontSize=13.sp);Spacer(Modifier.height(20.dp))
 OutlinedTextField(value=url,onValueChange={url=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("https://seu-tunel.trycloudflare.com")},shape=RoundedCornerShape(14.dp),colors=fieldColors())
 Spacer(Modifier.height(12.dp));Button(onClick={connect()},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"CONECTANDO…" else "TESTAR CONEXÃO")}
 if(status.isNotBlank())Text(status,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))}
 modelsScreen->{
 Row(verticalAlignment=Alignment.CenterVertically){Text("Modelos",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={refresh()}){Text("ATUALIZAR")};TextButton(onClick={modelsScreen=false}){Text("CHAT")}}
 MemoryCharts(running)
 TabRow(selectedTabIndex=tab,containerColor=Bg,contentColor=Accent){listOf("Baixar","Instalados","Na RAM").forEachIndexed{i,s->Tab(selected=tab==i,onClick={tab=i},text={Text(s,fontSize=11.sp)})}}
 when(tab){
 0->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 item{OutlinedButton(onClick=openCatalog,modifier=Modifier.fillMaxWidth()){Text("CATÁLOGO OFICIAL OLLAMA ↗")}}
 item{Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=modelId,onValueChange={modelId=it},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("ID do modelo")},shape=RoundedCornerShape(12.dp),colors=fieldColors());Spacer(Modifier.width(6.dp));Button(onClick={pull(modelId)},enabled=!downloading&&modelId.isNotBlank()){Text("BAIXAR")}}
 if(downloading||downloadInfo.isNotBlank()){Text(downloadInfo,color=Color.White,fontSize=11.sp);if(downloading||progress>0f)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())}}
 items(suggestions,key={it.id}){o->ModelRow(o.id,o.description,"BAIXAR",!downloading){modelId=o.id;pull(o.id)}}
 }
 1->LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){if(installed.isEmpty())item{Text("Nenhum modelo instalado.",color=Muted)};items(installed,key={it}){n->val inRam=running.any{it.name==n};ModelRow(n,if(inRam)"Carregado na memória" else "Instalado","CARREGAR",!busy){if(inRam){model=n;saveSettings(url,model);status=n+" selecionado."}else load(n)}}}
 else->LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){if(running.isEmpty())item{Text("Nenhum modelo carregado na memória.",color=Muted)};items(running,key={it.name}){m->ModelRow(m.name,"RAM estimada "+bytes((m.size-m.vram).coerceAtLeast(0))+" • VRAM "+bytes(m.vram),"DESCARREGAR",!busy){unload(m.name)}}}
 }
 if(status.isNotBlank())Text(status,color=Muted,fontSize=10.sp,maxLines=2)
 }
 savedScreen->{Row(verticalAlignment=Alignment.CenterVertically){Text("Conversas salvas",color=Color.White,fontSize=22.sp,modifier=Modifier.weight(1f));TextButton(onClick={savedScreen=false}){Text("VOLTAR")}}
 LazyColumn(Modifier.weight(1f)){items(saved,key={it.id}){c->ModelRow(c.title,c.messages.size.toString()+" mensagens","ABRIR",true){messages.clear();messages.addAll(c.messages);active=c.id;savedScreen=false}}}}
 else->{
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(status,color=Muted,fontSize=10.sp,modifier=Modifier.weight(1f),maxLines=1);TextButton(onClick={modelsScreen=true;refresh()}){Text("MODELS",color=Accent)}}
 LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 if(messages.isEmpty())item{Column(Modifier.fillParentMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("Bora conversar.",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Sua IA. Sua conexão. Seu espaço.",color=Muted,fontSize=13.sp)}}
 items(messages,key={it.id}){m->if(m.role=="assistant"&&m.thinking.isNotBlank())ThinkingBlock(m.thinking);if(m.content.isNotBlank())Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.role=="user")Arrangement.End else Arrangement.Start){Text(m.content,color=Color.White,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.widthIn(max=310.dp).background(if(m.role=="user")Color(0xFF393254)else Panel,RoundedCornerShape(17.dp)).padding(14.dp))}}
 }
 Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.Bottom){OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),placeholder={Text("Manda uma mensagem…")},shape=RoundedCornerShape(20.dp),maxLines=4,colors=fieldColors(),trailingIcon={IconButton(onClick={thinking=!thinking}){Text("🧠",fontSize=19.sp,color=if(thinking)Accent else Muted)}});Spacer(Modifier.width(7.dp));Button(onClick={send()},enabled=!busy&&!downloading&&draft.isNotBlank(),modifier=Modifier.height(56.dp),shape=RoundedCornerShape(16.dp)){Text("➤",fontSize=19.sp)}}
 }
 }
 }
}
}

@Composable private fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Accent,unfocusedBorderColor=Color(0xFF343847))
@Composable private fun ModelRow(name:String,sub:String,action:String,enabled:Boolean,onClick:()->Unit){Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(name,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(sub,color=Muted,fontSize=10.sp)};TextButton(onClick=onClick,enabled=enabled){Text(action,color=Accent,fontSize=10.sp)}}}
@Composable private fun MemoryCharts(models:List<ModelInfo>){val all=models.sumOf{it.size}.coerceAtLeast(1);val v=models.sumOf{it.vram};val r=(all-v).coerceAtLeast(0);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniChart("RAM (ESTIMADA)",r,all,Modifier.weight(1f),"Memória dos modelos");MiniChart("VRAM",v,all,Modifier.weight(1f),if(v>0)"Uso reportado pelo Ollama" else "Sem VRAM reportada")}}
@Composable private fun MiniChart(title:String,value:Long,scale:Long,modifier:Modifier,note:String){Column(modifier.background(Panel,RoundedCornerShape(12.dp)).padding(10.dp)){Text(title,color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold);Text(bytes(value),color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={(value.toFloat()/scale.coerceAtLeast(1).toFloat()).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp));Text(note,color=Muted,fontSize=9.sp)}}
@Composable private fun ThinkingBlock(value:String){var expanded by remember{mutableStateOf(false)};Column(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(13.dp)).padding(10.dp)){TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"▾ 🧠 Thinking" else "▸ 🧠 Thinking",color=Accent,fontSize=11.sp)};if(expanded)Text(value,color=Color(0xFFB7B9C8),fontSize=12.sp,lineHeight=18.sp)}}

private fun endpoint(base:String,path:String):okhttp3.HttpUrl{val u=base.trim().trimEnd('/').toHttpUrlOrNull()?:throw IllegalArgumentException("URL inválida");if(!u.isHttps)throw IllegalArgumentException("Use URL HTTPS");return u.newBuilder().addPathSegments(path).build()}
private fun http()=OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).writeTimeout(60,TimeUnit.SECONDS).build()
private fun getInstalled(base:String):List<String>{val q=Request.Builder().url(endpoint(base,"api/tags")).build();http().newCall(q).execute().use{val s=it.body?.string().orEmpty();if(!it.isSuccessful)throw IllegalStateException("HTTP "+it.code+": "+s.take(300));val a=JSONObject(s).optJSONArray("models")?:JSONArray();return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)}}}
private fun getRunning(base:String):List<ModelInfo>{val q=Request.Builder().url(endpoint(base,"api/ps")).build();http().newCall(q).execute().use{val s=it.body?.string().orEmpty();if(!it.isSuccessful)throw IllegalStateException("HTTP "+it.code+": "+s.take(300));val a=JSONObject(s).optJSONArray("models")?:JSONArray();return (0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{m->ModelInfo(m.optString("name"),m.optLong("size"),m.optLong("size_vram"))}}}}
private fun setLoaded(base:String,name:String,load:Boolean){val p=JSONObject().put("model",name).put("prompt","").put("stream",false).put("keep_alive",if(load)"10m" else 0);val q=Request.Builder().url(endpoint(base,"api/generate")).post(p.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build();http().newCall(q).execute().use{val s=it.body?.string().orEmpty();if(!it.isSuccessful)throw IllegalStateException("HTTP "+it.code+": "+s.take(300));if(JSONObject(s).has("error"))throw IllegalStateException(JSONObject(s).optString("error"))}}
private fun pullModel(base:String,name:String,cb:(Float,String)->Unit){val p=JSONObject().put("name",name).put("stream",true);val q=Request.Builder().url(endpoint(base,"api/pull")).post(p.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build();http().newCall(q).execute().use{res->if(!res.isSuccessful)throw IllegalStateException("HTTP "+res.code+": "+res.body?.string()?.take(300));val src=res.body?.source()?:throw IllegalStateException("Sem resposta");while(!src.exhausted()){val line=src.readUtf8Line()?:break;if(line.isBlank())continue;val o=JSONObject(line);if(o.has("error"))throw IllegalStateException(o.optString("error"));val total=o.optLong("total");val done=o.optLong("completed");cb(if(total>0)(done.toFloat()/total).coerceIn(0f,1f)else 0f,o.optString("status")+(if(total>0)" • "+bytes(done)+" / "+bytes(total)else""))};cb(1f,"Download concluído")}}
private fun streamChat(base:String,model:String,msgs:List<ChatMessage>,think:Boolean,cb:(String,String)->Unit){val a=JSONArray();msgs.forEach{m->val o=JSONObject().put("role",m.role).put("content",m.content);if(m.role=="assistant"&&m.thinking.isNotBlank())o.put("thinking",m.thinking);a.put(o)};val p=JSONObject().put("model",model).put("messages",a).put("stream",true).put("think",think);val q=Request.Builder().url(endpoint(base,"api/chat")).post(p.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build();http().newCall(q).execute().use{res->if(!res.isSuccessful)throw IllegalStateException("HTTP "+res.code+": "+res.body?.string()?.take(400));val src=res.body?.source()?:throw IllegalStateException("Sem resposta");while(!src.exhausted()){val line=src.readUtf8Line()?:break;if(line.isBlank())continue;val o=JSONObject(line);if(o.has("error"))throw IllegalStateException(o.optString("error"));val m=o.optJSONObject("message")?:continue;cb(if(think)m.optString("thinking").takeUnless{it=="null"}.orEmpty()else"",m.optString("content").takeUnless{it=="null"}.orEmpty())}}}
private fun bytes(n:Long):String{if(n<1024)return "$n B";val u=listOf("KB","MB","GB","TB");var v=n.toDouble();var i=-1;do{v/=1024;i++}while(v>=1024&&i<u.lastIndex);return String.format(java.util.Locale.US,"%.1f %s",v,u[i])}
private fun encodeChats(chats:List<SavedChat>):String{val a=JSONArray();chats.forEach{c->val m=JSONArray();c.messages.forEach{x->m.put(JSONObject().put("id",x.id).put("role",x.role).put("content",x.content).put("thinking",x.thinking))};a.put(JSONObject().put("id",c.id).put("title",c.title).put("messages",m))};return a.toString()}
private fun parseChats(raw:String): List<SavedChat> =try{val a=JSONArray(raw);(0 until a.length()).map{i->val c=a.getJSONObject(i);val ma=c.getJSONArray("messages");val ms=(0 until ma.length()).map{j->val m=ma.getJSONObject(j);ChatMessage(m.optLong("id"),m.optString("role"),m.optString("content"),m.optString("thinking"))};SavedChat(c.optString("id"),c.optString("title"),ms)}}catch(_:Exception){emptyList()}
