package md.oak.sonark.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import md.oak.sonark.BuildConfig
import md.oak.sonark.data.Dependencies

sealed class SignInResult {
    data class Success(val credential: GoogleIdTokenCredential) : SignInResult()
    data class Failure(val type: String, val message: String?) : SignInResult()
}

class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun updateDriveService(accessToken: String?, accountEmail: String?) {
        if (accessToken != null && accountEmail != null) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    context, listOf(DriveScopes.DRIVE_READONLY)
                ).apply {
                    selectedAccountName = accountEmail
                }
                Dependencies.driveProvider.credential = credential
            } catch (e: Exception) {
                Log.e("Sonark", "Error updating drive service", e)
                Dependencies.driveProvider.credential = null
            }
        } else {
            Dependencies.driveProvider.credential = null
        }
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
            
            Log.d("AuthManager", "Received credential type: ${credential.type}")
            
            if (credential is GoogleIdTokenCredential) {
                SignInResult.Success(credential)
            } else if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    SignInResult.Success(googleIdTokenCredential)
                } catch (e: Exception) {
                    SignInResult.Failure("PARSING_ERROR", "Failed to parse GoogleIdTokenCredential: ${e.message}")
                }
            } else {
                SignInResult.Failure("UNKNOWN_TYPE", "Got: ${credential.type}")
            }
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "SignIn failed: ${e.type} - ${e.message}", e)
            SignInResult.Failure(e.type, e.message)
        }
    }

    fun getAuthorizationClient() = Identity.getAuthorizationClient(context)

    fun createAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_READONLY)))
            .requestOfflineAccess(WEB_CLIENT_ID)
            .build()
    }

    suspend fun signOut(onComplete: () -> Unit) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            updateDriveService(null, null)
            onComplete()
        } catch (e: Exception) {
            Log.e("AuthManager", "SignOut failed", e)
        }
    }
}
