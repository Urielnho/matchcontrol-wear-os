package com.matchcontrol.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.matchcontrol.domain.*
import com.matchcontrol.presentation.components.*
import com.matchcontrol.presentation.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

private fun eventTime(event: SportEvent,pattern: String="dd MMM · HH:mm")=runCatching { if(event.kickoff.length==10) return@runCatching LocalDate.parse(event.kickoff).format(DateTimeFormatter.ofPattern("dd MMM"))+" · horario por confirmar";OffsetDateTime.parse(event.kickoff).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern)) }.getOrDefault("Horario por confirmar")
private fun stateTitle(event: SportEvent)=when(event.state) { EventState.LIVE->event.stage ?: "En juego";EventState.FINAL->"Final";EventState.SCHEDULED->event.stage ?: "Programado";EventState.POSTPONED->"Aplazado";EventState.CANCELLED->"Cancelado";EventState.PENDING->"Resultado pendiente" }
@Composable private fun EventStatus(event: SportEvent) {
 Text(stateTitle(event),style=MatchType.Label,color=when(event.state) { EventState.LIVE->MatchColors.Live;EventState.POSTPONED,EventState.PENDING->MatchColors.Warning;else->MatchColors.Secondary },textAlign=TextAlign.Center)
}
fun TransformingLazyColumnScope.sportsPicker(choose: (Sport)->Unit) {
 item { Text("Elige tu deporte",style=MatchType.Body,color=MatchColors.Secondary) }
 listOf(Sport.FOOTBALL,Sport.FORMULA1,Sport.BASKETBALL,Sport.BASEBALL).forEach { sport -> item { SportAction(sport.title,when(sport) { Sport.FORMULA1->Glyph.Flag;Sport.BASKETBALL->Glyph.Basketball;Sport.BASEBALL->Glyph.Baseball;else->Glyph.Ball },onClick={choose(sport)}) } }
}
@Composable private fun TeamLine(team: Team) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  TeamCrest(team,24.dp);Text(team.name,style=MatchType.Body,color=MatchColors.Text,modifier=Modifier.weight(1f))
 }
}
@Composable private fun GameScore(event: SportEvent,compact: Boolean=false) {
 Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(compact) 4.dp else 7.dp)) {
  EventStatus(event)
  if(compact) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   listOfNotNull(event.home,event.away).forEach { team -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
    TeamCrest(team,22.dp);Text(teamCode(team.name),style=MatchType.Label,color=MatchColors.Text)
   } }
  } else event.home?.let { TeamLine(it) }
  if(event.state==EventState.SCHEDULED) Text(eventTime(event,"HH:mm"),style=MatchType.Score,color=MatchColors.Text)
  else {
   val score="${event.homeScore ?: "—"} – ${event.awayScore ?: "—"}"
   Text(score,style=MatchType.Score.copy(fontSize=if(score.length>7) 26.sp else 30.sp),color=MatchColors.Text,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
   if(!compact) Text(if(event.sport==Sport.BASKETBALL) "Puntos" else "Carreras",style=MatchType.Caption,color=MatchColors.Secondary)
  }
  if(!compact) event.away?.let { TeamLine(it) }
  Text(event.competition,style=MatchType.Caption,color=MatchColors.Secondary,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
 }
}
@Composable private fun TransformingLazyColumnItemScope.EventCard(event: SportEvent,select: ()->Unit) {
 val spec=rememberTransformationSpec()
 Card(onClick=select,modifier=Modifier.fillMaxWidth().transformedHeight(this,spec).semantics { contentDescription=event.title },transformation=SurfaceTransformation(spec),contentPadding=PaddingValues(10.dp),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface)) {
  if(event.sport!=Sport.FORMULA1) GameScore(event,compact=true)
  else Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
   if(event.state==EventState.PENDING) Text("Ver resultados",style=MatchType.Label,color=MatchColors.Secondary) else EventStatus(event)
   Text(event.title,style=MatchType.Title,color=MatchColors.Text)
   event.circuit?.let { Text(it,style=MatchType.Caption,color=MatchColors.Secondary) }
   event.drivers.take(3).forEach { driver -> Text("${driver.position ?: "—"}. ${driver.name}",style=MatchType.Body,color=if(driver.position==1) MatchColors.Accent else MatchColors.Text) }
   Text(eventTime(event),style=MatchType.Caption,color=MatchColors.Secondary)
  }
 }
}
fun TransformingLazyColumnScope.sportsFeed(state: SportsState,day: (Long)->Unit,refresh: ()->Unit,select: (SportEvent)->Unit) {
 if(state.sport==Sport.FORMULA1) {
  item { SectionTitle("Temporada ${state.date.year}",Glyph.Calendar) }
  val next=state.events.filter { it.state==EventState.SCHEDULED }.minByOrNull { it.kickoff }
  val latest=state.events.filter { it.state==EventState.FINAL }.maxByOrNull { it.kickoff }
  if(next!=null) { item { SectionTitle("Próxima carrera",Glyph.Flag,MatchColors.Accent) };item { EventCard(next) {select(next)} };item { SportAction("Actualizar resultados",Glyph.Refresh,onClick=refresh) } }
  if(latest!=null) { item { SectionTitle("Último resultado",Glyph.Flag) };item { EventCard(latest) {select(latest)} };if(next==null) item { SportAction("Actualizar resultados",Glyph.Refresh,onClick=refresh) } }
  if(next==null && latest==null) item { SportAction("Actualizar resultados",Glyph.Refresh,onClick=refresh) }
  item { SectionTitle("Calendario",Glyph.Calendar) }
  state.events.filter { it.id!=next?.id && it.id!=latest?.id }.sortedByDescending { it.kickoff }.forEach { event -> item(key=event.id) { EventCard(event) {select(event)} } }
 } else {
  val order=listOf(EventState.LIVE,EventState.SCHEDULED,EventState.FINAL,EventState.POSTPONED,EventState.PENDING,EventState.CANCELLED)
  val events=order.flatMap { kind -> state.events.filter { it.state==kind }.let { if(kind==EventState.FINAL) it.sortedByDescending { e->e.kickoff } else it.sortedBy { e->e.kickoff } } }
  events.forEachIndexed { index,event ->
   if(index>0 && events[index-1].state!=event.state) item { SectionTitle(when(event.state) { EventState.LIVE->"En juego";EventState.FINAL->"Resultados";EventState.SCHEDULED->"Próximos";EventState.POSTPONED->"Aplazados";EventState.CANCELLED->"Cancelados";else->"Por confirmar" },Glyph.Calendar) }
   item(key=event.id) { EventCard(event) {select(event)} }
   if(index==0) { item { DateControl(state.date,day) };item { SportAction("Actualizar resultados",Glyph.Refresh,onClick=refresh) } }
  }
  if(events.isEmpty()) { item { DateControl(state.date,day) };item { SportAction("Actualizar resultados",Glyph.Refresh,onClick=refresh) } }

 }
 if(state.updated>0) item { Text("Actualizado "+Instant.ofEpochMilli(state.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),style=MatchType.Caption,color=MatchColors.Secondary) }
 if(state.events.isEmpty() && !state.loading && state.error==null) item { StatePanel(Glyph.Calendar,"Sin encuentros","No hay resultados disponibles para esta fecha.") }
}
@Composable private fun PeriodRow(period: PeriodScore) {
 Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
  Text(period.label,style=MatchType.Body,color=MatchColors.Secondary,modifier=Modifier.weight(1f))
  Text("${period.home ?: "—"} – ${period.away ?: "—"}",style=MatchType.Label,color=MatchColors.Text)
 }
}
fun TransformingLazyColumnScope.sportsDetail(state: SportsState) {
 val event=state.detail ?: return
 if(event.sport==Sport.FORMULA1) {
  item { EventStatus(event) }
  item { Text(event.title,style=MatchType.Title,color=MatchColors.Text,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()) }
  event.circuit?.let { item { Text(it,style=MatchType.Body,color=MatchColors.Secondary,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()) } }
  item { Text(eventTime(event),style=MatchType.Label,color=MatchColors.Secondary) }
  item { SectionTitle("Clasificación",Glyph.Results) }
  if(event.drivers.isEmpty()) item {
   when {
    state.detailLoading -> StatePanel(Glyph.Results,"Consultando clasificación","Buscando los resultados de esta carrera.")
    state.error!=null -> StatePanel(Glyph.Results,"Clasificación no disponible","Inténtalo de nuevo más tarde.")
    else -> StatePanel(Glyph.Results,"Sin clasificación publicada","Los resultados aparecerán cuando estén disponibles.")
   }
  }
  event.drivers.forEach { driver -> item {
   Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
    Text("${driver.position ?: "—"}. ${driver.name}",style=MatchType.Title,color=if(driver.position==1) MatchColors.Accent else MatchColors.Text)
    Text(driver.team,style=MatchType.Body,color=MatchColors.Secondary)
    driver.time?.let { Text(it,style=MatchType.Label,color=MatchColors.Text) }
    Text(listOfNotNull(driver.points?.let { "$it puntos" },driver.laps?.let { "$it vueltas" }).joinToString(" · "),style=MatchType.Caption,color=MatchColors.Secondary)
    if(driver.time==null) driver.status?.let { Text(it,style=MatchType.Caption,color=MatchColors.Secondary) }
   }
  } }
 } else {
  item { GameScore(event) }
  item { Text(eventTime(event),style=MatchType.Caption,color=MatchColors.Secondary) }
  item { SectionTitle(if(event.sport==Sport.BASKETBALL) "Por cuartos" else "Por entradas",Glyph.Stats) }
  if(event.breakdownInconsistent) item { StatusNotice(Glyph.Error,"Desglose por confirmar","Los periodos disponibles no coinciden con el marcador.") }
  item { Text("${event.home?.name ?: ""} · ${event.away?.name ?: ""}",style=MatchType.Caption,color=MatchColors.Secondary) }
  if(event.periods.isEmpty()) item { StatePanel(Glyph.Stats,"Sin desglose publicado","Se mostrará cuando esté disponible.") }
  event.periods.forEach { period -> item { PeriodRow(period) } }
  if(event.statistics.isNotEmpty()) item { SectionTitle("Estadísticas",Glyph.Stats) }
  event.statistics.forEach { period -> item { PeriodRow(period) } }
 }
}
