#!/bin/sh
# Build and launch SPEED-E-MART: Night Shift (needs JDK 17+)
# Playtest a later night:  NIGHT=5 ./run.sh   (such runs don't change your saved records)
cd "$(dirname "$0")" && mkdir -p out && javac -d out src/Game.java && java -Dspeedemart.night="${NIGHT:-1}" -cp out Game
