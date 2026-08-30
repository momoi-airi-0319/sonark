# Cleanup Project After Refactoring

The goal is to remove obsolete files, empty directories, and redundant artifacts left behind after the recent major refactoring.

## User Review Required

> [!IMPORTANT]
> This plan will delete several test files that appear to be obsolete because they refer to classes (like `SessionManager`) that no longer exist in the main source tree. Please verify if these tests should be updated instead of deleted.
>
> [!WARNING]
> I will also delete `app/src/main/jniLibs/arm64-v8a/libsonark_sdk.so` because the new UniFFI-based Kotlin code expects `libuniffi_sonark_sdk.so`. If you still need the old library for some reason, please let me know.

## Proposed Changes

### Agent Artifacts & Obsolete Plans

#### [DELETE] [.artifacts/](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/.artifacts)
- This directory inside the Java source tree contains agent implementation plans and tasks which do not belong in production code.

#### [DELETE] [implementation_plan.artifact.md](file:///C:/Users/airi/wksp/Sonark/implementation_plan.artifact.md)
- Old implementation plan at the project root.

---

### Refactoring Leftovers (Source & Tests)

#### [DELETE] [download/](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/download)
- Empty directory after the removal of the old `DownloadManager.kt`.

#### [DELETE] [download/](file:///C:/Users/airi/wksp/Sonark/app/src/test/java/md/oak/sonark/data/download)
- Contains `DownloadProgressTest.kt`, which tested logic from the now-deleted `DownloadManager.kt`.

#### [DELETE] [DriveSyncIntegrationTest.kt](file:///C:/Users/airi/wksp/Sonark/app/src/androidTest/java/md/oak/sonark/data/DriveSyncIntegrationTest.kt)
- Obsolete test that refers to the deleted `SessionManager`.

#### [DELETE] [SessionSwitchStressTest.kt](file:///C:/Users/airi/wksp/Sonark/app/src/androidTest/java/md/oak/sonark/ui/SessionSwitchStressTest.kt)
- Obsolete test that refers to the deleted `SessionManager`.

#### [DELETE] [TestConfigLoader.kt](file:///C:/Users/airi/wksp/Sonark/app/src/androidTest/java/md/oak/sonark/utils/TestConfigLoader.kt)
- Unused helper after the removal of the above tests.

---

### Redundant / Obsolete Build Artifacts

#### [DELETE] [generated/](file:///C:/Users/airi/wksp/Sonark/rust-sdk/generated)
- Redundant UniFFI generated code that is already gitignored and present in `src/main/kotlin`.

#### [DELETE] [libsonark_sdk.so](file:///C:/Users/airi/wksp/Sonark/app/src/main/jniLibs/arm64-v8a/libsonark_sdk.so)
- Obsolete library name. The current Kotlin bindings load `uniffi_sonark_sdk`.

#### [DELETE] [libjnidispatch.so](file:///C:/Users/airi/wksp/Sonark/app/src/main/jniLibs/x86_64/libjnidispatch.so)
- Duplicate of the same library in `:rust-sdk`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project still builds successfully after deletions.

### Manual Verification
- Verify that no other files import the deleted test classes.
- Check that the project structure looks cleaner in the IDE.
