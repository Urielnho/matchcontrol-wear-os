package com.matchcontrol.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class Glyph { Ball, Live, Calendar, Results, Star, Settings, Refresh, Back, Offline, Error, Events, Stats, Bell, Check, Add, Swap, Basketball, Baseball, Flag }

/** Small original vector marks; no bitmap assets or phone Material components. */
@Composable fun SportGlyph(glyph: Glyph, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(20.dp)) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x*u, y*u)
        val stroke = Stroke(1.7f*u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, p(x,y), p(x2,y2), strokeWidth = 1.7f*u, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Basketball -> { drawCircle(color,9*u,p(12f,12f),style=stroke);line(3f,12f,21f,12f);line(12f,3f,12f,21f);drawArc(color,-75f,150f,false,p(0f,3f),Size(12*u,18*u),style=stroke);drawArc(color,105f,150f,false,p(12f,3f),Size(12*u,18*u),style=stroke) }
            Glyph.Baseball -> { drawCircle(color,9*u,p(12f,12f),style=stroke);drawArc(color,-65f,130f,false,p(-3f,4f),Size(14*u,16*u),style=stroke);drawArc(color,115f,130f,false,p(13f,4f),Size(14*u,16*u),style=stroke);line(5f,7f,8f,8f);line(5f,17f,8f,16f);line(16f,8f,19f,7f);line(16f,16f,19f,17f) }
            Glyph.Flag -> { line(4f,3f,4f,22f);drawRect(color,p(5f,3f),Size(15*u,12*u),style=stroke);repeat(5) { x->repeat(4) { y->if((x+y)%2==0) drawRect(color,p(5f+x*3f,3f+y*3f),Size(3*u,3*u)) } } }

            Glyph.Live -> { drawCircle(color, 3*u, p(12f,12f)); drawArc(color, -55f,110f,false,p(3f,3f),Size(18*u,18*u),style=stroke); drawArc(color,125f,110f,false,p(3f,3f),Size(18*u,18*u),style=stroke) }
            Glyph.Calendar -> { drawRoundRect(color,p(3f,5f),Size(18*u,16*u),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3*u),style=stroke); line(3f,10f,21f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f);drawCircle(color,1.3f*u,p(8f,15f));drawCircle(color,1.3f*u,p(15f,15f)) }
            Glyph.Results, Glyph.Stats -> { line(4f,20f,21f,20f);line(6f,15f,6f,19f);line(12f,9f,12f,19f);line(18f,4f,18f,19f) }
            Glyph.Star -> { val path=Path();repeat(10) { i -> val a=(i*PI/5-PI/2);val r=if(i%2==0) 9f else 4.5f;val x=(12+cos(a)*r).toFloat()*u;val y=(12+sin(a)*r).toFloat()*u;if(i==0)path.moveTo(x,y)else path.lineTo(x,y) };path.close();drawPath(path,color,style=stroke) }
            Glyph.Settings -> { drawCircle(color,6*u,p(12f,12f),style=stroke);drawCircle(color,2*u,p(12f,12f),style=stroke);repeat(8) { i -> val a=i*PI/4;line((12+cos(a)*8).toFloat(),(12+sin(a)*8).toFloat(),(12+cos(a)*10).toFloat(),(12+sin(a)*10).toFloat()) } }
            Glyph.Refresh -> { drawArc(color,35f,290f,false,p(4f,4f),Size(16*u,16*u),style=stroke);line(21f,7f,19f,3f);line(19f,3f,15f,5f) }
            Glyph.Back -> { line(14f,5f,7f,12f);line(7f,12f,14f,19f) }
            Glyph.Error -> { val path=Path().apply { moveTo(12*u,3*u);lineTo(22*u,21*u);lineTo(2*u,21*u);close() };drawPath(path,color,style=stroke);line(12f,9f,12f,14f);drawCircle(color,0.9f*u,p(12f,18f)) }
            Glyph.Offline -> { drawArc(color,215f,110f,false,p(2f,3f),Size(20*u,20*u),style=stroke);drawArc(color,220f,100f,false,p(6f,8f),Size(12*u,12*u),style=stroke);drawCircle(color,1.3f*u,p(12f,19f));line(3f,3f,21f,21f) }
            Glyph.Events -> { line(6f,4f,6f,20f);repeat(3) { i -> drawCircle(color,2*u,p(6f,5f+i*7f));line(12f,5f+i*7f,21f,5f+i*7f) } }
            Glyph.Bell -> { val path=Path().apply { moveTo(5*u,17*u);lineTo(7*u,14*u);lineTo(7*u,9*u);cubicTo(7*u,2*u,17*u,2*u,17*u,9*u);lineTo(17*u,14*u);lineTo(19*u,17*u);close() };drawPath(path,color,style=stroke);drawArc(color,0f,180f,false,p(10f,18f),Size(4*u,3*u),style=stroke) }
            Glyph.Check -> { line(5f,12f,10f,17f);line(10f,17f,20f,6f) }
            Glyph.Add -> { line(5f,12f,19f,12f);line(12f,5f,12f,19f) }
            Glyph.Swap -> { line(4f,7f,19f,7f);line(19f,7f,15f,3f);line(5f,17f,20f,17f);line(5f,17f,9f,21f) }
            Glyph.Ball -> { drawCircle(color,9*u,p(12f,12f),style=stroke);val path=Path();repeat(5) { i->val a=i*2*PI/5-PI/2;val x=(12+cos(a)*4).toFloat()*u;val y=(12+sin(a)*4).toFloat()*u;if(i==0)path.moveTo(x,y)else path.lineTo(x,y) };path.close();drawPath(path,color,style=stroke);line(12f,8f,12f,3f);line(8f,13f,4f,15f);line(16f,13f,20f,15f);line(10f,16f,8f,20f);line(14f,16f,16f,20f) }
        }
    }
}
