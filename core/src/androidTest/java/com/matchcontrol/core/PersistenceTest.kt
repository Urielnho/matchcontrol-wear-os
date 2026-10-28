package com.matchcontrol.core

import android.content.ContentValues
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PersistenceTest {
 private val json=Json {encodeDefaults=true;ignoreUnknownKeys=true}
 private fun fixture()=Fixture("api:123",DemoTeam("10","Home Team","HOM"),DemoTeam("20","Away Team","AWY"),"100","League",System.currentTimeMillis()+3_600_000,source=RealData.Source)
 private fun feed(profile: Profile=Profile(),balance: Long=0)=Snapshot(profile=profile,balance=balance,fixtures=listOf(fixture()),source=RealData.Source,accountSignedIn=profile.id.isNotBlank())
 private fun context()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun clear(name: String) {val c=context();c.deleteDatabase(name);c.getSharedPreferences("v2-replica-$name",0).edit().clear().commit()}

 @Test fun orderedMirrorCannotRestoreAnotherAccountsWallet() {
  val c=context();val name="ordered-mirror.db";clear(name)
  val watch=DemoRepository(c,false,name)
  assertEquals(0L,watch.balance());assertTrue(watch.profile().id.isBlank());assertTrue(watch.matches().isEmpty())
  val a=feed(Profile("account-a","Ana"),10000).copy(revision=900)
  val b=a.copy(profile=Profile("account-b","Luis"),revision=1,balance=50000)
  assertTrue(watch.applyMirror(MirrorFrame("phone",10,a)))
  watch.enqueue(SyncCommand(id="old-account-action",action="team",target="10",enabled=true,profileId="account-a"))
  assertTrue(watch.applyMirror(MirrorFrame("phone",11,b)))
  assertTrue(watch.pending().isEmpty())
  assertFalse(watch.applyMirror(MirrorFrame("phone",10,a)))
  assertEquals("account-b",watch.profile().id);assertEquals(50000L,watch.balance())
  val reopened=DemoRepository(c,false,name)
  assertFalse(reopened.applyMirror(MirrorFrame("phone",9,a)))
  assertTrue(reopened.applyMirror(MirrorFrame("new-install",1,b)))
  assertFalse(reopened.applyMirror(MirrorFrame("phone",100,a)))
  assertEquals("account-b",reopened.profile().id)
 }
 @Test fun lateRealFavoriteCommandCannotUndoTheLatestToggle() {
  val c=context();val name="command-order.db";clear(name);SessionVault(c).clear()
  val repo=DemoRepository(c,true,name);repo.update {feed()}
  val account=AccountManager(c,repo)
  val origin=newId()
  val newer=SyncCommand(id="newer",action="team",target="10",enabled=false,profileId="",origin=origin,sequence=2)
  account.command(newer)
  account.command(newer.copy(id="late",enabled=true,sequence=1))
  assertTrue(repo.state.value.favoriteTeams.isEmpty())
  account.command(newer.copy(id="latest",enabled=true,sequence=3))
  account.command(newer.copy(id="late-again",enabled=false,sequence=2))
  assertEquals(setOf("10"),repo.state.value.favoriteTeams)
  assertTrue(setOf("newer","late","latest","late-again").all {it in repo.state.value.processed})
  assertTrue(repo.profile().id.isBlank());assertEquals(0L,repo.balance())
 }
 @Test fun realFavoriteOutboxSurvivesRestartAndAcknowledgementClearsIt() {
  val c=context();clear("phone-test.db");clear("watch-test.db")
  val phone=DemoRepository(c,true,"phone-test.db");phone.update {feed()}
  val watch=DemoRepository(c,false,"watch-test.db")
  val command=SyncCommand(id="offline-action",action="team",target="10",enabled=true)
  watch.enqueue(command)
  val reopened=DemoRepository(c,false,"watch-test.db");assertEquals(command,reopened.pending().single())
  phone.command(command);val revision=phone.state.value.revision
  phone.command(command);assertEquals(revision,phone.state.value.revision)
  reopened.applySnapshot(phone.state.value);reopened.ack(phone.state.value.processed)
  assertEquals(setOf("10"),reopened.state.value.favoriteTeams);assertTrue(reopened.pending().isEmpty())
  reopened.applySnapshot(phone.state.value.copy(revision=0,balance=1))
  assertEquals(0L,reopened.balance());assertEquals("api:123",reopened.matches().single().id)
 }
 @Test fun sessionIsEncryptedAndCanBeRestored() {
  val c=context();val vault=SessionVault(c);val session=AuthSession("test-access-token","test-refresh-token",3600)
  try {
   vault.save(session);assertEquals(session,vault.load())
   assertFalse(c.getSharedPreferences("auth-session-v2",0).getString("session","")!!.contains("test-access"))
  } finally {vault.clear()}
  assertNull(vault.load())
 }
 @Test fun realCachePersistsButCannotCreateLocalWalletOrBets() {
  val c=context();val name="real-cache.db";clear(name);clear("local-finance-replica.db")
  val repo=DemoRepository(c,true,name)
  assertEquals(RealData.Source,repo.state.value.source);assertTrue(repo.matches().isEmpty())
  val f=fixture();val o=Outcome("quote-123",f.id,MarketType.WINNER,"1",f.home.name,18500,System.currentTimeMillis())
  val bet=Bet("server-bet","server-request",10000,"1.85",18500,listOf(Pick(o.id,f.id,o.market,o.key,o.label,"Home Team · Away Team",o.odds)),createdAt=System.currentTimeMillis(),source=RealData.Source)
  repo.update {feed(Profile("account-a","Ana"),990000).copy(outcomes=listOf(o),bets=listOf(bet),transactions=listOf(WalletTransaction("stake","STAKE",-10000,bet.id,System.currentTimeMillis())))}
  val reopened=DemoRepository(c,true,name)
  assertEquals(listOf(f.id),reopened.matches().map {it.id});assertEquals(listOf(o),reopened.markets(f.id))
  assertTrue(reopened.profile().id.isBlank());assertEquals(0L,reopened.balance());assertTrue(reopened.history().isEmpty());assertTrue(reopened.movements().isEmpty())
  val request=BetRequest("local-attempt",10000,mapOf(o.id to o.odds))
  assertThrows(IllegalStateException::class.java) {reopened.place(request)}
  assertThrows(IllegalStateException::class.java) {reopened.reset("local-reset")}
  assertEquals(0L,reopened.balance());assertTrue(reopened.history().isEmpty())
  val replica=DemoRepository(c,false,"local-finance-replica.db")
  assertThrows(IllegalStateException::class.java) {replica.place(request)}
  assertThrows(IllegalStateException::class.java) {replica.reset("watch-reset")}
 }

 @Test fun legacyPracticeIsArchivedAndCannotReturnThroughTheMirror() {
  val c=context();val name="legacy-cache.db";clear(name);clear("legacy-watch.db");c.deleteDatabase("legacy-practice-archive.db")
  val generated=fixture().copy(id="910001",source="DEMO")
  val old=Snapshot(profile=Profile("local-demo","Practice"),balance=960000,fixtures=listOf(generated),favoriteTeams=setOf("10"),source="DEMO")
  c.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use {db->
   db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)")
   db.execSQL("CREATE TABLE outbox(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
   db.insertOrThrow("state",null,ContentValues().apply {put("id",1);put("payload",json.encodeToString(old))})
   db.version=1
  }
  val upgraded=DemoRepository(c,true,name)
  assertEquals(RealData.Source,upgraded.state.value.source);assertTrue(upgraded.matches().isEmpty());assertTrue(upgraded.state.value.favoriteTeams.isEmpty());assertEquals(0L,upgraded.balance())
  c.openOrCreateDatabase("legacy-practice-archive.db",Context.MODE_PRIVATE,null).use {db->
   db.rawQuery("SELECT payload FROM snapshot WHERE id=1",null).use {rows->assertTrue(rows.moveToFirst());assertEquals(old,json.decodeFromString<Snapshot>(rows.getString(0)))}
  }
  val watch=DemoRepository(c,false,"legacy-watch.db")
  assertTrue(watch.applyMirror(MirrorFrame("phone",1,old)))
  assertTrue(watch.matches().isEmpty());assertEquals(0L,watch.balance());assertTrue(watch.profile().id.isBlank())
  // Changing a source label is insufficient: an event still needs a canonical provider ID.
  val mixed=feed().copy(fixtures=listOf(fixture(),generated.copy(source=RealData.Source)))
  assertTrue(watch.applyMirror(MirrorFrame("phone",2,mixed)))
  assertEquals(listOf("api:123"),watch.matches().map {it.id})
 }
}
