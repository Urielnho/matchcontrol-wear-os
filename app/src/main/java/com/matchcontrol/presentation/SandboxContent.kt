package com.matchcontrol.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.*
import com.matchcontrol.core.*
import com.matchcontrol.core.sync.SyncState
import com.matchcontrol.presentation.components.*
import com.matchcontrol.presentation.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

fun TransformingLazyColumnScope.sandboxContent(snapshot: Snapshot,sync: SyncState,hasReplica: Boolean,refresh: ()->Unit,onBet: (Bet)->Unit) {
 if(hasReplica) item {Text(if(snapshot.accountSignedIn) "Cuenta de ${snapshot.profile.displayName}" else "DEMO del móvil",style=MatchType.Caption,color=MatchColors.Secondary)}
 item {Text("CRÉDITOS DEMO",style=MatchType.Label,color=MatchColors.Accent)}
 if(hasReplica) item {Text(credits(snapshot.balance),style=MatchType.Score,color=MatchColors.Text)}
 else item {StatePanel(Glyph.Refresh,"Vincula tu móvil","El saldo y los boletos aparecerán al recibir el primer resumen.")}
 item {Text("Simulación · sin dinero real",style=MatchType.Caption,color=MatchColors.Secondary)}
 item {SportAction("Sincronizar",Glyph.Refresh,onClick=refresh)}
 item {StatusNotice(if(sync.connected) Glyph.Check else Glyph.Offline,if(sync.connected) "Móvil conectado" else "Sin vínculo",if(sync.lastSync>0) "Última sincronización: "+Instant.ofEpochMilli(sync.lastSync).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) else "Aún sin sincronizar",MatchColors.Secondary)}
 if(sync.pending>0) item {Text("${sync.pending} acciones pendientes",style=MatchType.Caption,color=MatchColors.Warning)}
 item {SectionTitle("Tus boletos",Glyph.Star)}
 if(hasReplica && snapshot.bets.isEmpty()) item {Text("Crea tu primer boleto en el móvil.",style=MatchType.Body,color=MatchColors.Secondary)}
 if(hasReplica) snapshot.bets.asReversed().forEach {bet->item {
  Card(onClick={onBet(bet)},contentPadding=PaddingValues(12.dp),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface)) {
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Text(when(bet.status) {BetStatus.PENDING->"Pendiente";BetStatus.WON->"Ganada";BetStatus.LOST->"Perdida";BetStatus.VOID->"Anulada"},style=MatchType.Title,color=if(bet.status==BetStatus.WON) MatchColors.Accent else MatchColors.Text)
    Text(if(bet.picks.size>1) "Parlay · ${bet.picks.size} partidos" else "Apuesta simple",style=MatchType.Label,color=MatchColors.Text)
    Text("${credits(bet.stake)} créditos · cuota ${bet.totalOdds.toBigDecimal().setScale(2,java.math.RoundingMode.DOWN)}",style=MatchType.Caption,color=MatchColors.Secondary)
    Text(if(bet.status==BetStatus.PENDING) "Potencial ${credits(bet.potential)}" else "Retorno ${credits(bet.returned)}",style=MatchType.Label,color=MatchColors.Text)
    Text("Ver selecciones",style=MatchType.Caption,color=MatchColors.Accent)
   }
  }
 } }
 if(snapshot.totalBetCount>snapshot.bets.size) item {Text("${snapshot.bets.size} de ${snapshot.totalBetCount} boletos. Historial completo en el móvil.",style=MatchType.Caption,color=MatchColors.Secondary)}
}

private fun betStatus(status: BetStatus)=when(status) {BetStatus.PENDING->"Pendiente";BetStatus.WON->"Ganada";BetStatus.LOST->"Perdida";BetStatus.VOID->"Anulada"}
private fun marketLabel(m: MarketType)=when(m) {MarketType.WINNER->"Ganador · 1X2";MarketType.DOUBLE_CHANCE->"Doble oportunidad";MarketType.OVER_UNDER_15->"Goles 1.5";MarketType.OVER_UNDER_25->"Goles 2.5";MarketType.BOTH_SCORE->"Ambos anotan"}

fun TransformingLazyColumnScope.betDetailContent(bet: Bet?,snapshot: Snapshot) {
 if(bet==null) {item {StatePanel(Glyph.Star,"Boleto no disponible","Consulta los boletos de tu cuenta en el móvil.")};return}
 item {Text(if(bet.picks.size>1) "Parlay · ${bet.picks.size} partidos" else "Apuesta simple",style=MatchType.Title,color=MatchColors.Text)}
 item {Text(betStatus(bet.status),style=MatchType.Label,color=if(bet.status==BetStatus.WON) MatchColors.Accent else MatchColors.Secondary)}
 item {Text("${credits(bet.stake)} créditos",style=MatchType.Title,color=MatchColors.Text);Text("Cuota total ${bet.totalOdds.toBigDecimal().setScale(2,java.math.RoundingMode.DOWN)}",style=MatchType.Caption,color=MatchColors.Secondary)}
 bet.picks.forEachIndexed {index,pick->item(key="pick-${bet.id}-${pick.outcomeId}") {
  val fixture=snapshot.fixtures.firstOrNull {it.id==pick.matchId}
  Card(onClick={},contentPadding=PaddingValues(12.dp),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface)) {
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Text("${index+1}. "+(fixture?.let {"${it.home.name} · ${it.away.name}"} ?: pick.fixtureLabel),style=MatchType.Body,color=MatchColors.Text)
    Text(marketLabel(pick.market),style=MatchType.Caption,color=MatchColors.Secondary)
    Text(pick.label+" · "+java.math.BigDecimal.valueOf(pick.lockedOdds.toLong(),4).setScale(2,java.math.RoundingMode.DOWN),style=MatchType.Label,color=MatchColors.Accent)
    Text(betStatus(pick.status),style=MatchType.Caption,color=MatchColors.Secondary)
    if(fixture?.homeScore!=null && fixture.awayScore!=null) Text("Marcador ${fixture.homeScore} : ${fixture.awayScore}",style=MatchType.Caption,color=MatchColors.Text)
   }
  }
 } }
 item {Text(if(bet.status==BetStatus.PENDING) "Retorno potencial ${credits(bet.potential)}" else "Retorno ${credits(bet.returned)}",style=MatchType.Title,color=MatchColors.Accent)}
 item {Text(Instant.ofEpochMilli(bet.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")),style=MatchType.Caption,color=MatchColors.Secondary);Text("Créditos virtuales · sin dinero real",style=MatchType.Caption,color=MatchColors.Secondary)}
}

fun TransformingLazyColumnScope.sharedFavoritesContent(snapshot: Snapshot,toggle: (String,String,Boolean)->Unit,select: (Fixture)->Unit) {
 val teams=(snapshot.favoriteTeamDetails+snapshot.fixtures.flatMap {listOf(it.home,it.away)}).distinctBy {it.id}.filter {it.id in snapshot.favoriteTeams}
 val leagues=(snapshot.favoriteLeagueDetails+snapshot.fixtures.map {FavoriteLeague(it.leagueId,it.league)}).distinctBy {it.id}.filter {it.id in snapshot.favoriteLeagues}
 if(snapshot.favoriteTeams.isEmpty() && snapshot.favoriteLeagues.isEmpty() && snapshot.favoriteMatches.isEmpty()) item {StatePanel(Glyph.Star,"Sin favoritos","Los equipos y partidos que guardes en el móvil aparecerán aquí.")}
 if(teams.isNotEmpty()) item {SectionTitle("Tus equipos",Glyph.Star)}
 teams.forEach {team->item(key="favorite-team-${team.id}") {SportAction(team.name,Glyph.Star,true) {toggle("team",team.id,false)}}}
 if(leagues.isNotEmpty()) item {SectionTitle("Tus ligas",Glyph.Ball)}
 leagues.forEach {league->item(key="favorite-league-${league.id}") {SportAction(league.name,Glyph.Ball,true) {toggle("league",league.id,false)}}}
 val matches=snapshot.fixtures.filter {it.id in snapshot.favoriteMatches || it.home.id in snapshot.favoriteTeams || it.away.id in snapshot.favoriteTeams || it.leagueId in snapshot.favoriteLeagues}.sortedByDescending {it.startsAt}.take(12)
 if(matches.isNotEmpty()) item {SectionTitle("Tus partidos",Glyph.Calendar)}
 matches.forEach {fixture->item(key="favorite-match-${fixture.id}") {SportAction("${fixture.home.code} · ${fixture.away.code}",Glyph.Ball) {select(fixture)};Text(Instant.ofEpochMilli(fixture.startsAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")),style=MatchType.Caption,color=MatchColors.Secondary)}}
}
