# Handoff: Item Inspect Fabric mod — review + smoke test

Written for a fresh agent (ChatGPT/Codex) picking this up cold. Read this
first, then [PLAN.md](PLAN.md) for the full research/decision history if you
want more depth on any point below.

## What this is

A Fabric client-side mod for Minecraft **1.21.11**. Hold a key (bound to F7
on the test machine) → the held item eases toward screen-center, the camera
locks (mouse no longer turns the view), and mouse movement instead tilts the
item's pitch/yaw. Release the key → it eases back and camera control
returns. No GUI/menu opens; movement (WASD) stays live throughout.

Repo: https://github.com/FluffyBacon678/ItemInspectMod (this handoff is
already pushed to `main`).

## Architecture (3 files do the real work)

- [`src/main/java/dev/arielg/iteminspect/ItemInspectClient.java`](src/main/java/dev/arielg/iteminspect/ItemInspectClient.java) —
  client entrypoint. Registers the keybind, runs a per-tick state machine
  (`active` boolean gated on the key + several forced-exit conditions —
  GUI open, dead, unfocused, not-first-person, spectator, attacking/using),
  eases a `centerOffset` float 0→1, and exposes `inspectYaw`/`inspectPitch`
  accumulators that the mouse mixin feeds.
- [`src/main/java/dev/arielg/iteminspect/mixin/MouseHandlerMixin.java`](src/main/java/dev/arielg/iteminspect/mixin/MouseHandlerMixin.java) —
  `@WrapOperation` (MixinExtras, not a plain `@Redirect` — see "Why
  MixinExtras" below) around `MouseHandler.turnPlayer`'s call to
  `LocalPlayer.turn(DD)V`. While inspecting, swallows the turn and feeds the
  delta into `ItemInspectClient.accumulateTilt` instead.
- [`src/main/java/dev/arielg/iteminspect/mixin/ItemInHandRendererMixin.java`](src/main/java/dev/arielg/iteminspect/mixin/ItemInHandRendererMixin.java) —
  `@Inject` at `TAIL` of `ItemInHandRenderer.applyItemArmTransform(PoseStack,
  HumanoidArm, float)`, the vanilla method that positions the held item for
  its normal resting pose. Adds a translate-toward-center + yaw/pitch
  rotation on top, scaled by `centerOffset`, gated to the main hand only.
- [`InspectConfig.java`](src/main/java/dev/arielg/iteminspect/InspectConfig.java) —
  Gson-backed `config/iteminspect.json` (sensitivity, clamp angles, ease
  speed, translate offsets) so values can be tuned without a rebuild.
- [`ItemInspectMixinPlugin.java`](src/main/java/dev/arielg/iteminspect/mixin/ItemInspectMixinPlugin.java) —
  required boilerplate: calls `MixinExtrasBootstrap.init()` in `onLoad`, or
  `@WrapOperation` silently won't work.

All class/method names above were confirmed against real decompiled 1.21.11
source (via Loom's `genSources`), not guessed from Yarn-era tutorials — see
PLAN.md's "Critique of the original draft" section for why that distinction
mattered (this build uses **official Mojang mappings** via the
`net.fabricmc.fabric-loom-remap` plugin, not Yarn — 1.21.11 is the last
obfuscated MC version and the ecosystem has already moved to Mojmap for it).

## Why MixinExtras (`@WrapOperation` instead of plain `@Redirect`)

The target machine's real modpack (~120 mods) includes **Do a Barrel Roll**,
which hooks the *exact same* call (`MouseHandler.turnPlayer` →
`LocalPlayer.turn(DD)V`) using MixinExtras' composable `@WrapOperation`. A
plain Sponge `@Redirect` exclusively claims a call site — a second mod
redirecting the same instruction is a hard Mixin-apply crash at launch, not
a soft bug. `MouseHandlerMixin` was rewritten to use `@WrapOperation` for
this reason. If you touch that file, **do not swap it back to `@Redirect`**
without re-checking this.

Full compatibility investigation (which mods were checked, what their
mixins actually target, confirmed via grepping the compiled `.class` files
for readable constant-pool strings rather than guessing from mod
descriptions) is in PLAN.md under "Real-instance compatibility check &
install".

## Build / run

Standard Loom project. From repo root:

```bash
./gradlew build        # compile + produce build/libs/iteminspect-0.1.0.jar
./gradlew runClient    # launch a dev Minecraft client with the mod loaded
```

**Windows-specific gotcha hit during development:** Gradle's daemon needs a
local loopback socket for JVM IPC. On this Windows machine, Java's AF_UNIX
pipe implementation derives the socket path from `%TEMP%`, and the sandbox's
default temp path was long enough to exceed Windows' 108-byte `sun_path`
limit, causing `Unable to establish loopback connection`. Fixed by pointing
`TEMP`/`TMP` at a short path before invoking gradlew:
```bash
TEMP='C:\jtmp' TMP='C:\jtmp' ./gradlew build
```
If you're not on Windows or not hitting this, ignore it.

## Current status: everything compiles and loads cleanly, but the core visual effect is unconfirmed

This is the important part. Timeline:

1. All three mixins + config compile clean, and — critically — **Loom's
   Mixin annotation processor validates `@Inject`/`@Redirect`/`@WrapOperation`
   targets against real Minecraft bytecode at compile time**, so a
   successful build is a real signal the hooks target real methods, not
   just that the Java parses.
2. Installed into the user's real modded instance (`%APPDATA%\.minecraft\mods`,
   ~120 mods, Fabric Loader 0.19.3). The full modpack **loaded with zero
   Mixin-apply errors** in the log — meaning every mixin here (including the
   `applyItemArmTransform` injection) was confirmed to apply without a
   binding failure. If it hadn't bound, Fabric Loader would have crashed the
   whole game at launch (all our mixins are in a `"required": true` config).
3. User tested: **held the key, mouse-look correctly stopped turning the
   camera** (confirms `MouseHandlerMixin`/`@WrapOperation` is intercepting
   input correctly — the highest-risk part of this mod, working). **But the
   held item did not visibly move at all** — not even the toward-center
   translate, which requires zero mouse input and should appear from
   `centerOffset` ramping alone within ~0.4s of holding the key.

## What's already been ruled out (don't re-check these)

- Config file corruption / zeroed values — read directly, values are correct
  (`translateX`/`Y`/`Z`, `easePerTick`, clamp angles all present and non-zero).
- Keybind collision — F7 confirmed bound only to `key.iteminspect.inspect`
  in `options.txt`, no other mod defaults to F7.
- Mixin apply failure — confirmed via log grep, zero errors/warnings, mod
  list loaded cleanly including `iteminspect`.
- Iris shader interference — `enableShaders=false` in `iris.properties` at
  test time, so Iris's separate hand-rendering path (confirmed to exist —
  other mods carry `IrisHandRendererMixin` compat shims) was not active.
- NotEnoughAnimations rendering a hand for held tools — checked its
  compiled mixin classes directly; its `ItemInHandRendererMixin` only
  touches the *empty-hand* `renderPlayerArm` path, and its
  `ItemStackRenderStateMixin` is just a data-tagging accessor, not a
  renderer. Vanilla + this modpack genuinely does not render a separate
  arm/hand mesh behind a held sword — only the item mesh itself. (This
  matters for a separate, larger ask — see "Known follow-up ask" below —
  but rules it out as the explanation for "nothing moves" here.)
- Wrong build under test — theoretically possible earlier in the session
  (Minecraft doesn't hot-reload mods, and a rebuild happened while the game
  was running), but the build that was actually running during the "nothing
  moves" report already had the full config-driven transform logic — only
  debug `LOGGER.info`/actionbar lines were added afterward, no functional
  change. So this isn't just a "retest with the right jar" situation.

As a last diagnostic step before handoff, the live `config/iteminspect.json`
on the test machine was edited directly to dramatically exaggerate the
effect (`translateX/Y/Z` pushed from ~0.3 to 1.2/0.8/1.2 — several times
larger than the base item offset) so that if the transform is applying at
all, it should be unmissable. **This has not yet been retested** (the user
was asked to relaunch Minecraft — mods only load at startup — and hold F7
again; check `%APPDATA%\.minecraft\logs\latest.log` for lines containing
`[inspect-debug]` from `ItemInHandRendererMixin`, and whether an actionbar
message like `[inspect] offset=... yaw=... pitch=...` was seen in-game).

## Suggested next steps for you

1. **Get the actual test result first** if possible (ask the user, or check
   `latest.log` for `[inspect-debug]` lines) before changing anything — the
   debug logging will directly show whether `applyItemArmTransform`'s tail
   is being hit at all, what `centerOffset`/`humanoidArm`/`mainHandMatch`
   look like, and separately the tick-loop actionbar message shows whether
   `centerOffset`/`inspectYaw`/`inspectPitch` are ramping correctly.
   - If the debug log line **never appears**: the injection isn't firing at
     runtime despite compiling clean and loading without error — worth
     enabling Mixin's `-Dmixin.debug.export=true` (dumps transformed classes
     to `.mixin.out/`) and diffing the exported `ItemInHandRenderer.class`
     against the original to visually confirm the injected bytecode is
     actually there and reachable.
   - If it **does** appear with `mainHandMatch=true` and a healthy
     `centerOffset`, but the item still doesn't visibly move even at the
     exaggerated config values: suspect something *after* our injection in
     the render chain is discarding or overriding the `PoseStack` state —
     worth checking whether Sodium (installed, a major rendering rewrite)
     touches this path, or whether `ItemStackRenderState.submit()` (called
     right after, in `renderItem`) does anything unexpected with the item's
     own per-display-context model transform that could visually swamp a
     ~1 unit translate.
   - If `mainHandMatch=false` consistently: check `player.getMainArm()`
     vs. the `humanoidArm` parameter — logged in the debug line — for a
     mismatch (e.g. left-handed player settings).
2. **Smoke test the build/mixin-apply path yourself** even if you can't see
   a rendered frame: `./gradlew runClient` and check the log for a clean
   launch with no Mixin ApplicatorException, plus grep for `[inspect-debug]`
   after simulating holding the key isn't really possible headless — but at
   minimum confirm the client boots and the mod's log lines
   (`iteminspect 0.1.0` in the mod list, no ERROR lines referencing
   `dev.arielg.iteminspect`) look healthy.
3. **Code review** the three files under "Architecture" above for logic bugs
   — sign errors, wrong local-space assumptions about `PoseStack.translate`/
   `mulPose` ordering, etc. Multiple static re-reads by the previous agent
   didn't find one, but a second set of eyes on the actual matrix math is
   exactly what's needed here.

## Known follow-up ask (separate from the bug above, lower priority)

The user's real aesthetic target is closer to a fully-modeled first-person
arm that visibly *bends at the wrist* to follow the mouse (they shared
reference images of a skin-textured forearm gripping a sword from many
angles). Confirmed via decompiled source + item model JSON that **vanilla
does not render an arm/hand mesh for held tools at all** — only the item
mesh, positioned by `applyItemArmTransform`. What this mod currently does
(and is trying to debug above) is tilt *that item mesh*. Actually rendering
a bending arm would mean drawing our own skin-textured forearm cuboid
(similar to how vanilla's `ItemInHandRenderer.renderPlayerArm` /
`AvatarRenderer.renderRightHand` do it for the *empty*-hand case) and
extending that to the held-item case too — a materially bigger feature than
what exists now. Don't start this until the basic item-tilt is confirmed
working; no point building more on top of an unverified transform.
