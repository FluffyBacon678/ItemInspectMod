# Architecture: Item Inspect Fabric mod

Deeper detail than the [README](README.md) for anyone picking up this
codebase — how the render/input hooks work, why they're built the way they
are, and how the two real mod-compatibility issues found so far were fixed
and verified. For the day-to-day "what does this do / how do I build it"
basics, read the README first.

## Architecture

- [`ItemInspectClient.java`](src/main/java/dev/arielg/iteminspect/ItemInspectClient.java) —
  client entrypoint. Registers the keybind, runs a per-tick state machine
  (`active` boolean gated on the key + forced-exit conditions: GUI open,
  dead, unfocused, not-first-person, spectator, attacking/using), eases a
  `centerOffset` float 0→1, and exposes `inspectYaw`/`inspectPitch`
  accumulators that the mouse mixin feeds.
- [`MouseHandlerMixin.java`](src/main/java/dev/arielg/iteminspect/mixin/MouseHandlerMixin.java) —
  `@WrapOperation` (MixinExtras) around `MouseHandler.turnPlayer`'s call to
  `LocalPlayer.turn(DD)V`. While inspecting, swallows the turn and feeds the
  delta into `ItemInspectClient.accumulateTilt` instead. See "Why
  MixinExtras" below for why it's `@WrapOperation` and not a plain
  `@Redirect`.
- [`ItemInHandRendererMixin.java`](src/main/java/dev/arielg/iteminspect/mixin/ItemInHandRendererMixin.java) +
  [`InspectTransform.java`](src/main/java/dev/arielg/iteminspect/InspectTransform.java) —
  the render-side hook. See "Why the item-submission hook" below for why
  this targets `ItemStackRenderState.submit` rather than the more obvious
  `applyItemArmTransform`.
- [`InspectConfig.java`](src/main/java/dev/arielg/iteminspect/InspectConfig.java) —
  Gson-backed `config/iteminspect.json`.
- [`ItemInspectMixinPlugin.java`](src/main/java/dev/arielg/iteminspect/mixin/ItemInspectMixinPlugin.java) —
  required boilerplate: calls `MixinExtrasBootstrap.init()` in `onLoad`, or
  `@WrapOperation`/`@WrapMethod` silently won't work.
- [`src/gametest/`](src/gametest) — a separate `fabric-client-gametest`
  source set, not bundled in the shipped jar. Launches a real client
  headlessly and asserts on the *actual submitted render matrices*, not
  screenshots. See "Verification" below.

All class/method names were confirmed against real decompiled 1.21.11
source (via Loom's `genSources`), not guessed from Yarn-era tutorials — this
build uses **official Mojang mappings** via the `net.fabricmc.fabric-loom-remap`
plugin, not Yarn (1.21.11 is the last obfuscated MC version and the
ecosystem has already moved to Mojmap for it). See PLAN.md's "Critique of
the original draft" for the full reasoning if any of this needs revisiting
for a future MC version.

## Why MixinExtras (`@WrapOperation`/`@WrapMethod`, not plain `@Redirect`/`@Inject`)

**Do a Barrel Roll** hooks the *exact same* call `MouseHandlerMixin` does
(`MouseHandler.turnPlayer` → `LocalPlayer.turn(DD)V`), using MixinExtras'
composable `@WrapOperation`. A plain Sponge `@Redirect` exclusively claims a
call site — a second mod redirecting the same instruction is a hard
Mixin-apply crash at launch, not a soft bug. If you touch `MouseHandlerMixin`,
**do not swap it back to a plain `@Redirect`** without re-checking this.

Compatibility investigation methodology (relevant if you need to check
another mod): don't guess from a mod's description — extract its
`*.mixins.json` from the jar to see what classes it targets, then `unzip`
the specific `.class` file and grep the compiled bytecode's constant pool
for readable method/field names (`grep -aoE "[A-Za-z_][A-Za-z0-9_]{3,}"`) to
see what it actually hooks. Full investigation log for the ~120-mod test
instance is in PLAN.md under "Real-instance compatibility check & install".

## Why the item-submission hook (not `applyItemArmTransform`)

The first working version hooked `ItemInHandRenderer.applyItemArmTransform`
— the vanilla method that positions a held item for its idle pose. That
worked in vanilla but silently did nothing whenever **Punchy** was active:
Punchy injects at the `HEAD` of `renderHandsWithItems` to draw its own
hands, then cancels vanilla's `renderArmWithItem` outright — so
`applyItemArmTransform` is never reached, even though the mixin itself
binds and applies without error. This is why "the mixin compiles and loads
cleanly" is not the same as "the mixin's target code path actually runs" —
worth remembering for any future render hook here.

The fix hooks `ItemStackRenderState.submit` instead, called from inside
`ItemInHandRenderer.renderItem`, which is the one point both vanilla's and
Punchy's *ordinary* item rendering both funnel through:

- `ItemInHandRendererMixin` also `@WrapMethod`s the whole
  `renderHandsWithItems` pass, just to capture its starting `PoseStack.Pose`
  as a stable reference frame (`iteminspect$handPose`) — needed because by
  the time `submit` runs, Punchy may have applied its own rotations/scales
  using axes that aren't simply "the camera's."
- `InspectTransform.apply` computes the item's pose *relative to* that
  captured hand-pass frame, translates/rotates around the item's own origin
  in those stable axes, then re-applies the original relative transform on
  top — so the tilt works the same regardless of what coordinate gymnastics
  Punchy (or any other mod) did to get the item into its resting pose.
- Gated to `displayContext.firstPerson()` **or** `THIRD_PERSON_LEFT_HAND`/
  `THIRD_PERSON_RIGHT_HAND` while actually in first-person camera mode —
  Punchy uses third-person item-display transforms even during first-person
  rendering, a real quirk confirmed by hitting it in the automated test, not
  a defensive guess.
- Scoped with `pushPose()`/`popPose()` in a `finally` and an `entity != player`
  check so it can't leak into the off-hand, into other entities' rendered
  items (item frames, other players, etc.), or into later draws sharing the
  same `PoseStack`.

## Verification

`src/gametest` (`InspectSmokeTest`) launches an isolated client, gives the
player a diamond sword (main hand) + torch (off hand), then:

1. Unit-tests `InspectTransform.apply`'s matrix math directly (zero offset
   is a no-op, translate happens in view-stable axes not item-local axes,
   rotation doesn't move the pivot, popping the pose fully restores state).
2. Holds the inspect key via synthetic input, asserts the camera's yaw/pitch
   do **not** change while a mouse move **does** change `inspectYaw`
   (confirms `MouseHandlerMixin` is intercepting correctly without touching
   cursor-grab state).
3. Compares the *actual submitted* render matrix for the main hand against
   its pre-transform input matrix (via two small probe mixins,
   `ItemRenderProbe`/`ItemSubmitProbe`, that only exist in this test source
   set) — asserts the main hand's submitted pose changed and the off-hand's
   did not.
4. Releases the key, asserts state resets and camera control returns.
5. Saves screenshots at each stage (`build/run/clientGameTest/screenshots/`).

Run it plain, or against a specific compatibility target:

```bash
./gradlew runClientGameTest
./gradlew runClientGameTest '-PcompatMod=/path/to/punchy.jar'
```

Both variants pass clean as of `0.1.3` (zero `AssertionError`s), including
against the real Punchy jar — confirmed Punchy's `THIRD_PERSON_*`-during-
first-person quirk and the off-hand isolation both hold up in practice, not
just in the code's intent.

## Known limitations (real, not being tracked as bugs)

- **Punchy's own arm mesh isn't independently tilted** — only the item (and
  whatever Punchy attaches to that same submission) is. In practice this
  looks fine because Punchy renders its arm+item as one submission, so it
  moves together, but the arm's *own* rotation isn't something this mod
  controls separately.
- **Filled maps and Punchy's custom model/boat/chest rendering** bypass
  `ItemStackRenderState.submit` entirely (they use their own draw calls) and
  aren't covered.
- **No roll axis** — pitch/yaw only.
- **No arm/hand mesh in plain vanilla** (no Punchy, no other hand-rendering
  mod) — vanilla itself doesn't render a forearm behind a held tool, only
  the item mesh, so there's nothing for this mod to move besides the item
  in that case. A from-scratch skin-textured arm renderer would be a
  materially larger feature if ever wanted — not started.

## History

The full research/decision log (original concept critique, Fabric API
research, the Mojang-mappings-vs-Yarn discovery, initial compatibility
sweep of the ~120-mod test instance) is in [PLAN.md](PLAN.md). Day-to-day
narrative of what changed and why is in `git log` — the commit messages are
written to stand alone.
