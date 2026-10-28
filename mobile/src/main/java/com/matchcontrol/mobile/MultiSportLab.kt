package com.matchcontrol.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matchcontrol.core.*

@Composable internal fun MultiSportLab(s: Snapshot,vm: MobileViewModel) {
 var selected by remember {mutableStateOf<Fixture?>(null)}
 var h by remember {mutableStateOf("2")};var a by remember {mutableStateOf("1")}
 var order by remember {mutableStateOf<List<String>>(emptyList())}
 LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {Heading("Laboratorio DEMO");Text("Solo eventos ficticios. Define el resultado para comprobar tu boleto; no modifica resultados reales.",color=Muted)}
  item {OutlinedButton(onClick={vm.newRound()}) {Text("Crear nueva jornada DEMO")}}
  items(s.fixtures.filter {it.source=="DEMO" && it.status in listOf(FixtureStatus.SCHEDULED,FixtureStatus.LIVE)}) {f->
   Card(colors=CardDefaults.cardColors(containerColor=Surface)) {Column(Modifier.fillMaxWidth().padding(18.dp)) {
    Text(sportTitle(f.sport),color=Lime);Text(eventLabel(f))
    Row {
     TextButton(onClick={selected=f;order=f.participants.map {it.id};h=if(f.sport==SportKind.BASKETBALL) "112" else if(f.sport==SportKind.BASEBALL) "5" else "2";a=if(f.sport==SportKind.BASKETBALL) "105" else if(f.sport==SportKind.BASEBALL) "3" else "1"}) {Text("Finalizar DEMO")}
     TextButton(onClick={vm.cancel(f.id)}) {Text("Anular DEMO")}
    }
   }}
  }
 }
 selected?.let {f->AlertDialog(onDismissRequest={selected=null},title={Text(if(f.sport==SportKind.FORMULA1) "Clasificación ficticia" else "Resultado ficticio")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(eventLabel(f));Text(sportRules(f.sport),color=Muted)
  if(f.sport==SportKind.FORMULA1) order.forEachIndexed {index,id->Row(Modifier.fillMaxWidth()) {
   Text("${index+1}. "+f.participants.first {it.id==id}.name,Modifier.weight(1f))
   TextButton(enabled=index>0,onClick={val next=order.toMutableList();java.util.Collections.swap(next,index,index-1);order=next}) {Text("Subir")}
  }} else {
   OutlinedTextField(h,{h=it},label={Text(if(f.sport==SportKind.BASKETBALL) "Puntos local" else if(f.sport==SportKind.BASEBALL) "Carreras local" else "Goles local")})
   OutlinedTextField(a,{a=it},label={Text("Visitante")})
  }
 }},confirmButton={TextButton(onClick={
  if(f.sport==SportKind.FORMULA1) {vm.finishRace(f.id,order);selected=null}
  else {val home=h.toIntOrNull();val away=a.toIntOrNull();if(home!=null && away!=null) {vm.finish(f.id,home,away);selected=null} else vm.message.value="Marcador inválido"}
 }) {Text("Confirmar resultado DEMO")}},dismissButton={TextButton(onClick={selected=null}) {Text("Cancelar")}})}
}
