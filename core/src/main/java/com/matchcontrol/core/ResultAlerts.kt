package com.matchcontrol.core

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object ResultAlerts {
 fun changed(context: Context,old: Snapshot,next: Snapshot) {
  if(!next.notifications || (Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) return
  val manager=context.getSystemService(NotificationManager::class.java)
  if(!manager.areNotificationsEnabled()) return
  manager.createNotificationChannel(NotificationChannel("sandbox-results","Resultados de apuestas",NotificationManager.IMPORTANCE_DEFAULT))
  val seen=context.getSharedPreferences("sandbox-alerts",Context.MODE_PRIVATE)
  next.bets.filter {b->b.status!=BetStatus.PENDING && old.bets.any {it.id==b.id && it.status==BetStatus.PENDING} && !seen.contains(b.id)}.forEach {b->
   val launch=context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return@forEach
   val intent=PendingIntent.getActivity(context,b.id.hashCode(),launch,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   manager.notify(b.id.hashCode(),Notification.Builder(context,"sandbox-results").setSmallIcon(R.drawable.ic_sandbox_alert)
    .setContentTitle("Boleto · "+when(b.status) {BetStatus.WON->"ganado";BetStatus.LOST->"perdido";BetStatus.VOID->"anulado";else->"pendiente"})
    .setContentText("Retorno: ${credits(b.returned)} créditos virtuales").setContentIntent(intent).setAutoCancel(true).build())
   seen.edit().putBoolean(b.id,true).apply()
  }
 }
}
