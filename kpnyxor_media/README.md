# KPNYXOR — Facebook comic media hosting

This is a separate public **staging branch** for KPNYXOR Facebook multi-photo publishing. The original `main` branch remains unchanged.

## First batch target (temporary)
- 3 distinct Burmese comedy posts
- 25 separate ordered images per post = 75 images
- Each photo post contains the whole story and a natural Burmese caption
- **Do not publish** image collages, repeated frames, placeholder images or QA-failed prototypes
- Old automation targeting 3 × 30 images/hour is paused pending the user's later decision. Videos remain paused.

## Publishing connection
- Make EU team: `3638136`
- Make on-demand (inactive) 25-photo scenario: `8597402`
- Facebook Page: Kpnyxor, page ID `1448492685003638`
- Image URLs must be publicly readable PNG/JPEG. Store no tokens, passwords, or private images.

## Hosting layout
- `kpnyxor_media/public/albums/story-001/01.png` through `25.png`
- `kpnyxor_media/public/albums/story-002/01.png` through `25.png`
- `kpnyxor_media/public/albums/story-003/01.png` through `25.png`

After upload and checks, the public raw URL format is:

`https://raw.githubusercontent.com/gf835958-code/Kp/kpnyxor-media-host/kpnyxor_media/public/albums/story-001/01.png`

Only exact existing paths will work. Do not assume files are uploaded until verified.

## Release gate
A post is publishable only after 25 ordered images exist, story/character/dialogue and mobile-size text are reviewed, public URLs are fetchable, the destination Page is verified, and prior Facebook posts are checked to avoid duplicates.

**Status: infrastructure-only; 0/3 Facebook posts published.**
