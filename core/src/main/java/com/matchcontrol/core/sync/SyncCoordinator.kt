package com.matchcontrol.core.sync

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.wearable.*
import com.matchcontrol.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json

data class SyncState(val connected: Boolean=false,val lastSync: Long=0,val pending: Int=0,val message: String="Buscando dispositivo")
class SyncCoordinator(private val context: Context,val repository: DemoRepository) {
 var authoritySnapshot: ()->Snapshot = {repository.state.value}
 var authorityCommand: (SyncCommand)->Unit = repository::command
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
 private val syncPrefs=context.getSharedPreferences("sync-status-v2",Context.MODE_PRIVATE)
 private val authorityId=syncPrefs.getString("authority-id",null) ?: newId().also {syncPrefs.edit().putString("authority-id",it).commit()}
 private val publishLock=Mutex()
 private val commandLock=Mutex()
 private var observation: Job?=null
 private var lastPublished: String?=null
 private val mutable=MutableStateFlow(SyncState(lastSync=syncPrefs.getLong("last",0)))
 val state=mutable.asStateFlow()
 private val data get()=Wearable.getDataClient(context)
 private val messages get()=Wearable.getMessageClient(context)
 private val nodes get()=Wearable.getNodeClient(context)
 fun bindAccount(account: AccountManager) {
  check(repository.authority)
  authoritySnapshot=account::authoritySnapshot;authorityCommand=account::command
  observation?.cancel()
  observation=scope.launch {combine(account.state,account.signedIn) {_,_->Unit}.collect {
   runCatching {publish()}.onFailure {mutable.value=mutable.value.copy(message="Cambios guardados; sincronización pendiente")}
  }}
 }
 fun refresh() { scope.launch {
  try {
   val peers=nodes.connectedNodes.await()
   mutable.value=mutable.value.copy(connected=peers.isNotEmpty(),pending=repository.pending().size,message=if(peers.isEmpty()) "Sin vínculo · se conserva la información local" else "Dispositivo conectado")
   val items=data.dataItems.await()
   try { items.forEach { handleData(it,false) } } finally { items.release() }
   if(repository.authority) publish(true) else {
    repository.pending().forEach { send(it,peers) }
    peers.forEach { messages.sendMessage(it.id,"/matchcontrol/v2/refresh",byteArrayOf()).await() }
   }
  } catch(e: Exception) { mutable.value=mutable.value.copy(connected=false,message="Sin conexión con el otro dispositivo",pending=repository.pending().size) }
 } }
 fun changed() { scope.launch { runCatching { publish() }.onFailure { mutable.value=mutable.value.copy(message="Cambios guardados; sincronización pendiente") } } }
 @Synchronized fun command(command: SyncCommand) {
  if(repository.authority) scope.launch {commandLock.withLock {authorityCommand(command)};changed()}
  else if(!repository.hasReplica() || repository.state.value.fixtures.isEmpty()) mutable.value=mutable.value.copy(message="Abre el móvil para cargar resultados")
  else {
   val sequence=syncPrefs.getLong("command-sequence",0)+1
   syncPrefs.edit().putLong("command-sequence",sequence).commit()
   repository.enqueue(command.copy(profileId=repository.state.value.profile.id,source=repository.state.value.source,origin=authorityId,sequence=sequence));mutable.value=mutable.value.copy(pending=repository.pending().size);refresh()
  }
 }
 private suspend fun send(c: SyncCommand,peers: List<Node>) {
  val payload=json.encodeToString(c)
  data.putDataItem(PutDataMapRequest.create("/matchcontrol/v2/command/${c.id}").apply { dataMap.putString("payload",payload) }.asPutDataRequest().setUrgent()).await()
  peers.forEach { messages.sendMessage(it.id,"/matchcontrol/v2/action",payload.toByteArray()).await() }
 }
 private suspend fun publish(force: Boolean=false,acknowledged: Set<String> = emptySet()) = publishLock.withLock {
  if(!repository.authority) return@withLock
  val compact=WatchMirror.compact(authoritySnapshot())
  val snapshot=compact.copy(processed=compact.processed+acknowledged)
  val payload=json.encodeToString(snapshot)
  if(!force && payload==lastPublished) return@withLock
  val sequence=syncPrefs.getLong("outgoing",0)+1
  syncPrefs.edit().putLong("outgoing",sequence).commit()
  val bytes=json.encodeToString(MirrorFrame(authorityId,sequence,snapshot))
  val peers=nodes.connectedNodes.await()
  // Fast path for linked devices, durable path for reconnection. Both share ordering.
  peers.forEach {runCatching {messages.sendMessage(it.id,"/matchcontrol/v2/state",bytes.toByteArray()).await()}}
  data.putDataItem(PutDataMapRequest.create("/matchcontrol/v2/state").apply { dataMap.putString("payload",bytes);dataMap.putLong("sent",sequence) }.asPutDataRequest().setUrgent()).await()
  lastPublished=payload
 }
 private suspend fun applyCommand(command: SyncCommand,host: String?) {
  commandLock.withLock {authorityCommand(command)}
  publish(true,setOf(command.id))
  host?.let {runCatching {messages.sendMessage(it,"/matchcontrol/v2/command-ack",command.id.toByteArray()).await()}}
 }
 private suspend fun receive(payload: String,host: String?,live: Boolean) {
  val frame=if("authorityId" in json.parseToJsonElement(payload).jsonObject) json.decodeFromString<MirrorFrame>(payload) else null
  val s=frame?.snapshot ?: json.decodeFromString<Snapshot>(payload)
  if(frame!=null) repository.applyMirror(frame)
  else if(!syncPrefs.getBoolean("framed",false)) {repository.applySnapshot(s);repository.ack(s.processed)}
  if(frame!=null) syncPrefs.edit().putBoolean("framed",true).apply()
  val receivedAt=if(live) System.currentTimeMillis() else mutable.value.lastSync
  if(live) syncPrefs.edit().putLong("last",receivedAt).apply()
  mutable.value=mutable.value.copy(connected=mutable.value.connected || live,lastSync=receivedAt,pending=repository.pending().size,message=if(live) "Cuenta actualizada desde el móvil" else "Cuenta guardada")
  if(live) host?.let {runCatching {messages.sendMessage(it,"/matchcontrol/v2/ack",(frame?.sequence ?: s.revision).toString().toByteArray()).await()}}
 }
 suspend fun handleData(item: DataItem,live: Boolean=true) {
  val path=item.uri.path ?: return
  val payload=DataMapItem.fromDataItem(item).dataMap.getString("payload") ?: return
  when {
   path=="/matchcontrol/v2/state" && !repository.authority->{
    receive(payload,item.uri.host,live)
   }
   path.startsWith("/matchcontrol/v2/command/") && repository.authority->{applyCommand(json.decodeFromString(payload),item.uri.host);data.deleteDataItems(item.uri).await()}
  }
 }
 suspend fun handleMessage(event: MessageEvent) {
  if(!repository.authority) {
   when(event.path) {
    "/matchcontrol/v2/state"->receive(event.data.decodeToString(),event.sourceNodeId,true)
    "/matchcontrol/v2/command-ack"->{repository.ack(setOf(event.data.decodeToString()));mutable.value=mutable.value.copy(pending=repository.pending().size)}
   }
   return
  }
  when(event.path) {
   "/matchcontrol/v2/ack"->{val now=System.currentTimeMillis();syncPrefs.edit().putLong("last",now).apply();mutable.value=mutable.value.copy(connected=true,lastSync=now,message="Recepción confirmada por el reloj")}
   "/matchcontrol/v2/action"->applyCommand(json.decodeFromString(event.data.decodeToString()),event.sourceNodeId)
   "/matchcontrol/v2/refresh"->{runCatching {commandLock.withLock {authorityCommand(SyncCommand(action="refresh"))}};publish(true)}
  }
 }
}

object AppGraph {
 @Volatile private var accountInstance: AccountManager?=null
 @Volatile private var instance: DemoRepository?=null
 @Volatile private var coordinator: SyncCoordinator?=null
 fun repository(context: Context): DemoRepository = instance ?: synchronized(this) { instance ?: DemoRepository(context.applicationContext,!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).also { instance=it } }
 fun sync(context: Context): SyncCoordinator = coordinator ?: synchronized(this) { coordinator ?: SyncCoordinator(context.applicationContext,repository(context)).also { coordinator=it } }
 fun account(context: Context): AccountManager=accountInstance ?: synchronized(this) {accountInstance ?: AccountManager(context.applicationContext,repository(context)).also {accountInstance=it;sync(context).bindAccount(it)}}
 fun configureBackend(context: Context,url: String,key: String) {
  context.getSharedPreferences("backend-public-config",Context.MODE_PRIVATE).edit().putString("url",url).putString("key",key).commit()
  account(context)
 }
}
class MatchSyncService: WearableListenerService() {
 private fun coordinator(): SyncCoordinator {
  if(AppGraph.repository(applicationContext).authority) AppGraph.account(applicationContext)
  return AppGraph.sync(applicationContext)
 }
 override fun onDataChanged(events: DataEventBuffer) {
  // Consume buffer before the callback returns; never retain released DataItems.
  runBlocking(Dispatchers.IO) { events.forEach { if(it.type==DataEvent.TYPE_CHANGED) runCatching { coordinator().handleData(it.dataItem) } } }
 }
 override fun onMessageReceived(event: MessageEvent) { runBlocking(Dispatchers.IO) { runCatching { coordinator().handleMessage(event) } } }
}
