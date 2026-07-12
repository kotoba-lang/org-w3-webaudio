# kotoba-lang/org-w3-webaudio

Raw **W3C Web Audio API** JS surface — a thin ClojureScript wrapper, one
function per spec call, in the same narrow-scope-binding-repo pattern as
`kotoba-lang/org-w3-webgpu` (itself factored out of `kotoba-lang/webgpu` per
ADR-2607051400, `com-junkawasaki/root`). This repo is the audio-domain
sibling defined by ADR-2607121400 (`90-docs/adr/2607121400-kami-ongaku-eizo-commercial-grade-cljs-stack.md`).

## Why this repo exists

`kotoba-lang/audio` (KAMI Audio) is the DSP executor for the `ongaku`
domain — synthesis, effects, mixer bus, offline PCM render. It should not
call `AudioContext`/`AudioWorkletNode`/... inline any more than
`kotoba-lang/webgpu` should call `navigator.gpu` inline. This repo is that
boundary: **just the raw spec surface**, no synthesis/effects/mixer
opinions — those stay in `audio`.

**Scope is deliberately narrow, mirroring org-w3-webgpu exactly:**

- `src/w3/webaudio.cljs` — `AudioContext` lifecycle (create/resume/suspend/
  close), `AudioWorkletNode` creation + `audioWorklet.addModule`, node
  `connect!`/`disconnect!`, `GainNode`/`AudioBuffer`/`AudioBufferSourceNode`
  creation, and the `AudioWorkletNode.port` (MessagePort) primitives
  (`post-message!`/`on-message!`). Descriptors/options are plain JS objects
  built by the caller, same non-translation stance as org-w3-webgpu.
- `src/w3/webaudio/protocol.cljc` — **portable** (JVM + cljs) encode/decode
  for the small control-message envelope sent across the worklet
  `MessagePort` (note-on/note-off/param-change/pcm-block/ack/error). PCM
  sample data itself is a `Float32Array` and crosses the port directly
  (optionally as a Transferable); this namespace only frames the control
  messages — the one part of the port boundary with no browser-only API
  surface, and therefore the one part of this repo that unit tests can
  actually exercise.

## Status

**v0, thin binding layer only — no DSP.** `AudioContext`/`AudioWorkletNode`
only exist in a real browser, so `w3/webaudio.cljs` (like org-w3-webgpu's
`w3/webgpu.cljs`) has no JVM test suite. `w3/webaudio/protocol.cljc` **is**
unit tested (portable, no browser needed):

```
clojure -M:test
Ran 4 tests containing 17 assertions.
0 failures, 0 errors.
```

### Real-browser verification (Wave 5, 2026-07-12)

Actually exercised against a real headless Chromium via Playwright
(`kotoba-lang/playwright`), compiled with `cljs.main`:

- **`new-audio-context!` was broken and is now fixed.** `(new (ctor) opts)`
  — calling a private 0-arg fn to get the constructor, then `new`-ing the
  *call expression* — mis-compiles to `(new w3.webaudio.ctor.call(null))(opts)`
  (a `new` over the bare `.call` property, then invoking the result), which
  threw `TypeError: ... .call is not a constructor` on every invocation,
  in every browser, regardless of `opts`. This had shipped since Wave 2
  without being caught, because this file has no JVM test suite by design.
  Fixed by binding the constructor to a local before `new`-ing it. Verified
  fixed against real Chromium (`ctx.sampleRate` returned correctly).
- **`new-offline-audio-context!` + `start-rendering!` added** (this
  addition) and verified working in real Chromium — `OfflineAudioContext`
  is the only way to exercise Web Audio deterministically without a real
  audio output device, which headless environments don't have.
- **`audioWorklet` availability requires a secure context.** `about:blank`
  pages are *not* secure contexts by default in this Playwright/Chromium
  setup (`window.isSecureContext === false`), and `BaseAudioContext`
  doesn't even expose the `audioWorklet` property in that case. Serving
  the test page from `http://localhost` fixes this (`localhost` counts as
  a secure context). Anyone writing real browser tests against this repo
  needs a local static server, not `page.evaluate` on a blank page.
- **Known blocker, not fixed here: a ClojureScript/Closure-compiled
  bundle cannot be loaded as part of an `AudioWorkletProcessor` module in
  this environment.** `audioWorklet.addModule()` resolves without
  rejecting, but any code placed *after* a `cljs.main`-compiled bundle in
  the same worklet module file never runs (confirmed with a trivial
  single-function namespace, not specific to this repo's own code size or
  complexity) — `registerProcessor(...)` is never reached, so the node
  fails to construct with "not defined in AudioWorkletGlobalScope". This
  reproduces with `:optimizations` `:simple` and `:whitespace` alike.
  Isolated via bisection (all confirmed empirically, not guessed):
  - The same bundle loads fine as a dynamically-imported ES module on the
    **main thread**, including code appended after it.
  - A non-Closure plain-JS file of comparable/larger size, with
    `registerProcessor` appended, works fine **inside the worklet**.
  - A `cljs.main`-compiled bundle for a *trivial* one-function namespace
    (no DSP, no protocol code) still breaks trailing code **inside the
    worklet**.
  - So it's specifically "Closure/goog-bootstrapped code inside
    `AudioWorkletGlobalScope`" that's the problem, not bundle size and not
    this repo's own code. No `pageerror`/`console` event surfaces the
    actual failure (Closure's own `goog.require` "could not find" console
    warnings are a red herring — they come from `main-compiled.js` loaded
    on the *main page*, not from inside the worklet, and are non-fatal in
    both places).
  - **Root cause not further isolated** — would need Chromium-internal
    worklet-thread debugging tools beyond what Playwright's page-level API
    exposes. This blocks the ADR-2607121400 completion-gate item "実ブラウザ
    E2E: 音楽は AudioWorklet 経由の実 PCM render" for a cljs-compiled
    worklet processor specifically; a hand-written-JS processor (as used
    by the isolation tests above) is unaffected.

## Develop

```
clojure -M:test   ;; protocol.cljc only — webaudio.cljs needs a real browser
```
