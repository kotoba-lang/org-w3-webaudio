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
`w3/webgpu.cljs`) has no JVM test suite; correctness there is pinned by
`kotoba-lang/audio`'s own real-browser E2E once it's wired through this repo
(a later ADR-2607121400 completion-gate item, out of scope for this commit).
`w3/webaudio/protocol.cljc` **is** unit tested (portable, no browser needed):

```
clojure -M:test
Ran 4 tests containing 17 assertions.
0 failures, 0 errors.
```

## Develop

```
clojure -M:test   ;; protocol.cljc only — webaudio.cljs needs a real browser
```
