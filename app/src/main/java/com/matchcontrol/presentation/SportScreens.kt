package com.matchcontrol.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

@Composable fun TransformingLazyColumnItemScope.SportAction(label: String, glyph: Glyph, selected: Boolean = false, onClick: () -> Unit) {
 val spec=rememberTransformationSpec()
 Button(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=MatchSpace.Touch).transformedHeight(this,spec),
  colors=ButtonDefaults.buttonColors(containerColor=if(selected) MatchColors.Raised else MatchColors.Surface,contentColor=MatchColors.Text),
  contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),transformation=SurfaceTransformation(spec)) {
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
   SportGlyph(if(selected) Glyph.Check else glyph,Modifier.size(19.dp),if(selected) MatchColors.Accent else MatchColors.Secondary)
   Text(label,style=MatchType.Body,modifier=Modifier.weight(1f))
  }
 }
}
@Composable fun DateControl(date: LocalDate, day: (Long)->Unit) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
  IconButton(onClick={day(-1)},modifier=Modifier.size(MatchSpace.Touch).semantics { contentDescription="Día anterior" }) { Text("‹",style=MatchType.Score) }
  Text(date.format(DateTimeFormatter.ofPattern("dd MMM")),style=MatchType.Label,color=MatchColors.Secondary,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
  IconButton(onClick={day(1)},modifier=Modifier.size(MatchSpace.Touch).semantics { contentDescription="Día siguiente" }) { Text("›",style=MatchType.Score) }
 }
}
fun TransformingLazyColumnScope.sportListing(route: String, state: UiState, available: List<Match>, day: (Long)->Unit, select: (Match)->Unit) {
 val matches=available.filter { when(route) { "live"->it.live || it.status in setOf("SUSP","INT"); "upcoming"->it.upcoming; "results"->it.finished; else->isFavorite(it,state.settings.teams) } }.sortedBy { it.kickoff }
 if(route!="upcoming") item {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   if(route=="live") Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    val paused=matches.isNotEmpty() && matches.none { it.live }
    val tint=if(paused) MatchColors.Warning else MatchColors.Live
    SportGlyph(Glyph.Live,Modifier.size(16.dp),tint)
    Text(if(paused) "EN PAUSA" else "${matches.count { it.live }} en vivo",style=MatchType.Label,color=tint)
   }
   Text(state.date.format(DateTimeFormatter.ofPattern("dd MMM")),style=MatchType.Caption,color=MatchColors.Secondary)
  }
 }
 if(matches.isEmpty() && !state.loading) item {
  StatePanel(if(route=="live") Glyph.Live else Glyph.Calendar,
   if(route=="live") "Todo en pausa" else "Sin partidos",
   "Prueba otra fecha o revisa tus competiciones en Configuración.")
 }
 if(route=="upcoming") {
  matches.groupBy { localMatchTime(it,"HH:mm") }.forEach { (time,games) ->
   item { SectionTitle("${state.date.format(DateTimeFormatter.ofPattern("dd MMM"))} · $time",Glyph.Calendar,MatchColors.Accent) }
   games.forEach { m ->
    item(key="upcoming-${m.id}") { UpcomingFixture(m) { select(m) } }
    if(m.id==matches.first().id) item { DateControl(state.date,day) }
   }
  }
 } else matches.forEachIndexed { index,m ->
  item(key="match-${m.id}") { MatchTile(m,isFavorite(m,state.settings.teams)) { select(m) } }
  if(index==0) item { DateControl(state.date,day) }
 }
 if(matches.isEmpty()) item { DateControl(state.date,day) }
}
@Composable private fun TransformingLazyColumnItemScope.UpcomingFixture(match: Match, select: ()->Unit) {
 val spec=rememberTransformationSpec()
 Card(onClick=select,modifier=Modifier.fillMaxWidth().transformedHeight(this,spec),transformation=SurfaceTransformation(spec),
  colors=CardDefaults.cardColors(containerColor=MatchColors.Surface),shape=RoundedCornerShape(MatchSpace.CardRadius),contentPadding=PaddingValues(10.dp)) {
  Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
   listOf(match.home,match.away).forEach { team -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
    TeamCrest(team,20.dp); Text(team.name,style=MatchType.Body,color=MatchColors.Text,modifier=Modifier.weight(1f))
   } }
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)) {
    if(match.status=="PST") SportGlyph(Glyph.Calendar,Modifier.size(12.dp),MatchColors.Warning)
    Text(if(match.status=="PST") "Aplazado" else match.league,style=MatchType.Caption,color=if(match.status=="PST") MatchColors.Warning else MatchColors.Secondary)
   }
  }
 }
}
fun TransformingLazyColumnScope.favoriteContent(state: UiState, available: List<Match>, toggle: (Team)->Unit, navigate: (String)->Unit, day: (Long)->Unit, select: (Match)->Unit) {
 val teams=(state.knownTeams+state.matches.flatMap { listOf(it.home,it.away) }).distinctBy { it.id }
 val followed=teams.filter { it.id.toString() in state.settings.teams }
 if(followed.isEmpty()) {
  item { StatePanel(Glyph.Star,"Tu propia tribuna","Sigue un equipo desde un partido.",MatchColors.Accent) }
  item { SportAction("Explorar partidos",Glyph.Add,onClick={navigate("upcoming")}) }
 } else {
  item { SectionTitle("Tus equipos",Glyph.Star,MatchColors.Accent) }
  followed.forEach { team -> item(key="team-${team.id}") {
   val spec=rememberTransformationSpec()
   Card(onClick={toggle(team)},modifier=Modifier.fillMaxWidth().transformedHeight(this,spec).semantics { contentDescription="Dejar de seguir a ${team.name}" },
    transformation=SurfaceTransformation(spec),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface),contentPadding=PaddingValues(MatchSpace.Medium)) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
     TeamCrest(team,30.dp);Column(Modifier.weight(1f)) { Text(team.name,style=MatchType.Body,color=MatchColors.Text);Text("Toca para dejar de seguir",style=MatchType.Micro,color=MatchColors.Secondary) }
     SportGlyph(Glyph.Star,Modifier.size(17.dp),MatchColors.Accent)
    }
   }
  } }
  item { SectionTitle("Partidos de tus equipos",Glyph.Calendar) }
  sportListing("favorites",state,available,day,select)
  item { SportAction("Seguir otro equipo",Glyph.Add,onClick={navigate("upcoming")}) }
 }
}
@Composable private fun Scoreboard(match: Match) {
 val compact=LocalConfiguration.current.screenWidthDp<210 || (match.homeGoals ?: 0)>9 || (match.awayGoals ?: 0)>9
 Column(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(MatchSpace.Small)) {
  MatchStatus(match)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
   TeamCrest(match.home,if(compact) 24.dp else 32.dp)
   if(!match.upcoming || match.homeGoals!=null || match.awayGoals!=null) AnimatedScore("${match.homeGoals ?: "–"} : ${match.awayGoals ?: "–"}",detail=true,compact=compact)
   else Text(localMatchTime(match,"HH:mm"),style=MatchType.Score,color=MatchColors.Text)
   TeamCrest(match.away,if(compact) 24.dp else 32.dp)
  }
  Text("${match.home.name}\nvs ${match.away.name}",style=MatchType.Body,color=MatchColors.Text,textAlign=TextAlign.Center)
  Text(match.league,style=MatchType.Caption,color=MatchColors.Secondary,textAlign=TextAlign.Center)
  Text(localMatchTime(match),style=MatchType.Caption,color=MatchColors.Secondary)
 }
}
private fun eventLabel(event: MatchEvent): String = when {
 event.kind=="Goal" -> if(event.detail.contains("Missed",true)) "Penalti fallado" else if(event.detail.contains("Own",true)) "Autogol" else "Gol"
 event.kind=="Card" -> if(event.detail.contains("Red",true)) "Tarjeta roja" else if(event.detail.contains("Yellow",true)) "Tarjeta amarilla" else event.detail.ifBlank { "Tarjeta" }
 event.kind=="subst" -> "Sustitución"
 event.kind=="Var" -> "Revisión VAR"
 else -> event.detail.ifBlank { event.kind }
}
@Composable private fun EventRow(event: MatchEvent, match: Match, last: Boolean) {
 val color=when { event.kind=="Goal"->MatchColors.Accent;event.detail.contains("Red",true)->MatchColors.Live;event.kind=="Card"->MatchColors.Warning;else->MatchColors.Secondary }
 Row(Modifier.fillMaxWidth().heightIn(min=MatchSpace.Touch),horizontalArrangement=Arrangement.spacedBy(MatchSpace.Small)) {
  Text(event.minute?.let { "$it′" } ?: "—",style=MatchType.Label,color=MatchColors.Secondary,modifier=Modifier.width(28.dp).padding(top=3.dp))
  Canvas(Modifier.width(9.dp).height(if(last) 20.dp else 53.dp)) {
   if(!last) drawLine(MatchColors.Raised,Offset(size.width/2,8.dp.toPx()),Offset(size.width/2,size.height),2.dp.toPx())
   drawCircle(color,3.dp.toPx(),Offset(size.width/2,8.dp.toPx()))
  }
  Column(Modifier.weight(1f).padding(bottom=12.dp)) {
   Text(eventLabel(event),style=MatchType.Label,color=color)
   event.player?.let { Text(it,style=MatchType.Body,color=MatchColors.Text) }
   val team=when(event.teamId) { match.home.id->match.home.name;match.away.id->match.away.name;else->null }
   team?.let { Text(it,style=MatchType.Caption,color=MatchColors.Secondary) }
   event.assist?.let { Text("↔ $it",style=MatchType.Caption,color=MatchColors.Secondary) }
  }
 }
}
private fun statisticLabel(type: String) = when(type) { "Ball Possession"->"Posesión";"Total Shots"->"Tiros";"Shots on Goal"->"Tiros a puerta";"Corner Kicks"->"Córners";else->type }
@Composable private fun Comparison(stat: MatchStatistic) {
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Text(statisticLabel(stat.type),style=MatchType.Label,color=MatchColors.Secondary,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text(stat.home ?: "—",style=MatchType.Title,color=MatchColors.Text);Text(stat.away ?: "—",style=MatchType.Title,color=MatchColors.Text)
  }
  val fraction=stat.fraction()
  if(fraction!=null) Canvas(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) {
   drawRect(MatchColors.Raised)
   drawRect(MatchColors.Accent,size=androidx.compose.ui.geometry.Size(size.width*fraction,size.height))
   drawLine(MatchColors.Background,Offset(size.width*fraction,0f),Offset(size.width*fraction,size.height),3.dp.toPx())
  }
 }
}
fun TransformingLazyColumnScope.detailContent(state: UiState,toggle: (Team)->Unit) {
 val match=state.detail
 if(match==null) { item { StatePanel(Glyph.Ball,"Elige un partido","Abre un encuentro para ver su detalle.") };return }
 item { Scoreboard(match) }
 item { SectionTitle("Seguir equipos",Glyph.Star) }
 listOf(match.home,match.away).forEach { team -> item {
  val followed=team.id.toString() in state.settings.teams
  SportAction(if(followed) "Siguiendo a ${team.name}" else "Seguir a ${team.name}",Glyph.Star,followed) { toggle(team) }
 } }
 item { SectionTitle("Línea de tiempo",Glyph.Events) }
 if(match.timeline.isEmpty()) {
  if(match.events.isEmpty()) item { StatePanel(Glyph.Events,"Sin eventos todavía","Se mostrarán cuando estén disponibles.") }
  else match.events.forEach { item { StatusNotice(Glyph.Events,"Evento",it) } }
 } else match.timeline.forEachIndexed { i,event -> item(key="event-$i") { EventRow(event,match,i==match.timeline.lastIndex) } }
 item { SectionTitle("Estadísticas",Glyph.Stats) }
 if(match.comparisons.isEmpty()) {
  if(match.statistics.isEmpty()) item { StatePanel(Glyph.Stats,"Datos no disponibles","No hay estadísticas publicadas para este encuentro.") }
  else match.statistics.forEach { item { StatusNotice(Glyph.Stats,"Dato del partido",it,MatchColors.Secondary) } }
 } else {
  item { Text("${teamCode(match.home.name)}   ·   ${teamCode(match.away.name)}",style=MatchType.Caption,color=MatchColors.Secondary,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()) }
  val order=listOf("Ball Possession","Total Shots","Shots on Goal","Corner Kicks")
  match.comparisons.sortedBy { order.indexOf(it.type).let { i -> if(i<0) 99 else i } }.forEach { stat -> item { Comparison(stat) } }
 }

}
fun TransformingLazyColumnScope.settingsContent(state: UiState,vm: MatchViewModel,permission: ()->Unit) {
 item { SectionTitle("Experiencia",Glyph.Settings) }
 item { SportAction(if(state.settings.notifications) "Avisos activados" else "Activar avisos",Glyph.Bell,state.settings.notifications) { if(state.settings.notifications) vm.setNotifications(false) else permission() } }
 item { Text("Inicio, goles y final de tus equipos de fútbol al actualizar la app. Requiere permiso del reloj.",style=MatchType.Caption,color=MatchColors.Secondary) }
 if(state.settings.teams.isEmpty()) item { Text("Sigue un equipo desde un partido para recibir sus avisos.",style=MatchType.Caption,color=MatchColors.Warning) }
 item { Text("MatchControl 1.0.1",style=MatchType.Caption,color=MatchColors.Secondary) }
 item { SectionTitle("Conexión",Glyph.Stats) }
 item { StatusNotice(if(state.online) Glyph.Check else Glyph.Offline,if(state.online) "Conectado" else "Sin internet","Los datos guardados siguen disponibles.",if(state.online) MatchColors.Accent else MatchColors.Warning) }
 item { Text("Actualizado: "+if(state.updated==0L) "pendiente" else Instant.ofEpochMilli(state.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")),style=MatchType.Caption,color=MatchColors.Secondary) }
 item { SectionTitle("Competiciones",Glyph.Ball) }
 item { Text("Sin selección se muestran todas.",style=MatchType.Caption,color=MatchColors.Secondary) }
 state.competitions.forEach { c -> item { SportAction(c.name,Glyph.Ball,c.id.toString() in state.settings.leagues) { vm.toggleLeague(c) } } }
}
