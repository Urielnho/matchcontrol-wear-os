package com.matchcontrol.mobile

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchcontrol.core.*
import com.matchcontrol.core.sync.AppGraph
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MobileViewModel(app: Application): AndroidViewModel(app) {
 val repo=AppGraph.repository(app)
 val account=AppGraph.account(app)
 val state=account.state
 val signedIn=account.signedIn
 private val connectivity=app.getSystemService(ConnectivityManager::class.java)
 val online=MutableStateFlow(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
 private val networkCallback=object: ConnectivityManager.NetworkCallback() {
  override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities) {val connected=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);val was=online.value;online.value=connected;if(connected && !was) refresh()}
  override fun onLost(network: Network) {online.value=connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true}
 }
 val loading=MutableStateFlow(false)
 val sync=AppGraph.sync(app)
 val slip=MutableStateFlow<List<Outcome>>(emptyList())
 val message=MutableStateFlow<String?>(null)
 private var requestId=newId()
 fun act(action: ()->Unit) { viewModelScope.launch(Dispatchers.IO) {loading.value=true;try { action();sync.changed() } catch(e: Exception) { message.value=when(e) {is java.io.IOException->"Sin conexión. Puedes consultar los datos guardados.";is NumberFormatException,is ArithmeticException->"Introduce un importe válido con hasta dos decimales.";else->e.message ?: "No se pudo completar"} } finally {loading.value=false} } }
 fun pick(outcome: Outcome) {
  val current=slip.value
  if(current.any { it.id==outcome.id }) slip.value=current.filterNot { it.id==outcome.id }
  else if(current.any { it.matchId==outcome.matchId }) message.value="Quita la selección de ese partido antes de elegir otra"
  else if(current.size>=10) message.value="Máximo 10 eventos por boleto"
  else slip.value=current+outcome
  requestId=newId()
 }
 fun confirm(stake: String,onDone: ()->Unit) {
 if(!signedIn.value) {message.value="Inicia sesión para confirmar tu boleto";return}
 val request=runCatching {BetRequest(requestId,parseCredits(stake),slip.value.associate {it.id to it.odds})}.getOrElse {message.value="Introduce un importe válido";return}
 act {
  val bet=account.place(request)
  if(requestId==request.requestId) {slip.value=emptyList();requestId=newId()};message.value="Boleto ${bet.id.take(8)} confirmado con créditos virtuales"
  withContextOnMain(onDone)
 } }
 private fun withContextOnMain(block: ()->Unit) { viewModelScope.launch(Dispatchers.Main) { block() } }
 fun favorite(action: String,id: String,enabled: Boolean) = act { account.command(SyncCommand(action=action,target=id,enabled=enabled)) }
 fun notifications(enabled: Boolean)=act {account.notifications(enabled)}
 fun login(email: String,password: String,create: Boolean)=act {account.signIn(email,password,create);slip.value=emptyList();runCatching {account.loadDate(java.time.LocalDate.now())}.onFailure {message.value="No se pudieron actualizar los partidos. Intenta de nuevo."}}
 fun logout()=act {account.signOut();slip.value=emptyList()}
 fun refresh()=act {account.refresh();if(signedIn.value) account.loadDate(java.time.LocalDate.now());sync.refresh()}
 fun date(date: java.time.LocalDate,sport: SportKind=SportKind.FOOTBALL)=act {account.loadDate(date,sport)}
 fun details(id: String)=act {account.details(id)}
 fun markets(id: String)=act {account.odds(id)}
 fun poll()=act {account.refresh()}
 init {connectivity.registerDefaultNetworkCallback(networkCallback);sync.refresh();if(online.value) refresh()}
 override fun onCleared() {connectivity.unregisterNetworkCallback(networkCallback);super.onCleared()}
}
