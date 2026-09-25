# CozyUI+ compatibility theme sources

This directory is an **unofficial compatibility theme** for HuHoBot Inventory. It is
not an official continuation of CozyUI+, and HuHoBot Inventory does not claim the UI
design as its own work.

## Original UI work

- Project: CozyUI+
- Author: 零雾〇五 Fogg05
- Source: https://github.com/Fogg05/CozyUI-Plus
- Input pack: `CozyUI+ v1.10 no-fonts`
- License: GNU General Public License v3.0
- License copy: `LICENSE-CozyUI-Plus.txt`
- Adaptation date: 2026-09-07

The input pack declares support for Minecraft Java Edition 1.20 through 1.21.11.

## Inventory background adaptation

`background.png` starts from
`assets/minecraft/textures/gui/container/creative_inventory/tab_inventory.png` in the input pack.

- The complete 780x544 visible panel is retained at its original 4x GUI scale, including
  both rounded side edges; the canvas is not narrowed or stretched.
- The native creative layout is used: armor slots flank the player, with the offhand on
  the left and the storage/hotbar grids in their original positions.
- The unused trash button at output pixels x=688..759, y=436..519 is filled row by row
  with the adjacent native panel color sampled at x=764. No surrounding border is cropped.
- HuHoBot Inventory's extra Faithful player-preview matte is disabled because CozyUI+
  already supplies a complete dark player panel.

## Ender Chest background adaptation

`ender-chest-background.png` starts from
`assets/minecraft/optifine/gui/container/chest_ender.png` in the input pack.

The 9x3 panel follows the accepted visual construction requested for this addon:

1. Source x=4..699 is used as the 696-pixel panel width.
2. Source y=44..139 supplies the original top border and first row, including its
   original four-pixel row divider.
3. Source y=140..211 supplies the second row and its own four-pixel divider.
4. Source y=44..135 is vertically mirrored below the second row. This makes the
   original first row become row three and the original top border become a complete
   bottom border. Source y=136..139 is intentionally omitted so the central divider
   remains four pixels instead of being doubled to eight.
5. Eight transparent pixels are retained on every side, producing a 712x276 canvas
   without clipping the rounded edge.

No AI-generated pixels are used in either background.

## Item and player resources

The item icons, special item variants, runtime composites and unknown-item fallback in
this theme are byte-identical copies of the accepted `faithful32x` theme resources.
They remain governed by the Faithful License in `LICENSE-Faithful.txt`; the CozyUI+
GPL license does not relicense those independent files.

Player skins and armor previews are rendered at runtime by HuHoBot Inventory and are
not copied from CozyUI+.

The copied Faithful special set now includes fixed frame-00 clock/compass icons and individually
modeled creeper, skeleton, wither-skeleton, zombie, dragon, piglin and generic player heads. The
player-head file is only a fallback: captured Paper profile textures are prepared from Mojang's
texture service and take priority at render time. See the Faithful theme manifest and
`tools/GenerateFaithfulRc21Icons.java` for exact hashes and the non-mirrored GUI orientation.
