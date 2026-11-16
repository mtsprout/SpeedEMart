# SPEED-E-MART: Night Shift

A cartoon convenience-store simulator built to raise your blood pressure. Survive an
8-hour night shift (10 PM to 6 AM): ring up customers, make exact change, restock,
mop, and keep your health up and your rage down. Each night you survive gets 10% harder.

The game is a single Java file (`src/Game.java`) with no dependencies. There is also a
Roblox port in `roblox/` (see [Roblox](#roblox)).

## Requirements

**JDK 17 or newer** (the game is developed on Java 21). You need the JDK, not just the
JRE, because the game is compiled from source.

- **Mac:** `brew install openjdk@21`, or download from <https://adoptium.net>
- **Windows:** `winget install EclipseAdoptium.Temurin.21.JDK`, or download the
  installer from <https://adoptium.net>

Check it worked by opening a new terminal and running `javac -version`.

## Run it

### Mac

```sh
./run.sh
```

That compiles `src/Game.java` into `out/` and launches the game.

### Windows

`run.sh` is a shell script, so on Windows run the two commands directly in
PowerShell or Command Prompt, from the project folder:

```bat
mkdir out
javac -d out src\Game.java
java -cp out Game
```

(If `mkdir out` says the folder already exists, that's fine; carry on.)

### Without installing Java (Mac only)

`./package.sh` builds a standalone `dist/QuikStop.app` and `.dmg` with a bundled Java
runtime, which you can hand to someone who has no JDK. It needs a JDK on the machine
doing the build. A Windows equivalent isn't set up yet.

## Controls

| Key | Action |
| --- | --- |
| W A S D / arrows | Move |
| Shift | Sprint |
| E (hold) | Interact: take items, ring up, mop, climb |
| Q | Put down what you're carrying |
| F | "Be right with you!" (slows impatience, has a cooldown) |
| 1 to 4 | Add $1 / $5 / $10 / $20 to the change you're giving |
| 5, 6 | Add a $50 / $100 bill to the change |
| Backspace | Clear the change |
| Enter / Space | Hand over the change |
| C | Call in a co-worker (once per shift, only when rage is over 50%) |
| H, F1 or P | Help / pause |
| M | Mute |

Enter or Space starts a night from the title screen.

## Roblox

The `roblox/` folder holds a Luau port of the game. Run `python3 build_roblox.py` to
rebuild `roblox/QuikStop.rbxlx` from the two scripts, open it in Roblox Studio, and press
Play. See `ROBLOX_PORT_NOTES.md` for the content swaps and what's still missing (the
Roblox version doesn't have the co-worker or large-bill change yet).

## Android

Not supported. Swing doesn't run on Android, so it would need a port.
