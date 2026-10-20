package com.matchcontrol
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchcontrol.presentation.*
import com.matchcontrol.domain.Match
import kotlinx.serialization.json.Json
class MainActivity: ComponentActivity() {
 private val vm: MatchViewModel by viewModels()
 private var notificationMatch by mutableStateOf<Match?>(null)
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setNotifications(granted) }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  readNotification(intent)
  setContent { MatchApp(vm,notificationMatch) {
   if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
   else vm.setNotifications(NotificationManagerCompat.from(this).areNotificationsEnabled())
  } }
 }
 override fun onResume() {
  super.onResume()
  if(!NotificationManagerCompat.from(this).areNotificationsEnabled()) vm.setNotifications(false)
 }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);readNotification(intent) }
 private fun readNotification(intent: Intent) {
  notificationMatch=intent.getStringExtra("match")?.let { runCatching { Json.decodeFromString<Match>(it) }.getOrNull() }
 }
}
