package com.matchcontrol.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json

/** Session tokens encrypted using a non-exportable Android Keystore key. */
class SessionVault(context: Context) {
 private val prefs=context.getSharedPreferences("auth-session-v2",Context.MODE_PRIVATE)
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
  return (store.getKey("matchcontrol-session",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("matchcontrol-session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 fun save(session: AuthSession) {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
  val encrypted=cipher.doFinal(Json.encodeToString(session).toByteArray())
  check(prefs.edit().putString("session",Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)).putLong("expires",System.currentTimeMillis()+session.expires_in*1000).commit())
 }
 fun load(): AuthSession?=runCatching {
  val bytes=Base64.decode(prefs.getString("session",null) ?: return null,Base64.NO_WRAP)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
  Json {ignoreUnknownKeys=true}.decodeFromString<AuthSession>(cipher.doFinal(bytes.copyOfRange(12,bytes.size)).decodeToString())
 }.getOrNull()
 fun expiredSoon()=System.currentTimeMillis()+60000>=prefs.getLong("expires",0)
 fun clear() {prefs.edit().clear().commit()}
}
