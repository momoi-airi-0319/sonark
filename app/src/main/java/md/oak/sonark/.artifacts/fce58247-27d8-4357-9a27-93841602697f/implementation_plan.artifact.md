# Add Home Tab alongside Library

The goal is to create a new "Home" tab that is at the same level as the "Library" tab in the bottom navigation.

## Proposed Changes

### Navigation

#### [MODIFY] [Destinations.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/navigation/Destinations.kt)
- Add `HomeKey` data object.

#### [MODIFY] [NavGraph.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/navigation/NavGraph.kt)
- Add entry for `HomeKey` in `createNavEntryProvider`.

### UI Screens

#### [NEW] [HomeScreen.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/HomeScreen.kt)
- Create a simple placeholder screen for the "Home" tab.

### Main Activity

#### [MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)
- Add `HomeKey` to `topLevelRoutes`.
- Update `SonarkBottomBar` to include the "Home" item.

## Verification Plan

### Automated Tests
- N/A (UI change)

### Manual Verification
- Deploy the app.
- Check if the "Home" tab appears in the bottom bar.
- Verify that clicking "Home" switches to the placeholder screen.
- Verify that "Library" still works as expected.
