package com.matchcontrol.core

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PersistenceTest {
 @Test fun orderedMirrorCannotRestoreAnotherAccountsWallet() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val name="ordered-mirror.db";c.deleteDatabase(name);c.getSharedPreferences("v2-replica-$name",0).edit().clear().commit()
  val watch=DemoRepository(c,false,name)
  assertEquals(0L,watch.balance());assertTrue(watch.profile().id.isBlank());assertTrue(watch.matches().isEmpty())
  val a=DemoEngine.seed().copy(profile=Profile("a","Ana"),revision=900,balance=10000)
  val b=a.copy(profile=Profile("b","Luis"),revision=1,balance=50000,favoriteTeams=emptySet())
  assertTrue(watch.applyMirror(MirrorFrame("phone",10,a)))
  watch.enqueue(SyncCommand(id="old-account-action",action="team",target="900001",enabled=true,profileId="a"))
  assertTrue(watch.applyMirror(MirrorFrame("phone",11,b)))
  assertTrue(watch.pending().isEmpty())
  assertFalse(watch.applyMirror(MirrorFrame("phone",10,a)))
  assertEquals("b",watch.profile().id);assertEquals(50000L,watch.balance())
  val reopened=DemoRepository(c,false,name)
  assertFalse(reopened.applyMirror(MirrorFrame("phone",9,a)))
  assertTrue(reopened.applyMirror(MirrorFrame("new-install",1,b)))
  assertFalse(reopened.applyMirror(MirrorFrame("phone",100,a)))
  assertEquals("b",reopened.profile().id)
 }
 @Test fun lateFavoriteCommandCannotUndoTheLatestToggle() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val name="command-order.db";c.deleteDatabase(name)
  val repo=DemoRepository(c,true,name);val account=AccountManager(c,repo)
  val origin=newId()
  val newer=SyncCommand(id="newer",action="team",target="900001",enabled=false,profileId="local-demo",origin=origin,sequence=2)
  account.command(newer)
  account.command(newer.copy(id="late",enabled=true,sequence=1))
  assertTrue(repo.state.value.favoriteTeams.isEmpty())
  account.command(newer.copy(id="latest",enabled=true,sequence=3))
  account.command(newer.copy(id="late-again",enabled=false,sequence=2))
  assertTrue("900001" in repo.state.value.favoriteTeams)
  assertTrue(setOf("newer","late","latest","late-again").all {it in repo.state.value.processed})
 }
 @Test fun outboxSurvivesRestartAndAcknowledgementClearsIt() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  c.deleteDatabase("phone-test.db");c.deleteDatabase("watch-test.db")
  val phone=DemoRepository(c,true,"phone-test.db");val watch=DemoRepository(c,false,"watch-test.db")
  val command=SyncCommand(id="offline-action",action="team",target="900001",enabled=true)
  watch.enqueue(command)
  val reopened=DemoRepository(c,false,"watch-test.db");assertEquals(command,reopened.pending().single())
  phone.command(command);val revision=phone.state.value.revision;phone.command(command);assertEquals(revision,phone.state.value.revision)
  reopened.applySnapshot(phone.state.value);reopened.ack(phone.state.value.processed)
  assertTrue(reopened.state.value.favoriteTeams.contains("900001"));assertTrue(reopened.pending().isEmpty())
  reopened.applySnapshot(phone.state.value.copy(revision=0,balance=1))
  assertEquals(1_000_000L,reopened.balance())
 }
 @Test fun sessionIsEncryptedAndCanBeRestored() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val vault=SessionVault(c);val session=AuthSession("test-access-token","test-refresh-token",3600)
  vault.save(session);assertEquals(session,vault.load())
  assertFalse(c.getSharedPreferences("auth-session-v2",0).getString("session","")!!.contains("test-access"))
  vault.clear();assertNull(vault.load())
 }
 @Test fun atomicBetPersistedAndRetryDoesNotDebitTwice() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext;c.deleteDatabase("matchcontrol-v2.db")
  val repo=DemoRepository(c,true);val o=repo.state.value.outcomes.first {it.open};val request=BetRequest("sqlite-retry",10000,mapOf(o.id to o.odds))
  val bet=repo.place(request);val reopened=DemoRepository(c,true);assertEquals(990000,reopened.balance());assertEquals(bet,reopened.place(request));assertEquals(1,reopened.history().size)
  reopened.command(SyncCommand(id="repeat",action="team",target="900001",enabled=true));val rev=reopened.state.value.revision
  reopened.command(SyncCommand(id="repeat",action="team",target="900001",enabled=true));assertEquals(rev,reopened.state.value.revision)
  val replica=DemoRepository(c,false);assertThrows(IllegalStateException::class.java) {replica.place(request)}
 }
}
