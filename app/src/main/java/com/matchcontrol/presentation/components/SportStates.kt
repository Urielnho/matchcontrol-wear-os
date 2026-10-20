package com.matchcontrol.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.matchcontrol.presentation.theme.*

@Composable fun StatePanel(glyph: Glyph, title: String, body: String, tint: Color = MatchColors.Secondary) {
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp), horizontalAlignment=Alignment.CenterHorizontally,
  verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Box(Modifier.size(36.dp).background(MatchColors.Surface,CircleShape),contentAlignment=Alignment.Center) { SportGlyph(glyph,Modifier.size(23.dp),tint) }
  Text(title,style=MatchType.Title,color=MatchColors.Text,textAlign=TextAlign.Center)
  Text(body,style=MatchType.Body,color=MatchColors.Secondary,textAlign=TextAlign.Center)
 }
}
@Composable fun StatusNotice(glyph: Glyph, title: String, body: String, tint: Color = MatchColors.Warning) {
 Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MatchSpace.Small)) {
  SportGlyph(glyph,Modifier.size(18.dp),tint)
  Column(Modifier.weight(1f)) {
   Text(title,style=MatchType.Label,color=tint)
   Text(body,style=MatchType.Caption,color=MatchColors.Secondary)
  }
 }
}
@Composable fun SectionTitle(title: String, glyph: Glyph? = null, color: Color = MatchColors.Secondary) {
 Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
  if(glyph!=null) SportGlyph(glyph,Modifier.size(17.dp),color)
  Text(title,style=MatchType.Label,color=color)
 }
}
