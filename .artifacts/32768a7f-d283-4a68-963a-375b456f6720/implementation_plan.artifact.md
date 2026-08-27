# Change Library Page to Apple Music Style

This plan outlines the changes to transform the Library page into a layout similar to Apple Music, while maintaining Material Design and keeping top-level controls unchanged.

## Proposed Changes

### [Component] UI Models
#### [MODIFY] [AlbumUiItem.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/model/AlbumUiItem.kt)
- Update `GridItem` to include the artist name below the album title.
- Implement logic to show "Various Artists" if the album artist is empty or "Unknown Artist".

### [Component] Library Screen
#### [MODIFY] [LibraryGrid.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/library/LibraryGrid.kt)
- Update `LibraryGrid` to include the navigation list and "Recently Added" section header.
- Use `item` with `span` in `LazyVerticalGrid` to add these non-grid elements at the top.
- Navigation items will include: Playlists, Artists, Albums, Songs, and Downloaded.

## Verification Plan

### Automated Tests
- Check if `AlbumUiItem.from(album).artist` handles empty cases correctly. (Manual inspection of code first).

### Manual Verification
- Deploy the app and navigate to the Library screen.
- Verify the new navigation list appears at the top.
- Verify the "Recently Added" header is present.
- Verify the album grid now shows artist names and uses "Various Artists" where appropriate.
- Ensure the floating top bar still works as expected.
