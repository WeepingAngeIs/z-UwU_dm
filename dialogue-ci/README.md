# Built-in UwU villager speech

Base: integrated Playtest 9 `1f489c5308b686a30672ec19b671a64534eb28a4`.
Isolated branch: `codex/villager-dialogue-20261121`.

Ordinary Petal and Dusk keeper interaction speaks the existing profession greeting.
Existing `/sakuraambient talk` openings and selected replies speak their authored
lines. The same speaker-prefixed component goes to regular Minecraft chat and
one built-in overhead bubble. Choices, session tickets, errors, and interaction
instructions remain private. Vanilla villagers, saved residents, worldgen,
dialogue text, and the integrated cave/weapon/book assets are unchanged.

The audience is an inclusive 80-block **spherical** radius from the keeper, in
the same dimension. Each bubble wraps text, follows its keeper, lasts 240 game
ticks (12 seconds at normal tick rate), fades out, and replaces the previous
bubble for that keeper. Repeating the identical line within 40 ticks is suppressed;
different replies can appear immediately. State is transient and clears with
world changes, expiry, removal, death, or leaving range.

Chat Bubbles is a reference only. Its exact `1.1.1+1.21.1` JAR is downloaded into
the disposable compatibility fixture, never into the production JAR or its
dependency list. The two runs load the same frozen, hash-checked UwU candidate.
The reference's normal player-chat pipeline is exercised as a positive control;
NPC lines use normal system-chat delivery and their own UwU payload.

## Hosted checks

Use `.github/workflows/villager-dialogue.yml` on the isolated branch. All Gradle,
Java compilation, and Minecraft execution run on the public GitHub-hosted runner.
Do not execute the fixture locally.

The fixture uses a native integrated client and server, plus packet-capturing
server players for near, exact-boundary, far, and other-dimension recipients.
It records native in-game screenshots for both factions, an existing conversation
and reply, and expiry, with and without the reference mod. The scope gate compares
all packaged entries to the exact passing integrated JAR and permits only the
two new speech classes, three precise native hooks, and candidate version change.

Evidence includes logs, PNG captures, source and JAR hashes, a scope report, and
a clean patch against the integrated baseline. The independent weapon follow-up
and Dusk frequency work are separate branches and are not included in this JAR.
