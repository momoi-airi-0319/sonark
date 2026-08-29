# Implementation Plan - Migrate to Credential Manager and AuthorizationClient

Migrate the authentication and authorization flow from the deprecated `GoogleSignIn` API to the modern `Credential Manager` and `AuthorizationClient` APIs. This will provide a more seamless, bottom-sheet-based login experience and future-proof the app's identity logic.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/airi/wksp/Sonark/gradle/libs.versions.toml)
- Add versions and library definitions for `androidx.credentials` and `googleid`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/airi/wksp/Sonark/app/build.gradle.kts)
- Add the new dependencies to the app module.

---

### Authentication & Authorization Layer

#### [MODIFY] [AuthManager.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/auth/AuthManager.kt)
- Replace `GoogleSignInClient` with `CredentialManager`.
- Implement `signIn` using `GetCredentialRequest` and `GetGoogleIdTokenOption`.
- Implement `authorizeDrive` using `Identity.getAuthorizationClient`.
- Update `signOut` to use `credentialManager.clearCredentialState()`.
- Update `updateDriveService` to handle the new authorization result.

---

### UI Layer

#### [MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)
- Update the sign-in launcher to handle the new `Credential Manager` flow.
- Coordinate authentication (who the user is) and authorization (Drive access).
- Update the `LoginScreen` and `AccountPopDialog` interactions to use the new `AuthManager` methods.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.
- (Optional) Run existing UI tests if available.

### Manual Verification
1. Launch the app.
2. Verify the `LoginScreen` appears.
3. Click "Sign in with Google" and verify the Credential Manager bottom sheet appears.
4. Select an account and verify the app requests Drive authorization.
5. Confirm that the library still syncs correctly after signing in.
6. Test "Add another account" and verify it works with the new flow.
7. Test "Sign out" and verify the credentials are cleared.
