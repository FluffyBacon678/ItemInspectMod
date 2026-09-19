# Item Inspect

A Fabric client-side mod for Minecraft **1.21.11**. Hold a key to bring your
held item toward the center of the view and tilt it with the mouse — like a
weapon-inspect in a shooter, but without opening a menu, playing a canned
animation, or freezing movement. Release the key and it eases back; camera
control returns immediately.

- Movement (WASD, jumping, sprinting) stays live the whole time.
- The camera locks while inspecting — mouse movement tilts the item's
  pitch/yaw instead of turning the view.
- No GUI/screen opens.
- A skin-textured hand moves along with plain block items while inspecting
  (vanilla itself never draws one there — see "Optional integration: Punchy"
  below for how other item types are handled).

## Install

Drop the built jar (`iteminspect-<version>.jar`) into your `mods` folder
alongside Fabric Loader and Fabric API for 1.21.11. MixinExtras is required
but not bundled — Fabric Loader 0.15.0+ already ships it, so nothing extra
to install as long as your loader is reasonably current.

Bind the "Inspect Held Item" key in **Options → Controls → Key Binds**; it's
unbound by default so it can't collide with an existing bind.

## Config

`config/iteminspect.json` (created on first launch):

| Field | Meaning |
|---|---|
| `maxYawDegrees` / `maxPitchDegrees` | Clamp on how far the item can tilt |
| `tiltSensitivity` | Mouse-to-tilt sensitivity multiplier |
| `easePerTick` | How quickly the item eases in/out on press/release |
| `translateX` / `translateY` | How far the item shifts toward center |
| `translateZ` | Push toward the camera — left at `0` by default; positive values read as the item popping out of the hand's grip |

Config is loaded once at startup — **restart Minecraft** after editing it.

## Building from source

Standard [Fabric Loom](https://docs.fabricmc.net/develop/loom/) project.

```bash
./gradlew build              # -> build/libs/iteminspect-<version>.jar
./gradlew runClient          # launch a dev client with the mod loaded
./gradlew runClientGameTest  # run the automated smoke test (see below)
```

**Windows:** if you hit `Unable to establish loopback connection`, it's a
known JDK/Windows issue where Gradle's daemon IPC socket path exceeds
Windows' 108-byte limit when `%TEMP%` is deeply nested. Point `TEMP`/`TMP`
at something short first:
```bash
TEMP='C:\jtmp' TMP='C:\jtmp' ./gradlew build
```

### Automated smoke test

`src/gametest` launches a real client headlessly and checks the actual
submitted render matrices (not just screenshots) — confirms the item tilts,
the off-hand stays untouched, the camera stays locked while inspecting and
returns control on release. To also test against a specific mod for
compatibility (see "Known compatibility notes" below):

```bash
./gradlew runClientGameTest '-PcompatMod=/path/to/some-mod.jar'
```

## Optional integration: Punchy

Vanilla renders no arm/hand behind a held item at all — inspecting just
tilts the floating item mesh in place. If [Punchy](https://modrinth.com/mod/punchy)
is installed too (listed as an optional `suggests` dependency — nothing
breaks if it's absent), its arm moves together with the item for the item
kinds it gives special animation to (swords, tools, bows, etc.), which
looks noticeably better. This is a tested, intentional integration, not
just incidental compatibility — see ARCHITECTURE.md for how the hook works.

For item kinds Punchy doesn't specially animate, it falls back to a render
path this mod's hook doesn't reach — confirmed via Punchy's own bytecode,
not a guess. **Plain blocks are the one such case this mod handles itself**:
it draws its own hand for held `BlockItem`s (using vanilla's own arm-drawing
code, not Punchy's), so those get a moving hand too, with or without Punchy
installed. Other unclassified item types, if any turn up, still just tilt
without a hand for now.

Separately, **Do a Barrel Roll** redirects the same mouse-turn call this
mod does — handled via MixinExtras' composable `@WrapOperation` instead of
an exclusive `@Redirect`, so both mods hook it without conflicting.

## Known limitations

- No moving hand for item types that are neither a plain block nor
  something Punchy specially animates — see "Optional integration: Punchy"
  above.
- Filled maps and Punchy's custom model/boat/chest rendering bypass the
  item-submission hook this mod uses and aren't covered yet.
- No roll axis (pitch/yaw only).
- Purely client-side/cosmetic — other players never see it.

## More detail

- [PLAN.md](PLAN.md) — design decisions and research log from the mod's
  original build-out.
- [ARCHITECTURE.md](ARCHITECTURE.md) — deeper technical notes on the render
  pipeline, the Punchy compatibility fix, and verification history.

## License

MIT — see [LICENSE](LICENSE).
