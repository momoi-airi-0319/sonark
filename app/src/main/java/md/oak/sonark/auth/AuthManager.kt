package md.oak.sonark.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import md.oak.sonark.data.Dependencies

class AuthManager(private val context: Context) {

    val googleSignInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
        .build()

    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    fun updateDriveService(account: GoogleSignInAccount?) {
        if (account != null) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    context, listOf(DriveScopes.DRIVE_READONLY)
                ).setSelectedAccount(account.account)
                
                Dependencies.driveProvider.credential = credential
            } catch (e: Exception) {
                Log.e("Sonark", "Error updating drive service", e)
                Dependencies.driveProvider.credential = null
            }
        } else {
            Dependencies.driveProvider.credential = null
        }
    }

    fun handleSignInResult(
        data: Intent?,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onError: (ApiException) -> Unit
    ) {
        @Suppress("DEPRECATION")
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                updateDriveService(account)
                onSuccess(account)
            } else {
                onError(ApiException(com.google.android.gms.common.api.Status.RESULT_INTERNAL_ERROR))
            }
        } catch (e: ApiException) {
            updateDriveService(null)
            onError(e)
        }
    }

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            updateDriveService(null)
            onComplete()
        }
    }
    
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        @Suppress("DEPRECATION")
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}
