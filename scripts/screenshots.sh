#!/usr/bin/env bash
# Installs the debug APK on a running emulator, opens each screen and saves a screenshot.
# Fails if the app crashes. Used by .github/workflows/screenshots.yml.
set -u
OUT=${1:-screenshots}
PKG=com.tailorsfit.app
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c

shot() { # name route [extra adb input commands...]
  local name=$1 route=$2; shift 2
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es route "$route" --es customer "Lakshmi" >/dev/null
  sleep 6
  for cmd in "$@"; do adb shell "$cmd"; sleep 2; done
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "captured $name"
}

shot 01-home home
shot 02-catalog catalog/blouse
shot 03-measurements measure/blouse_round_classic
shot 04-measurements-scrolled measure/blouse_round_classic "input swipe 540 1900 540 500 400" "input swipe 540 1900 540 500 400"
shot 05-pattern pattern/blouse_round_classic
shot 06-pattern-details pattern/blouse_round_classic "input swipe 540 1900 540 400 400"
shot 07-pattern-sweetheart pattern/blouse_sweetheart
shot 08-projector projector/blouse_round_classic
shot 09-customers customers

if adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG" > "$OUT/crash.txt"; then
  adb logcat -d | grep -A 30 "FATAL EXCEPTION" >> "$OUT/crash.txt"
  echo "App crashed:"; cat "$OUT/crash.txt"
  exit 1
fi
rm -f "$OUT/crash.txt"
