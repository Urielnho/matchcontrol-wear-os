package com.matchcontrol.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Local provider cache and paired-device replica. Legacy database names are retained for upgrades. */
class DemoRepository(private val context: Context,val authority: Boolean,private val databaseName: String="matchcontrol-v2.db"): MatchesRepository,ResultsRepository,OddsRepository,UsersRepository,WalletsRepository,BetsRepository,HistoryRepository {
 private val db=object:SQLiteOpenHelper(context,databaseName,null,1) {
  override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)");db.execSQL("CREATE TABLE outbox(id TEXT PRIMARY KEY,payload TEXT NOT NULL)") }
  override fun onUpgrade(db: SQLiteDatabase,old: Int,new: Int) = Unit
 }
 private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
 private fun read(): Snapshot?=db.readableDatabase.rawQuery("SELECT payload FROM state WHERE id=1",null).use { if(it.moveToFirst()) json.decodeFromString<Snapshot>(it.getString(0)) else null }
 private fun write(s: Snapshot) { check(db.writableDatabase.insertWithOnConflict("state",null,ContentValues().apply { put("id",1);put("payload",json.encodeToString(s)) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L) {"No se pudo guardar el estado"} }
 private fun initial(): Snapshot {
  val saved=read()
  val clean=if(authority) RealData.guest(saved) else saved?.let(RealData::only) ?: RealData.guest()
  // Preserve the old file as an inaccessible upgrade backup rather than deleting financial history.
  if(saved!=null && saved!=clean) context.openOrCreateDatabase("legacy-practice-archive.db",Context.MODE_PRIVATE,null).use {archive->
   archive.execSQL("CREATE TABLE IF NOT EXISTS snapshot(id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
   archive.insertWithOnConflict("snapshot",null,ContentValues().apply {put("id",1);put("payload",json.encodeToString(saved))},SQLiteDatabase.CONFLICT_IGNORE)
  }
  return clean.also(::write)
 }
 private val mutable=MutableStateFlow(initial())
 val state=mutable.asStateFlow()
 private val queued=MutableStateFlow(pending())
 val pendingCommands=queued.asStateFlow()
 @Synchronized fun update(block: (Snapshot)->Snapshot): Snapshot {
  check(authority) { "El reloj es una réplica; confirma operaciones desde el móvil" }
  val database=db.writableDatabase;database.beginTransaction()
  val result=try { val old=read()!!;val next=RealData.guest(block(old)).let { if(it==old) old else it.copy(revision=old.revision+1,updatedAt=System.currentTimeMillis()) };write(next);database.setTransactionSuccessful();next } finally { database.endTransaction() }
  val previous=mutable.value;mutable.value=result;runCatching {ResultAlerts.changed(context,previous,result)};return result
 }
 @Synchronized fun applySnapshot(receivedSnapshot: Snapshot) {
  check(!authority)
  val snapshot=RealData.only(receivedSnapshot)
  // First authoritative snapshot replaces a seeded preview, then revisions are monotonic.
  val received=contextPreferences.getBoolean("received",false)
  if(received && snapshot.profile.id==mutable.value.profile.id && snapshot.source==mutable.value.source && snapshot.revision<mutable.value.revision) return
  val previous=mutable.value;write(snapshot);mutable.value=snapshot;contextPreferences.edit().putBoolean("received",true).apply();runCatching {ResultAlerts.changed(context,previous,snapshot)}
 }
 private val contextPreferences=context.getSharedPreferences("v2-replica-$databaseName",Context.MODE_PRIVATE)
 @Synchronized fun applyMirror(receivedFrame: MirrorFrame): Boolean {
  check(!authority)
  val frame=receivedFrame.copy(snapshot=RealData.only(receivedFrame.snapshot))
  require(frame.authorityId.isNotBlank() && frame.sequence>0)
  val epoch=contextPreferences.getString("authority",null)
  val retired=contextPreferences.getStringSet("retired",emptySet()).orEmpty()
  if(frame.authorityId in retired || (frame.authorityId==epoch && frame.sequence<=contextPreferences.getLong("sequence",0))) return false
  val old=mutable.value
  write(frame.snapshot)
  // Account changes also discard offline actions owned by the previous account.
  pending().filter {it.profileId!=frame.snapshot.profile.id || it.source!=frame.snapshot.source || it.id in frame.snapshot.processed}.forEach {db.writableDatabase.delete("outbox","id=?",arrayOf(it.id))}
  queued.value=pending()
  contextPreferences.edit().putBoolean("received",true).putString("authority",frame.authorityId).putLong("sequence",frame.sequence)
   .putStringSet("retired",if(epoch!=null && epoch!=frame.authorityId) retired+epoch else retired).commit()
  mutable.value=frame.snapshot
  runCatching {ResultAlerts.changed(context,old,frame.snapshot)}
  return true
 }
 fun hasReplica()=contextPreferences.getBoolean("received",false)
 override fun matches()=state.value.fixtures
 override fun results()=matches().filter { it.status==FixtureStatus.FINAL }
 override fun markets(matchId: String)=state.value.outcomes.filter { it.matchId==matchId }
 override fun profile()=state.value.profile
 override fun balance()=state.value.balance
 override fun history()=state.value.bets
 override fun movements()=state.value.transactions
 override fun place(request: BetRequest): Bet = error("Inicia sesión en el móvil para confirmar tu boleto")
 override fun reset(requestId: String) {error("El saldo se administra en tu cuenta")}
 fun command(c: SyncCommand) { update { s->
  if(c.id in s.processed) s else {
   require(c.action in setOf("team","league","match","select","refresh","sport")) { "Acción no permitida" }
   fun Set<String>.set()=if(c.enabled) this+c.target else this-c.target
   val next=when(c.action) {
    "team"->{ require(s.fixtures.any { it.home.id==c.target || it.away.id==c.target || it.participants.any {d->d.id==c.target} });s.copy(favoriteTeams=s.favoriteTeams.set()) }
    "league"->{ require(s.fixtures.any { it.leagueId==c.target });s.copy(favoriteLeagues=s.favoriteLeagues.set()) }
    "match"->{ require(s.fixtures.any { it.id==c.target });s.copy(favoriteMatches=s.favoriteMatches.set()) }
    "select"->{ require(s.fixtures.any { it.id==c.target });s.copy(selectedMatch=c.target) }
    else->s
   };next.copy(processed=next.processed+c.id)
  }
 } }
 @Synchronized fun enqueue(c: SyncCommand) { db.writableDatabase.insertWithOnConflict("outbox",null,ContentValues().apply { put("id",c.id);put("payload",json.encodeToString(c)) },SQLiteDatabase.CONFLICT_IGNORE);queued.value=pending() }
 fun pending(): List<SyncCommand> = db.readableDatabase.rawQuery("SELECT payload FROM outbox ORDER BY rowid",null).use { cursor->buildList { while(cursor.moveToNext()) add(json.decodeFromString<SyncCommand>(cursor.getString(0))) } }
 @Synchronized fun ack(ids: Set<String>) { ids.forEach { db.writableDatabase.delete("outbox","id=?",arrayOf(it)) };queued.value=pending() }
}
