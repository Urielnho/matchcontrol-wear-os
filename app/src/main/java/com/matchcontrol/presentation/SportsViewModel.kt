package com.matchcontrol.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchcontrol.data.SportsRepository
import com.matchcontrol.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class SportsState(val sport: Sport=Sport.BASKETBALL,val date: LocalDate=LocalDate.now(),val events: List<SportEvent> = emptyList(),val detail: SportEvent?=null,val loading: Boolean=false,val error: String?=null,val stale: Boolean=false,val updated: Long=0,val detailLoading: Boolean=false)
class SportsViewModel(app: Application): AndroidViewModel(app) {
 private val repo=SportsRepository(app)
 private val mutable=MutableStateFlow(SportsState())
 val state=mutable.asStateFlow()
 private var job: Job?=null
 private var detailJob: Job?=null
 fun choose(sport: Sport) { job?.cancel();detailJob?.cancel();mutable.value=SportsState(sport=sport);refresh() }
 fun day(offset: Long) { detailJob?.cancel();mutable.update { it.copy(date=it.date.plusDays(offset),events=emptyList(),detail=null,detailLoading=false,updated=0,stale=false) };refresh() }
 fun refresh(force: Boolean=false) {
  mutable.update { it.copy(loading=false,events=emptyList(),error="Abre este deporte en el móvil para cargar resultados.") }
  return
  @Suppress("UNREACHABLE_CODE")
  job?.cancel();val initial=mutable.value
  job=viewModelScope.launch {
   mutable.update { it.copy(loading=true,error=null) }
   try {
    val result=repo.events(initial.sport,initial.date,force)
    mutable.update { if(it.sport==initial.sport && it.date==initial.date) it.copy(events=result.events,loading=false,stale=result.stale,updated=result.updated) else it }
   } catch(e: Exception) { if(e is CancellationException) throw e;mutable.update { it.copy(loading=false,error=e.message,stale=it.events.isNotEmpty()) } }
  }
 }
 fun select(event: SportEvent) = selectInternal(event,false)
 fun reloadDetail() { mutable.value.detail?.let { selectInternal(it,true) } }
 private fun selectInternal(event: SportEvent,force: Boolean) {
  detailJob?.cancel();mutable.update { it.copy(detail=event,error=null,detailLoading=true) }
  detailJob=viewModelScope.launch { try {
   val result=repo.detail(event,force)
   mutable.update { if(it.detail?.id==event.id && it.sport==event.sport) it.copy(detailLoading=false,detail=result.events.firstOrNull { row->row.id==event.id } ?: event,stale=result.stale,updated=result.updated) else it }
  } catch(e: Exception) { if(e is CancellationException) throw e;mutable.update { it.copy(error=e.message,detailLoading=false) } } }
 }
}
