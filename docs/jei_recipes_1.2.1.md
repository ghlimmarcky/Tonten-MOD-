# Tonten 1.2.1 Recipe Data for JEI

Minecraft 26.1.2 / NeoForge 26.1.2

JEI displays these as normal Minecraft shaped crafting recipes from `src/main/resources/data/tonten/recipe`.

## Tonkachi

All Tonkachi recipes use this shape:

```text
M M M
  L
  L
```

`L` is `#minecraft:logs`.

| Item ID | Japanese Name | English Name | Material `M` | Category |
| --- | --- | --- | --- | --- |
| `tonten:wooden_tonkachi` | 木のトンカチ | Wooden Tonkachi | `#minecraft:planks` | `tools` |
| `tonten:stone_tonkachi` | 石のトンカチ | Stone Tonkachi | `minecraft:cobblestone` | `tools` |
| `tonten:cupper_tonkachi` | 銅のトンカチ | Copper Tonkachi | `minecraft:copper_ingot` | `tools` |
| `tonten:iron_tonkachi` | 鉄のトンカチ | Iron Tonkachi | `minecraft:iron_ingot` | `tools` |
| `tonten:golden_tonkachi` | 金のトンカチ | Golden Tonkachi | `minecraft:gold_ingot` | `tools` |
| `tonten:diamond_tonkachi` | ダイヤのトンカチ | Diamond Tonkachi | `minecraft:diamond` | `tools` |

## Temporary Blocks

### Utsusemi Block

Item ID: `tonten:utsusemi_block`

```text
P P P
P W P
P P P
```

| Key | Ingredient |
| --- | --- |
| `P` | `minecraft:paper` |
| `W` | `tonten:wooden_tonkachi` |

Category: `building`

### Solidify Space Block

Item ID: `tonten:solidify_space_block`

```text
G G G
G D G
G G G
```

| Key | Ingredient |
| --- | --- |
| `G` | `minecraft:gold_block` |
| `D` | `tonten:diamond_tonkachi` |

Category: `building`

## Item Tags

All Tonkachi items are included in:

- `tonten:tonkachi`
- `minecraft:tools`
- `minecraft:enchantable/durability`

