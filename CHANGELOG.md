First release. Your status effects now come with the one thing Minecraft never shows you: how much time is left. Pick how you want to see it — worked into Minecraft's own icons, or as a display you place yourself — and everything else follows from that one choice.

Full documentation: **https://eymistaken.github.io/Eymistaken-s-HUD-Website/**

---

## One setting decides everything

**Display Mode** has four values and the whole mod hangs off it. **Vanilla** leaves Minecraft's icons untouched. **Enhanced** keeps them and reads the duration off them. **Custom** hides them and draws its own list wherever you put it. **Hidden** shows nothing at all.

There is deliberately no second switch anywhere that can disagree with this one, so no configuration exists where the same effect is on screen twice.

A separate master toggle turns the mod off completely, which is different from Hidden: switched off, Minecraft's icons behave exactly as they would with the mod uninstalled.

> **[IMAGE]** Sağ tık menüsü açık, Display Mode satırı ve seçili değer görünüyor. Önerilen 1280x720.

---

## Duration on the vanilla icons

Enhanced mode is the default and the one most people will stay on. A hairline runs along the inside of each icon's bottom edge, tinted with the effect's own colour and backed by nothing — no black track, so a full-duration effect looks untouched. Thickness is one to three pixels.

**Drain** is the alternative: no line at all, the icon simply darkens from the top down as the effect is spent. A countdown and a level number can be turned on as well, both off by default.

Minecraft does not report where it put those icons, so their grid is re-derived. Every number in that arithmetic is a slider — icon spacing, row gap and a nudge on both axes — so a future change to Minecraft's layout is something you can correct yourself.

> **[IMAGE]** Enhanced modda efekt ikonları, altlarındaki renkli çizgiler farklı doluluklarda. Önerilen 1280x720.

---

## A display of your own

Custom mode draws a list you control: five entry layouts, four growth directions, wrapping after a chosen number of entries per line, and scaling from 50% to 300%.

Icons can be the effect's own sprite, a coloured dot, or nothing. Names, Roman or numeric levels, and three duration formats are each optional. Colours come from one setting, from the effect's category, or from a rainbow, and a duration bar can sit under the text, under the icon, or drain across the row as its background.

Ambient effects from beacons and conduits, effects with hidden particles, whole categories and individually named effect ids can all be filtered out. What remains sorts by time left, name, category or Minecraft's own ordering. The list can also split into beneficial and harmful halves that are placed independently.

> **[IMAGE]** Custom mod, kendi listen ekranda: ikon, isim, seviye, süre, süre barı ve arka plan. Önerilen 1280x720.

---

## The expiry warning

Below a threshold you set, an effect turns your warning colour and pulses. The same setting drives both displays, so a vanilla icon and a custom row look the same when time is short.

Duration bars need a length to measure against and Minecraft does not expose one, so the mod remembers the longest duration it has seen for each effect and re-anchors when you top it up.

---

## Works with Eymistaken's HUD, and without it

With **Eymistaken's HUD** installed, this registers as a single plugin module: draggable in the editor, resizable with the scroll wheel, and carried through named presets, share codes and per-server configs like any built-in module. On version 1.1.1-8 and later its settings page is split into tabs; on earlier versions everything still appears, as one list.

Without it, the same display is drawn from its own anchor and offsets, and **Mod Menu** with **Cloth Config** gives you a settings screen. All three of those mods are optional and none is a hard dependency.

A keybind cycles the display modes and prints the one you landed on above your hotbar. It ships unbound.

---

## Requirements

Fabric for Minecraft 26.2 with Fabric API. Client-side only.

---

## For developers

The mod publishes three events onto Eymistaken's HUD's event bus when that mod is present: an effect being gained, an effect approaching its expiry threshold, and an effect being lost, the last of which distinguishes running out from being cured. Subscribe from your own plugin entrypoint if you want to react to them.

Plugin API documentation: **https://eymistaken.github.io/Eymistaken-s-HUD-Website/api-guide.html**
