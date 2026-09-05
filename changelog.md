# Changelog

## 1.21.1 — 2026-09-05

### AGENT Addon Metadata

- AGENT AddonManager 中的作者由占位值 `HuHoBot Inventory` 改为 `RiegaLee`。
- 扩展说明汉化为“为 HuHoBot 提供 Minecraft 背包与末影箱图片查询”。
- Bukkit `plugin.yml` 同步补充中文说明与作者，命令、兼容宿主、绑定和渲染行为不变。
- 104/104 自动测试与 JAR 边界检查通过；用户确认 AGENT 扩展列表中的 1.21.1、中文说明、`RiegaLee` 和 12 个命令全部正确，状态提升为 `FULL PASS`。

### First Public Source Release

- 补充中文 README、MIT 源码许可证、第三方资源声明和 Gradle Wrapper。
- HuHoBot API 与 QQ SDK 编译依赖支持通过 Gradle 属性或环境变量指定，不再只能使用维护者本机目录结构。
- 离线 Vanilla 资产工具改为只依赖 `compileJava`，可在尚未生成用户本地资源缓存时先独立构建。
- QQ Addon 的三个命令说明汉化；查询、按钮、绑定、快照与渲染行为不变。
- Faithful 32x 资源继续适用独立 Faithful License，不纳入 MIT；JAR 内保留许可证与精确来源记录。
- 公开发布构建再次通过 104/104 测试、资源完整性、Addon JAR 与独立资产工具边界检查。

## 1.21.0 — 2026-09-05

### Official Upstream Embedded Host

- 将稳定 API 1.3、owner-scoped 服务宿主、官方命令桥、文本/内存图片网关和动态绑定门面直接打入 Inventory JAR；不再要求独立兼容桥，也不修改官方 HuHoBot 主体。
- 同一 JAR 兼容官方 Mainline 1.2.2 与 AGENT 1.6.1；Mainline 可由 GameAuthCode 1.4.0 提供双账号绑定，AGENT 可读取内置单账号记录。
- AGENT 旧绑定只含玩家名，映射为 `LEGACY_UNVERIFIED`：默认允许在线本人查询，不冒充可信离线绑定。

### Validation

- Inventory 104/104、GameAuthCode 12/12 自动测试及 clean build 通过。
- 官方 Mainline 1.2.2 完成真实 QQ 双账号按钮、在线/离线 PNG 与超时；AGENT 1.6.1 完成 AddonManager、原生绑定在线 PNG 与离线拒绝。AGENT 发布包的 Bukkit 元数据仍自报 1.6.0，按上游版本遗漏记录；本地 1.6.1 另通过隔离 Paper 启停与自动兼容门禁。1.21.0 状态提升为 `FULL PASS`。

## 1.20.3 — 2026-09-05

### Proactive Expired Button Feedback

- 真实 1.20.2 日志确认互动事件 ID 不能作为群消息 `msg_id`；QQ 以 `40034024 请求参数msg_id无效或越权` 拒绝明确超时文本，客户端只剩固定“操作成功”。
- 超时文本改走 Inventory 已有的 `MessageGateway.sendText(groupOpenId, text)` 主动群消息通道；过期 ACK 使用 `code=1`（操作失败），不再显示错误的“操作成功”。
- 主动反馈的异常、空结果和非成功状态均写入 Inventory 日志；HuHoBot Core、共享 API 与 GameAuthCode 仍未修改。

### Validation

- Inventory 102/102 自动测试通过；clean build、Bundled Asset、Addon JAR 边界和独立资产工具 JAR 检查全部通过。
- 用户提供同一 `14:56` 时间戳的离线背包与离线末影箱图片，并确认玩家在重启前后未上线、重启后直接查询；两套 Cross-Restart 门禁通过。
- 1.20.3 的真实 QQ `/背包` 与 `/末影箱` 61 秒超时文本均已通过；结合即时按钮和两套离线 Cross-Restart 证据，版本状态提升为 `FULL PASS`。

## 1.20.2 — 2026-09-05

### Expired Button Feedback

- 将超过 60 秒的账号选择与真正的重复回调分离：有效期内再次消费同一 nonce 仍返回 QQ `code=3`，过期 nonce 不再冒充“重复操作”。
- 过期互动以 `code=0` 正常确认，Inventory Addon 再通过互动事件被动回复明确文案：`/背包` 与 `/末影箱` 分别提示重新发送对应命令。
- 新增已消费 nonce 的短期记录，只保留至原选择轮次截止时间；不会把旧按钮重新变为可用。
- HuHoBot Core、共享 API 与 GameAuthCode 未修改。

### Validation

- 用户已确认 1.20.1 背包按钮、“已选择”和末影箱查询正常；重启后的实图确认 PV8 头顶与空白合成区。
- Inventory 102/102 自动测试通过；clean build、Bundled Asset、Addon JAR 边界和独立资产工具 JAR 检查全部通过。
- 1.20.2 的 61 秒真实 QQ 过期文案待部署复验。

## 1.20.1 — 2026-09-05

### Vertical Full-Name Account Buttons

- 双账号 QQ Keyboard 从一行两个按钮改为两行、每行一个按钮，使每个账号独占整行宽度。
- 移除旧的 10 字符主动截断；标签上限改为 18 个 Unicode code point，可完整容纳“序号 + 空格 + 16 字符 Minecraft ID”。
- 两个按钮继续统一使用 `style=1`；点击权限、60 秒有效期、一次性消费、正确 PUT ACK 及文字命令回退不变。
- 新增 Keyboard JSON 结构测试，断言两行各一个按钮，并以 16 字符玩家名验证标签不被截断。

### Validation

- Inventory 101/101 自动测试通过；clean build、Bundled Asset、Addon JAR 边界和独立资产工具 JAR 检查全部通过。
- 真实 QQ 桌面端/移动端按钮宽度与 16 字符标签显示待 Paper/QQ 复验。

## 1.20.0 — 2026-09-05

### PV8 Player Skin Detail + Creative-Style Inventory

- 使用用户提供的原始 `蓝色末影人头像.png` 复现头顶缺块；确认皮肤本身 64×64 UV 完整，缺失来自 Java2D 相邻投影面之间的取整裂缝，以及稀疏第二层只有平面、没有可见厚度。
- PV8 为共享投影边加入 `0.6` 输出像素重叠，封闭头顶/正面/侧面的黑色裂缝；Hat、Jacket、双袖和双裤腿第二层在非透明像素边界生成朝向镜头的侧壁，单像素装饰仍保持原生 UV 与 nearest-neighbor 像素风格。
- 基础皮肤、第二层和装备改为三个明确材质通道；装备继续在皮肤之后绘制。Player Preview 缓存键升级为 `pv8-<width>x<height>`，不会复用 PV7 错误图片。
- 参考 `tr7zw/3d-Skin-Layers` commit `64326789a5caf31b6651f2edc8dd23b59f88f6f6` 的公开行为与标准 Hat UV；没有复制或捆绑该项目的源码、二进制或材质。
- Faithful 背景移除右上 2×2 合成格、箭头和输出格，改为创造模式风格的空白区域；盔甲、副手、人物、背包 27 格和快捷栏 9 格坐标均不改变。
- Bundled Asset Pack 升级为 `inventory-assets-v11-mb7-pv8-glint-bed-shield-enderchest-hd64-pd1337875`，主题版本升级为 `1.8.0`。

### Validation

- 用户原皮肤 SHA-256：`ACCDDA8D620FC8530B96B3C891460F3EDC15F231CE00D59A08E579DFCDFEC4FF`；PV7/PV8 人物对照中共有 2290 个像素变化，新增 331 个更不透明像素，原色蓝像素由 65 增至 465。
- 新增原皮肤、PV8 体素侧壁、面裂缝、创造风格空白区及完整 704×664 合成回归；Inventory 100/100 自动测试通过。
- clean build、Bundled Asset 完整性、Addon JAR 边界和独立资产工具 JAR 检查全部通过；真实 Paper/QQ Online、Offline Snapshot 与 Cross-Restart 待复验。

## 1.19.3 — 2026-09-05

### Uniform Unselected Button Style

- 根据 QQ 桌面端/移动端截图修复第一账号在未点击时呈现为实心强调色、看似已选中的问题。
- 移除第一账号专用的 `style=4`；所有账号按钮统一使用 `style=1`，初始视觉状态一致，主账号不再通过颜色暗示。
- 保留点击后的“已选择”文案、一次性消费、正确 PUT ACK 和精简正文。
- 新增两个账号按钮样式均为 1 的回归断言；Inventory 97/97 自动测试及 clean build/JAR 边界检查通过。

## 1.19.2 — 2026-09-05

### Correct Interaction PUT ACK

- 新 Paper 日志确认按钮发送与 `INTERACTION_CREATE` 接收均正常，但 SDK 的互动回应返回 HTTP 405，导致 QQ 客户端等待后显示“请求超时”。
- 根因定位到 SpringTool 0.6.4 HTTP 代理：它只显式构造 POST，声明为 PUT 的接口最终沿用默认 GET；Inventory 不再调用该错误回应路径。
- Addon 复用运行中 QQ 客户端的认证上下文，直接发送官方要求的 `PUT /interactions/{interaction_id}`，状态码非 2xx 时记录明确诊断；HuHoBot Core 与共享 API 仍未修改。
- 按钮消息正文精简为“请选择账号（60 秒内有效）：”，账号名只保留在按钮；只有按钮发送失败时才显示紧凑的文字命令备选。

### Validation

- 新增本地 HTTP 服务器测试，实际断言 PUT 请求行、认证头与 ACK JSON；Inventory 97/97 自动测试和 clean build/JAR 边界检查通过。
- 1.19.1 的首次点击实际查询与图片发送成功，但 ACK 失败；1.19.2 待 Paper/QQ 复验。

## 1.19.1 — 2026-09-05

### Delayed QQ Button Bridge

- 根据真实 Paper 日志修复启动顺序：Inventory 启用时 QQ `QClient` 尚未初始化，旧版只接入一次并永久降级文字；现在保留 Addon 自有路由，首次实际发送按钮时延迟接入已就绪的 QQ `Starter`。
- Inventory Addon 直接生成符合当前群消息协议的 Markdown + Keyboard：`content` 保持空、Keyboard 只放消息顶层，不再调用会重复/混填字段的 Core Markdown helper。
- 校验 QQ 发送响应必须包含消息 ID；被拒绝时记录明确诊断并回退文字选择，便于区分协议、权限和网络问题。
- HuHoBot Core 仍为 `1.2.1-addon.2`，共享 API 仍为 `1.3.0`，均未修改。

### Validation

- 新增群 Markdown/Keyboard payload 结构测试；Inventory 96/96 自动测试及 clean build/JAR 边界检查通过。
- 真实日志已确认 1.19.0 失败原因为启动时间差：Inventory `14:15:40`，QQ Ready `14:15:46`；1.19.1 待 Paper/QQ 复验。

## 1.19.0 — 2026-09-05

### QQ Callback Account Selection

- 双账号 `/背包` 与 `/末影箱` 选择提示新增 QQ 原生回调按钮；点击后直接查询，不再要求玩家手工输入第二条带序号命令。
- 每轮选择使用随机 nonce、60 秒有效期并一次性消费；群、QQ openid、命令类别、序号和当前绑定均在插件侧再次校验。
- 按钮同时设置 QQ 指定用户权限，其他用户不能代点；背包与末影箱 data prefix 独立，旧按钮不能消费新选择。
- 保留 `/背包 <序号>` 与 `/末影箱 <序号>`；按钮/Markdown 发送失败自动回退文字列表。
- 按钮发送、QQ `INTERACTION_CREATE` 监听、路由与 ACK 全部放在 Inventory Addon 自己的可选适配层；HuHoBot API 保持 `1.3.0`，Mainline Core 保持 `1.2.1-addon.2`，两者均不需要修改或替换。
- Inventory 只在编译期引用 HuHoBot 已有的 QQ SDK 类型，成品 JAR 不捆绑 Core/QQ SDK；适配层接入失败时自动保留原文字序号流程。

### Validation

- API 14/14、Mainline Spigot Host 10/10、Inventory 95/95 自动测试通过。
- 未改动的 Core `addon.2` Shadow JAR 可重建；Inventory clean build、Bundled Asset 与 Addon JAR 边界检查通过。
- Paper + QQ 真实按钮门禁待验收。

## 1.18.0 — 2026-09-05

### Multi-Account Text Selection

- 双账号用户可在 60 秒内通过 `/背包 <序号>` 或 `/末影箱 <序号>` 选择查询账号；选择一次性消费且查询前复核绑定。
- 提示使用本次实际调用的中文/英文别名，不再固定显示英文命令。
- 本地自动测试通过，随后由 1.19.0 在保留文字流程的基础上增加按钮入口。

## 1.17.0 — 2026-09-05

### Ender Chest Query

- 新增 `/末影箱`，兼容 `/enderchest` 和 `/ec`；普通用户按群内已绑定身份查询自己，管理员可显式查询在线玩家。
- 新增 Bukkit 主线程 `Player#getEnderChest()` 数据源，严格捕获 27 槽，不读取或伪造离线玩家对象。
- 新增 704×308 Faithful 32x 三行容器渲染器；物品复用 MB7 混合分辨率和 64×64 最终槽位管线。
- 新增独立 `data/offline-ender-chest-snapshots` 持久化目录，支持退出、周期、关闭及 Paper 重启后离线读取，不覆盖普通背包快照。
- Config Schema 升级至 8；Bundled Asset Pack 升级为 `inventory-assets-v10-mb7-pv7-glint-bed-shield-enderchest-hd64-pd1337875`。

### Validation

- 新增数据源、渲染器、命令注册、Schema 7 → 8 迁移和独立离线 Store 回归；93/93 自动测试通过。
- clean build、Bundled Asset 完整性和 JAR 边界检查通过。
- 本地 Online/Offline 参考图均为 704×308；真实 Paper/QQ Online 与 Offline Snapshot 已通过。Offline 图显示 `2026-09-05 10:00`，两图槽位区域逐像素一致。
- 玩家保持离线并完整重启 Paper 后仍返回同一 `10:00` 快照；重启前后 PNG 文件大小、SHA-256 与全部字节一致。Cross-Restart 通过，1.17.0 标记为 `FULL PASS`。

## 1.16.0 — 2026-09-05

### Mixed-Resolution HD64 Asset Pipeline + Direct-Size Player Preview

- 修正 MB6 将 64×64 方块模型工作画布缩回 32×32、再由最终背包放大的细节损失；MB7 直接保留 64×64 方块模型成品。
- Faithful 普通 2D 物品继续保持原生 32×32，不进行全局放大或双线性平滑；最终合成仍按像素整数倍缩放。
- 16 种 Bed、11 个 Chest/Seasonal Chest 和 17 个 Shulker Explicit Override 统一重烘焙为 64×64。
- 移除早期历史遗留的手绘 32×32 Shield；改由 Faithful 原始 `shield_base_nopattern.png`、Minecraft 26.1.2 `ShieldModel` 几何和 GUI 变换生成 64×64 普通盾牌。旗帜底色/图案因 Snapshot 尚无对应组件数据而继续延期。
- 根据真实 Paper/QQ 首轮图修正盾牌纵向位置：v8 静态投影在 64×64 边界内发生底部裁切并贴底；v9 保持纹理、几何、角度和缩放，只对盾牌成品纵向居中，alpha 边界由 `y=14..63` 调整为 `y=1..61`。
- v9 已由用户真实 Paper/QQ Online 图复测通过：盾牌位于物品格中央，上下留白基本对称，无贴底、裁切或新增毛边。
- v9 Offline Snapshot 图继续保持相同盾牌位置与尺寸，未命中 v8 缓存；Offline 位置门禁通过。
- Paper 完整重启且玩家始终离线后，QQ 仍读取同一 `09:27` 快照；v9 盾牌、人物、盔甲和代表物品无回退，Cross-Restart 通过。
- 人物预览不再先生成固定 `128×256`、再以非整数 nearest-neighbor 缩放进主题区域；PV7 按主题最终区域直接光栅化，Faithful 为 `198×283`。
- 人物皮肤保持原生 Minecraft 像素，Faithful 128×64 盔甲/纹饰与 glint 的既有 UV、层次和材质分辨率不变；只移除有损的第二次缩放。
- 确认现代 64×64 皮肤 Hat/Jacket/双袖/双裤腿第二层已完整渲染，使用客户端 0.5/0.25 模型像素外扩；新增最终尺寸三栏证据图。
- Bundled Asset Pack 升级为 `inventory-assets-v9-mb7-pv7-glint-bed-shield-centered-hd64-pd1337875`；缓存键保持 `26.1.2-B1B315857266-MB7-PD1337875`。
- 官方 Minecraft 26.1.2 Client JAR 保存至 `local-inputs/minecraft/26.1.2/26.1.2-client.jar`，并由 `.gitignore` 排除，不会打入插件成品。

### Validation

- 86/86 自动测试和 clean build 通过；JAR 内含 1413 个 MB7 生成图标，未包含 Client JAR。
- 分辨率门禁确认：704 个普通 2D 图标为 32×32，709 个 Block Model 图标为 64×64，45 个 Explicit Override 为 64×64。
- 全量审计：1506 Total、1466 PASS、0 FAIL、40 DEFERRED、0 NEEDS_MANUAL_REVIEW；46 张最终 Inventory 页和 7 张重点样本生成成功。
- 已人工抽查 Bed、Chest、Shulker、复杂方块、透明物件、红石/铁轨、Spear，以及人物皮肤、全套盔甲、Trim 与 Glint 的 PV6/PV7 前后对照；真实 Paper + QQ Online / Offline / Cross-Restart E2E 全部通过，Inventory 1.16.0 标记为 `FULL PASS`。

## 1.15.0 — 2026-09-04

### Bed Family

- 新增 16 种床的 Minecraft 26.1.2 客户端等价静态 Explicit Override。
- 新增可复现 Bed 生成器，复制 head/foot 几何、实体 UV、节点变换与 `template_bed` GUI transform。
- 颜色由对应原版 Bed entity texture 决定；不使用运行时染色，不影响物品数据和缓存键。
- Bundled Asset Pack 升级为 `inventory-assets-v5-mb6-pv6-glint-bed-pd1337875`；MB6 和 PV6 保持不变。

### Validation

- 83/83 自动测试通过，成品 JAR 包含 16/16 Bed override。
- 全量审计：1506 Total、1466 PASS、0 FAIL、40 DEFERRED、0 NEEDS_MANUAL_REVIEW。
- Explicit Override 由 29 增至 45，Special Unsupported 由 49 降至 33。
- 单图总览与最终 704×664 Inventory 合成已通过本地视觉检查。
- Paper + QQ Online / Offline / Cross-Restart E2E 待用户测试，通过前不标记 FULL PASS。

## 1.14.1 — 2026-08-29

### Leather Armor Glint Correction

- 修正皮革 dye color 被错误用于独立 armor glint pass 调制的问题；此前米色/部分染色皮革会压低紫色炫光通道。
- glint pass 改为客户端等价白色 vertex modulation，皮革染色仍只作用于第一 dyeable equipment layer。
- 不提高全局 glint strength，因此铁甲、钻石甲、下界合金甲及 Armor Trim 的既有表现不变。
- Preview Cache 升级为 `pv6`；Bundled Asset Pack 标识更新为 `inventory-assets-v4-mb6-pv6-glint-pd1337875`，MB6 key 与 1412 图标不变。

### Validation

- 新增未染色皮革 No Glint / Corrected Glint 前后对照；修正后淡紫高光清楚且保留棕色底色。
- 定向渲染、缓存、最终 Inventory 与 Bundled Asset 测试通过。
- 2026-09-04 完成真实 Paper + QQ 未染色皮革 Online A/B、Offline Snapshot 与 Cross-Restart 门禁。
- 完成单件附魔隔离和 Netherite Gold Trim + Enchantment 门禁；底色、皮肤、其他盔甲部位及纹饰均未被 glint 污染。
- Inventory 1.14.1 Leather Armor Glint Correction 与 Player Armor Enchantment Glint Phase 均标记为 FULL PASS。
- 同轮实测确认多色 Bed 仍显示 question-cube；保持 Special Renderer `DEFERRED`，未计为 1.14.1 回归。

## 1.14.0 — 2026-08-29

### Player Armor Enchantment Glint

- 3D Player Preview 新增确定性静态盔甲附魔炫光，仅作用于实际 armor face 像素。
- 直接复用 `ArmorVisualDescriptor.glint` 与 Snapshot Schema 3 数据链，不重复读取 Bukkit ItemStack。
- 使用 Minecraft 26.1.2 独立 `enchanted_glint_armor.png`，并实现客户端 `0.16` UV scale、`10°` rotation、默认 `0.75` strength 与 `SRC_COLOR + ONE` blend。
- 固定客户端等价 `t=242s` frame，不使用系统时间，保证重复查询、缓存与跨重启输出可复现。
- 保持客户端 layer order：第一 equipment layer → glint → 皮革 overlay/其余 layer → Armor Trim。
- 四个盔甲槽独立响应 glint，并兼容 Classic、Slim、Leather dye 与 Armor Trim。
- Preview Cache 升级为 `pv5`；Bundled Asset Pack 升级为 `inventory-assets-v4-mb6-pv5-glint-pd1337875`，MB6 cache key 与 1412 图标不变。

### Validation

- 本地六行视觉参考、Before/After 与最终 704×664 Inventory 合成通过。
- 80/80 自动测试通过，包含单件/全套 glint、Trim、Leather、Classic/Slim、PV5 cache、Snapshot store reopen 与确定性输出。
- Paper + QQ Online/Offline/Cross-Restart E2E 待执行，通过前不标记 FULL PASS。

## 1.13.0 — 2026-08-29

### Priority A3+A4

- 修复 `minecraft:display_context` item definition 解析，使 Trident 在 Inventory GUI 中选择普通 `item/trident` 2D model，手持 special 分支不进入背包渲染。
- 新增一套 `PotionTintCompositor`，共享支持 Potion、Splash Potion、Lingering Potion 与 Tipped Arrow。
- 严格按客户端模型顺序合成 tintable layer0 与 untinted layer1，不对瓶身或整支箭进行整体染色。
- 使用 Paper `PotionMeta.computeEffectiveColor()` 取得客户端等价颜色；显式 custom color、base potion 与 custom effects 均受支持，不维护手写药水颜色表。
- 新增稳定 potion visual key 和独立运行时缓存，避免同一 item type 的不同颜色互相污染。
- Offline Snapshot 升级到 Schema 3，持久化必要 potion 视觉字段，并继续读取 Schema 1/2。
- Bundled Asset Pack 升级为 `inventory-assets-v3-mb6-a34-pd1337875`；MB6 本体及其 1412 图标保持不变。

### Validation

- 本地参考图与最终 704×664 Inventory 合成视觉门禁通过。
- 全量审计：1506 Total、1450 PASS、0 FAIL、56 DEFERRED、0 NEEDS_MANUAL_REVIEW。
- Trident 日志路径为 `GENERATED_2D_GUI_MODEL / GUI_MODEL`；四种药水为 `POTION_TINT / RUNTIME_COMPOSITE`，fallback 均为 false。
- 77 项自动测试全部通过；候选 JAR 内容边界与七个 runtime layer 完整性检查通过。
- Paper + QQ Online、玩家退出后的 Offline Snapshot、Paper 重启且玩家未重新登录时的 Cross-Restart E2E 均通过。
- 重启后成功从磁盘读取 `capturedAt=2026-08-29T04:16:34.892970849Z` 的同一快照；三张最终图片中的 Trident 与四类 Potion Tint 保持一致。
- Inventory 1.13.0 Priority A3+A4 标记为 FULL PASS。

## 1.12.0 — 2026-08-29

### Special Static Priority A1+A2

- 增加 10 个 Chest Family 和 17 个 Shulker Family 的客户端等价静态图标。
- Chest Family 复用已验收的 ChestModel 几何、实体 UV、`[30,45,0]` GUI rotation、固定 `0.625` scale 与 slot projection。
- Shulker Family 共享一套基于 Minecraft 26.1.2 `ShulkerModel` lid/base 几何与实体 UV 的生成器。
- Trapped Chest Christmas 图标作为独立 seasonal variant，仅在 12 月 24—26 日选择；普通图标与缓存键不受日期切换污染。
- Bundled Asset Pack 升级为 `inventory-assets-v2-mb6-special-a12-pd1337875`，避免旧服务器资产目录被误复用。
- MB6、Host、Binding、GameAuthCode、Armor、Offline Snapshot 与 Inventory 布局保持不变。

### Validation

- 本地单图和最终 704×664 Inventory 合成人工视觉门禁通过。
- 全量审计：1506 Total、1445 PASS、0 FAIL、61 DEFERRED、0 NEEDS_MANUAL_REVIEW。
- 46 张全量背包页和 6 张重点样本生成成功；PASS 项 fallback 为 0。
- 69 项自动测试全部通过；候选 JAR 内容边界和捆绑资产完整性检查通过。
- Paper + QQ 五项抽样 E2E 已通过：Ender Chest、Copper Chest、Trapped Chest、White Shulker Box、Black Shulker Box 均正确进入最终 Inventory 图片。
- Inventory 1.12.0 Special Static Priority A1+A2 标记为 FULL PASS。

## 1.11.1 — 2026-08-29

### Full Item Render Audit

- 对全部 1506 个物品定义生成最终 Inventory 合成页与 1.11.1 contact sheet。
- 最终结果：1418 PASS、0 FAIL、88 DEFERRED、0 NEEDS_MANUAL_REVIEW。
- 将四件已由运行时合成器支持的皮革盔甲纳入真实 PASS 口径。
- 新增可重复执行的全量审计测试与 TSV/JSON 汇总工具；未修改生产渲染逻辑。
- 完整测试更新为 63 项，全部通过。

### Armor Rendering + Armor Trim

- 增加 `ArmorVisualDescriptor`，统一盔甲物品图标与人物预览的数据来源。
- 通过 Bukkit/Paper API 解析盔甲槽位、Equipment Model、Armor Trim、皮革染色与 Glint 标记。
- Offline Snapshot 升级为 Schema 2，持久化盔甲视觉字段。
- 增加带 Armor Trim 的盔甲物品图标运行时合成。
- 3D Player Preview 增加头盔、胸甲、护腿和靴子的独立装备层及 Trim 层。
- Preview Cache 升级为 `pv4`，缓存键包含完整装备指纹。
- 保持 MB6、Host、Binding、GameAuthCode 与 QQ MessageGateway 不变。

### Validation

- 62 项自动测试全部通过，0 failures，0 errors。
- Online Armor Trim E2E：PASS。
- Offline Snapshot Armor Trim E2E：PASS。
- Cross-Restart Armor Trim E2E：PASS。
- 最终验证中，Paper 重启后从磁盘读取 `Admin_Lee` 于 2026-08-29 11:03（本地时间）保存的快照，QQ PNG 中的胸甲物品图标和 3D 人物金色纹饰均完整保留。
