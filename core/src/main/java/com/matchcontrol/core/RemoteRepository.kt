package com.matchcontrol.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/** Prepared adapter, explicitly opt-in. Never constructed by the DEMO graph. */
data class RemoteConfig(val baseUrl: String,val publishableKey: String) {
 init {require(baseUrl.startsWith("https://"));require(publishableKey.isNotBlank())}
}
@Serializable data class AuthSession(val access_token: String,val refresh_token: String,val expires_in: Long)

class SupabaseAuth(private val config: RemoteConfig) {
 private val json=Json {ignoreUnknownKeys=true}
 fun signIn(email: String,password: String): AuthSession = json.decodeFromString(requestJson(config,"/auth/v1/token?grant_type=password",buildJsonObject {put("email",email);put("password",password)},null))
 fun refresh(refreshToken: String): AuthSession=json.decodeFromString(requestJson(config,"/auth/v1/token?grant_type=refresh_token",buildJsonObject {put("refresh_token",refreshToken)},null))
 fun signOut(token: String) {requestJson(config,"/auth/v1/logout",buildJsonObject {},token)}
 fun signUp(email: String,password: String): AuthSession? {
  val data=json.parseToJsonElement(requestJson(config,"/auth/v1/signup",buildJsonObject {put("email",email);put("password",password)},null)).jsonObject
  return if(data["access_token"]!=null) json.decodeFromJsonElement<AuthSession>(data) else null
 }
}

class RemoteRepository(private val config: RemoteConfig,private val accessToken: ()->String): MatchesRepository,ResultsRepository,OddsRepository,UsersRepository,WalletsRepository,BetsRepository,HistoryRepository {
 private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
 private val mutable=MutableStateFlow(Snapshot(fixtures=emptyList(),balance=0,transactions=emptyList(),revision=0))
 val state=mutable.asStateFlow()
 private fun invoke(body: JsonObject)=json.parseToJsonElement(requestJson(config,"/functions/v1/sandbox",body,accessToken())).jsonObject
 fun refresh() {mutable.value=json.decodeFromJsonElement(invoke(buildJsonObject {put("action","snapshot")}))}
 fun fetchDate(date: LocalDate) {
  requestJson(config,"/functions/v1/football",buildJsonObject {put("endpoint","fixtures");put("params",buildJsonObject {put("date",date.toString());put("timezone",ZoneId.systemDefault().id)})},accessToken())
  refresh()
 }
 fun fetchDetail(matchId: String) {
  if(!matchId.startsWith("api:")) return
  requestJson(config,"/functions/v1/football",buildJsonObject {put("endpoint","fixtures");put("params",buildJsonObject {put("id",matchId.substringAfter(':'))})},accessToken())
  refresh()
 }
 fun fetchOdds(matchId: String) {
  if(!matchId.startsWith("api:")) return
  requestJson(config,"/functions/v1/football",buildJsonObject {put("endpoint","odds");put("params",buildJsonObject {put("fixture",matchId.substringAfter(':'))})},accessToken())
  refresh()
 }
 fun notifications(enabled: Boolean) {mutable.value=json.decodeFromJsonElement(invoke(buildJsonObject {put("action","notifications");put("enabled",enabled)}))}
 override fun matches()=mutable.value.fixtures
 override fun results()=matches().filter {it.status==FixtureStatus.FINAL}
 override fun markets(matchId: String)=mutable.value.outcomes.filter {it.matchId==matchId}
 override fun profile()=mutable.value.profile
 override fun balance()=mutable.value.balance
 override fun history()=mutable.value.bets
 override fun movements()=mutable.value.transactions
 override fun place(request: BetRequest): Bet {
  val response=invoke(buildJsonObject {put("action","place");put("requestId",request.requestId);put("stake",request.stake);put("outcomes",json.encodeToJsonElement(request.outcomes))})
  mutable.value=json.decodeFromJsonElement(requireNotNull(response["snapshot"]))
  return mutable.value.bets.first {it.id==response["betId"]?.jsonPrimitive?.content}
 }
 override fun reset(requestId: String) {mutable.value=json.decodeFromJsonElement(invoke(buildJsonObject {put("action","reset");put("requestId",requestId)}))}
 fun favorite(kind: String,target: String,enabled: Boolean) {mutable.value=json.decodeFromJsonElement(invoke(buildJsonObject {put("action","favorite");put("kind",kind);put("target",target);put("enabled",enabled)}))}
}

private fun requestJson(config: RemoteConfig,path: String,body: JsonObject,token: String?): String {
 val connection=URL(config.baseUrl.trimEnd('/')+path).openConnection() as HttpURLConnection
 try {
  connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=20000;connection.doOutput=true
  connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("apikey",config.publishableKey)
  if(token!=null) connection.setRequestProperty("Authorization","Bearer $token")
  connection.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}
  check(connection.responseCode in 200..299) {when(connection.responseCode) {401->"La sesión no está disponible. Vuelve a iniciar sesión.";429->"Demasiadas consultas. Inténtalo más tarde.";502,503->"Los resultados no están disponibles en este momento.";else->"No se pudo completar. Actualiza los datos y revisa la selección."}}
  return connection.inputStream.bufferedReader().use {it.readText()}.ifBlank {"{}"}
 } finally {connection.disconnect()}
}
