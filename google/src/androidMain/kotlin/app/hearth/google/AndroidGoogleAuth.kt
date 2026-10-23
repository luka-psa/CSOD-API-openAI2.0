package app.hearth.google

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google sign-in on the tablet (Google Identity Services' AuthorizationClient). No client id or secret
 * in the app: Google knows Hearth by its package name and signing key (see docs/GOOGLE.md).
 * Tokens are refreshed by Google Play services; Hearth never stores them.
 */
class AndroidGoogleAuth(private val context: Context) : GoogleAuth {
    override val available = true

    /** Set by the activity while it is on screen: shows Google's account/consent screen, returns its result. */
    var consent: (suspend (IntentSender) -> Intent?)? = null

    override suspend fun token(scope: String, interactive: Boolean): String? {
        val client = Identity.getAuthorizationClient(context)
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()
        val result = runCatching { client.authorize(request).await() }.getOrNull() ?: return null
        if (!result.hasResolution()) return result.accessToken
        if (!interactive) return null
        val sender = result.pendingIntent?.intentSender ?: return null
        val data = consent?.invoke(sender) ?: return null
        return runCatching { client.getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
    }

    private suspend fun Task<AuthorizationResult>.await(): AuthorizationResult? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }
}
