package com.example.trackpro.online

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `signing in stores the session and publishes who is signed in`() = runBlocking {
        val store = InMemoryTokenStore()
        val auth = AuthRepository(FakeApi(), store)

        auth.signIn("  me@example.com ", "correct-horse-battery")

        assertEquals("refresh-1", store.session!!.refreshToken)
        val state = auth.state.value as AccountState.SignedIn
        assertEquals("Me", state.displayName)
    }

    @Test
    fun `a 401 refreshes once and retries with the new token`() = runBlocking {
        val api = FakeApi()
        val store = signedInStore()
        val auth = AuthRepository(api, store)
        val tokensUsed = mutableListOf<String>()

        val result = auth.authorized { token ->
            tokensUsed += token
            if (token == "access-1") throw ApiException(401, "expired")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("access-1", "access-2"), tokensUsed)
        // The rotated refresh token is what is kept; the spent one would now be a theft signal.
        assertEquals("refresh-2", store.session!!.refreshToken)
    }

    @Test
    fun `an access token about to expire is refreshed before it is used`() = runBlocking {
        val now = 1_000_000L
        val store = signedInStore(expiresAt = now + 30_000) // inside the one-minute margin
        val auth = AuthRepository(FakeApi(), store, clock = { now })

        val used = auth.authorized { it }

        assertEquals("access-2", used)
    }

    @Test
    fun `concurrent callers share one refresh instead of racing the rotated token`() = runBlocking {
        val api = FakeApi().apply {
            refresh = {
                delay(50) // long enough for every caller to arrive while it is in flight
                authResponse("access-2", "refresh-2")
            }
        }
        val now = 1_000_000L
        val auth = AuthRepository(api, signedInStore(expiresAt = now), clock = { now })

        val tokens = (1..5).map { async { auth.authorized { it } } }.awaitAll()

        assertEquals(List(5) { "access-2" }, tokens)
        assertEquals(1, api.calls.count { it.startsWith("refresh") })
    }

    @Test
    fun `a rejected refresh signs the driver out and says why`() = runBlocking {
        val api = FakeApi().apply { refresh = { throw ApiException(401, "revoked") } }
        val store = signedInStore()
        val auth = AuthRepository(api, store)

        try {
            auth.authorized { token -> if (token == "access-1") throw ApiException(401, "expired") else token }
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertNull(store.session)
        val state = auth.state.value as AccountState.SignedOut
        assertNotNull(state.notice)
    }

    @Test
    fun `a refresh that fails for a network reason keeps the driver signed in`() = runBlocking {
        val api = FakeApi().apply { refresh = { throw NetworkException("offline") } }
        val store = signedInStore()
        val auth = AuthRepository(api, store)

        try {
            auth.authorized { token -> if (token == "access-1") throw ApiException(401, "expired") else token }
            fail("expected NetworkException")
        } catch (e: NetworkException) {
            // expected
        }

        assertNotNull("being offline must not cost the driver their sign-in", store.session)
        assertTrue(auth.isSignedIn)
    }

    @Test
    fun `signing out works offline`() = runBlocking {
        val api = object : FakeApi() {
            override suspend fun logout(accessToken: String, refreshToken: String) = throw NetworkException("offline")
        }
        val store = signedInStore()
        val auth = AuthRepository(api, store)

        auth.signOut()

        assertNull(store.session)
        assertTrue(auth.state.value is AccountState.SignedOut)
    }
}
