# Porting Notes · 移植說明

> English first; 繁體中文在下半部。

---

## English

### Relationship to upstream

This repository is a fork of [mortuusars/Envelope](https://github.com/mortuusars/Envelope),
ported from upstream **0.8.2** (Minecraft 1.21.1, commit `e8987c08`) to
**Fabric / Minecraft 1.21.11**. It is distributed under the original GPLv3 license
(see `LICENSE.md`). The full upstream history is kept; the port's own commits start after
`e8987c08` on the `port-1.21.11` branch.

Upstream is an Architectury multi-loader project (`common`, `fabric`, `neoforge`). This fork
keeps that directory layout so upstream diffs stay comparable, but builds `common` and
`fabric` as **one Fabric project**. The `neoforge/` folder is left in the tree untouched and is
not built.

Envelope depends on mortuusars' library [Mortaar](https://github.com/mortuusars/Mortaar)
(MIT). Only the classes Envelope uses are bundled, under `mortaar/`, repackaged to
`io.github.mortuusars.envelope.lib.mortaar` so they cannot clash with a separately installed
Mortaar. Its license is kept in `mortaar/LICENSE.md` and inside the jar.

The goal was a faithful port: same items, blocks, mail flow and services as upstream 0.8.2.

### Intentional deviations from upstream

- **Removed integrations.** The Sable and Every Compat integrations were removed: neither mod
  has a Minecraft 1.21.11 version to build against.
- **No world migration.** Worlds saved with upstream on 1.21.1 are not supported; this port
  only reads the data it writes itself.
- **Letter cloning in the 2×2 grid.** Upstream only allowed copying letters in a 3×3 crafting
  table. Minecraft 1.21.11 no longer tells a recipe how big the crafting grid is, so the port
  cannot keep that limit; vanilla's own book cloning recipe dropped it the same way.
- **Lost mail "plants" gives bamboo.** Upstream's loot table listed bamboo sapling, which has
  no item form, so on 1.21.1 that roll gave nothing (and 1.21.11 rejects the whole table). The
  port gives bamboo, which is what the entry was meant to be.
- **Own spawn egg textures.** 1.21.11 needs a texture per spawn egg; the port draws its own
  from the two colours in upstream's code rather than copying vanilla assets.
- **Item model definitions.** 1.21.4 moved item models to `assets/envelope/items/*.json`. These
  were written by script from upstream's models, because upstream's data generator lives in the
  NeoForge project that is not built here.
- **Mail recipes are synced to clients** (Fabric API recipe sync) so JEI can show them. Clients
  and server both need this mod installed, as they did before.
- **Debug-only Mortaar overlays removed.** Mortaar's client "Bugger" debug mixins hooked
  rendering code that no longer exists; the server-side test framework is kept.
- **Blocks replaced by commands.** 1.21.11 commands (`/setblock`, `/fill`, `/clone`) skip the
  usual block-removal hook. The port handles this explicitly: a replaced mailbox drops its
  stored inbox and is unregistered (stale registrations are also cleaned up when mail is sent
  to them), and replaced letters and packages drop their contents, as they did upstream.
- **Compatibility fixes.**
  - The address and name boxes keep their clipping balanced when another mod (Modern UI) cancels
    text-box rendering; otherwise the screen's title and buttons disappeared.
  - The mailbox naming and letter editing screens keep keyboard focus on the text box when
    keyboard navigation is active.
- **Added translation:** Traditional Chinese (`zh_tw`).

### Known limitations

- **Cinematic Interaction** (0.18.30): its crafting screen only shows recipes from vanilla
  serializers, so Envelope's special crafting recipes (attaching an address tag, sealing, …)
  do not show a result there. Use the vanilla crafting table.
- **Missing sound** `envelope:item.soulbound_seal_stamp.unlock` — also missing upstream; left as
  is.
- **Not tested:** CarryOn relocation of mailboxes, the Create-only recipe
  `paper_box_from_cardboard`, and online-mode (premium-account) servers.

### Tests

- `./gradlew runClientGameTest` — scripted client tests (Fabric client gametest API, sources in
  `src/gametest`): opens a world, runs upstream's Bugger tests plus the port's own, checks
  screens, recipe sync, save/reload, mail services, and a dedicated server join.
- `/mortaar debug tests` (operator only) runs the Bugger tests inside a running game.

### Build

Standard Fabric Loom project:

```
./gradlew build
```

The built jar is produced under `build/libs/`.

---

## 繁體中文

### 與上游的關係

本專案是 [mortuusars/Envelope](https://github.com/mortuusars/Envelope) 的 fork，從上游
**0.8.2**（Minecraft 1.21.1，commit `e8987c08`）移植到 **Fabric / Minecraft 1.21.11**，
以原始 GPLv3 授權散布（見 `LICENSE.md`）。上游的完整歷史保留，移植的 commit 從 `port-1.21.11`
分支上 `e8987c08` 之後開始。

上游是 Architectury 多 loader 專案（`common`、`fabric`、`neoforge`）。本 fork 保留同樣的資料夾
結構，方便跟上游比對差異，但把 `common` 與 `fabric` 當成**單一 Fabric 專案**建置。`neoforge/`
資料夾原封不動留著，不參與建置。

Envelope 依賴 mortuusars 的函式庫 [Mortaar](https://github.com/mortuusars/Mortaar)（MIT）。
本 fork 只內嵌 Envelope 用得到的類別，放在 `mortaar/`，並改套件名為
`io.github.mortuusars.envelope.lib.mortaar`，避免和另外安裝的 Mortaar 衝突。授權保留在
`mortaar/LICENSE.md` 與 jar 內。

目標是忠實移植：物品、方塊、寄信流程與郵務服務都與上游 0.8.2 相同。

### 刻意偏離上游之處

- **移除的整合。** 移除了 Sable 與 Every Compat 整合：兩個 mod 都沒有 Minecraft 1.21.11 版可以對著建置。
- **不轉換舊世界。** 不支援上游在 1.21.1 存下的世界；本移植只讀得回自己寫出的資料。
- **2×2 格也能複製信件。** 上游只允許在 3×3 工作台複製信件。Minecraft 1.21.11 不再告訴配方
  合成格有多大，所以無法保留這個限制；原版自己的複製書本配方也同樣拿掉了它。
- **遺失的郵件「植物」那一項給竹子。** 上游戰利品表寫的是竹筍，它沒有物品形態，所以在 1.21.1
  抽到那一項等於什麼都沒有（1.21.11 則會整張表拒收）。本移植改給竹子，也就是那一項原本的用意。
- **自己畫的生怪蛋貼圖。** 1.21.11 每顆生怪蛋都要有貼圖；本移植依上游程式裡的兩個顏色自己畫，
  不複製原版資產。
- **物品模型定義。** 1.21.4 把物品模型移到 `assets/envelope/items/*.json`。這些檔案是用腳本從
  上游的模型轉出來的，因為上游的資料產生器在沒有建置的 NeoForge 專案裡。
- **郵寄配方會同步給玩家端**（Fabric API 配方同步），JEI 才看得到。玩家端和伺服器都要裝本 mod，這點跟以前一樣。
- **移除 Mortaar 只在除錯用的畫面輔助。** Mortaar 的 client 端「Bugger」除錯 mixin 掛在已不存在的
  渲染程式上；伺服器端的測試框架保留。
- **被指令換掉的方塊。** 1.21.11 的指令（`/setblock`、`/fill`、`/clone`）會略過一般的方塊移除流程。
  本移植另外處理：被換掉的信箱會丟棄暫存的收件匣並解除登記（殘留的登記在有人寄信過去時也會清掉）；
  被換掉的信件與包裹方塊會掉出內容，跟上游一樣。
- **相容性修正。**
  - 其他 mod（Modern UI）中途取消文字框的繪製時，地址欄與取名欄的裁切範圍仍會正確關閉；
    不然畫面的標題與按鈕會消失。
  - 啟用鍵盤導覽時，信箱取名畫面與信紙編輯畫面的鍵盤焦點會留在文字框上。
- **新增翻譯：** 繁體中文（`zh_tw`）。

### 已知限制

- **Cinematic Interaction**（0.18.30）：它的工作台畫面只顯示原版序列化器的配方，所以 Envelope 的
  特殊合成（貼地址標籤、封蠟等）在那裡不會顯示成品。請改用原版工作台。
- **缺少音效** `envelope:item.soulbound_seal_stamp.unlock`——上游同樣缺少，維持原狀。
- **沒有測過：** 用 CarryOn 搬信箱、只有裝 Create 才會出現的配方 `paper_box_from_cardboard`、
  正版驗證（online-mode）伺服器。

### 測試

- `./gradlew runClientGameTest`——腳本化的玩家端測試（Fabric client gametest，原始碼在
  `src/gametest`）：開一個世界，跑上游的 Bugger 測試與本移植自己的測試，檢查畫面、配方同步、
  存檔重開、郵務服務，以及連線到專用伺服器。
- `/mortaar debug tests`（限管理員）在執行中的遊戲裡跑 Bugger 測試。

### 從原始碼 build

標準的 Fabric Loom 專案：

```
./gradlew build
```

建置產物位於 `build/libs/`。
