# Multitouch protocol determination

Captured from `sensor00fn11` (`/dev/input/event3`) on 2026-07-30 during deliberate
physical swipes on the device by the operator. Two captures were taken:

| Fixture | Contents |
|---|---|
| `daemon/src/test/resources/single-finger-swipes.getevent.txt` | One forward swipe, one downward swipe, single finger |
| `daemon/src/test/resources/two-finger-down.getevent.txt` | A two-finger downward swipe |

## Evidence

Counts from `two-finger-down.getevent.txt` (1648 lines):

| Event code | Occurrences |
|---|---|
| `ABS_MT_SLOT` | **0** |
| `SYN_MT_REPORT` | 176 |
| `ABS_MT_TRACKING_ID` | 171 |
| `SYN_REPORT` | 104 |

## Determination: protocol A

`ABS_MT_SLOT` never appears, while `SYN_MT_REPORT` appears 176 times across 104 frames.
That is multitouch **protocol A**: contacts within a frame are delimited by
`SYN_MT_REPORT`, and the frame ends at `SYN_REPORT`. Protocol B would instead carry
`ABS_MT_SLOT` to index each contact and use `ABS_MT_TRACKING_ID == -1` to signal a lift.

This resolves spec §7.1. `EvdevReader` (Task 11) is implemented against protocol A:
count contacts per frame by counting `SYN_MT_REPORT` between `SYN_REPORT` boundaries.

## Two-contact resolution confirmed

Parsing the same capture into frames yields:

```
frames by contact count: {0: 5, 1: 27, 2: 72}
max simultaneous contacts: 2
```

**72 frames report two simultaneous contacts.** This was the single largest open risk in
the project: the touchpad is roughly 9 mm tall, and it was not obvious that two fingers
placed on such a narrow strip would resolve as distinct tracked contacts rather than one
broad blob. They do, at the kernel level, which is what the daemon reads.

## A framework/kernel discrepancy worth knowing

Raw evdev reports two contacts reliably, but the Android framework does **not** always
surface `pointerCount == 2` for the same physical gesture. During hardware testing a
two-finger downward swipe reached `LauncherActivity` as a single-pointer contact with
small displacement, was classified `TAP`, and launched the selected app. See Task 13.

The daemon is unaffected: it reads `/dev/input/event3` directly and sees the two contacts
the kernel reports, bypassing InputReader entirely. This is a point in favour of the
raw-evdev design beyond the focus problem that originally motivated it.

## Device enumeration

`proc-bus-input-devices.txt` is captured from `/proc/bus/input/devices` and is used by
`EvdevDeviceLocatorTest`. It resolves:

- `sensor00fn11` → `event3` (the touchpad)
- `gpio-keys` → `event5` (the physical camera button)

Node numbering is not guaranteed stable across boots, which is why the daemon resolves by
name rather than hardcoding `event3`.
