# Inspect Mod — Plan (Fabric, MC 1.21.11)

> This file is the historical research/decision log from the mod's initial
> build-out. It's out of date in places (e.g. the render hook it describes
> was later moved after a real compatibility bug — see below). For current
> architecture and status, read [README.md](README.md) and
> [HANDOFF.md](HANDOFF.md) instead; this file is kept for the reasoning
> trail, not as a live status document.

## Concept

Hold a key → held item eases toward screen-center, world camera locks, and mouse
movement tilts the item (pitch/yaw) in the player's hand instead of turning the
camera. Player can still walk/jump/sprint normally. Release the key → item eases
back to its normal position and camera control returns.

Scope locked in for v1 (see decisions below): client-side only (no networking),
camera fully locked during inspect, pitch/yaw only (no roll), applies to all
held items uniformly.

## Critique of the original draft

The original AI-written prompt gets the *shape* of the idea right but is
architecturally out of date and skips every hard edge case. Specifics:

1. **Wrong-generation render API.** The draft's `matrices.multiply(RotationAxis...)`
   + implicit `VertexConsumerProvider` pattern is how this worked in ~1.19–1.20.
   As of MC 1.21.9+ (confirmed still true in 1.21.11), most render submission —
   including held items — goes through a new `OrderedRenderCommandQueue`
   deferred-command system, not immediate-mode `VertexConsumerProvider` calls.
   `HeldItemRenderer.renderFirstPersonItem(...)` in 1.21.11 takes an
   `OrderedRenderCommandQueue` parameter that didn't exist in older tutorials.
   `MatrixStack` is still there and still the right thing to perturb, but *how*
   you hook the method and *what* you do inside it needs to match the new
   signature — any tutorial/code you find online for MC ≤1.21.4 is likely stale.

2. **No real injection point named.** "Hook `ItemRenderer`" is too vague —
   `ItemRenderer` isn't where per-hand positioning happens; `HeldItemRenderer`
   is, and the method that matters (`renderFirstPersonItem`) is **private**.
   That means either an `@Inject` at a local-capture point, an `@ModifyVariable`
   on the `MatrixStack` argument, or an `@ModifyArg`/`@Redirect` around the
   rotation calls inside it — decided from decompiled source once Loom is set
   up (`genSources`), not guessed from docs alone.

3. **Mouse interception is hand-waved.** "`Mouse.onCursorPos`" exists but
   Fabric API has no clean event for "cancel this frame's look input." Redirecting
   deltas away from the camera and into our own pitch/yaw accumulator means a
   `@Redirect`/`@Inject` mixin into `Mouse`'s internal update method, done
   carefully so the OS cursor stays grabbed (FPS mouselook mode) the whole
   time — only where the delta *goes* changes, not the capture mode. The draft
   implies this correctly but never says it explicitly; worth stating as a hard
   requirement so it isn't lost during implementation.

4. **Missing forced-exit conditions.** The draft only describes entering and
   releasing the key. It never accounts for: opening any `Screen` (inventory,
   chat, pause), death/respawn, disconnect, switching hotbar slot, taking damage,
   attacking/using the item, entering third-person or spectator view, or losing
   window focus. Any of these must hard-cancel inspect mode and snap the camera
   state back to normal, or you get stuck cursor/camera state — this is the
   single most likely source of "soft-lock" bug reports.

5. **Camera-lock tradeoff was undiscussed.** Locking the camera during inspect
   (as drafted) means you can't see threats around you while inspecting —
   acceptable for this mod's purpose per your decision below, but worth
   re-confirming after the first playtest since it's the biggest UX risk.

6. **Toolchain timing — bigger deal than first thought.** 1.21.11 is the last
   obfuscated Minecraft version, and it turns out Fabric's tooling has *already*
   moved ahead of that cutover: the current `fabric-example-mod` template no
   longer uses Yarn at all. It uses the new `net.fabricmc.fabric-loom-remap`
   Gradle plugin (Loom 1.18.2) with `loom.officialMojangMappings()` — i.e. we
   build directly against **Mojang's official mapping names**, not Yarn's.
   This matters a lot: every class/method name from the earlier research pass
   (`HeldItemRenderer`, `MatrixStack`, `RotationAxis`, `Mouse`) was found via
   Yarn javadocs and **will not be the real name in our code.** Official
   mappings use different naming (e.g. Yarn's `KeyBinding` is Mojang's
   `KeyMapping`, confirmed directly from Fabric's own 1.21.11 docs while
   scaffolding). Treat every Yarn-derived class name in this document as a
   placeholder until confirmed against real Mojang-mapped source — see
   Phase 0 note below.

7. **Compatibility risk, not a blocker.** Mixing into a hot per-frame render
   path (`HeldItemRenderer`) can collide with other hand/FOV/shader mods
   (Iris, other first-person tweak mods). Mitigate by injecting at the
   narrowest point possible (perturb the matrix, don't overwrite the method)
   and testing alongside Sodium + Iris before release.

## Decisions locked in (2026-09-18)

| Question | Decision |
|---|---|
| Seen by other players? | No — client-side visual only, no networking for v1 |
| Camera during inspect | Fully locked; mouse fully repurposed to item pitch/yaw |
| Roll axis | Not in v1; pitch/yaw only, roll is a possible stretch goal |
| Item scope | All held items uniformly, revisit exclusions after playtesting |

## Research to do before/while implementing

These needed a real decompiled workspace, not just doc search — all done:

- [x] Scaffolded the mod pinned to 1.21.11, ran `genSources` via Loom, read the
      real decompiled `ItemInHandRenderer` and `MouseHandler` classes.
- [x] Confirmed exact mixin target: `ItemInHandRenderer.applyItemArmTransform`
      — see Phase 3.
- [x] Confirmed exact method in `MouseHandler`: `turnPlayer(double)`, redirecting
      its `LocalPlayer.turn(DD)V` call — see Phase 2.
- [x] Skipped `WorldRenderEvents` — it doesn't expose per-hand transform
      control, so the mixin is the only route; not worth chasing further.
- [x] Turned out unnecessary: `applyItemArmTransform` is only reached for the
      idle/default and eat/drink poses, and both of those are already covered
      by the attack/use forced-exit condition, so there was no separate
      swing/equip-bob suppression to write.
- [x] Forced-exit conditions implemented directly in `ItemInspectClient`'s
      tick handler — see Phase 1 (no separate `ScreenEvents` mixin needed,
      polling `client.screen`/`isWindowActive()`/etc. each tick was simpler
      and sufficient).

## Implementation plan (after research)

**Phase 0 — Project scaffold — DONE and building (2026-09-18)**
- Group `dev.arielg`, mod id `iteminspect`, gradle.properties pinned to real
  confirmed versions: `minecraft_version=1.21.11`, `loader_version=0.19.5`,
  `loom_version=1.17.21`, `fabric_api_version=0.141.6+1.21.11`.
  (Loom 1.18.x requires a JDK 25 runtime for Gradle itself — this machine
  only has JDK 21, so pinned to 1.17.21, the last release that runs on
  JDK 21. Mod bytecode still correctly targets Java 21 either way.)
- `build.gradle` uses `net.fabricmc.fabric-loom-remap` + `loom.officialMojangMappings()`
  (confirmed correct plugin id for 1.21.11, an obfuscated version — 26.1+ would
  need the non-remapping `net.fabricmc.fabric-loom` plugin instead, not applicable here).
- No split client/common source sets — mod is `"environment": "client"` only,
  single source set, since there's no server-side code at all.
- `./gradlew build` confirmed green end-to-end: `build/libs/iteminspect-0.1.0.jar`
  built successfully (BUILD SUCCESSFUL, 1m47s first run).
- Windows-specific gotcha hit and fixed: Gradle's daemon needs a local loopback
  socket for JVM IPC; Java's AF_UNIX-based pipe implementation on Windows
  derives the socket path from `%TEMP%`, and this environment's default temp
  path is long enough to exceed Windows' 108-byte `sun_path` limit, causing
  `Unable to establish loopback connection`. Fixed by pointing `TEMP`/`TMP` at
  a short `C:\jtmp` when invoking `gradlew`. **Use this every time you build:**
  `TEMP='C:\jtmp' TMP='C:\jtmp' ./gradlew <task>` (create `C:\jtmp` once if it
  doesn't exist).
- Still to do: run `genSources` so decompiled MC is browsable locally.

**Phase 1 — Input & state machine — DONE (2026-09-18)**
- `ItemInspectClient.java` registers the Inspect key via Fabric API's
  `KeyBindingHelper.registerKeyBinding`, confirmed real classes for 1.21.11:
  `net.minecraft.client.KeyMapping` (this is Mojang's name for what Yarn calls
  `KeyBinding`), `com.mojang.blaze3d.platform.InputConstants` for keycodes,
  `net.minecraft.resources.Identifier` for the category id. Bound to
  `InputConstants.UNKNOWN` by default (unbound) so it can't collide with an
  existing key — player assigns it themselves in Controls.
- State machine implemented as a simple `active` boolean (key held + no
  forced-exit condition) driving an eased `centerOffset` float (0→1,
  exponential ease per tick), updated in `ClientTickEvents.END_CLIENT_TICK`.
  Forced-exit conditions wired: screen open (`client.screen != null`), player
  dead/null, window unfocused (`client.isWindowActive()`), not first-person
  (`client.options.getCameraType().isFirstPerson()`), spectator, and
  attacking/using the item (`client.options.keyAttack`/`keyUse`.isDown()) —
  covers every condition from the research checklist except third-person,
  which is folded into the first-person check.

**Phase 2 — Mouse redirection — DONE (2026-09-18)**
- Real class confirmed via decompiled source: `net.minecraft.client.MouseHandler`
  (Mojang's name for Yarn's `Mouse`). The actual camera-turn call happens in
  its private `turnPlayer(double)` method, which computes the final
  sensitivity/smoothing-adjusted delta and calls `LocalPlayer.turn(d, e)`
  (inherited from `Entity`, but the call site's static type is `LocalPlayer`,
  which is what the `@Redirect` target has to name).
- `MouseHandlerMixin` redirects that exact call: while inspecting, the delta
  is accumulated into clamped `inspectYaw`/`inspectPitch` (±60°/±45°) instead
  of being applied to the camera. Nothing touches cursor-grab state, so the
  "stay in FPS mouselook the whole time" requirement falls out for free —
  confirmed by reading `MouseHandler.grabMouse()`/`releaseMouse()`, which are
  entirely separate from `turnPlayer()`.

**Phase 3 — Render transform — DONE, untuned (2026-09-18)**
- Real class/method confirmed via decompiled source:
  `net.minecraft.client.renderer.ItemInHandRenderer.applyItemArmTransform(PoseStack, HumanoidArm, float)`
  — this is the one place vanilla positions the item for its normal
  resting/idle first-person pose. Attack swings and special use-animations
  (eat/block/bow-draw/etc.) apply their own transforms instead and skip this
  method entirely — which lines up exactly with the forced-exit-on-attack/use
  logic from Phase 1, so no extra gating was needed for those cases.
- `ItemInHandRendererMixin` injects at `TAIL`, gated on `centerOffset > 0` and
  main-hand-only (`humanoidArm == player.getMainArm()`, off-hand untouched per
  the v1 decision), applying a translate-toward-center plus
  `inspectYaw`/`inspectPitch` rotation via `PoseStack.mulPose(Axis...)`,
  scaled by `centerOffset` so it eases in/out instead of snapping.
- **The translate/rotation constants are placeholder guesses, not visually
  tuned** — this environment can build and compile-time-validate the mixins
  (Loom's Mixin annotation processor checks `@Inject`/`@Redirect` targets
  against real Minecraft bytecode at compile time, which passed), but it
  cannot launch and look at the actual game. Confirmed compiling and mixin
  targets are valid; **actual in-game visual tuning of the offsets/feel is
  still needed and has to happen on your end.**

**Phase 4 — Polish & guardrails — config done, visual polish still blocked on playtesting**
- Off-hand item: resolved in Phase 3 — inspect only affects the main hand
  (`humanoidArm == player.getMainArm()`), off-hand renders normally, untouched.
- Config added: [InspectConfig.java](src/main/java/dev/arielg/iteminspect/InspectConfig.java),
  a Gson-backed JSON file at `config/iteminspect.json` (via
  `FabricLoader.getInstance().getConfigDir()` — stable Fabric Loader API,
  unaffected by the Mojang-mappings switch since it's not a Minecraft class).
  Holds `maxYawDegrees`, `maxPitchDegrees`, `tiltSensitivity`, `easePerTick`,
  `translateX/Y/Z`. **This is the answer to "constants are untuned guesses":**
  edit the JSON and rejoin to tune feel without a rebuild — turns the
  can't-see-the-game limitation into a non-issue.
- Still blocked on playtesting (needs a human at the keyboard, not more code):
  block-item/flat-icon items sanity check, actually tuning the config
  defaults, and re-confirming the camera-lock UX decision feels right.

**Phase 5 — Compatibility & release — blocked on playtesting**
- Third-person/spectator: already gated in code (`wantsInspect` requires
  first-person + non-spectator), but needs eyes-on confirmation.
- Test alongside Sodium + Iris — needs a live session with those mods installed.
- Package for Modrinth/CurseForge if you want to publish; otherwise just a
  local build (`build/libs/iteminspect-0.1.0.jar` already builds clean).

## Real-instance compatibility check & install (2026-09-18)

Checked against the user's actual modded 1.21.11 Fabric instance
(`%APPDATA%\.minecraft`, ~120 mods, profile `fabric-loader-1.21.11` on
Fabric Loader 0.19.3) by extracting each candidate mod's `*.mixins.json`
and grepping the compiled mixin `.class` files for their real injection
targets (not guessed from mod descriptions):

- **Fabric Loader mismatch, fixed:** `fabric.mod.json` required
  `fabricloader": ">=0.19.5"`, but the installed loader is 0.19.3 — would
  have failed to load. Lowered to `>=0.15.0` (the actual functional floor,
  driven by MixinExtras bundling — see below); nothing else in this mod
  needs a newer loader.
- **Real conflict found and fixed: Do a Barrel Roll.** Its `client.roll.MouseMixin`
  hooks the *exact same* call we do (`LocalPlayer.turn(DD)V` inside
  `MouseHandler.turnPlayer`), using MixinExtras' composable `@WrapOperation`.
  Our original `MouseHandlerMixin` used a plain Sponge `@Redirect`, which
  exclusively claims a call site — two mods redirecting the same instruction
  is a hard Mixin-apply crash at launch, not a soft bug. Rewrote
  `MouseHandlerMixin` to use `@WrapOperation` too (added
  `io.github.llamalad7:mixinextras-fabric:0.5.5` as a compile-time
  dependency — not `include`d, since Fabric Loader 0.15.0+ already bundles
  MixinExtras at runtime), plus [ItemInspectMixinPlugin.java](src/main/java/dev/arielg/iteminspect/mixin/ItemInspectMixinPlugin.java)
  to call the required `MixinExtrasBootstrap.init()`.
- **Checked, not a conflict: NotEnoughAnimations.** Also has an
  `ItemInHandRendererMixin`, but it hooks `renderPlayerArm` (the *empty-hand*
  idle-arm animation path) — a different method than ours
  (`applyItemArmTransform`, the held-item path). No shared injection point.
- **Checked, not a conflict: Freecam.** Also has an `ItemInHandRendererMixin`,
  but hooks `renderItem` (the general item-draw call) via `@Inject`/`@ModifyVariable`
  — different method again, no overlap.
- **Checked, not a conflict: Zoomify.** Its `zoom.MouseMixin` modifies the
  sensitivity/smoothing *values* earlier in `turnPlayer` via
  `@ModifyArg`/`@ModifyExpressionValue`, not the final `turn()` call itself —
  compatible with our `@WrapOperation` on that call; zoom-adjusted sensitivity
  will just carry over into tilt speed if both are active at once, which is a
  reasonable interaction, not a bug.
- **ImmediatelyFast:** doesn't touch `ItemInHandRenderer` or `MouseHandler`
  at all (font/map/buffer batching only) — no interaction.

**Installed:** `build/libs/iteminspect-0.1.0.jar` copied into
`%APPDATA%\.minecraft\mods\`. Ready to launch via the existing
`fabric-loader-1.21.11` profile. Bind the "Inspect Held Item" key (Controls
menu, unbound by default) and tune `config/iteminspect.json` (created in the
real `.minecraft/config` folder on first launch) to adjust feel.

## Open items to revisit later (explicitly out of v1 scope)

- Roll axis / full tumble control.
- Networked visibility to other players.
- Per-item exclusion/tag list.
- Mojang-mappings migration (only needed once MC moves past 1.21.11).
