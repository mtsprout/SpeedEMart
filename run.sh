#!/bin/sh
# Build and launch QUIK-STOP: Night Shift (needs JDK 17+)
cd "$(dirname "$0")" && mkdir -p out && javac -d out src/Game.java && java -cp out Game
