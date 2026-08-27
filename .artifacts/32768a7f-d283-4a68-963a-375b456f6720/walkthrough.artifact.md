# Library Page Update - Apple Music Style

The Library page has been updated to follow an Apple Music-inspired layout while maintaining Material Design principles and keeping existing top-level controls.

## Key Changes

### 1. Navigation List
A new navigation section has been added to the top of the library content, providing quick access to:
- **Playlists**
- **Artists**
- **Albums**
- **Songs**
- **Downloaded**

Each item features a leading icon, a label, and a trailing chevron for a clear interaction model.

### 2. "Recently Added" Section
The album grid is now preceded by a "Recently Added" header, clearly demarcating the different sections of the library.

### 3. Album Grid Enhancements
- **Artist Display**: The album artist is now displayed below the album title in the grid.
- **"Various Artists" Logic**: If an album's artist is unknown or blank, it now defaults to "Various Artists" as requested.

## Files Modified
- [AlbumUiItem.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/model/AlbumUiItem.kt): Updated `GridItem` and `ListItem` to handle artist display and "Various Artists" logic.
- [LibraryGrid.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/library/LibraryGrid.kt): Implemented the navigation list and "Recently Added" header using `LazyVerticalGrid`'s span capability.

## Verification
- Verified code structure and imports.
- Checked that the navigation items are correctly integrated into the scrollable grid.
- Ensured the "Various Artists" logic is applied consistently.
