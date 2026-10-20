package com.matchcontrol.data

import android.content.Context
import com.matchcontrol.BuildConfig
import com.matchcontrol.domain.FootballRepository
import com.matchcontrol.domain.Match
import java.io.File
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface FootballApi {
    @GET("fixtures") suspend fun fixtures(@Query("date") date: String, @Query("timezone") timezone: String): JsonObject
    @GET("fixtures") suspend fun fixture(@Query("id") id: Int): JsonObject
    @GET("leagues") suspend fun leagues(@Query("search") search: String): JsonObject
}

class ApiRepository(context: Context) : FootballRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "api-cache").apply { mkdirs() }
    private val quota = context.getSharedPreferences("api-quota", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            check(BuildConfig.FOOTBALL_API_KEY.isNotBlank()) { "Los resultados no están disponibles en este momento." }
            val response = chain.proceed(chain.request().newBuilder()
                .header("x-apisports-key", BuildConfig.FOOTBALL_API_KEY).build())
            response.header("x-ratelimit-requests-remaining")?.toIntOrNull()?.let { remaining ->
                quota.edit().putInt("remoteRemaining", remaining)
                    .putString("remoteDay", LocalDate.now(ZoneOffset.UTC).toString()).apply()
            }
            response
        }.build()
    private val api = Retrofit.Builder().baseUrl("https://v3.football.api-sports.io/")
        .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(FootballApi::class.java)

    var stale = false; private set
    var updated: Long = 0; private set
    val remainingCalls: Int?
        get() = if (quota.getString("remoteDay", "") == LocalDate.now(ZoneOffset.UTC).toString())
            quota.getInt("remoteRemaining", -1).takeIf { it >= 0 } else null

    private suspend fun request(key: String, ttl: Long, force: Boolean = false, block: suspend () -> JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val file = File(dir, "$key.json")
                val now = System.currentTimeMillis()
                val cached = if (file.exists()) runCatching {
                    json.parseToJsonElement(file.readText()).jsonObject.also(FootballResponse::validate)
                }.getOrNull() else null
                if (!force && cached != null && now - file.lastModified() < ttl) {
                    updated = file.lastModified(); stale = false
                    return@withLock cached
                }
                try {
                    check(BuildConfig.FOOTBALL_API_KEY.isNotBlank()) { "Los resultados no están disponibles en este momento." }
                    val day = LocalDate.now(ZoneOffset.UTC).toString()
                    val count = if (quota.getString("day", "") == day) quota.getInt("count", 0) else 0
                    check(RequestBudget.allows(count, remainingCalls)) {
                        "No se pueden actualizar más resultados por ahora. Inténtalo más tarde."
                    }
                    // Failed HTTP attempts can consume quota too: reserve before sending.
                    check(quota.edit().putString("day", day).putInt("count", count + 1).commit())
                    val result = block()
                    FootballResponse.validate(result)
                    val temporary = File(dir, "$key.tmp")
                    temporary.writeText(result.toString())
                    check(temporary.renameTo(file)) { "No se pudo guardar la caché local" }
                    updated = now; stale = false
                    result
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (cached != null) {
                        stale = true; updated = file.lastModified()
                        cached
                    } else {
                        val code=(error as? HttpException)?.code()
                        val message = when {
                            error is java.net.UnknownHostException -> "No se pudo conectar. Revisa la conexión del reloj."
                            error is java.net.SocketTimeoutException -> "Los resultados tardaron demasiado en llegar. Inténtalo de nuevo."
                            error is java.io.IOException -> "La conexión se interrumpió. Inténtalo de nuevo."
                            code==401 || code==403 -> "No se pudo acceder a los resultados."
                            code==429 -> "Has alcanzado el límite de consultas. Inténtalo más tarde."
                            code!=null && code>=500 -> "El servicio de resultados no está disponible. Inténtalo más tarde."
                            code!=null -> "No se pudieron consultar los resultados. Inténtalo de nuevo."
                            else -> error.message ?: "No se pudieron descargar los resultados"
                        }
                        throw IllegalStateException(message, error)
                    }
                }
            }
        }

    override suspend fun matches(date: LocalDate): List<Match> = matches(date, false)

    suspend fun matches(date: LocalDate, force: Boolean): List<Match> {
        val timezone = ZoneId.systemDefault().id
        return request("day-$date-${timezone.hashCode()}", 15 * 60_000L, force) {
            api.fixtures(date.toString(), timezone)
        }.getValue("response").jsonArray.mapNotNull { row ->
            (row as? JsonObject)?.let(FootballMapper::parse)
        }
    }

    override suspend fun details(match: Match): Match = details(match, false)

    suspend fun details(match: Match, force: Boolean): Match {
        val result = request("match-${match.id}", if (match.finished) 24 * 60 * 60_000L else 15 * 60_000L, force) {
            api.fixture(match.id)
        }.getValue("response").jsonArray.firstOrNull() as? JsonObject
        return result?.let(FootballMapper::parse) ?: match
    }

    suspend fun competitions(search: String): JsonObject =
        request("league-${search.hashCode()}", 24 * 60 * 60_000L) { api.leagues(search) }
}
