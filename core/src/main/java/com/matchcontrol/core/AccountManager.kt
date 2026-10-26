package com.matchcontrol.core

import android.content.Context
import android.content.ContentValues
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

class AccountManager(private val context: Context,val demo: DemoRepository) {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val settings=context.getSharedPreferences("backend-public-config",Context.MODE_PRIVATE)
 private val config=if(settings.getString("url","").orEmpty().isNotBlank() && settings.getString("key","").orEmpty().isNotBlank()) RemoteConfig(settings.getString("url","")!!,settings.getString("key","")!!) else null
 private val vault=SessionVault(context)
 private var session=vault.load()
 private val cache=context.openOrCreateDatabase("remote-v2-cache.db",Context.MODE_PRIVATE,null).apply {execSQL("CREATE TABLE IF NOT EXISTS snapshot(id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")}
 private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
 private val mutable=MutableStateFlow(if(session!=null) readCache() ?: Snapshot(balance=0,fixtures=emptyList(),source="API_FOOTBALL") else demo.state.value)
 val state=mutable.asStateFlow()
 private val signed=MutableStateFlow(session!=null)
 val signedIn=signed.asStateFlow()
 val configured get()=config!=null
 @Volatile private var remote: RemoteRepository?=null
 private fun readCache(): Snapshot?=cache.rawQuery("SELECT payload FROM snapshot WHERE id=1",null).use {if(it.moveToFirst()) json.decodeFromString<Snapshot>(it.getString(0)) else null}
 private fun accept(s: Snapshot) {val old=mutable.value;cache.insertWithOnConflict("snapshot",null,ContentValues().apply {put("id",1);put("payload",json.encodeToString(s))},android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);mutable.value=s;runCatching {ResultAlerts.changed(context,old,s)}}
 @Synchronized private fun token(): String {
  val current=session ?: error("Inicia sesión de nuevo")
  if(vault.expiredSoon()) {
   try {session=SupabaseAuth(requireNotNull(config)).refresh(current.refresh_token);vault.save(requireNotNull(session))} catch(e: Exception) {throw IllegalStateException("No se pudo renovar la sesión. Comprueba tu conexión.",e)}
  }
  return requireNotNull(session).access_token
 }
 private fun client(): RemoteRepository = remote ?: RemoteRepository(requireNotNull(config) {"Servicio no configurado"},::token).also {remote=it}
 init {scope.launch {demo.state.collect {if(!signed.value) mutable.value=it}}}
 fun signIn(email: String,password: String,create: Boolean=false) {
  require(email.contains('@') && password.length>=6) {"Revisa el correo y la contraseña (mínimo 6 caracteres)"}
  val auth=SupabaseAuth(requireNotNull(config) {"Inicio de sesión no disponible"})
  val next=if(create) auth.signUp(email.trim(),password) ?: error("Cuenta creada. Confirma el correo antes de iniciar sesión.") else auth.signIn(email.trim(),password)
  session=next;vault.save(next);remote=null;signed.value=true
  // Remove any cached data from a previous account before loading this identity.
  cache.delete("snapshot",null,null);mutable.value=Snapshot(balance=0,source="API_FOOTBALL")
  refresh()
 }
 fun signOut() {
  val token=session?.access_token
  try {if(token!=null && config!=null) runCatching {SupabaseAuth(requireNotNull(config)).signOut(token)}} finally {vault.clear();session=null;remote=null;cache.delete("snapshot",null,null);signed.value=false;mutable.value=demo.state.value}
 }
 fun refresh() {if(signed.value) {val c=client();c.refresh();accept(c.state.value)} else mutable.value=demo.state.value}
 fun loadDate(date: LocalDate) {if(signed.value) {val c=client();try {c.fetchDate(date)} finally {c.refresh();accept(c.state.value)}}}
 fun details(id: String) {if(signed.value) {val c=client();try {c.fetchDetail(id)} finally {c.refresh();accept(c.state.value)}}}
 fun odds(id: String) {if(signed.value) {val c=client();try {c.fetchOdds(id)} finally {c.refresh();accept(c.state.value)}}}
 fun place(r: BetRequest): Bet=if(signed.value) {val c=client();c.place(r).also {accept(c.state.value)}} else demo.place(r)
 fun reset(id: String) {if(signed.value) {val c=client();c.reset(id);accept(c.state.value)} else demo.reset(id)}
 fun notifications(enabled: Boolean) {if(signed.value) {val c=client();c.notifications(enabled);accept(c.state.value)} else demo.update {it.copy(notifications=enabled)}}
 fun command(c: SyncCommand) {
  if(c.profileId.isNotBlank() && (c.profileId!=state.value.profile.id || c.source!=state.value.source)) {demo.update {it.copy(processed=it.processed+c.id)};return}
  if(!signed.value) {demo.command(c);return}
  if(c.id in demo.state.value.processed) return
  if(c.action in listOf("team","league","match")) {val remote=client();remote.favorite(c.action,c.target,c.enabled);accept(remote.state.value)}
  else if(c.action=="refresh") refresh()
  else if(c.action=="select") {require(state.value.fixtures.any {it.id==c.target});mutable.update {it.copy(selectedMatch=c.target)}}
  else error("Acción no permitida")
  demo.update {it.copy(processed=it.processed+c.id)}
 }
 fun consumeSelection() {if(signed.value) mutable.update {it.copy(selectedMatch=null)} else demo.update {it.copy(selectedMatch=null)}}
 fun authoritySnapshot()=state.value.copy(processed=demo.state.value.processed)
}
