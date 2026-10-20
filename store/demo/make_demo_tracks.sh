#!/usr/bin/env bash
# Creates neutral demo tracks for store screenshots: invented titles and artists, abstract covers
# and soft synthesized chords, so no commercial music or artwork appears. Nothing here is part of
# the app; the files are copied to a phone or emulator only while taking screenshots.
#
# Needs ffmpeg (with libmp3lame) and ImageMagick. Run from the repository root:
#   bash store/demo/make_demo_tracks.sh
# Then copy them to the device (see store/SCREENSHOTS.md):
#   adb push store/demo/out "/sdcard/Music/Veyra Demo"
set -euo pipefail
OUT="store/demo/out"
mkdir -p "$OUT"

# title | artist | album | seconds | base frequency (Hz) | cover hue (0-360)
TRACKS=(
  "Quiet Harbour|Lumen Ensemble|Coastal Studies|214|220|200"
  "Amber Hours|Mara Solis|Small Hours|187|247|35"
  "Salt and Cedar|Lumen Ensemble|Coastal Studies|252|196|160"
  "Night Ferry|The Glass Pines|Night Ferry|231|175|250"
  "Paper Lanterns|Ione Varga|Lanterns|169|262|15"
  "Slow Tide|Lumen Ensemble|Coastal Studies|276|165|190"
  "Northern Window|The Glass Pines|Night Ferry|203|208|280"
  "Linen Sky|Mara Solis|Small Hours|245|233|55"
  "Copper Rain|Ione Varga|Lanterns|198|185|25"
  "Late Orchard|Mara Solis|Small Hours|222|294|95"
)

n=0
for entry in "${TRACKS[@]}"; do
  IFS='|' read -r title artist album seconds freq hue <<<"$entry"
  n=$((n + 1))
  cover="$OUT/cover-$n.png"
  # Abstract cover: two-tone gradient with a soft circle, in the track's hue.
  magick -size 800x800 "gradient:hsl($hue,45%,32%)-hsl($(( (hue + 40) % 360 )),35%,14%)" \
    \( -size 800x800 xc:none -fill "hsla($hue,60%,75%,0.35)" -draw "circle 520,300 520,520" -blur 0x30 \) \
    -composite "$cover"
  # A soft major chord with slow fades, quiet enough not to clip.
  third=$(echo "$freq * 1.25" | bc -l); fifth=$(echo "$freq * 1.5" | bc -l)
  ffmpeg -loglevel error -y \
    -f lavfi -i "sine=frequency=$freq:duration=$seconds" \
    -f lavfi -i "sine=frequency=$third:duration=$seconds" \
    -f lavfi -i "sine=frequency=$fifth:duration=$seconds" \
    -i "$cover" \
    -filter_complex "[0][1][2]amix=inputs=3,volume=0.6,afade=t=in:d=3,afade=t=out:st=$((seconds - 4)):d=4[a]" \
    -map "[a]" -map 3 -c:a libmp3lame -b:a 128k -c:v copy -id3v2_version 3 \
    -metadata title="$title" -metadata artist="$artist" -metadata album="$album" -metadata track="$n" \
    -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
    "$OUT/$(printf '%02d' "$n") - $artist - $title.mp3"
  rm "$cover"
done
echo "Created $n demo tracks in $OUT"
