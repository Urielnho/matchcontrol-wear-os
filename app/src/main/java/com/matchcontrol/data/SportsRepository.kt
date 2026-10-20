package com.matchcontrol.data

import android.content.Context
import com.matchcontrol.BuildConfig
import com.matchcontrol.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.File
import java.time.*
import java.util.concurrent.TimeUnit

data class SportsResponse(val events: List<SportEvent>,val stale: Boolean,val updated: Long)
class SportsRepository(context: Context) {
 private val directory=File(context.filesDir,"sports-cache").apply { mkdirs() }
 private val quota=context.getSharedPreferences("sports-quota",Context.MODE_PRIVATE)
 private val mutex=Mutex()
 private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 private var stale=false
 private var updated=0L
 private suspend fun fetch(sport: Sport,path: String,ttl: Long): JsonObject=withContext(Dispatchers.IO) {
  val file=File(directory,"${sport.name}-${path.hashCode()}.json")
  fun validate(root: JsonObject) { if(sport==Sport.FORMULA1) check(root["MRData"] is JsonObject) { "No se pudo leer la clasificación." } else FootballResponse.validate(root) }
  val cached=if(file.exists()) runCatching { Json.parseToJsonElement(file.readText()).jsonObject.also(::validate) }.getOrNull() else null
  if(cached!=null && System.currentTimeMillis()-file.lastModified()<ttl) { updated=maxOf(updated,file.lastModified());return@withContext cached }
  try {
   val day=LocalDate.now(ZoneOffset.UTC).toString();val key="${sport.name}-$day";val count=quota.getInt(key,0)
   val remaining=quota.getInt("remaining-$key",-1).takeIf { it>=0 }
   check(RequestBudget.allows(count,remaining)) { "Límite de consultas alcanzado. Inténtalo más tarde." }
   check(quota.edit().putInt(key,count+1).commit()) { "No se pudo guardar la consulta." }
   val url=if(sport==Sport.FORMULA1) "https://api.jolpi.ca/ergast/f1/$path" else "https://${sport.host}/$path"
   val builder=Request.Builder().url(url).header("User-Agent","MatchControl/1.0.0")
   if(sport!=Sport.FORMULA1) {
    check(BuildConfig.FOOTBALL_API_KEY.isNotBlank()) { "Los resultados no están disponibles en este momento." }
    builder.header("x-apisports-key",BuildConfig.FOOTBALL_API_KEY)
   }
   val root=client.newCall(builder.build()).execute().use { response ->
    response.header("x-ratelimit-requests-remaining")?.toIntOrNull()?.let { quota.edit().putInt("remaining-$key",it).apply() }
    check(response.isSuccessful) { when(response.code) { 401,403->"Este contenido no está disponible en este momento.";429->"Demasiadas consultas. Inténtalo más tarde.";else->"El servicio no está disponible. Inténtalo más tarde." } }
    Json.parseToJsonElement(requireNotNull(response.body).string()).jsonObject.also(::validate)
   }
   val temp=File(file.path+".tmp");temp.writeText(root.toString());check(temp.renameTo(file)) { "No se pudieron guardar los resultados." }
   updated=maxOf(updated,file.lastModified());root
  } catch(e: Exception) {
   if(e is CancellationException) throw e
   if(cached!=null) { stale=true;updated=maxOf(updated,file.lastModified());cached }
   else throw IllegalStateException(when(e) { is java.net.UnknownHostException->"Sin conexión. Inténtalo de nuevo cuando tengas internet.";is java.io.IOException->"La conexión se interrumpió. Inténtalo de nuevo.";is kotlinx.serialization.SerializationException->"Los resultados no se pudieron leer. Inténtalo de nuevo.";else->e.message ?: "No se pudieron cargar los resultados." })
  }
 }
 suspend fun events(sport: Sport,date: LocalDate,force: Boolean=false): SportsResponse=mutex.withLock {
  stale=false;updated=0
  val events=if(sport==Sport.FORMULA1) {
   val calendar=SportsMapper.races(fetch(sport,"${date.year}.json?limit=100",if(force) 0 else 6*60*60_000L)).mapNotNull { SportsMapper.race(it) }
   val latest=SportsMapper.races(fetch(sport,"${date.year}/last/results.json?limit=100",if(force) 0 else 30*60_000L)).mapNotNull { SportsMapper.race(it) }
   (latest+calendar).distinctBy { it.id }
  } else {
   // Query both UTC dates that intersect the watch's local day, then filter locally.
   val zone=ZoneId.systemDefault();val start=date.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDate()
   val end=date.plusDays(1).atStartOfDay(zone).minusNanos(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDate()
   listOf(start,end).distinct().flatMap { utc -> fetch(sport,"games?date=$utc",if(force) 0 else 15*60_000L).listAt("response").mapNotNull { SportsMapper.game(it,sport) } }.distinctBy { it.id }.filter { it.localDate==date }.sortedBy { it.kickoff }
  }
  SportsResponse(events,stale,updated)
 }
 suspend fun detail(event: SportEvent,force: Boolean=false): SportsResponse=mutex.withLock {
  stale=false;updated=0
  val rows=if(event.sport==Sport.FORMULA1) SportsMapper.races(fetch(event.sport,"${event.season}/${event.round}/results.json?limit=100",if(force) 0 else if(event.state==EventState.FINAL) 24*60*60_000L else 15*60_000L)).mapNotNull { SportsMapper.race(it) }
  else fetch(event.sport,"games?id=${event.id}",if(force) 0 else if(event.state==EventState.FINAL) 24*60*60_000L else 15*60_000L).listAt("response").mapNotNull { SportsMapper.game(it,event.sport) }
  SportsResponse(rows.ifEmpty { listOf(event) },stale,updated)
 }
}
