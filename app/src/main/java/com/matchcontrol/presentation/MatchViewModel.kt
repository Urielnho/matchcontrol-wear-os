package com.matchcontrol.presentation
import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchcontrol.data.*
import com.matchcontrol.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import kotlinx.serialization.json.Json

data class UiState(val settings: Settings=Settings(), val date: LocalDate=LocalDate.now(), val matches: List<Match> = emptyList(), val detail: Match?=null, val loading: Boolean=false, val error: String?=null, val updated: Long=0, val stale: Boolean=false, val online: Boolean=false, val competitions: List<Competition> = emptyList(), val remainingCalls: Int? = null, val knownTeams: List<Team> = emptyList(), val settingsReady: Boolean = false, val detailLoading: Boolean = false)
class MatchViewModel(app: Application): AndroidViewModel(app) {
 private val store=SettingsStore(app)
 private val api=ApiRepository(app)
 private val notifications=MatchNotifications(app)
 private val catalog=app.assets.open("competitions.json").bufferedReader().use { Json.decodeFromString<List<Competition>>(it.readText()) }
 private val mutable=MutableStateFlow(UiState(competitions=catalog))
 val state=mutable.asStateFlow()
 private var refreshJob: Job?=null
 private var refreshKey: String?=null
 private var detailJob: Job?=null
 private val connectivity=app.getSystemService(ConnectivityManager::class.java)
 private val networkCallback=object: ConnectivityManager.NetworkCallback() {
  override fun onAvailable(network: android.net.Network) { mutable.update { it.copy(online=connected()) } }
  override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
   mutable.update { it.copy(online=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) }
  }
  override fun onLost(network: android.net.Network) { mutable.update { it.copy(online=connected()) } }
 }
 init { connectivity.registerDefaultNetworkCallback(networkCallback); viewModelScope.launch { store.teamFlow.collect { teams -> mutable.update { it.copy(knownTeams=teams) } } }; viewModelScope.launch { store.migrateLeagues(catalog); store.flow.collect { settings ->
  mutable.update { it.copy(settings=settings,settingsReady=true) }
  refresh()
 } } }
 fun refresh(force: Boolean=false) {
  if(!mutable.value.settingsReady) return
  val key="${mutable.value.date}"
  if(refreshJob?.isActive==true && refreshKey==key) return
  refreshJob?.cancel()
  refreshKey=key
  refreshJob=viewModelScope.launch {
   val initial=mutable.value
   mutable.update { it.copy(loading=true,error=null,online=connected()) }
   try {
    val matches=api.matches(initial.date,force)
    if(initial.date==LocalDate.now() && !api.stale) matches.forEach { next ->
     notifications.observe(next,initial.settings.teams,initial.settings.notifications)
    }
    store.rememberTeams(matches.flatMap { listOf(it.home,it.away) })
    if(mutable.value.date!=initial.date) return@launch
    mutable.update { it.copy(matches=matches, loading=false,online=connected(),updated=api.updated,stale=api.stale, remainingCalls=api.remainingCalls) }
   } catch(e: Exception) { if(e is CancellationException) throw e; mutable.update { it.copy(loading=false,error=e.message ?: "No se pudieron cargar los partidos",stale=it.matches.isNotEmpty(),remainingCalls=api.remainingCalls) } }
  }
 }
 fun today() {
  val today=LocalDate.now()
  if(mutable.value.date!=today) {
   mutable.update { it.copy(date=today,matches=emptyList(),detail=null,updated=0,stale=false) }
   refresh()
  }
 }
 fun day(offset: Long) { mutable.update { it.copy(date=it.date.plusDays(offset),matches=emptyList(),detail=null,updated=0,stale=false) }; refresh() }
 fun select(match: Match) = selectInternal(match,false)
 fun reloadDetail() { mutable.value.detail?.let { selectInternal(it,true) } }
 private fun selectInternal(match: Match,force: Boolean) {
  detailJob?.cancel()
  mutable.update { it.copy(detail=match,detailLoading=true) }
  detailJob=viewModelScope.launch { try {
   val result=api.details(match,force)
   mutable.update { if(it.detail?.id==match.id) it.copy(detail=result, detailLoading=false,online=connected(), stale=api.stale, updated=api.updated, remainingCalls=api.remainingCalls) else it }
  } catch(e: Exception) { if(e is CancellationException) throw e; mutable.update { it.copy(detailLoading=false,error="No se pudieron cargar los detalles: ${e.message}",remainingCalls=api.remainingCalls) } } }
 }
 fun setNotifications(value: Boolean) { viewModelScope.launch { val changed=value && !mutable.value.settings.notifications;store.notifications(value);if(changed) notifications.enabledConfirmation() } }
 fun toggleTeam(team: Team) { viewModelScope.launch { store.rememberTeams(listOf(team)); store.toggle("teams",team.id.toString()) } }
 fun toggleLeague(competition: Competition) { viewModelScope.launch { store.toggle("leagues",competition.id.toString()) } }
 private fun favorite(m: Match,s: Settings)=m.home.id.toString() in s.teams || m.away.id.toString() in s.teams
 override fun onCleared() {
  connectivity.unregisterNetworkCallback(networkCallback)
  super.onCleared()
 }
 private fun connected(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
}
