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

## Known compatibility notes

Some mods hook the exact same rendering/input points this mod does:

- **Do a Barrel Roll** redirects the same mouse-turn call — handled via
  MixinExtras' composable `@WrapOperation` instead of an exclusive
  `@Redirect`, so both mods can hook it without conflicting.
- **Punchy** replaces first-person hand rendering and cancels the vanilla
  method this mod originally hooked — handled by moving the transform to
  the item's actual render-submission call, which both vanilla and Punchy's
  ordinary items funnel through.

## Known limitations

- Filled maps and Punchy's custom model/boat/chest rendering bypass the
  item-submission hook this mod uses and aren't covered yet.
- No roll axis (pitch/yaw only).
- Purely client-side/cosmetic — other players never see it.

## More detail

- [PLAN.md](PLAN.md) — design decisions and research log from the mod's
  original build-out.
- [HANDOFF.md](HANDOFF.md) — deeper technical notes on the render pipeline,
  the Punchy compatibility fix, and verification history.

## License

MIT — see [LICENSE](LICENSE).
