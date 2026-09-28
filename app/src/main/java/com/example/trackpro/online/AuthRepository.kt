package com.example.trackpro.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

sealed interface AccountState {
    /** [notice] explains an involuntary sign-out, e.g. an expired session. */
    data class SignedOut(val notice: String? = null) : AccountState
    data class SignedIn(val userId: String, val email: String, val displayName: String) : AccountState
}

/**
 * The TrackBoard sign-in: who is signed in, and a way to make authenticated calls that
 * survive an expired access token.
 *
 * Refresh tokens rotate on every use and the server treats a replayed one as theft, revoking
 * every session. Two refreshes racing with the same token would therefore sign the driver
 * out everywhere — so refreshing is single-flight behind [refreshLock], and a caller that
 * waited on it reuses the token the winner obtained instead of refreshing again.
 */
class AuthRepository(
    private val api: TrackBoardApi,
    private val store: TokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val refreshLock = Mutex()

    private val _state = MutableStateFlow(stateOf(store.load()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value is AccountState.SignedIn

    suspend fun signIn(email: String, password: String) {
        adopt(api.login(LoginRequest(email.trim(), password)))
    }

    suspend fun register(email: String, displayName: String, password: String) {
        adopt(api.register(RegisterRequest(email.trim(), displayName.trim(), password)))
    }

    /**
     * Signs out locally no matter what. The server-side revoke is best effort: being offline
     * must not leave the driver stuck signed in on their own phone.
     */
    suspend fun signOut() {
        val session = store.load()
        store.clear()
        _state.value = AccountState.SignedOut()
        if (session != null) {
            runCatching { api.logout(session.accessToken, session.refreshToken) }
        }
    }

    /** The current access token for an optional-auth call, or null when signed out. */
    suspend fun currentAccessTokenOrNull(): String? =
        if (store.load() == null) null else runCatching { validAccessToken() }.getOrNull()

    /**
     * Runs [block] with a valid access token. On a 401 the token is refreshed once and the
     * call retried; if the refresh itself is rejected, the driver is signed out.
     */
    suspend fun <T> authorized(block: suspend (accessToken: String) -> T): T {
        val token = validAccessToken()
        return try {
            block(token)
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            block(refresh(staleToken = token))
        }
    }

    private suspend fun validAccessToken(): String {
        val session = store.load() ?: throw ApiException(401, "Not signed in.")
        return if (session.accessExpiresAt - EXPIRY_MARGIN_MS > clock()) {
            session.accessToken
        } else {
            refresh(staleToken = session.accessToken)
        }
    }

    private suspend fun refresh(staleToken: String): String = refreshLock.withLock {
        val session = store.load() ?: throw ApiException(401, "Not signed in.")

        // Someone refreshed while this caller waited for the lock: use their result rather
        // than spending the already-rotated refresh token.
        if (session.accessToken != staleToken && session.accessExpiresAt - EXPIRY_MARGIN_MS > clock()) {
            return@withLock session.accessToken
        }

        val refreshed = try {
            api.refresh(session.refreshToken)
        } catch (e: ApiException) {
            if (e.status == 401) {
                store.clear()
                _state.value = AccountState.SignedOut("Your TrackBoard sign-in expired. Sign in again.")
            }
            throw e
        }
        adopt(refreshed).accessToken
    }

    /** Persists first, then publishes: the new refresh token must survive a crash right here. */
    private fun adopt(response: AuthResponse): StoredSession {
        val session = StoredSession(
            accessToken = response.accessToken,
            accessExpiresAt = parseInstantMillis(response.expiresAt) ?: (clock() + FALLBACK_LIFETIME_MS),
            refreshToken = response.refreshToken,
            userId = response.user.id,
            email = response.user.email,
            displayName = response.user.displayName,
        )
        store.save(session)
        _state.value = stateOf(session)
        return session
    }

    private companion object {
        /** Refresh a little early, so a token does not expire between check and use. */
        const val EXPIRY_MARGIN_MS = 60_000L

        /** Used only if the server's expiry is unparseable; well inside its 60-minute lifetime. */
        const val FALLBACK_LIFETIME_MS = 15 * 60_000L

        fun stateOf(session: StoredSession?): AccountState =
            if (session == null) AccountState.SignedOut()
            else AccountState.SignedIn(session.userId, session.email, session.displayName)

        fun parseInstantMillis(value: String): Long? =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                // .NET may send an offset rather than "Z", which Instant.parse rejects before API 34.
                ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }
}
