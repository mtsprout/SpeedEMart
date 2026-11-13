#!/bin/sh
# Builds dist/QuikStop.app (bundled Java runtime, no install needed) and dist/QuikStop.dmg
set -e
cd "$(dirname "$0")"
rm -rf build dist && mkdir -p build/classes dist
javac --release 17 -d build/classes src/Game.java
printf 'Main-Class: Game\n' > build/manifest.txt
jar cfm build/QuikStop.jar build/manifest.txt -C build/classes .
jlink --add-modules java.desktop --strip-debug --no-man-pages --no-header-files --output build/runtime
jpackage --type app-image --name QuikStop --app-version 1.0 \
  --input build --main-jar QuikStop.jar --main-class Game \
  --runtime-image build/runtime --dest dist \
  --java-options "-Dapple.awt.application.name=QuikStop"
jpackage --type dmg --name QuikStop --app-version 1.0 --app-image dist/QuikStop.app --dest dist 2>/dev/null \
  || (cd dist && ditto -c -k --keepParent QuikStop.app QuikStop.zip)
ls -lh dist
