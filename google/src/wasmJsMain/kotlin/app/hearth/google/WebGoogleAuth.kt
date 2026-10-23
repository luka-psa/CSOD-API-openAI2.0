@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.google

import app.hearth.core.model.nowMillis
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google sign-in in the browser (Google Identity Services token client, [clientId] from the build).
 * Browsers only allow its popup right after a tap, so background work uses the token from the last
 * sign-in while it is valid (about an hour) and otherwise waits for the next tap.
 */
class WebGoogleAuth(private val clientId: String) : GoogleAuth {
    override val available = clientId.isNotBlank()
    private val cache = mutableMapOf<String, Pair<String, Long>>()

    init {
        if (available) loadGis()
    }

    override suspend fun token(scope: String, interactive: Boolean): String? {
        cache[scope]?.let { (token, until) -> if (until > nowMillis()) return token }
        if (!interactive || !available) return null
        val (token, seconds) = suspendCancellableCoroutine { cont ->
            requestToken(clientId, scope) { t, s -> if (cont.isActive) cont.resume(t to s) }
        }
        if (token.isEmpty()) return null
        cache[scope] = token to (nowMillis() + (seconds - 60).coerceAtLeast(60) * 1000L)
        return token
    }
}

private fun loadGis(): Unit = js(
    """{
        if (window.google && google.accounts && google.accounts.oauth2) return;
        if (document.getElementById('gis-client')) return;
        const s = document.createElement('script');
        s.id = 'gis-client';
        s.src = 'https://accounts.google.com/gsi/client';
        s.async = true;
        document.head.appendChild(s);
    }""",
)

private fun requestToken(clientId: String, scope: String, done: (String, Int) -> Unit): Unit = js(
    """{
        if (!(window.google && google.accounts && google.accounts.oauth2)) { done('', 0); return; }
        const client = google.accounts.oauth2.initTokenClient({
            client_id: clientId,
            scope: scope,
            callback: (r) => done(r && r.access_token ? r.access_token : '', r && r.expires_in ? Number(r.expires_in) : 0),
            error_callback: () => done('', 0),
        });
        client.requestAccessToken({ prompt: '' });
    }""",
)
