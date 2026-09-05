# HuHoBot Inventory 构建说明

本文面向需要修改、移植或从源码构建 Inventory 的开发者。普通服主请直接从 [Releases](https://github.com/RiegaLee/HuHoBotInventory/releases/latest) 下载插件 JAR。

## 1. 准备 HuHoBot 编译依赖

准备兼容版本的：

- `huhobot-api-*.jar`
- `common-Bot-*.jar`

复制 `gradle.properties.example` 为 `gradle.properties`，并把其中的路径改为本机实际文件位置。`gradle.properties` 是本地配置，不会被 Git 提交。

## 2. 构建离线素材工具

Windows：

```powershell
.\gradlew.bat clean vanillaAssetsToolJar
```

Linux 或 macOS：

```bash
./gradlew clean vanillaAssetsToolJar
```

输出文件：

```text
build/libs/HuHoBot-InventoryAssetsTool-1.21.2.jar
```

素材工具是开发构建工具，不是服务器插件，不应放入服务器的 `plugins/` 目录。

## 3. 导入构建所需资源

Inventory `1.21.2` 的已验收资源基线为 Minecraft Java Edition `26.1.2` 与 Faithful 32x `26.2`。请自行从合法来源取得 Minecraft 客户端 JAR 和 Faithful 压缩包，然后把命令中的两个路径占位符替换为实际文件位置：

```text
java -jar build/libs/HuHoBot-InventoryAssetsTool-1.21.2.jar import "<Minecraft 客户端 JAR 路径>" "data/imported-assets/vanilla" "26.1.2" "<Faithful 32x 压缩包路径>"
```

输入文件可以位于任意磁盘或目录。已验收输入会生成缓存目录：

```text
data/imported-assets/vanilla/26.1.2-B1B315857266-MB7-PD1337875
```

输入文件或哈希不同时会产生不同缓存键。不要把未经复验的缓存作为正式资源基线发布。

## 4. 构建 Inventory

Windows：

```powershell
.\gradlew.bat clean build
```

Linux 或 macOS：

```bash
./gradlew clean build
```

构建产物：

```text
build/libs/HuHoBot-MinecraftInventory-1.21.2.jar
build/libs/HuHoBot-InventoryAssetsTool-1.21.2.jar
```

其中 `HuHoBot-MinecraftInventory-1.21.2.jar` 才是放入服务器 `plugins/` 目录的插件。

`build` 会运行自动测试、内置资源完整性检查和 JAR 边界检查，确保不打包 HuHoBot Core、QQ SDK、SkinsRestorer 实现、服务端运行数据、Minecraft 客户端 JAR 或开发审计文件。

## 第三方资源

构建和分发前请阅读 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [Faithful License](THIRD_PARTY_LICENSES/Faithful-LICENSE.txt)。Faithful 资源不适用本项目的 MIT License，并包含署名及非商业使用要求。
