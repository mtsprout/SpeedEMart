# QUIK-STOP on Roblox: notes for later

Status: parked. Come back after the Java version is where the user wants it
and the Mac Mini has arrived. The kids asked for this.

## Verdict
Possible, but a rewrite in Luau inside Roblox Studio. Java code cannot be
imported or converted. The design and rules carry over; the world, UI and
characters get rebuilt.

## Content swaps (decided, for Roblox moderation)
- Beer / alcohol  ->  **Energy Drinks** (the ID-check hold-E becomes e.g. an age check or "sugar limit" check, or can be dropped)
- Cigarettes  ->  **Batteries** (still on the top shelf, still the step-stool climb, the "NO, not that one" flip becomes wrong battery size, e.g. AA vs AAA)
- Scratchers can likely stay as-is (check Roblox rules on gambling-like content; may need to become "mystery stickers" or "prize cards")
- Robber: keep cartoonish. Check it stays within Roblox violence/crime guidelines.

## What carries over unchanged (game rules)
- 480 s shift (10 PM to 6 AM), nights scaling by nm() = 1 + 0.10*(night-1)
- Schedule phases and events (beer/energy rush at 1:30 AM, lottery fever, morning commute)
- Customer types: normal, big bill, top-shelf, scratcher, asker, drink buyer, robber
- Cash system: drawer by denomination, safe, DROP CASH / BUY CASH, exact change, wrong-change penalty
- Rage meter, 80% for 7 s meltdown, mop cinematic, lawsuit tip penalty
- Health drain from messes; chores (mop, rug, soda machine, stocking, facing shelves)
- F "Be right with you!" with cooldown and per-customer limits
- Proactive pre-fetch bonus (+$5, +3 health, -8 rage)

## What must be rebuilt
- 3D store from parts in Studio (biggest job; kids could help build)
- E interactions -> ProximityPrompt (hold-duration prompts map well to hold-E)
- HUD, order ticket, change panel, help -> Roblox GUI (ScreenGui)
- Customers -> animated NPC characters using PathfindingService
- Sound -> Roblox audio library / uploaded sounds (Java synth doesn't carry over)
- Mop cinematic -> camera tween + effects

## Decisions to make
- Multiplayer: one store per player, or shared store with friends
- Publishing: needs a Roblox account (use a parent's account); moderation review
- Keep the "frustration on purpose" tone, but check kid-appropriate wording

## Suggested first step
Tiny prototype: one room, register, a few customers, rage meter. Check that
the kids like it before building the full store.

## Reference
Java source: QuikStop/src/Game.java (single file). Tunable knobs listed in
the project history: nm(), PHASES/spawnLogic, SNAP_LIMIT, finishBeat() lawsuit %,
reassure()/reassureCooldown().
