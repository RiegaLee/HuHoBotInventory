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

### 快速安装

安装前，请先确认服务器已经装好并能正常使用 HuHoBot-Penguin 主分支或 AGENT 分支。

1. 从 [最新 Release](https://github.com/RiegaLee/HuHoBotInventory/releases/latest) 下载 `HuHoBot-MinecraftInventory-1.21.1.jar`。
2. 将下载的 JAR 放入服务器的 `plugins/` 目录。
3. 重启服务器。
4. 在 HuHoBot 的“已安装扩展”中看到 `HuHoBotInventory 1.21.1`，就表示安装成功。

首次启动会自动生成 `plugins/HuHoBotInventory/config.yml`，一般不需要修改即可使用。

### 我还需要安装哪些插件？

| 使用的 HuHoBot 版本 | 建议安装 |
| --- | --- |
| HuHoBot-Penguin 主分支 | Inventory、[AuthMeReloaded](https://github.com/AuthMe/AuthMeReloaded)、[GameAuthCode](https://github.com/RiegaLee/HuHoBotGameAuthCode) |
| HuHoBot-Penguin AGENT 分支 | 只安装 Inventory 即可使用内置单账号绑定；需要双账号或离线查询时，再安装 AuthMe 和 GameAuthCode |

Inventory 本身不负责登录和绑定。AuthMe 用来验证游戏账号，GameAuthCode 用来把经过验证的 Minecraft 账号绑定到 QQ。

### 绑定游戏账号

使用 GameAuthCode 时：

1. 玩家进入服务器并完成 AuthMe 登录。
2. 在游戏内输入 `/authcode`，获取六位验证码。
3. 回到 QQ 群发送 `/绑定 <验证码>`。
4. 机器人提示绑定成功后，即可查询背包。绑定第二个账号时，用另一个游戏账号重复以上步骤。

使用 AGENT 分支内置绑定的服主，可以继续使用原有绑定方式；这种绑定默认支持玩家在线时查询。

### 开始查询

绑定完成后，在 QQ 群发送：

- `/背包`：查看背包；
- `/末影箱`：查看末影箱。

绑定了两个账号时，机器人会显示账号选择按钮。按钮不能使用时，也可以发送 `/背包 1`、`/背包 2`、`/末影箱 1` 或 `/末影箱 2`。

### 离线查询说明

玩家至少需要成功登录服务器一次，Inventory 才能保存背包和末影箱快照。快照会在玩家退出、服务器正常关闭或定时保存时更新。

如果机器人提示“未经游戏内验证”或“暂时没有离线快照”，请让玩家完成 GameAuthCode 绑定并登录服务器一次。AGENT 分支原有的旧式绑定默认只能查询在线玩家。

### 可选：显示玩家皮肤

如需优先显示玩家当前使用的皮肤，可以安装 [SkinsRestorer](https://github.com/SkinsRestorer/SkinsRestorer)。不安装也能正常查询背包和末影箱。

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

本项目 `1.21.1` 的已验收资源基线为 Minecraft Java Edition `26.1.2` 与 Faithful 32x `26.2`。请自行从合法来源取得客户端 JAR 和 Faithful 压缩包，然后把下面两个路径占位符替换为文件的实际位置。文件可以放在任意磁盘或目录，不要求使用 D 盘。

```text
java -jar build/libs/HuHoBot-InventoryAssetsTool-1.21.1.jar import "<Minecraft 客户端 JAR 路径>" "data/imported-assets/vanilla" "26.1.2" "<Faithful 32x 压缩包路径>"
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
