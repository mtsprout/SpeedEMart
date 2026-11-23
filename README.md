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

### Linux (Flatpak)

Download `SpeedEMart.flatpak` from the
[latest release](https://github.com/mtsprout/SpeedEMart/releases/latest) and install it.
Java is bundled, so you don't need a JDK. You do need Flatpak with the
[Flathub](https://flathub.org/setup) remote set up, since the Freedesktop runtime is
fetched from there.

```sh
flatpak install SpeedEMart.flatpak
flatpak run io.github.mtsprout.SpeedEMart
```

Notes: the bundle is x86_64 only and about 400 MB (it includes a full JDK). It builds
cleanly on GitHub Actions, but it hasn't been run on real Linux hardware yet, so
please report problems. It isn't on Flathub.

To build it yourself, install `flatpak` and `flatpak-builder`, then from the project folder:

```sh
flatpak install flathub org.freedesktop.Platform//25.08 org.freedesktop.Sdk//25.08 org.freedesktop.Sdk.Extension.openjdk21//25.08
flatpak-builder --user --install --force-clean build-flatpak packaging/flatpak/io.github.mtsprout.SpeedEMart.yml
```

The manifest is in `packaging/flatpak/`. The workflow `.github/workflows/flatpak.yml`
builds it on every change to the game, and pushing a tag like `v1.1` publishes a new
release with the bundle attached.

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
| X | Call the manager (night 4 and up, once per shift, costs a favor) |
| H, F1 or P | Help (two pages: arrows, A/D, Space or a click turn the page) / pause |
| M | Mute |

Enter or Space starts a night from the title screen.

**Three strikes:** if your health hits zero you're fired and retry the same night, but that
costs a strike. Get fired three times in one run and the run is over, back to night 1.
Your strikes show in the top-left of the screen.

**Favors and the manager:** every night you clear banks a favor, and a night with no walkouts
banks two (keep up to 3; they show top-center). From night 4 on, press X when a line has built up
and the manager steps in: the whole line leaves without any walkout penalties, you get +25
health and -30 rage. It's once per shift and costs a favor. Favors survive getting fired (a
strike), but a finished run resets them.

**Norm:** from night 2 on, when your rage is high and your health is low, there's a chance the
next person through the door is Norm, the regular. Everybody yells "HEY NORM!", health goes up
by 25, rage drops by 40 and the line cheers up. For the next 45 seconds everyone basks in the
glow: customers' patience stops draining, rage builds at 30% speed and health drains at half
speed. He chats at the end of the counter, tips $2 and leaves. He shows up at most once a shift and never joins the line. The game doesn't mention him,
so don't count on him.

**Vape spot:** a calm-down station in the back room. Hold E for 1.5 seconds to take a hit
and knock 30 off your rage. You get 3 hits per shift, with a 45 second cooldown, a few
seconds of buzzed slow-walking, and the line watching you do it.

**Health recovery:** healing scales up with the night, the same way the drain does. Two
ways to claw back health quickly:

- **Roller dog:** a hot dog roller along the left wall. Hold E for 2 seconds to
  eat one and get +15 health. You get 2 per shift, 60 seconds apart, and about one in five
  is questionable (half the health, and you walk slowly for a few seconds).
- **Line cleared:** serve a line of 3 or more customers with no walkouts and you get $3 in
  tips (plus $1 per extra customer), +8 health and a bit of rage relief. If your health is already
  full, the leftover health turns into even more rage relief, so the bonus is never wasted.

**Coffee pot (night 5 and up):** at 4:30 AM the morning crew wants fresh coffee by 5:30. Dump the
old pot (hold E at the machine, top right), grab the grounds sack from the back room, carry it to
the machine and brew (30 seconds). A pot ready by 5:30 earns a +200 shift bonus and calms the
morning crowd (rage gains drop 15%). No pot makes them grumpier (rage gains rise 20%). A late pot
only lifts the penalty. It never gets you fired.

**Playtesting a later night:** `NIGHT=5 ./run.sh` starts a run on night 5. Runs started this way
don't update your saved best records.

Your best night and best score are saved between sessions in `best.properties`, in
`~/Library/Application Support/SpeedEMart` (Mac), `%APPDATA%\SpeedEMart` (Windows) or
`~/.local/share/speedemart` (Linux). Delete the file to reset them.

## Roblox

> **Warning: the Roblox port is garbage. Don't touch it.** It's an unfinished, buggy
> experiment. Don't build on it, and don't expect it to work.

The `roblox/` folder holds a Luau port of the game. Run `python3 build_roblox.py` to
rebuild `roblox/QuikStop.rbxlx` from the two scripts, open it in Roblox Studio, and press
Play. See `ROBLOX_PORT_NOTES.md` for the content swaps and what's still missing (the
Roblox version doesn't have the co-worker or large-bill change yet).

## Android

Not supported. Swing doesn't run on Android, so it would need a port.

## License

MIT. See [LICENSE](LICENSE).
