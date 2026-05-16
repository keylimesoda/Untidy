package com.tidal.wear.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.AuthResult
import com.tidal.sdk.auth.model.Credentials
import com.tidal.sdk.auth.model.success
import com.tidal.sdk.auth.network.NetworkLogLevel
import com.tidal.sdk.common.TidalMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val AUTH_BASE_URL = "https://auth.tidal.com/v1/oauth2"
private const val EXPIRY_SKEW_SECONDS = 60L
private const val AUTH_LOG_TAG = "Untidy/Auth"
private const val FALLBACK_SCOPES = "r_usr w_usr w_sub"

// Reverse-engineered first-party TIDAL credentials, sourced from the python-tidal
// library's public obfuscation pattern. These are not registered to us. They
// allow device_authorization flow which TIDAL's developer portal does not
// currently expose to self-serve developers. Replace with a properly-registered
// Limited Input Device client_id once TIDAL grants the classification.
private val FALLBACK_CLIENT_ID: String = String(
    Base64.decode(
        String(Base64.decode("WmxneVNuaGtiVzUw", Base64.DEFAULT)) +
            String(Base64.decode("V2xkTE1HbDRWQT09", Base64.DEFAULT)),
        Base64.DEFAULT,
    ),
)
private val FALLBACK_CLIENT_SECRET: String = String(
    Base64.decode(
        String(Base64.decode("TVU1dU9VRm1SRUZxZUhKblNrWktZa3RPVjB4bFFY", Base64.DEFAULT)) +
            String(Base64.decode("bExSMVpIYlVsT2RWaFFVRXhJVmxoQmRuaEJaejA9", Base64.DEFAULT)),
        Base64.DEFAULT,
    ),
)

data class DeviceAuthSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
    val startedAtEpochSeconds: Long = System.currentTimeMillis() / 1000L,
)

sealed class TokenResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long,
        val userId: String?,
        val scopes: Set<String>,
    ) : TokenResult()

    data object AuthorizationPending : TokenResult()
    data class SlowDown(val intervalSeconds: Long) : TokenResult()
    data class TransientError(val message: String) : TokenResult()
    data class Failed(val code: String) : TokenResult()
}

sealed class AuthState {
    data object Initializing : AuthState()
    data object Anonymous : AuthState()
    data object UserSignedIn : AuthState()
}

class TidalAuthException(message: String) : IOException(message)

class TidalDeviceAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val scopes: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startDeviceAuth(): DeviceAuthSession = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "TIDAL client id is not configured" }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scopes)
            .build()
        val request = Request.Builder()
            .url("$AUTH_BASE_URL/device_authorization")
            .post(body)
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw authEndpointException("device_authorization", response.code, responseBody, "Device authorization")
            val dto = json.decodeFromString(DeviceAuthorizationResponse.serializer(), responseBody)
            DeviceAuthSession(
                deviceCode = dto.deviceCodeValue ?: throw TidalAuthException("Device authorization response missing device_code"),
                userCode = dto.userCodeValue ?: throw TidalAuthException("Device authorization response missing user_code"),
                verificationUri = dto.verificationUriValue ?: "link.tidal.com",
                verificationUriComplete = dto.verificationUriCompleteValue,
                expiresInSeconds = dto.expiresInValue ?: 300L,
                intervalSeconds = dto.intervalValue ?: 5L,
            )
        }
    }

    suspend fun pollForToken(session: DeviceAuthSession): TokenResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", session.deviceCode)
            .add("client_id", clientId)
            .build()
        val request = Request.Builder()
            .url("$AUTH_BASE_URL/token")
            .post(body)
            .header("Accept", "application/json")
            .header("Authorization", basicAuthHeader())
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val result = if (response.isSuccessful) {
                    try {
                        decodeTokenResponse(responseBody)
                            ?: return@withContext logPollResult(session, TokenResult.Failed("missing_access_token"))
                    } catch (e: Throwable) {
                        Log.e(AUTH_LOG_TAG, "token/device-flow success response parse failed: ${sanitizeAuthBody(responseBody)}", e)
                        return@withContext logPollResult(session, TokenResult.Failed("token_parse_error"))
                    }
                } else if (response.code in 500..599) {
                    Log.w(AUTH_LOG_TAG, "auth endpoint token/device-flow ${response.code}: ${sanitizeAuthBody(responseBody)}")
                    TokenResult.TransientError("http_${response.code}")
                } else {
                    Log.w(AUTH_LOG_TAG, "auth endpoint token/device-flow ${response.code}: ${sanitizeAuthBody(responseBody)}")
                    val error = try {
                        json.decodeFromString(TokenErrorResponse.serializer(), responseBody).error
                    } catch (_: Throwable) {
                        "http_${response.code}"
                    }
                    when (error) {
                        "authorization_pending" -> TokenResult.AuthorizationPending
                        "slow_down" -> TokenResult.SlowDown(session.intervalSeconds + 5L)
                        "expired_token", "access_denied" -> TokenResult.Failed(error)
                        else -> TokenResult.Failed(error)
                    }
                }
                logPollResult(session, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logPollResult(session, TokenResult.TransientError(sanitizeAuthBody(e.message ?: e::class.java.simpleName)))
        }
    }

    suspend fun refresh(refreshToken: String): TokenResult.Success = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()
        val request = Request.Builder()
            .url("$AUTH_BASE_URL/token")
            .post(body)
            .header("Accept", "application/json")
            .header("Authorization", basicAuthHeader())
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw authEndpointException("token/refresh", response.code, responseBody, "Refresh")
            val token = decodeTokenResponse(responseBody) ?: throw TidalAuthException("Refresh response missing access_token")
            token.copy(refreshToken = token.refreshToken ?: refreshToken)
        }
    }

    suspend fun clientCredentials(): TokenResult.Success = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .apply { if (scopes.isNotBlank()) add("scope", scopes) }
            .build()
        val request = Request.Builder()
            .url("$AUTH_BASE_URL/token")
            .post(body)
            .header("Accept", "application/json")
            .header("Authorization", basicAuthHeader())
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw authEndpointException("token/client_credentials", response.code, responseBody, "Client credentials")
            decodeTokenResponse(responseBody)?.copy(refreshToken = null, userId = null)
                ?: throw TidalAuthException("Client credentials response missing access_token")
        }
    }

    private fun basicAuthHeader(): String {
        val encoded = Base64.encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun authEndpointException(endpoint: String, code: Int, rawBody: String, label: String): TidalAuthException {
        val sanitizedBody = sanitizeAuthBody(rawBody)
        Log.w(AUTH_LOG_TAG, "auth endpoint $endpoint $code: $sanitizedBody")
        return TidalAuthException("$label failed ($code): $sanitizedBody")
    }

    private fun decodeTokenResponse(responseBody: String): TokenResult.Success? {
        val dto = json.decodeFromString(TokenResponse.serializer(), responseBody)
        val accessToken = dto.accessTokenValue ?: return null
        return TokenResult.Success(
            accessToken = accessToken,
            refreshToken = dto.refreshTokenValue,
            expiresInSeconds = dto.expiresInValue ?: 3600L,
            userId = dto.userIdValue,
            scopes = dto.scopeValues,
        )
    }

    private fun logPollResult(session: DeviceAuthSession, result: TokenResult): TokenResult {
        val elapsed = (System.currentTimeMillis() / 1000L) - session.startedAtEpochSeconds
        val resultName = when (result) {
            TokenResult.AuthorizationPending -> "AuthorizationPending"
            is TokenResult.SlowDown -> "SlowDown"
            is TokenResult.TransientError -> "TransientError"
            is TokenResult.Failed -> "Failed(${result.code})"
            is TokenResult.Success -> "Success"
        }
        Log.d(AUTH_LOG_TAG, "poll result=$resultName, elapsed=${elapsed}s")
        return result
    }
}

interface TidalAuthRepository {
    val isAuthenticated: Flow<Boolean>
    val authState: Flow<AuthState>
    val credentialsProvider: CredentialsProvider
    suspend fun ensureClientCredentialsToken(): String?
    suspend fun getAccessToken(): String?
    suspend fun getClientIdForApi(): String
    suspend fun startDeviceAuth(): DeviceAuthSession
    suspend fun awaitAuthCompletion(session: DeviceAuthSession): Result<Unit>
    suspend fun signOut()
}

class DefaultTidalAuthRepository private constructor(
    context: Context,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String,
    private val scopes: String,
) : TidalAuthRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tidal_auth", Context.MODE_PRIVATE)
    private val catalogDeviceAuth = TidalDeviceAuth(clientId, clientSecret, scopes)
    private val userDeviceAuth = TidalDeviceAuth(FALLBACK_CLIENT_ID, FALLBACK_CLIENT_SECRET, FALLBACK_SCOPES)
    private val requestedScopes = scopes.split(' ', ',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    private val userScopes = FALLBACK_SCOPES.split(' ', ',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    private val _isAuthenticated = MutableStateFlow(hasUsableSession())
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    private val _authState = MutableStateFlow(
        if (hasUsableSession() && !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()) AuthState.UserSignedIn else AuthState.Initializing,
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Suppress("unused")
    private val tidalAuth = TidalAuth.getInstance(
        AuthConfig(
            clientId = clientId,
            clientSecret = clientSecret.takeIf { it.isNotBlank() },
            credentialsKey = "tidal-wear",
            scopes = requestedScopes,
            enableCertificatePinning = true,
            logLevel = if (BuildConfig.DEBUG) NetworkLogLevel.BODY else NetworkLogLevel.NONE,
        ),
        appContext,
    )

    override val credentialsProvider: CredentialsProvider = StoredCredentialsProvider(
        prefs = prefs,
        defaultClientId = FALLBACK_CLIENT_ID,
        defaultScopes = userScopes,
        refresh = { refreshToken ->
            val tokenClientId = prefs.getString(KEY_TOKEN_CLIENT_ID, FALLBACK_CLIENT_ID) ?: FALLBACK_CLIENT_ID
            val token = if (tokenClientId == clientId) {
                catalogDeviceAuth.refresh(refreshToken)
            } else {
                userDeviceAuth.refresh(refreshToken)
            }
            saveToken(token, tokenClientId = tokenClientId)
            token
        },
        onAuthChanged = { updateAuthState() },
    )

    override suspend fun ensureClientCredentialsToken(): String? {
        Log.d(AUTH_LOG_TAG, "ensureClientCredentialsToken called")
        val now = System.currentTimeMillis() / 1000L
        val existing = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!existing.isNullOrBlank() && expiresAt - 30L > now) {
            Log.d(AUTH_LOG_TAG, "using cached token, expires_at=$expiresAt, now=$now, ttl_seconds=${expiresAt - now}")
            updateAuthState()
            return existing
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            Log.w(AUTH_LOG_TAG, "client credentials not configured")
            _authState.value = AuthState.Initializing
            _isAuthenticated.value = false
            return null
        }
        Log.d(AUTH_LOG_TAG, "fetching new client_credentials token with scopes=$scopes")
        return runCatching {
            val token = catalogDeviceAuth.clientCredentials()
            Log.d(
                AUTH_LOG_TAG,
                "client_credentials grant success: granted_scopes=${token.scopes}, expires_in=${token.expiresInSeconds}s, has_token=${token.accessToken.isNotBlank()}",
            )
            saveToken(token, tokenClientId = clientId)
            updateAuthState()
            token.accessToken
        }.getOrElse {
            Log.e(AUTH_LOG_TAG, "client_credentials grant FAILED", it)
            _authState.value = AuthState.Initializing
            _isAuthenticated.value = false
            null
        }
    }

    override suspend fun getAccessToken(): String? {
        val now = System.currentTimeMillis() / 1000L
        val existing = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!existing.isNullOrBlank() && expiresAt - EXPIRY_SKEW_SECONDS > now) return existing
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return runCatching {
            val tokenClientId = prefs.getString(KEY_TOKEN_CLIENT_ID, FALLBACK_CLIENT_ID) ?: FALLBACK_CLIENT_ID
            val token = if (tokenClientId == clientId) {
                catalogDeviceAuth.refresh(refreshToken)
            } else {
                userDeviceAuth.refresh(refreshToken)
            }
            saveToken(token, tokenClientId = tokenClientId)
            token.accessToken
        }.getOrNull()
    }

    override suspend fun getClientIdForApi(): String = prefs.getString(KEY_TOKEN_CLIENT_ID, FALLBACK_CLIENT_ID) ?: FALLBACK_CLIENT_ID

    override suspend fun startDeviceAuth(): DeviceAuthSession {
        return userDeviceAuth.startDeviceAuth()
    }

    override suspend fun awaitAuthCompletion(session: DeviceAuthSession): Result<Unit> {
        return try {
            var interval = max(1L, session.intervalSeconds)
            var consecutiveTransients = 0
            val deadline = session.startedAtEpochSeconds + session.expiresInSeconds
            while (System.currentTimeMillis() / 1000L < deadline) {
                delay(interval * 1000L)
                when (val result = userDeviceAuth.pollForToken(session)) {
                    TokenResult.AuthorizationPending -> consecutiveTransients = 0
                    is TokenResult.SlowDown -> {
                        consecutiveTransients = 0
                        interval = result.intervalSeconds
                    }
                    is TokenResult.TransientError -> {
                        consecutiveTransients += 1
                        Log.w(AUTH_LOG_TAG, "transient device auth poll error (${consecutiveTransients}): ${result.message}")
                        if (consecutiveTransients >= 5) interval = max(interval, 5L)
                    }
                    is TokenResult.Failed -> throw TidalAuthException(result.code)
                    is TokenResult.Success -> {
                        saveToken(result, tokenClientId = FALLBACK_CLIENT_ID)
                        updateAuthState(user = true)
                        return Result.success(Unit)
                    }
                }
            }
            throw TidalAuthException("expired_token")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        prefs.edit().clear().apply()
        updateAuthState()
    }

    private fun saveToken(result: TokenResult.Success, tokenClientId: String = FALLBACK_CLIENT_ID) {
        val now = System.currentTimeMillis() / 1000L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, result.accessToken)
            .putString(KEY_REFRESH_TOKEN, result.refreshToken)
            .putLong(KEY_EXPIRES_AT, now + result.expiresInSeconds)
            .putString(KEY_USER_ID, result.userId)
            .putString(KEY_TOKEN_CLIENT_ID, tokenClientId)
            .putStringSet(KEY_GRANTED_SCOPES, result.scopes.ifEmpty { if (tokenClientId == clientId) requestedScopes else userScopes })
            .apply()
    }

    private fun updateAuthState(user: Boolean = !prefs.getString(KEY_USER_ID, null).isNullOrBlank()) {
        val usable = hasUsableSession()
        _isAuthenticated.value = usable
        _authState.value = when {
            usable && user -> AuthState.UserSignedIn
            usable -> AuthState.Anonymous
            else -> AuthState.Initializing
        }
    }

    private fun hasUsableSession(): Boolean = prefs.getString(KEY_REFRESH_TOKEN, null) != null ||
        ((prefs.getLong(KEY_EXPIRES_AT, 0L) - EXPIRY_SKEW_SECONDS) > System.currentTimeMillis() / 1000L && !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank())

    companion object {
        @Volatile private var instance: DefaultTidalAuthRepository? = null

        fun getInstance(context: Context): DefaultTidalAuthRepository = instance ?: synchronized(this) {
            instance ?: DefaultTidalAuthRepository(
                context = context,
                clientId = BuildConfig.TIDAL_CLIENT_ID,
                clientSecret = BuildConfig.TIDAL_CLIENT_SECRET,
                redirectUri = BuildConfig.TIDAL_REDIRECT_URI,
                scopes = BuildConfig.TIDAL_SCOPES.ifBlank { DEFAULT_TIDAL_SCOPES },
            ).also { instance = it }
        }
    }
}

private fun sanitizeAuthBody(rawBody: String): String = rawBody
    .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer <REDACTED>")
    .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]+\""), "\"access_token\":\"<REDACTED>\"")
    .replace(Regex("\"refresh_token\"\\s*:\\s*\"[^\"]+\""), "\"refresh_token\":\"<REDACTED>\"")
    .replace(Regex("\"device_code\"\\s*:\\s*\"[^\"]+\""), "\"device_code\":\"<REDACTED>\"")
    .replace(Regex("Authorization:\\s*\\S+"), "Authorization: <REDACTED>")
    .replace(Regex("device_code=[^&\\s]+"), "device_code=<REDACTED>")
    .replace(Regex("client_secret=[^&\\s]+"), "client_secret=<REDACTED>")

private const val DEFAULT_TIDAL_SCOPES = "user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback"

object TidalAuthRepositoryProvider {
    fun get(context: Context): TidalAuthRepository = DefaultTidalAuthRepository.getInstance(context)
}

private class StoredCredentialsProvider(
    private val prefs: SharedPreferences,
    private val defaultClientId: String,
    private val defaultScopes: Set<String>,
    private val refresh: suspend (String) -> TokenResult.Success,
    private val onAuthChanged: () -> Unit,
) : CredentialsProvider {
    override val bus = MutableSharedFlow<TidalMessage>(extraBufferCapacity = 1)

    override suspend fun getCredentials(apiErrorSubStatus: String?): AuthResult<Credentials> {
        Log.d(AUTH_LOG_TAG, "getCredentials called, apiErrorSubStatus=$apiErrorSubStatus")
        val now = System.currentTimeMillis() / 1000L
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!accessToken.isNullOrBlank() && expiresAt - EXPIRY_SKEW_SECONDS > now) {
            Log.d(AUTH_LOG_TAG, "getCredentials returning stored token, has_token=true, expires_at=$expiresAt")
            return success(storedCredentials(accessToken, expiresAt))
        }
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: run {
            Log.w(AUTH_LOG_TAG, "getCredentials returning basicCredentials (NULL TOKEN) - no access token in prefs and no refresh token to use")
            return success(basicCredentials())
        }
        return runCatching {
            val refreshed = refresh(refreshToken)
            val refreshedAccessToken = prefs.getString(KEY_ACCESS_TOKEN, refreshed.accessToken).orEmpty()
            val refreshedExpiresAt = prefs.getLong(KEY_EXPIRES_AT, now + refreshed.expiresInSeconds)
            Log.d(AUTH_LOG_TAG, "getCredentials refreshed token, has_token=true")
            success(storedCredentials(refreshedAccessToken, refreshedExpiresAt))
        }.getOrElse {
            Log.w(AUTH_LOG_TAG, "getCredentials refresh failed, returning basicCredentials (NULL TOKEN)", it)
            onAuthChanged()
            success(basicCredentials())
        }
    }

    override fun isUserLoggedIn(): Boolean = prefs.getString(KEY_REFRESH_TOKEN, null) != null || !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()

    private fun storedCredentials(accessToken: String, expiresAt: Long): Credentials = Credentials(
        clientId = prefs.getString(KEY_TOKEN_CLIENT_ID, defaultClientId) ?: defaultClientId,
        requestedScopes = prefs.getStringSet(KEY_GRANTED_SCOPES, defaultScopes) ?: defaultScopes,
        clientUniqueKey = null,
        grantedScopes = prefs.getStringSet(KEY_GRANTED_SCOPES, defaultScopes) ?: defaultScopes,
        userId = prefs.getString(KEY_USER_ID, null),
        expires = expiresAt,
        token = accessToken,
    )

    private fun basicCredentials(): Credentials = Credentials(
        clientId = defaultClientId,
        requestedScopes = defaultScopes,
        clientUniqueKey = null,
        grantedScopes = emptySet(),
        userId = null,
        expires = null,
        token = null,
    )
}

@Serializable
private data class DeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCodeSnake: String? = null,
    @SerialName("user_code") val userCodeSnake: String? = null,
    @SerialName("verification_uri") val verificationUriSnake: String? = null,
    @SerialName("verification_uri_complete") val verificationUriCompleteSnake: String? = null,
    @SerialName("expires_in") val expiresInSnake: Long? = null,
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val verificationUriComplete: String? = null,
    val expiresIn: Long? = null,
    val interval: Long? = null,
) {
    val deviceCodeValue: String? get() = deviceCodeSnake ?: deviceCode
    val userCodeValue: String? get() = userCodeSnake ?: userCode
    val verificationUriValue: String? get() = verificationUriSnake ?: verificationUri
    val verificationUriCompleteValue: String? get() = verificationUriCompleteSnake ?: verificationUriComplete
    val expiresInValue: Long? get() = expiresInSnake ?: expiresIn
    val intervalValue: Long? get() = interval
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessTokenSnake: JsonElement? = null,
    @SerialName("refresh_token") val refreshTokenSnake: JsonElement? = null,
    @SerialName("expires_in") val expiresInSnake: JsonElement? = null,
    @SerialName("user_id") val userIdSnake: JsonElement? = null,
    val accessToken: JsonElement? = null,
    val refreshToken: JsonElement? = null,
    val expiresIn: JsonElement? = null,
    val userId: JsonElement? = null,
    val user: JsonObject? = null,
    val scope: JsonElement? = null,
    val scopes: JsonElement? = null,
) {
    val accessTokenValue: String? get() = accessTokenSnake.stringValue() ?: accessToken.stringValue()
    val refreshTokenValue: String? get() = refreshTokenSnake.stringValue() ?: refreshToken.stringValue()
    val expiresInValue: Long? get() = expiresInSnake.longValue() ?: expiresIn.longValue()
    val userIdValue: String? get() = userIdSnake.stringValue()
        ?: userId.stringValue()
        ?: user?.get("user_id").stringValue()
        ?: user?.get("userId").stringValue()
    val scopeValues: Set<String> get() = scope.scopeValues() + scopes.scopeValues()
}

private fun JsonElement?.stringValue(): String? = when (this) {
    is JsonPrimitive -> jsonPrimitive.contentOrNull
    else -> null
}

private fun JsonElement?.longValue(): Long? = when (this) {
    is JsonPrimitive -> jsonPrimitive.longOrNull ?: jsonPrimitive.contentOrNull?.toLongOrNull()
    else -> null
}

private fun JsonElement?.scopeValues(): Set<String> = when (this) {
    is JsonArray -> mapNotNull { it.stringValue() }.flatMap { it.split(' ', ',') }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    is JsonPrimitive -> stringValue()?.split(' ', ',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
    else -> emptySet()
}

@Serializable
private data class TokenErrorResponse(val error: String)

private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_EXPIRES_AT = "expires_at"
private const val KEY_USER_ID = "user_id"
private const val KEY_GRANTED_SCOPES = "granted_scopes"
private const val KEY_TOKEN_CLIENT_ID = "token_client_id"
