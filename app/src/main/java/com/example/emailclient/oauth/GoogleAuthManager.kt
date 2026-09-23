package com.example.emailclient.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.*
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Wraps the AppAuth library for Google's OAuth2 flow ("Sign in with
 * Google"), scoped to full Gmail access (https://mail.google.com/) so the
 * resulting access token can be used for IMAP/SMTP (via SASL XOAUTH2), not
 * just Google's own REST APIs.
 *
 * IMPORTANT: replace CLIENT_ID below if you register a different OAuth
 * client in Google Cloud Console. It must be an "Android" type client
 * matching this app's applicationId + signing certificate SHA-1.
 */
object GoogleAuthManager {

    private const val CLIENT_ID = "177901659788-gmh05t67ae3tegl1pj13i0j5cjajgl2s.apps.googleusercontent.com"
    private const val REDIRECT_URI = "com.example.emailclient:/oauth2redirect"

    // Full Gmail scope is required for IMAP/SMTP access via XOAUTH2 — the
    // narrower gmail.readonly/gmail.send scopes only work with Gmail's REST
    // API, not raw IMAP/SMTP.
    private const val SCOPES = "https://mail.google.com/ email profile openid"

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    /** Builds the Intent that launches the Google sign-in / consent browser screen. */
    fun buildSignInIntent(context: Context): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope(SCOPES)
            .setPrompt("consent select_account") // always let the user pick/confirm the account
            .build()

        val authService = AuthorizationService(context)
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Call this from the Activity Result callback after the sign-in browser
     * screen returns. Exchanges the authorization code for access + refresh
     * tokens and returns a serialized AuthState (safe to store — contains
     * the refresh token) plus the signed-in email address.
     */
    suspend fun handleSignInResult(
        context: Context,
        data: Intent
    ): Result<Pair<String, String>> { // (authStateJson, emailAddress)
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(error ?: Exception("Google sign-in was cancelled"))
        }

        return suspendCancellableCoroutine { cont ->
            val authService = AuthorizationService(context)
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                authService.dispose()
                if (tokenResponse == null) {
                    cont.resume(Result.failure(exception ?: Exception("Token exchange failed")))
                    return@performTokenRequest
                }
                val authState = AuthState(response, exception)
                authState.update(tokenResponse, exception)

                val email = tokenResponse.idToken?.let { extractEmailFromIdToken(it) }
                if (email == null) {
                    cont.resume(Result.failure(Exception("Couldn't read email address from Google sign-in")))
                    return@performTokenRequest
                }
                cont.resume(Result.success(authState.jsonSerializeString() to email))
            }
        }
    }

    /**
     * Returns a currently-valid access token, transparently refreshing via
     * the stored refresh token if the previous access token has expired.
     * Returns the (possibly updated) AuthState JSON alongside the token, so
     * callers can persist it back to CredentialStore if it changed.
     */
    suspend fun getFreshAccessToken(
        context: Context,
        authStateJson: String
    ): Result<Pair<String, String>> { // (accessToken, updatedAuthStateJson)
        val authState = AuthState.jsonDeserialize(authStateJson)
        return suspendCancellableCoroutine { cont ->
            val authService = AuthorizationService(context)
            authState.performActionWithFreshTokens(authService) { accessToken, _, exception ->
                authService.dispose()
                if (accessToken == null) {
                    cont.resume(Result.failure(exception ?: Exception("Couldn't refresh Google access token")))
                } else {
                    cont.resume(Result.success(accessToken to authState.jsonSerializeString()))
                }
            }
        }
    }

    /** Decodes the (unverified — fine here, we only read our own fresh token) JWT payload for the email claim. */
    private fun extractEmailFromIdToken(idToken: String): String? = try {
        val payload = idToken.split(".")[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val json = JSONObject(String(decoded, Charsets.UTF_8))
        if (json.has("email")) json.getString("email") else null
    } catch (e: Exception) {
        null
    }
}
