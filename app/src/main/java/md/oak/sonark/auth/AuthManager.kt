package md.oak.sonark.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.suspendCancellableCoroutine
import md.oak.sonark.BuildConfig
import kotlin.coroutines.resume

sealed class SignInResult {
    data class Success(val credential: GoogleIdTokenCredential) : SignInResult()
    data class Failure(val type: String, val message: String?) : SignInResult()
}

class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    
    @Volatile
    private var lastAccessToken: String? = null

    fun getLastKnownToken(): String? = lastAccessToken

    fun updateDriveService(accessToken: String?, accountEmail: String?) {
        lastAccessToken = accessToken
        // Logic for Google Drive SDK (if still used elsewhere)
    }

    suspend fun signIn(): SignInResult {
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            
            if (credential is GoogleIdTokenCredential) {
                SignInResult.Success(credential)
            } else if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    SignInResult.Success(googleIdTokenCredential)
                } catch (e: Exception) {
                    SignInResult.Failure("PARSING_ERROR", "Failed to parse GoogleIdTokenCredential: ${e.message}")
                }
            } else {
                SignInResult.Failure("UNKNOWN_TYPE", "Expected GoogleIdTokenCredential but got ${credential.type}")
            }
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "SignIn failed: ${e.type} - ${e.message}", e)
            SignInResult.Failure(e.type, e.message)
        }
    }

    fun getAuthorizationClient() = Identity.getAuthorizationClient(context)

    fun createAuthorizationRequest(email: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_READONLY)))
            .requestOfflineAccess(WEB_CLIENT_ID)
        
        email?.let { 
            builder.setAccount(android.accounts.Account(it, "com.google"))
        }
        
        return builder.build()
    }

    suspend fun signOut(onComplete: () -> Unit) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            lastAccessToken = null
            onComplete()
        } catch (e: Exception) {
            Log.e("AuthManager", "SignOut failed", e)
        }
    }

    suspend fun silentSignIn(email: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val authRequest = createAuthorizationRequest(email)
            getAuthorizationClient().authorize(authRequest)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        continuation.resume(null)
                    } else {
                        lastAccessToken = result.accessToken
                        continuation.resume(result.accessToken)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AuthManager", "Silent sign-in failed", e)
                    continuation.resume(null)
                }
        }
    }
}
