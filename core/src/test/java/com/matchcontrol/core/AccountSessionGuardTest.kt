package com.matchcontrol.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AccountSessionGuardTest {
 @Test fun delayedSignInCannotRestoreAccountAfterSignOut() {
  val sessions=AccountSessionGuard()
  var saved="local"
  val attempt=sessions.advance {}
  sessions.advance {saved="local"}
  val result=runCatching {sessions.requireCurrent(attempt) {saved="old account"}}
  assertTrue(result.exceptionOrNull() is IllegalStateException)
  assertEquals("local",saved)
 }

 @Test fun delayedRefreshCannotWriteOldTokensAfterAnotherSignIn() {
  val sessions=AccountSessionGuard()
  var vault="old token"
  val issued=sessions.capture()
  val response=CountDownLatch(1)
  val requestStarted=CountDownLatch(1)
  val worker=Executors.newSingleThreadExecutor()
  try {
   val request=worker.submit<Boolean> {
    sessions.requireCurrent(issued) {assertEquals("old token",vault)}
    requestStarted.countDown()
    check(response.await(5,TimeUnit.SECONDS))
    runCatching {sessions.requireCurrent(issued) {vault="refreshed old token"}}.isSuccess
   }
   assertTrue(requestStarted.await(5,TimeUnit.SECONDS))
   // A network request must not hold the account lock or block a new identity.
   sessions.advance {vault="new account token"}
   response.countDown()
   assertFalse(request.get(5,TimeUnit.SECONDS))
   assertEquals("new account token",vault)
  } finally {response.countDown();worker.shutdownNow()}
 }

 @Test fun oldCommandCannotCommitOrderingOrAcknowledgeAnotherAccount() {
  val sessions=AccountSessionGuard()
  val issued=sessions.capture()
  var acknowledged=false
  var lastCommandSequence=0L
  sessions.advance {}
  assertFalse(sessions.commit(issued) {acknowledged=true;lastCommandSequence=15})
  assertFalse(acknowledged)
  assertEquals(0L,lastCommandSequence)
 }

 @Test fun currentGenerationCommitsAndOldOneStaysRetiredAfterSameAccountSignsInAgain() {
  val sessions=AccountSessionGuard()
  val first=sessions.capture()
  var saved="account"
  assertTrue(sessions.commit(first) {saved="current snapshot"})
  sessions.advance {saved="local"}
  val next=sessions.advance {saved="account"}
  assertFalse(sessions.commit(first) {saved="old delayed snapshot"})
  assertTrue(sessions.commit(next) {saved="new current snapshot"})
  assertEquals("new current snapshot",saved)
 }
}
