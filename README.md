# HuHoBot Inventory

HuHoBot Inventory 是一个不修改 HuHoBot 主体的 Minecraft 背包与末影箱图片查询 Addon。玩家可以在 QQ 群中查看已绑定游戏账号的在线实时数据或最近一次可信离线快照，并通过按钮选择多个已绑定账号。

- 作者：`RiegaLee`
- 插件标识：`HuHoBotInventory`
- 当前版本：`1.21.1`
- 状态：`FULL PASS`

正式构建请从仓库的 [Releases](../../releases) 页面下载，不要从第三方来源下载来历不明的 JAR。

## 功能

- 生成 704×664 的 Minecraft 背包图片。
- 生成独立的 704×308 末影箱图片。
- 在线玩家读取实时背包/末影箱；离线玩家读取持久化快照。
- 服务器完整重启且玩家保持离线后，仍可读取此前保存的快照。
- 与 GameAuthCode 双账号绑定兼容，使用上下排列的 QQ 按钮选择查询账号。
- 按钮限制发起用户、60 秒有效且只能消费一次，并提供准确的过期反馈。
- HuHoBot-Penguin AGENT 分支未安装外部绑定权威时，可读取其内置单账号绑定；旧绑定只允许在线本人查询，不冒充已验证离线身份。
- Faithful 32x 背包主题、混合分辨率物品图标、3D 人物皮肤第二层、盔甲/纹饰/附魔炫光及常见特殊物品渲染。
- 可选使用 SkinsRestorer 获取玩家当前皮肤。
- 内置兼容宿主与 HuHoBot API，不需要修改 HuHoBot-Penguin 主分支或 HuHoBot-Penguin AGENT 分支主体。

## 已验证环境

- [HuHoBot-Penguin 主分支（PenguinClient）](https://github.com/HuHoBot/PenguinClient) `1.2.2`
- [HuHoBot-Penguin AGENT 分支（PenguinAgent）](https://github.com/HuHoBot/PenguinAgent) `1.6.1`
- HuHoBot GameAuthCode `1.5.0`
- Minecraft/Paper `1.21.11`
- SkinsRestorer `15.x`（可选）
- Addon 字节码兼容 Java 8；实际 Java 版本仍须满足所用服务端要求

同一个 Inventory JAR 已在 HuHoBot-Penguin 主分支与 HuHoBot-Penguin AGENT 分支完成真实 QQ 验收。

## 安装

### 1. 确认服务端环境

- 使用 Spigot/Paper 服务端，并先确保 HuHoBot-Penguin 本体能够独立正常启动。
- 主分支与 AGENT 分支只能选择实际使用的一套，不要把两个 HuHoBot-Penguin 本体同时放进同一服务器。
- Inventory 编译为 Java 8 字节码，但启动服务器所需的 Java 版本仍以服务端和 HuHoBot-Penguin 的要求为准。

Inventory 必须依赖 Bukkit 插件名为 `HuHoBotPenguin` 的本体。缺少该插件时，服务端会拒绝加载 Inventory。

### 2. 选择账号绑定方式

#### HuHoBot-Penguin 主分支

如需普通玩家查询自己的背包，尤其是离线背包，请同时安装：

1. [AuthMeReloaded](https://github.com/AuthMe/AuthMeReloaded)，负责离线服玩家登录验证；
2. [HuHoBot GameAuthCode](https://github.com/RiegaLee/HuHoBotGameAuthCode)，负责经过游戏内验证的 QQ 与 Minecraft 多账号绑定；
3. HuHoBot Inventory。

玩家完成 AuthMe 登录后，在游戏内执行 `/authcode` 获取六位验证码，再在 QQ 群发送 `/绑定 <验证码>`。不需要先在 QQ 发起绑定。每个 QQ 最多可绑定两个 Minecraft 账号。

#### HuHoBot-Penguin AGENT 分支

Inventory 可以读取 AGENT 分支内置的单账号绑定，不要求另外安装 GameAuthCode。但这种旧式记录只包含玩家名，默认只允许玩家在线时查询；要安全读取持久化离线快照，应使用能够证明账号所有权的 GameAuthCode 绑定。

AuthMe 和 GameAuthCode 都不是 Inventory 本身的硬依赖；管理员只查询在线玩家时可以不安装它们。

### 3. 下载并放置插件

1. 完整停止服务器。
2. 首次安装前建议备份整个 `plugins/` 目录；升级时至少备份 `plugins/HuHoBotInventory/`。
3. 从 [最新 Release](https://github.com/RiegaLee/HuHoBotInventory/releases/latest) 下载 `HuHoBot-MinecraftInventory-1.21.1.jar`。不要下载 GitHub 自动生成的 `Source code (zip)` 或 `Source code (tar.gz)` 作为插件。
4. 删除或移走 `plugins/` 中旧版本的 Inventory JAR，避免同一插件存在多个版本；不要删除已有的 `plugins/HuHoBotInventory/` 数据目录。
5. 把 `HuHoBot-MinecraftInventory-1.21.1.jar` 直接放进服务端的 `plugins/` 目录，不要解压，也不要放进 HuHoBot-Penguin 的数据目录。
6. 如需显示玩家当前皮肤，可选安装 [SkinsRestorer](https://github.com/SkinsRestorer/SkinsRestorer)。未安装时会使用可用的皮肤来源或本地默认皮肤，不影响背包查询主体功能。

示例目录：

```text
server/
├─ server.jar
└─ plugins/
   ├─ HuHoBot-Penguin_Spigot-<版本>.jar
   ├─ HuHoBot-MinecraftInventory-1.21.1.jar
   ├─ AuthMe-<版本>.jar                 # 主分支绑定方案需要
   ├─ HuHoBot-GameAuthCode-1.5.0.jar   # 主分支绑定方案需要
   └─ SkinsRestorer.jar                # 可选
```

### 4. 首次启动与检查

1. 正常启动服务器，等待插件全部加载完成；不要使用 `/reload` 或第三方热重载插件。
2. 检查控制台没有 `UnknownDependency`、`NoClassDefFoundError` 或 Inventory 启动失败信息。
3. 在 HuHoBot 的“已安装扩展”列表中确认出现：

```text
HuHoBotInventory 1.21.1
作者：RiegaLee
说明：为 HuHoBot 提供 Minecraft 背包与末影箱图片查询
```

4. 首次启动后会生成配置文件：

```text
plugins/HuHoBotInventory/config.yml
```

默认配置已经启用背包、末影箱和离线快照。修改配置后请再次完整重启服务器。

### 5. 建立首份离线快照

1. 让已正确绑定的玩家至少登录服务器一次，并完成 AuthMe 登录（如已安装）。
2. 玩家退出、到达默认的 300 秒周期保存时间，或服务器正常关闭时，Inventory 会保存背包和末影箱快照。
3. 玩家离线后在 QQ 群发送 `/背包` 和 `/末影箱`，确认图片顶部出现 `Offline Snapshot` 与快照时间。

服务器被强制结束进程时，尚未到周期保存时间的最新改动可能来不及写入；部署和维护时应使用正常的 `stop` 流程。

### 6. 最小验收流程

安装完成后依次检查：

1. 玩家在线时发送 `/背包`，能收到当前背包图片。
2. 发送 `/末影箱`，能收到独立的末影箱图片。
3. 双账号用户发送 `/背包`，能看到上下排列的账号选择按钮；按钮不可用时可发送 `/背包 1` 或 `/背包 2`。
4. 等待账号选择超过 60 秒再点击，机器人应提示选择已超时。
5. 玩家正常退出并完整重启服务器，在玩家保持离线时再次查询，仍能读取最近一次可信快照。

若提示“未经游戏内验证”或“暂时没有离线快照”，请先确认玩家使用的是经过验证的绑定，并已至少登录和正常保存过一次。

## QQ 群命令

| 命令 | 别名 | 说明 |
| --- | --- | --- |
| `/背包` | `/inventory`、`/inv` | 查询本人已绑定账号的背包 |
| `/背包 <序号>` | `/inventory <序号>` | 文字方式选择多个绑定账号 |
| `/背包 <在线玩家名>` | — | 管理员查询指定在线玩家 |
| `/末影箱` | `/enderchest`、`/ec` | 查询本人已绑定账号的末影箱 |
| `/末影箱 <序号>` | `/enderchest <序号>` | 文字方式选择多个绑定账号 |

多账号用户直接发送 `/背包` 或 `/末影箱` 时，机器人优先显示纵向账号按钮；按钮不可用时会自动回退到文字序号流程。

`/inventorytest`（别名 `/invtest`）是关闭菜单发布的渲染诊断命令，通常只用于部署检查。

## 在线与离线数据

- 在线查询由 Bukkit 主线程捕获玩家的真实槽位状态。
- 玩家退出、周期保存或服务器正常关闭时，背包和末影箱分别保存快照。
- 普通背包和末影箱使用不同目录，数据不会互相覆盖。
- 离线图片顶部显示 `Offline Snapshot` 及快照时间。
- 未经游戏内验证的旧绑定默认不能读取离线快照。

运行期快照、皮肤缓存和用户绑定数据都已被 `.gitignore` 排除，不能上传到公开仓库。

## 配置

默认配置见 [`src/main/resources/config.yml`](src/main/resources/config.yml)。常用选项：

| 配置项 | 默认值 | 说明 |
| --- | ---: | --- |
| `online.cooldown-seconds` | `3` | 背包查询冷却 |
| `ender-chest.cooldown-seconds` | `3` | 末影箱查询冷却 |
| `offline-inventory.enabled` | `true` | 保存和读取离线背包快照 |
| `offline-ender-chest.enabled` | `true` | 保存和读取离线末影箱快照 |
| `player-preview.provider` | `auto` | 自动选择皮肤来源 |
| `player-preview.allow-texture-downloads` | `true` | 是否允许下载玩家皮肤纹理 |
| `render.theme` | `faithful32x` | 默认渲染主题 |
| `render.max-output-bytes` | `4194304` | 单张输出图片大小上限 |

## 从源码构建

普通服主只需下载 Release JAR。源码构建面向希望开发或移植 Addon 的维护者。

### 1. 准备 HuHoBot 编译依赖

准备兼容版本的：

- `huhobot-api-*.jar`
- `common-Bot-*.jar`

复制 `gradle.properties.example` 为 `gradle.properties` 并填写实际路径。该本地文件不会被 Git 提交。

### 2. 构建离线资产工具

此步骤不要求已经存在本地物品缓存：

```powershell
.\gradlew.bat clean vanillaAssetsToolJar
```

工具输出：

```text
build/libs/HuHoBot-InventoryAssetsTool-1.21.1.jar
```

### 3. 导入用户自行取得的资源

本项目 `1.21.1` 的已验收资源基线为 Minecraft Java Edition `26.1.2` 与 Faithful 32x `26.2`。请自行从合法来源取得客户端 JAR 和 Faithful 压缩包，然后执行：

```powershell
java -jar build/libs/HuHoBot-InventoryAssetsTool-1.21.1.jar import `
  "D:/path/to/26.1.2-client.jar" `
  "data/imported-assets/vanilla" `
  "26.1.2" `
  "D:/path/to/Faithful-32x-26.2.zip"
```

已验收输入会生成缓存目录：

```text
data/imported-assets/vanilla/26.1.2-B1B315857266-MB7-PD1337875
```

输入文件或哈希不同会产生不同缓存键；不要把未经复验的缓存伪装成正式基线。

### 4. 构建 Addon

```powershell
.\gradlew.bat clean build
```

输出文件：

```text
build/libs/HuHoBot-MinecraftInventory-1.21.1.jar
build/libs/HuHoBot-InventoryAssetsTool-1.21.1.jar
```

`build` 会运行自动测试、内置资源完整性检查和 JAR 边界检查，确保不打包 HuHoBot Core、QQ SDK、SkinsRestorer 实现、服务端运行数据、Minecraft 客户端 JAR 或开发审计文件。

## 第三方资源与非商业限制

仓库代码采用 MIT License，但内置 `faithful32x` 主题仍受 Faithful License 约束，不能被 MIT 重新许可。使用或分发含 Faithful 资源的版本时必须：

- 明确注明使用了 Faithful 32x GUI/纹理；
- 链接 https://faithfulpack.net/；
- 不得冒充 Faithful 官方项目；
- 不得商业化含有其资源的内容。

完整信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)、[Faithful License](THIRD_PARTY_LICENSES/Faithful-LICENSE.txt) 与[精确资源来源记录](src/main/resources/themes/faithful32x/SOURCES.md)。

Minecraft、HuHoBot、Paper/Spigot、SkinsRestorer 等名称及资源属于各自权利人。本插件不是其官方产品。

## 许可证

原创源代码及默认主题原创资源采用 [MIT License](LICENSE)，Copyright (c) 2026 RiegaLee。第三方内容适用其各自许可证。
