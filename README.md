<div align="center">
  <img src="https://raw.githubusercontent.com/mortuusars/resources/main/media/envelope/main.png" width="225">
  <br>
  <img src="https://img.shields.io/badge/Fabric-1.21.11-dbd0b4" alt="Fabric 1.21.11">
</div>

# Envelope — Fabric 1.21.11 Port

> A classy mailing system for Minecraft: letters, packages, mailboxes and pigeon couriers.

An unofficial **Fabric / Minecraft 1.21.11** port of
[mortuusars/Envelope](https://github.com/mortuusars/Envelope), distributed under the
original GPLv3 license.

> English first; 繁體中文在下半部。

---

## English

### About

This is a fork of the original [Envelope](https://github.com/mortuusars/Envelope) by
**mortuusars**. Upstream 0.8.2 targets Minecraft 1.21.1 on NeoForge and Fabric through an
Architectury multi-loader setup; this fork is a **single-target Fabric 1.21.11 build** — a
faithful port, not a behavioral rewrite.

This port is not affiliated with or supported by the original author. Please report issues
with this port here, not upstream.

For the relationship to upstream, intentional deviations, and known limitations, see
[`PORTING.md`](PORTING.md).

### Features

- **Mailboxes** with their own addresses. Put a letter or package in, feed the mailbox, and a
  pigeon (by day) or a bat (by night) picks it up and flies it to the recipient.
- **Letters** you can write, format and seal with a **seal stamp**; a soulbound seal stamp
  keeps sealed mail with you on death.
- **Packages** and paper boxes for sending items.
- **Address tags** to set where mail goes.
- **Mail services** you can write to: Mail Service, Automated Supply Service, Equine
  Assurance Bureau and Cloud Depository.
- **Payback**: request a payment for a letter and let the post office hold it until the
  buyer pays.
- Pigeons and pigeonholes, plus dovecotes and a collapsed mail hub to find in the world.

Full documentation lives in the upstream wiki sources:
[`wiki/content`](https://github.com/mortuusars/Envelope/tree/1.21.1/wiki/content).

### Requirements

| | |
|---|---|
| Minecraft | `1.21.11` |
| Fabric Loader | `0.19.2` (the version this port is built and tested with) |
| Java | `21` |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | required (`21.11.0` or newer) |
| [JEI](https://modrinth.com/mod/jei) | optional — shows the mail recipes |

The mod runs on both client and server. For multiplayer, install it (and its dependencies)
on the server as well as every client.

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Drop the following into your `mods/` folder:
   - this mod's jar (from [Releases](../../releases) or built from source)
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port)
3. Launch Minecraft.

### Building from source

Standard Fabric Loom project:

```
./gradlew build
```

The built jar is produced under `build/libs/`.

### Credits & License

- **Original mod:** [Envelope](https://github.com/mortuusars/Envelope) by mortuusars
  (authors: mortuusars, ceit.wonders) — GPLv3 —
  [CurseForge](https://curseforge.com/minecraft/mc-mods/envelope) ·
  [Modrinth](https://modrinth.com/mod/envelope)
- **Bundled library:** [Mortaar](https://github.com/mortuusars/Mortaar) by mortuusars — MIT
  (see `mortaar/LICENSE.md`)
- **This fork:** Fabric 1.21.11 port, with an added Traditional Chinese (`zh_tw`) translation
  — GPLv3 (see [`LICENSE.md`](LICENSE.md))
- **Porting notes:** [`PORTING.md`](PORTING.md)

---

## 繁體中文

### 關於

本專案是 **mortuusars** 所作 [Envelope](https://github.com/mortuusars/Envelope) 的 fork。
上游 0.8.2 透過 Architectury 多 loader 設定，同時支援 Minecraft 1.21.1 的 NeoForge 與 Fabric；
本 fork 則是**單一的 Fabric 1.21.11 目標**——忠實的移植，而非行為上的重寫。

本移植版與原作者無關，原作者也不提供支援。這個版本的問題請回報到這裡，不要回報給上游。

與上游的關係、刻意偏離之處、已知限制，詳見 [`PORTING.md`](PORTING.md)。

### 功能

- **信箱**有自己的地址。把信件或包裹放進去、餵食信箱，白天由鴿子、夜晚由蝙蝠來取件，飛去送給收件人。
- **信件**可以書寫、排版，並用**封蠟章**封口；靈魂綁定封蠟章封的郵件，死亡時會留在身上。
- 用**包裹**和紙箱寄送物品。
- 用**地址標籤**指定郵件寄到哪裡。
- 可以寄信給**郵務服務**：郵政服務、自動補給服務、馬匹保險局、雲端寄存處。
- **回寄**：替信件設定付款條件，由郵局扣住，等買方付款後才交出。
- 鴿子與鴿舍，以及散落在世界中的鴿房（dovecote）與墜毀郵務中心。

完整說明見上游 wiki 原始檔：
[`wiki/content`](https://github.com/mortuusars/Envelope/tree/1.21.1/wiki/content)（英文）。

### 需求

| | |
|---|---|
| Minecraft | `1.21.11` |
| Fabric Loader | `0.19.2`（本移植建置與測試所用的版本） |
| Java | `21` |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 必需 |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | 必需（`21.11.0` 以上） |
| [JEI](https://modrinth.com/mod/jei) | 選用——顯示郵寄配方 |

本 mod 在 client 與 server 端都會運行。多人遊玩時，server 與每個 client 都要安裝本 mod（及其依賴）。

### 安裝

1. 為 Minecraft 1.21.11 安裝 [Fabric Loader](https://fabricmc.net/use/installer/)。
2. 把以下檔案放進你的 `mods/` 資料夾：
   - 本 mod 的 jar（從 [Releases](../../releases) 下載、或自行 build）
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port)
3. 啟動 Minecraft。

### 從原始碼 build

標準的 Fabric Loom 專案：

```
./gradlew build
```

建置產物位於 `build/libs/`。

### 致謝與授權

- **原作 mod：** [Envelope](https://github.com/mortuusars/Envelope) by mortuusars
  （作者：mortuusars、ceit.wonders）— GPLv3 —
  [CurseForge](https://curseforge.com/minecraft/mc-mods/envelope) ·
  [Modrinth](https://modrinth.com/mod/envelope)
- **內嵌函式庫：** [Mortaar](https://github.com/mortuusars/Mortaar) by mortuusars — MIT（見 `mortaar/LICENSE.md`）
- **本 fork：** Fabric 1.21.11 移植，並新增繁體中文（`zh_tw`）翻譯 — GPLv3（見 [`LICENSE.md`](LICENSE.md)）
- **移植說明：** [`PORTING.md`](PORTING.md)
