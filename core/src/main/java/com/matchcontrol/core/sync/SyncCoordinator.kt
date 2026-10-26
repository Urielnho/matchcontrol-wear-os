package com.matchcontrol.core.sync

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.wearable.*
import com.matchcontrol.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class SyncState(val connected: Boolean=false,val lastSync: Long=0,val pending: Int=0,val message: String="Buscando dispositivo")
class SyncCoordinator(private val context: Context,val repository: DemoRepository) {
 var authoritySnapshot: ()->Snapshot = {repository.state.value}
 var authorityCommand: (SyncCommand)->Unit = repository::command
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
 private val syncPrefs=context.getSharedPreferences("sync-status-v2",Context.MODE_PRIVATE)
 private val mutable=MutableStateFlow(SyncState(lastSync=syncPrefs.getLong("last",0)))
 val state=mutable.asStateFlow()
 private val data get()=Wearable.getDataClient(context)
 private val messages get()=Wearable.getMessageClient(context)
 private val nodes get()=Wearable.getNodeClient(context)
 fun refresh() { scope.launch {
  try {
   val peers=nodes.connectedNodes.await()
   mutable.value=mutable.value.copy(connected=peers.isNotEmpty(),pending=repository.pending().size,message=if(peers.isEmpty()) "Sin vínculo · se conserva la información local" else "Dispositivo conectado")
   val items=data.dataItems.await()
   try { items.forEach { handleData(it,false) } } finally { items.release() }
   if(repository.authority) publish() else {
    repository.pending().forEach { send(it,peers) }
    peers.forEach { messages.sendMessage(it.id,"/matchcontrol/v2/refresh",byteArrayOf()).await() }
   }
  } catch(e: Exception) { mutable.value=mutable.value.copy(connected=false,message="Sin conexión con el otro dispositivo",pending=repository.pending().size) }
 } }
 fun changed() { scope.launch { runCatching { publish() }.onFailure { mutable.value=mutable.value.copy(message="Cambios guardados; sincronización pendiente") } } }
 fun command(command: SyncCommand) {
  if(repository.authority) { authorityCommand(command);changed() }
  else { repository.enqueue(command.copy(profileId=repository.state.value.profile.id,source=repository.state.value.source));mutable.value=mutable.value.copy(pending=repository.pending().size);refresh() }
 }
 private suspend fun send(c: SyncCommand,peers: List<Node>) {
  val payload=json.encodeToString(c)
  data.putDataItem(PutDataMapRequest.create("/matchcontrol/v2/command/${c.id}").apply { dataMap.putString("payload",payload) }.asPutDataRequest().setUrgent()).await()
  peers.forEach { messages.sendMessage(it.id,"/matchcontrol/v2/action",payload.toByteArray()).await() }
 }
 private suspend fun publish() {
  if(!repository.authority) return
  val snapshot=authoritySnapshot()
  val recentBets=snapshot.bets.takeLast(15)
  val referenced=recentBets.flatMap {it.picks.map {p->p.matchId}}.toSet()
  val favorites=snapshot.fixtures.filter {it.id in snapshot.favoriteMatches || it.home.id in snapshot.favoriteTeams || it.away.id in snapshot.favoriteTeams}
  val fixtures=(favorites+snapshot.fixtures.filter {it.id in referenced}+snapshot.fixtures.filter {it.status==FixtureStatus.LIVE}.take(8)+snapshot.fixtures.filter {it.status==FixtureStatus.SCHEDULED}.sortedBy {it.startsAt}.take(10)+snapshot.fixtures.filter {it.status==FixtureStatus.FINAL}.sortedByDescending {it.startsAt}.take(6)).distinctBy {it.id}.take(35)
  val s=snapshot.copy(fixtures=fixtures,outcomes=emptyList(),bets=recentBets,transactions=emptyList())
  val bytes=json.encodeToString(s)
  check(bytes.toByteArray().size<95_000) { "El resumen supera el tamaño permitido" }
  data.putDataItem(PutDataMapRequest.create("/matchcontrol/v2/state").apply { dataMap.putString("payload",bytes);dataMap.putLong("sent",System.currentTimeMillis()) }.asPutDataRequest().setUrgent()).await()
 }
 suspend fun handleData(item: DataItem,live: Boolean=true) {
  val path=item.uri.path ?: return
  val payload=DataMapItem.fromDataItem(item).dataMap.getString("payload") ?: return
  when {
   path=="/matchcontrol/v2/state" && !repository.authority->{
    val s=json.decodeFromString<Snapshot>(payload);repository.applySnapshot(s);repository.ack(s.processed)
    val receivedAt=if(live) System.currentTimeMillis() else mutable.value.lastSync
    if(live) syncPrefs.edit().putLong("last",receivedAt).apply()
    mutable.value=mutable.value.copy(lastSync=receivedAt,pending=repository.pending().size,message=if(live) "Resumen recibido del móvil" else "Resumen guardado")
    item.uri.host?.let {host->runCatching {messages.sendMessage(host,"/matchcontrol/v2/ack",s.revision.toString().toByteArray()).await()}}
   }
   path.startsWith("/matchcontrol/v2/command/") && repository.authority->{authorityCommand(json.decodeFromString(payload));publish();data.deleteDataItems(item.uri).await()}
  }
 }
 suspend fun handleMessage(event: MessageEvent) {
  if(!repository.authority) return
  when(event.path) {
   "/matchcontrol/v2/ack"->{val now=System.currentTimeMillis();syncPrefs.edit().putLong("last",now).apply();mutable.value=mutable.value.copy(lastSync=now,message="Recepción confirmada por el reloj")}
   "/matchcontrol/v2/action"->{authorityCommand(json.decodeFromString(event.data.decodeToString()));publish()}
   "/matchcontrol/v2/refresh"->publish()
  }
 }
}

object AppGraph {
 @Volatile private var accountInstance: AccountManager?=null
 @Volatile private var instance: DemoRepository?=null
 @Volatile private var coordinator: SyncCoordinator?=null
 fun repository(context: Context): DemoRepository = instance ?: synchronized(this) { instance ?: DemoRepository(context.applicationContext,!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).also { instance=it } }
 fun sync(context: Context): SyncCoordinator = coordinator ?: synchronized(this) { coordinator ?: SyncCoordinator(context.applicationContext,repository(context)).also { coordinator=it } }
 fun account(context: Context): AccountManager=accountInstance ?: synchronized(this) {accountInstance ?: AccountManager(context.applicationContext,repository(context)).also {accountInstance=it;sync(context).authoritySnapshot=it::authoritySnapshot;sync(context).authorityCommand=it::command}}
 fun configureBackend(context: Context,url: String,key: String) {
  context.getSharedPreferences("backend-public-config",Context.MODE_PRIVATE).edit().putString("url",url).putString("key",key).commit()
  account(context)
 }
}
class MatchSyncService: WearableListenerService() {
 override fun onDataChanged(events: DataEventBuffer) {
  // Consume buffer before the callback returns; never retain released DataItems.
  runBlocking(Dispatchers.IO) { events.forEach { if(it.type==DataEvent.TYPE_CHANGED) runCatching { AppGraph.sync(applicationContext).handleData(it.dataItem) } } }
 }
 override fun onMessageReceived(event: MessageEvent) { runBlocking(Dispatchers.IO) { runCatching { AppGraph.sync(applicationContext).handleMessage(event) } } }
}
