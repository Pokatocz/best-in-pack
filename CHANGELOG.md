# Changelog

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
