package com.matchcontrol.core

import android.content.Context
import android.content.ContentValues
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Orders account transitions while allowing requests to run without holding this lock. */
internal class AccountSessionGuard {
 private var generation=0L
 @Synchronized fun capture()=generation
 @Synchronized fun <T> locked(block: ()->T): T=block()
 @Synchronized fun advance(block: ()->Unit): Long {generation++;block();return generation}
 @Synchronized fun commit(expected: Long,block: ()->Unit): Boolean {
  if(expected!=generation) return false
  block();return true
 }
 @Synchronized fun <T> requireCurrent(expected: Long,block: ()->T): T {
  check(expected==generation) {"La cuenta cambió. Inténtalo de nuevo."}
  return block()
 }
}

class AccountManager(private val context: Context,val demo: DemoRepository) {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val settings=context.getSharedPreferences("backend-public-config",Context.MODE_PRIVATE)
 private val config=if(settings.getString("url","").orEmpty().isNotBlank() && settings.getString("key","").orEmpty().isNotBlank()) RemoteConfig(settings.getString("url","")!!,settings.getString("key","")!!) else null
 private val sessions=AccountSessionGuard()
 private val refreshLock=Any()
 private val vault=SessionVault(context)
 private var session=vault.load()
 private val cache=context.openOrCreateDatabase("remote-v2-cache.db",Context.MODE_PRIVATE,null).apply {execSQL("CREATE TABLE IF NOT EXISTS snapshot(id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")}
 private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
 private val mutable=MutableStateFlow(if(session!=null) readCache() ?: Snapshot(profile=Profile(id="",displayName=""),balance=0,fixtures=emptyList(),source="API_FOOTBALL") else demo.state.value)
 val state=mutable.asStateFlow()
 private val signed=MutableStateFlow(session!=null)
 val signedIn=signed.asStateFlow()
 val configured get()=config!=null
 @Volatile private var remote: RemoteRepository?=null
 private fun readCache(): Snapshot?=cache.rawQuery("SELECT payload FROM snapshot WHERE id=1",null).use {if(it.moveToFirst()) json.decodeFromString<Snapshot>(it.getString(0)) else null}
 private fun accept(client: RemoteRepository,s: Snapshot) = sessions.locked {
  // A response belongs to the exact client/session that issued it, even across the same user signing in again.
  if(!signed.value || client!==remote) return@locked
  val old=mutable.value
  if(s.profile.id==old.profile.id && s.revision<old.revision) return@locked
  cache.insertWithOnConflict("snapshot",null,ContentValues().apply {put("id",1);put("payload",json.encodeToString(s))},android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
  mutable.value=s;runCatching {ResultAlerts.changed(context,old,s)}
 }
 private fun token(epoch: Long): String = synchronized(refreshLock) {
  val current=sessions.requireCurrent(epoch) {check(signed.value);requireNotNull(session) {"Inicia sesión de nuevo"}}
  if(!vault.expiredSoon()) return@synchronized current.access_token
  val next=try {SupabaseAuth(requireNotNull(config)).refresh(current.refresh_token)} catch(e: Exception) {throw IllegalStateException("No se pudo renovar la sesión. Comprueba tu conexión.",e)}
  sessions.requireCurrent(epoch) {check(signed.value);vault.save(next);session=next;next.access_token}
 }
 private fun client(expected: Long?=null): RemoteRepository = sessions.locked {
  val epoch=sessions.capture()
  if(expected!=null) check(epoch==expected) {"La cuenta cambió. Inténtalo de nuevo."}
  check(signed.value && session!=null) {"Inicia sesión de nuevo"}
  remote ?: RemoteRepository(requireNotNull(config) {"Servicio no configurado"},{token(epoch)}).also {remote=it}
 }
 init {scope.launch {demo.state.collect {s->sessions.locked {if(!signed.value) mutable.value=s}}}}
 fun signIn(email: String,password: String,create: Boolean=false) {
  require(email.contains('@') && password.length>=6) {"Revisa el correo y la contraseña (mínimo 6 caracteres)"}
  val auth=SupabaseAuth(requireNotNull(config) {"Inicio de sesión no disponible"})
  val attempt=sessions.advance {remote=null}
  val next=if(create) auth.signUp(email.trim(),password) ?: error("Cuenta creada. Confirma el correo antes de iniciar sesión.") else auth.signIn(email.trim(),password)
  sessions.requireCurrent(attempt) {
   vault.save(next);session=next;remote=null
   // Remove any cached data from a previous account before loading this identity.
   cache.delete("snapshot",null,null);mutable.value=Snapshot(profile=Profile(id="",displayName=""),balance=0,source="API_FOOTBALL");signed.value=true
  }
  refresh()
 }
 fun signOut() {
  var oldToken: String?=null
  sessions.advance {
   oldToken=session?.access_token;vault.clear();session=null;remote=null
   cache.delete("snapshot",null,null);signed.value=false;mutable.value=demo.state.value
  }
  oldToken?.let {if(config!=null) runCatching {SupabaseAuth(requireNotNull(config)).signOut(it)}}
 }
 fun refresh() {if(signed.value) {val c=client();c.refresh();accept(c,c.state.value)} else sessions.locked {if(!signed.value) mutable.value=demo.state.value}}
 fun loadDate(date: LocalDate,sport: SportKind=SportKind.FOOTBALL) {if(signed.value && state.value.source!="DEMO") {val c=client();try {c.fetchSportDate(date,sport)} finally {c.refresh();accept(c,c.state.value)}}}
 fun details(id: String) {if(signed.value && state.value.source!="DEMO") {val c=client();try {c.fetchDetail(id)} finally {c.refresh();accept(c,c.state.value)}}}
 fun odds(id: String) {if(signed.value && state.value.source!="DEMO") {val c=client();try {c.fetchOdds(id)} finally {c.refresh();accept(c,c.state.value)}}}
 fun mode(demoMode: Boolean) {check(signed.value);val c=client();c.mode(demoMode);accept(c,c.state.value)}
 fun newDemoRound() {check(state.value.source=="DEMO");if(signed.value) {val c=client();c.newDemoRound();accept(c,c.state.value)} else demo.update {DemoLab.newRound(it)}}
 fun demoResult(id: String,home: Int?=null,away: Int?=null,order: List<String>?=null,void: Boolean=false) {
  check(state.value.source=="DEMO") {"Solo se modifican eventos DEMO"}
  if(signed.value) {val c=client();c.demoResult(id,home,away,order,void);accept(c,c.state.value)}
  else demo.update {when {void->DemoLab.cancel(it,id);order!=null->DemoLab.finishRace(it,id,order);else->DemoLab.finish(it,id,requireNotNull(home),requireNotNull(away))}}
 }
 fun place(r: BetRequest): Bet=if(signed.value) {val c=client();c.place(r).also {accept(c,c.state.value)}} else demo.place(r)
 fun reset(id: String) {if(signed.value) {val c=client();c.reset(id);accept(c,c.state.value)} else demo.reset(id)}
 fun notifications(enabled: Boolean) {if(signed.value) {val c=client();c.notifications(enabled);accept(c,c.state.value)} else demo.update {it.copy(notifications=enabled)}}
 @Synchronized fun command(c: SyncCommand) {
  val epoch=sessions.capture()
  if(c.profileId.isNotBlank() && (c.profileId!=state.value.profile.id || c.source!=state.value.source)) {demo.update {it.copy(processed=it.processed+c.id)};return}
  if(c.id in demo.state.value.processed) return
  val order=context.getSharedPreferences("watch-command-order",Context.MODE_PRIVATE)
  val ordered=c.origin.isNotBlank() && c.sequence>0 && c.action in listOf("team","league","match")
  val key="${state.value.profile.id}|${state.value.source}|${c.origin}|${c.action}|${c.target}"
  if(ordered && c.sequence<=order.getLong(key,0)) {demo.update {it.copy(processed=it.processed+c.id)};return}
  if(!signed.value) {
   sessions.requireCurrent(epoch) {demo.command(c);if(ordered) order.edit().putLong(key,c.sequence).commit()}
   return
  }
  if(c.action in listOf("team","league","match")) {val remote=client(epoch);remote.favorite(c.action,c.target,c.enabled);accept(remote,remote.state.value)}
  else if(c.action=="refresh") refresh()
  else if(c.action=="sport") loadDate(LocalDate.now(),SportKind.valueOf(c.target))
  else if(c.action=="select") sessions.requireCurrent(epoch) {require(state.value.fixtures.any {it.id==c.target});mutable.update {it.copy(selectedMatch=c.target)}}
  else error("Acción no permitida")
  sessions.commit(epoch) {demo.update {it.copy(processed=it.processed+c.id)};if(ordered) order.edit().putLong(key,c.sequence).commit()}
 }
 fun consumeSelection()=sessions.locked {if(signed.value) mutable.update {it.copy(selectedMatch=null)} else demo.update {it.copy(selectedMatch=null)}}
 fun authoritySnapshot()=sessions.locked {state.value.copy(processed=demo.state.value.processed,accountSignedIn=signed.value)}
}
