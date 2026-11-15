# Changelog

## 0.9.5 – EMI
- EMI support, the same as JEI and REI: click a row for the recipe (right-click for uses), press B over an item in
  EMI's item list to jump to its row, items hidden in EMI are left out of the rankings, and EMI's own
  "view recipes" / "view uses" keys work on the rows.
- EMI exists for Fabric 1.20–1.21.1, Forge 1.20–1.20.2 and NeoForge 1.20.4–1.21.1, so the bridge is in those ten
  files. With EMI and JEI both installed, EMI is used (EMI takes over JEI's screens). The other 28 files only
  carry the new version number.
- Known (not new, found by the new real-install test): JEI for Forge 1.20.2 only starts when the first inventory
  screen opens. Until then a click on a row answers "The recipe viewer is still loading". EMI is not affected.
- Tools: `tools/smokeemi.sh` (the in-game test with EMI), `tools/prodself.sh` (the whole in-game test in a real
  install with a chosen recipe viewer) and `tools/verifyall.sh` (`verify.py` for every build).

## 0.9.4
- The release JARs no longer include the in-game self-test (`SmokeTest`, which drives the mouse with `xdotool`
  on the test machine). It only runs in development runs; `gradle build -PwithSelfTest` keeps it. No gameplay changes.
- `tools/jarcheck.py` checks every release JAR before publishing; `tools/prodcheck.sh` / `tools/prodall.sh` start
  the JARs in real Fabric, Forge and NeoForge installs of every game version they cover.
- Self-test: works in remapped production installs too and steps over the loader's "loaded with warnings" screen.

## 0.9.3 – Minecraft 1.20.x
- Fabric, Forge and NeoForge for every Minecraft version from 1.20 to 1.20.6 (11 more files, same features).
  NeoForge 1.20.1 uses the Forge 1.20–1.20.1 file.

## 0.9.3
- Modpacks can set the default open key in `config/bestinpack/bestinpack.json` (`"openKey": "N"`).

## 0.9.2
- No false "key conflict" chat warning on 1.21.11+ (vanilla's F3 + B debug key is not a clash).

## 0.9.1
- Only items that are in the creative inventory are ranked (the same list JEI shows), so debug items without a
  recipe stay out. `onlyCreativeItems: false` brings back every registered item.

## 0.9.0
- Fabric, Forge and NeoForge for every Minecraft version from 1.21 to 26.3 (27 files).
- First-join chat hint ("Press B to see…") and a warning when another mod uses the same key – both can be
  switched off in the config.
