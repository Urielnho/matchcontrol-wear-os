package com.matchcontrol.data
import android.content.Context
import com.matchcontrol.domain.Competition
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import com.matchcontrol.domain.Team
private val Context.store by preferencesDataStore("matchcontrol")
data class Settings(val notifications: Boolean = false, val teams: Set<String> = emptySet(), val leagues: Set<String> = emptySet())
class SettingsStore(context: Context) {
 private val store = context.store
 val flow = store.data.map { Settings(it[booleanPreferencesKey("notifications")] ?: false, it[stringSetPreferencesKey("teams")] ?: emptySet(), it[stringSetPreferencesKey("leagues")] ?: emptySet()) }.distinctUntilChanged()
 val teamFlow = store.data.map { runCatching { Json.decodeFromString<List<Team>>(it[stringPreferencesKey("team_catalog")] ?: "[]") }.getOrDefault(emptyList()) }.distinctUntilChanged()
 suspend fun rememberTeams(teams: List<Team>) {
  store.edit { p ->
   val key=stringPreferencesKey("team_catalog")
   val old=runCatching { Json.decodeFromString<List<Team>>(p[key] ?: "[]") }.getOrDefault(emptyList())
   val favorites=p[stringSetPreferencesKey("teams")] ?: emptySet()
   val merged=(teams+old).distinctBy { it.id }
   // Retain followed teams plus a bounded recent catalog.
   p[key]=Json.encodeToString((merged.filter { it.id.toString() in favorites }+merged.take(120)).distinctBy { it.id })
  }
 }
 suspend fun migrateLeagues(catalog: List<Competition>) {
  store.edit { p ->
   p.remove(booleanPreferencesKey("demo"))
   val key=stringSetPreferencesKey("teams")
   p[key]=(p[key] ?: emptySet()).filter { (it.toIntOrNull() ?: -1)>0 }.toSet()
   val catalogKey=stringPreferencesKey("team_catalog")
   val teams=runCatching { Json.decodeFromString<List<Team>>(p[catalogKey] ?: "[]") }.getOrDefault(emptyList())
   p[catalogKey]=Json.encodeToString(teams.filter { it.id>0 })
  }
  store.edit { preferences ->
   val key=stringSetPreferencesKey("leagues")
   preferences[key]=(preferences[key] ?: emptySet()).map { value ->
    catalog.firstOrNull { it.name==value || it.apiName==value }?.id?.toString() ?: value
   }.toSet()
  }
 }
 suspend fun notifications(value: Boolean) { store.edit { it[booleanPreferencesKey("notifications")] = value } }
 suspend fun toggle(key: String, value: String) { store.edit { val k = stringSetPreferencesKey(key); val old = it[k] ?: emptySet(); it[k] = if(value in old) old - value else old + value } }
}
