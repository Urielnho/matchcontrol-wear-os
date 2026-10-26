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

fun TransformingLazyColumnScope.sandboxContent(snapshot: Snapshot,sync: SyncState,hasReplica: Boolean,refresh: ()->Unit) {
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
  Card(onClick={},contentPadding=PaddingValues(12.dp),colors=CardDefaults.cardColors(containerColor=MatchColors.Surface)) {
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Text(when(bet.status) {BetStatus.PENDING->"Pendiente";BetStatus.WON->"Ganada";BetStatus.LOST->"Perdida";BetStatus.VOID->"Anulada"},style=MatchType.Title,color=if(bet.status==BetStatus.WON) MatchColors.Accent else MatchColors.Text)
    Text("${credits(bet.stake)} créditos · ${bet.picks.size} evento(s)",style=MatchType.Caption,color=MatchColors.Secondary)
    Text(if(bet.status==BetStatus.PENDING) "Potencial ${credits(bet.potential)}" else "Retorno ${credits(bet.returned)}",style=MatchType.Label,color=MatchColors.Text)
   }
  }
 } }
}
