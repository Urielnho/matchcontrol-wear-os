package com.matchcontrol.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.*
import com.matchcontrol.core.*
import com.matchcontrol.presentation.components.*
import com.matchcontrol.presentation.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

private fun fixtureTime(f: Fixture)=Instant.ofEpochMilli(f.startsAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm"))
fun TransformingLazyColumnScope.sharedSportContent(snapshot: Snapshot,sport: SportKind,select: (Fixture)->Unit) {
 val fixtures=snapshot.fixtures.filter {it.sport==sport && it.source==RealData.Source}.sortedWith(compareBy<Fixture> {it.status!=FixtureStatus.SCHEDULED}.thenBy {if(it.startsAt>System.currentTimeMillis()) it.startsAt else Long.MAX_VALUE-it.startsAt})
 if(fixtures.isEmpty()) item {StatePanel(Glyph.Ball,"Sin eventos disponibles","Abre este deporte en el móvil para consultar resultados.")}
 fixtures.take(12).forEach {f->item(key="shared-${f.id}") {
  Card(onClick={select(f)},contentPadding=PaddingValues(12.dp),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface)) {
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Text(f.league,style=MatchType.Caption,color=MatchColors.Secondary)
    Text(eventLabel(f),style=MatchType.Title,color=MatchColors.Text)
    if(sport!=SportKind.FORMULA1 && f.homeScore!=null && f.awayScore!=null) Text("${f.homeScore} : ${f.awayScore}",style=MatchType.Score,color=MatchColors.Accent)
    Text(fixtureTime(f),style=MatchType.Caption,color=MatchColors.Secondary)
    Text(when(f.status) {FixtureStatus.SCHEDULED->"Programado";FixtureStatus.LIVE->"En vivo";FixtureStatus.FINAL->"Final";FixtureStatus.VOID->"Anulado";FixtureStatus.SUSPENDED->"Por confirmar"},style=MatchType.Label,color=MatchColors.Accent)
   }
  }
 } }
}
fun TransformingLazyColumnScope.sharedFixtureContent(f: Fixture?,toggle: (String,String,Boolean)->Unit,snapshot: Snapshot,send: (Fixture)->Unit) {
 if(f==null || f.source!=RealData.Source) {item {StatePanel(Glyph.Ball,"Evento no disponible","Actualiza desde el móvil.")};return}
 item {Text(f.home.name,style=MatchType.Title,color=MatchColors.Text);Text(f.away.name,style=MatchType.Body,color=MatchColors.Secondary);Text(fixtureTime(f),style=MatchType.Caption,color=MatchColors.Secondary)}
 if(f.sport!=SportKind.FORMULA1) {
  item {Text("${f.homeScore ?: "—"} : ${f.awayScore ?: "—"}",style=MatchType.Score,color=MatchColors.Accent);Text(if(f.sport==SportKind.BASKETBALL) "Puntos" else "Carreras",style=MatchType.Caption,color=MatchColors.Secondary)}
  listOf(f.home,f.away).forEach {t->item {SportAction(t.name,Glyph.Star,t.id in snapshot.favoriteTeams) {toggle("team",t.id,t.id !in snapshot.favoriteTeams)}}}
 } else {
  if(f.raceResults.isEmpty()) item {Text("Clasificación pendiente",style=MatchType.Body,color=MatchColors.Secondary)}
  f.raceResults.sortedBy {it.position ?: Int.MAX_VALUE}.forEach {r->item {Text((r.position?.toString() ?: "NC")+" · "+(f.participants.firstOrNull {it.id==r.driverId}?.name ?: r.driverId),style=MatchType.Body,color=MatchColors.Text)}}
  f.participants.forEach {d->item {SportAction(d.name,Glyph.Star,d.id in snapshot.favoriteTeams) {toggle("team",d.id,d.id !in snapshot.favoriteTeams)}}}
 }
 f.statistics.forEach {(label,value)->item {Text(label,style=MatchType.Caption,color=MatchColors.Secondary);Text(value,style=MatchType.Body,color=MatchColors.Text)}}
 item {SportAction(if(f.id in snapshot.favoriteMatches) "Quitar favorito" else "Guardar evento",Glyph.Star,f.id in snapshot.favoriteMatches) {toggle("match",f.id,f.id !in snapshot.favoriteMatches)}}
 item {SportAction("Enviar al móvil",Glyph.Refresh) {send(f)}}
 item {Text(sportRules(f.sport),style=MatchType.Caption,color=MatchColors.Secondary)}
}
