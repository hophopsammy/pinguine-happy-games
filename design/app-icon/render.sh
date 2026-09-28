#!/bin/sh
# Renders the app icon SVGs into the Xcode asset catalog.
# Needs librsvg and ImageMagick: brew install librsvg imagemagick
set -e
cd "$(dirname "$0")"
OUT=../../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset

# The App Store rejects an app icon with an alpha channel, so the default icon is flattened.
rsvg-convert -w 1024 -h 1024 app-icon.svg | magick - -alpha off "$OUT/AppIcon.png"
# Dark and tinted keep a transparent background: iOS draws its own backdrop behind them.
rsvg-convert -w 1024 -h 1024 -o "$OUT/AppIcon-Dark.png" app-icon-dark.svg
rsvg-convert -w 1024 -h 1024 app-icon-tinted.svg | magick - -colorspace Gray "$OUT/AppIcon-Tinted.png"
