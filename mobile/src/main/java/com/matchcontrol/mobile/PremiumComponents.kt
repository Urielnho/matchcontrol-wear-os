package com.matchcontrol.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import com.matchcontrol.core.*
import coil.compose.AsyncImage

@Composable internal fun Heading(title: String,subtitle: String="") {
 Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
  Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
  if(subtitle.isNotBlank()) Text(subtitle,fontSize=11.sp,color=Muted)
 }
}
@Composable internal fun Empty(title: String,body: String) {
 Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Surface(shape=CircleShape,color=Raised) {Icon(Icons.Outlined.SportsSoccer,null,tint=Muted,modifier=Modifier.padding(14.dp).size(24.dp))}
  Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
  Text(body,color=Muted,fontSize=12.sp,textAlign=TextAlign.Center)
 }
}
@Composable internal fun TeamBadge(team: DemoTeam,large: Boolean=false) {
 var failed by remember(team.logo) {mutableStateOf(false)}
 Surface(color=Raised,shape=CircleShape,modifier=Modifier.size(if(large) 48.dp else 30.dp)) {
  Box(contentAlignment=Alignment.Center) {if(team.logo!=null && !failed) AsyncImage(model=team.logo,contentDescription=team.name,onError={failed=true},modifier=Modifier.fillMaxSize().padding(5.dp)) else Text(team.code,fontSize=if(large) 13.sp else 9.sp,color=Color.White,fontWeight=FontWeight.SemiBold)}
 }
}
@Composable internal fun Statistics(values: Map<String,String>) {
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {values.forEach { (label,value)->
  val sides=value.split('·');val h=sides.getOrNull(0)?.trim()?.removeSuffix("%")?.toFloatOrNull();val a=sides.getOrNull(1)?.trim()?.removeSuffix("%")?.toFloatOrNull()
  Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(label,fontSize=12.sp,color=Muted);Text(value,fontSize=12.sp)}
   if(h!=null && a!=null && h>=0 && a>=0 && (h+a).isFinite()) LinearProgressIndicator(progress={if(h+a==0f) .5f else h/(h+a)},modifier=Modifier.fillMaxWidth().height(4.dp),color=Lime,trackColor=Raised)
  }
 } }
}
@Composable internal fun FixtureCard(f: Fixture,hero: Boolean=false,onClick: ()->Unit) {
 val live=f.status==FixtureStatus.LIVE
 val state=when(f.status) {FixtureStatus.LIVE->"● EN VIVO";FixtureStatus.FINAL->"Final";FixtureStatus.VOID->"Anulado";FixtureStatus.SUSPENDED->"Suspendido";else->if(f.startsAt>System.currentTimeMillis()) time(f.startsAt,"HH:mm") else "Por confirmar"}
 Card(onClick=onClick,shape=shape,colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth().semantics {contentDescription="${f.home.name} contra ${f.away.name}"}) {
  Column(Modifier.padding(if(hero) 20.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(if(hero) 18.dp else 10.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(sportTitle(f.sport)+" · "+f.league,color=Muted,fontSize=11.sp,modifier=Modifier.weight(1f));Text(state,color=if(live) LiveRed else Muted,fontSize=11.sp,fontWeight=FontWeight.Medium)}
   if(f.sport==SportKind.FORMULA1) {
    Icon(Icons.Outlined.Flag,null,tint=Lime,modifier=Modifier.size(28.dp))
    Text(f.home.name,fontWeight=FontWeight.Bold,fontSize=if(hero) 24.sp else 18.sp)
    Text(f.away.name,color=Muted,fontSize=12.sp)
    if(f.raceResults.isNotEmpty()) f.raceResults.filter {it.classified && it.position!=null}.sortedBy {it.position}.take(3).forEach {r->Text("${r.position}. "+(f.participants.firstOrNull {it.id==r.driverId}?.name ?: r.driverId),fontSize=13.sp)}
   } else if(hero) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {TeamBadge(f.home,true);Text(f.home.name,fontSize=12.sp,textAlign=TextAlign.Center,maxLines=2)}
    Text(if(f.status==FixtureStatus.SCHEDULED) time(f.startsAt,"HH:mm") else "${f.homeScore ?: "—"} : ${f.awayScore ?: "—"}",fontSize=32.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=12.dp))
    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {TeamBadge(f.away,true);Text(f.away.name,fontSize=12.sp,textAlign=TextAlign.Center,maxLines=2)}
   } else listOf(f.home to f.homeScore,f.away to f.awayScore).forEach { (team,score)->
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {TeamBadge(team);Text(team.name,fontSize=14.sp,modifier=Modifier.weight(1f),maxLines=1);Text(if(f.status==FixtureStatus.SCHEDULED) "—" else score?.toString() ?: "—",fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
   }
   if(hero || f.sport==SportKind.FORMULA1) Text(time(f.startsAt)+(if(f.source=="DEMO") " · DEMO" else ""),fontSize=11.sp,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
  }
 }
}
@Composable internal fun SportPicker(selected: SportKind?,choose: (SportKind?)->Unit) {
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  FilterChip(selected=selected==null,onClick={choose(null)},label={Text("Todos")})
  SportKind.entries.forEach {sport->FilterChip(selected=selected==sport,onClick={choose(sport)},label={Text(sportTitle(sport))})}
 }
}
@Composable internal fun BetCard(b: Bet) {
 var expanded by rememberSaveable(b.id) {mutableStateOf(false)}
 Card(onClick={expanded=!expanded},shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(time(b.createdAt)+(if(b.source=="DEMO") " · DEMO" else ""),fontSize=11.sp,color=Muted);Text(status(b.status),fontSize=12.sp,color=if(b.status==BetStatus.WON) Lime else Muted,fontWeight=FontWeight.SemiBold)}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Column {Text("${credits(b.stake)} créditos",fontSize=19.sp,fontWeight=FontWeight.Bold);Text((if(b.picks.size==1) "1 selección" else "${b.picks.size} selecciones")+" · cuota ${totalOddsLabel(b.totalOdds)}",fontSize=12.sp,color=Muted)};Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Detalles del boleto",tint=Muted)}
   if(b.status!=BetStatus.PENDING) Text("Retorno ${credits(b.returned)}",color=if(b.returned>0) Lime else Muted,fontSize=13.sp)
   AnimatedVisibility(expanded) {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {HorizontalDivider(color=Raised);b.picks.forEach {Text("${it.fixtureLabel} · ${it.label} @ ${odds(it.lockedOdds)}",fontSize=12.sp)};Text("Potencial ${credits(b.potential)} · #${b.id.take(8)}",fontSize=11.sp,color=Muted)}}
  }
 }
}
@Composable internal fun AuthPanel(vm: MobileViewModel,signed: Boolean,name: String,loading: Boolean) {
 if(signed) {
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {Surface(shape=CircleShape,color=Raised) {Icon(Icons.Outlined.PersonOutline,null,modifier=Modifier.padding(16.dp),tint=Lime)};Text(name,fontSize=20.sp,fontWeight=FontWeight.SemiBold)}
  TextButton(onClick={vm.logout()},enabled=!loading) {Text("Cerrar sesión")}
 } else {
  var email by rememberSaveable {mutableStateOf("")};var password by remember {mutableStateOf("")};var create by rememberSaveable {mutableStateOf(false)}
  Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Heading(if(create) "Crear cuenta" else "Iniciar sesión")
   OutlinedTextField(email,{email=it},label={Text("Correo electrónico")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
   OutlinedTextField(password,{password=it},label={Text("Contraseña")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
   Button(onClick={vm.login(email,password,create);password=""},enabled=!loading && email.isNotBlank() && password.length>=6,modifier=Modifier.fillMaxWidth().height(48.dp)) {Text(if(create) "Crear cuenta" else "Entrar")}
   TextButton(onClick={create=!create}) {Text(if(create) "Ya tengo cuenta" else "Crear una cuenta")}
  }
 }
}
