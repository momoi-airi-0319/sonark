# Implementation Plan - Custom Wavy Progress Bar

Modify the `WavySlider` component to match the style shown in the provided image: a vertical bar thumb and waves emanating from the thumb position.

## Proposed Changes

### [UI Components]

#### [MODIFY] [WavySlider.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/components/WavySlider.kt)
- Update imports to include `CornerRadius` and `Size`.
- Refactor `Canvas` drawing logic:
    - Change thumb from a circle to a vertical rounded rectangle (capsule).
    - Adjust wave math so the phase is relative to the thumb position (`activeWidth`), making it the "source".
    - Simplify drawing by using `drawLine` for the inactive part instead of a `Path`.
    - Remove complex dampening logic to keep the code clean as requested.
    - Standardize stroke widths and amplitudes to match the visual reference.

## Verification Plan

### Automated Tests
- N/A (UI visual change)

### Manual Verification
- Deploy the app and navigate to the `PlayerScreen`.
- Observe the progress bar:
    - The thumb should be a vertical bar.
    - When playing, the wave should appear to flow from the thumb towards the left.
    - Dragging the thumb should update the wave's "source" point.
    - When paused, the wave should flatten.
