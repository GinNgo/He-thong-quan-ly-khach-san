# LuxeStay Logo Refresh Brief

Status: Planned

## Direction

Create an original luxury-hospitality identity inspired by the supplied reference: a refined emblem, balanced symmetry and restrained navy/champagne-gold contrast. The reference is a visual direction only; do not copy its lettering, layout, watermark or ornamental details.

The current LuxeStay mark is a blue building-shaped `H`. The refresh should retain the hospitality cue while making the mark more distinctive, legible and usable across the public site, customer account, admin portal and favicon.

## Recommended concept

- Combination mark with a compact `LS` or hotel-arch monogram, a subtle crown/roof cue and an optional laurel frame.
- Deep navy as the primary dark surface, champagne gold as the premium accent and white as the inverse mark.
- Cormorant for the display lockup and existing Montserrat tokens for supporting UI text.
- Clean geometry and limited ornament so the mark remains recognizable at small sizes.
- Map the palette through the existing global `--hotel-heading`, `--hotel-primary` and `--hotel-gold` tokens; any champagne-gold refinement belongs in the shared token layer, never in component-local CSS.

## Required deliverables

- Primary horizontal lockup: emblem plus `LuxeStay Hotels` and optional tagline.
- Stacked lockup for login/marketing surfaces.
- Standalone emblem for header, footer, chat/support and compact mobile layouts.
- Monochrome dark, monochrome light and inverse variants.
- Favicon/app mark that remains recognizable at 16px and 32px.
- Source SVG with a clean `viewBox`, optimized paths and transparent background; PNG/WebP fallbacks only where a raster asset is required.

## Acceptance criteria

- Original artwork is approved against the brief and has no third-party watermark or copied lettering.
- Logo remains clear in full color, one color, grayscale and reversed-on-dark modes.
- Contrast is checked on light and dark surfaces; decorative details do not carry the only meaning.
- Header, footer, login, admin sidebar, management shell and favicon use the same asset family without layout shift or overflow at 375, 768, 1024 and 1440px.
- Angular tests/build pass and the browser review records visual consistency, keyboard focus visibility and reduced-motion neutrality.
