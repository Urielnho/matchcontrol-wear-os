package com.matchcontrol.data
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.json.Json
import com.matchcontrol.MainActivity
import com.matchcontrol.R
import com.matchcontrol.domain.Match
import com.matchcontrol.domain.matchAlerts
class MatchNotifications(private val context: Context) {
 private val seen=context.getSharedPreferences("seen-events",Context.MODE_PRIVATE)
 private val manager=context.getSystemService(NotificationManager::class.java)
 init { manager.createNotificationChannel(NotificationChannel("matches", "Partidos favoritos", NotificationManager.IMPORTANCE_DEFAULT)) }
 fun enabledConfirmation() {
  if(!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
  val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  manager.notify(100,NotificationCompat.Builder(context,"matches").setSmallIcon(R.drawable.ic_notification)
   .setContentTitle("Avisos activados").setContentText("Recibirás novedades de tus equipos de fútbol al actualizar MatchControl.")
   .setContentIntent(intent).setAutoCancel(true).build())
 }
 fun observe(match: Match, favorites: Set<String>, notify: Boolean) {
  val key="snapshot-${match.id}"
  val old=seen.getString(key,null)?.let { runCatching { Json.decodeFromString<Match>(it) }.getOrNull() }
  if(notify) matchAlerts(old,match,favorites).forEach { post(match,it) }
  seen.edit().putString(key,Json.encodeToString(match)).apply()
 }
 fun post(match: Match, event: String): Boolean {
  if(!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
  if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
  val key="match-${match.id}-$event-${match.homeGoals}-${match.awayGoals}"
  if(seen.contains(key)) return false
  val intent=PendingIntent.getActivity(context,match.id,Intent(context,MainActivity::class.java).putExtra("match",Json.encodeToString(match)),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  manager.notify(key.hashCode(),NotificationCompat.Builder(context,"matches").setSmallIcon(R.drawable.ic_notification).setContentTitle(event).setContentText("${match.home.name} ${match.homeGoals ?: "—"} - ${match.awayGoals ?: "—"} ${match.away.name}").setContentIntent(intent).setAutoCancel(true).build())
  seen.edit().putBoolean(key,true).apply()
  return true
 }
}
