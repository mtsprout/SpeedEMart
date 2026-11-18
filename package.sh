#!/bin/sh
# Builds dist/SpeedEMart.app (bundled Java runtime, no install needed) and dist/SpeedEMart.dmg
set -e
cd "$(dirname "$0")"
rm -rf build dist && mkdir -p build/classes dist
javac --release 17 -d build/classes src/Game.java
printf 'Main-Class: Game\n' > build/manifest.txt
jar cfm build/SpeedEMart.jar build/manifest.txt -C build/classes .
mkdir -p build/input && cp build/SpeedEMart.jar build/input/
jlink --add-modules java.desktop --strip-debug --no-man-pages --no-header-files --output build/runtime
jpackage --type app-image --name SpeedEMart --app-version 1.1 \
  --input build/input --main-jar SpeedEMart.jar --main-class Game \
  --runtime-image build/runtime --dest dist \
  --java-options "-Dapple.awt.application.name=SPEED-E-MART"
jpackage --type dmg --name SpeedEMart --app-version 1.1 --app-image dist/SpeedEMart.app --dest dist 2>/dev/null \
  || (cd dist && ditto -c -k --keepParent SpeedEMart.app SpeedEMart.zip)
ls -lh dist
