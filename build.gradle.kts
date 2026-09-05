import java.util.zip.ZipFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "cn.huohuas001.huhobot.addons"
version = "1.21.1"

val bundledAssetPackId = "inventory-assets-v11-mb7-pv8-glint-bed-shield-enderchest-hd64-pd1337875"
val bundledVanillaCacheKey = "26.1.2-B1B315857266-MB7-PD1337875"
val bundledVanillaSource = file("data/imported-assets/vanilla/$bundledVanillaCacheKey")
val generatedBundledResources = layout.buildDirectory.dir("generated/bundled-assets/resources")

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

// API 1.3 is embedded in the Inventory JAR so pristine HuHoBot releases can discover the addon
// without a patched Core or a third compatibility plugin. GameAuthCode compiles against the same
// classes through its hard dependency on HuHoBotInventory and remains the binding authority.
fun dependencyJar(propertyName: String, environmentName: String, candidates: List<File>): File {
    val configured = providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orNull
        ?.trim()
        .orEmpty()
    if (configured.isNotEmpty()) return file(configured)
    return candidates.firstOrNull { it.isFile } ?: candidates.first()
}

val huhobotApiJar = dependencyJar(
    "huhobotApiJar",
    "HUHOBOT_API_JAR",
    listOf(file("../PenguinClient-master/huhobot-api/build/libs/huhobot-api-1.2.1.jar"))
)
val huhobotQqSdkJar = dependencyJar(
    "huhobotQqSdkJar",
    "HUHOBOT_QQ_SDK_JAR",
    listOf(
        file("../official-upstream-compat/2026-09-05/PenguinAgent-official/common/Bot/build/libs/common-Bot-1.6.1.jar"),
        file("../official-upstream-compat/2026-09-05/PenguinClient-official/common/Bot/build/libs/common-Bot-1.2.2.jar"),
        // ASCII junction used by the Windows build lane.
        file("../mainline/common/Bot/build/libs/common-Bot-1.2.1.jar"),
        file("../主分支/PenguinClient-1.2.1/common/Bot/build/libs/common-Bot-1.2.1.jar")
    )
)

dependencies {
    compileOnly(files(huhobotQqSdkJar))
    compileOnly(files(huhobotApiJar))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.2")

    testImplementation(files(huhobotApiJar))
    testImplementation(files(huhobotQqSdkJar))
    testImplementation("com.alibaba:fastjson:2.0.32")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("net.skinsrestorer:skinsrestorer-api:15.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

// The addon remains Java 8 bytecode, but its optional SkinsRestorer v15 adapter runs only on the
// project's Java 21 Paper baseline. Declaring that resolution baseline lets javac 21 consume the
// provided API without bundling it or raising our own class-file version.
configurations.configureEach {
    if (isCanBeResolved) attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

val generateBundledAssets by tasks.registering {
    group = "build"
    description = "Builds the versioned, integrity-checked runtime asset pack embedded in the addon JAR."
    inputs.dir("src/main/resources/themes")
    inputs.dir("src/armor-assets")
    inputs.dir(bundledVanillaSource)
    inputs.property("inventoryVersion", project.version.toString())
    inputs.property("assetPackId", bundledAssetPackId)
    inputs.property("vanillaCacheKey", bundledVanillaCacheKey)
    outputs.dir(generatedBundledResources)

    doLast {
        check(bundledVanillaSource.isDirectory) {
            "Missing accepted MB7 source ${bundledVanillaSource.absolutePath}"
        }
        val output = generatedBundledResources.get().asFile.toPath()
        project.delete(output.toFile())
        val packRoot = output.resolve("bundled-assets/pack")
        Files.createDirectories(packRoot)

        val themeIndex = file("src/main/resources/themes/bundled-resources.txt")
        val themeResources = themeIndex.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        themeResources.forEach { relative ->
            check(!relative.startsWith("/") && !relative.contains("\\") && !relative.contains("..")) {
                "Unsafe bundled theme resource $relative"
            }
            val source = file("src/main/resources/$relative").toPath()
            check(Files.isRegularFile(source)) { "Missing bundled theme source $relative" }
            val destination = packRoot.resolve(relative)
            Files.createDirectories(destination.parent)
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }

        val armorSource = file("src/armor-assets").toPath()
        check(Files.isDirectory(armorSource)) { "Missing self-contained armor asset source" }
        Files.walk(armorSource).use { paths ->
            paths.forEach { source ->
                val relative = armorSource.relativize(source)
                val destination = packRoot.resolve("armor").resolve(relative.toString())
                if (Files.isDirectory(source)) Files.createDirectories(destination)
                else {
                    Files.createDirectories(destination.parent)
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        val vanillaRoot = packRoot.resolve("vanilla")
        Files.createDirectories(vanillaRoot)
        Files.write(
            vanillaRoot.resolve("current-version.txt"),
            listOf(bundledVanillaCacheKey),
            StandardCharsets.UTF_8
        )
        Files.walk(bundledVanillaSource.toPath()).use { paths ->
            paths.forEach { source ->
                val relative = bundledVanillaSource.toPath().relativize(source)
                val destination = vanillaRoot.resolve(bundledVanillaCacheKey).resolve(relative.toString())
                if (Files.isDirectory(source)) Files.createDirectories(destination)
                else {
                    Files.createDirectories(destination.parent)
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        fun sha256(path: java.nio.file.Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02X".format(it) }
        }
        fun json(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        val files = Files.walk(packRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { packRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
        val entries = files.map { relative ->
            val path = packRoot.resolve(relative)
            Triple(relative, sha256(path), Files.size(path))
        }
        val packDigest = MessageDigest.getInstance("SHA-256")
        entries.forEach { (path, hash, size) ->
            packDigest.update(path.toByteArray(StandardCharsets.UTF_8))
            packDigest.update(0)
            packDigest.update(hash.toByteArray(StandardCharsets.US_ASCII))
            packDigest.update(0)
            packDigest.update(size.toString().toByteArray(StandardCharsets.US_ASCII))
            packDigest.update('\n'.code.toByte())
        }
        val packSha256 = packDigest.digest().joinToString("") { "%02X".format(it) }
        val manifest = buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"assetPackVersion\": 11,\n")
            append("  \"packId\": ").append(json(bundledAssetPackId)).append(",\n")
            append("  \"compatibleInventoryVersion\": ").append(json(project.version.toString())).append(",\n")
            append("  \"minecraftVersion\": \"26.1.2\",\n")
            append("  \"modelBakerVersion\": 7,\n")
            append("  \"vanillaCacheKey\": ").append(json(bundledVanillaCacheKey)).append(",\n")
            append("  \"generatedIcons\": 1413,\n")
            append("  \"totalDefinitions\": 1506,\n")
            append("  \"defaultTheme\": \"faithful32x\",\n")
            append("  \"packSha256\": ").append(json(packSha256)).append(",\n")
            append("  \"files\": [\n")
            entries.forEachIndexed { index, (path, hash, size) ->
                append("    {\"path\": ").append(json(path))
                    .append(", \"sha256\": ").append(json(hash))
                    .append(", \"size\": ").append(size).append("}")
                if (index + 1 < entries.size) append(',')
                append('\n')
            }
            append("  ]\n}\n")
        }
        val manifestPath = output.resolve("bundled-assets/asset-manifest.json")
        Files.createDirectories(manifestPath.parent)
        Files.write(manifestPath, manifest.toByteArray(StandardCharsets.UTF_8))
    }
}

tasks.processResources {
    dependsOn(generateBundledAssets)
    from(generatedBundledResources)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

tasks.jar {
    archiveFileName.set("HuHoBot-MinecraftInventory-${project.version}.jar")
    from(zipTree(huhobotApiJar)) {
        include("cn/huohuas001/huhobot/api/**")
    }
}

val vanillaAssetsToolJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the dependency-free offline Vanilla asset import tool."
    val compileJava = tasks.named<JavaCompile>("compileJava")
    dependsOn(compileJava)
    archiveFileName.set("HuHoBot-InventoryAssetsTool-${project.version}.jar")
    // Deliberately use compiled classes only. This tool must be buildable before the user-owned
    // Minecraft/Faithful input has been imported and processResources can assemble the addon.
    from(compileJava.flatMap { it.destinationDirectory }) {
        include("cn/huohuas001/huhobot/inventory/asset/**")
    }
    manifest {
        attributes["Main-Class"] = "cn.huohuas001.huhobot.inventory.asset.VanillaAssetImporter"
    }
}

val verifyAddonJar by tasks.registering {
    group = "verification"
    description = "Checks the Inventory addon boundary and required resources."
    dependsOn(tasks.jar)

    doLast {
        check(huhobotApiJar.isFile) {
            "Build huhobot-api first; expected ${huhobotApiJar.absolutePath}"
        }
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val bundledThemeResources = file("src/main/resources/themes/bundled-resources.txt")
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            (listOf(
                "plugin.yml",
                "config.yml",
                "themes/bundled-resources.txt",
                "bundled-assets/asset-manifest.json",
                "bundled-assets/pack/vanilla/current-version.txt",
                "bundled-assets/pack/vanilla/$bundledVanillaCacheKey/metadata.json",
                "bundled-assets/pack/armor/equipment/diamond.json",
                "bundled-assets/pack/armor/textures/entity/equipment/humanoid/diamond.png",
                "bundled-assets/pack/armor/textures/trims/entity/humanoid/spire.png",
                "bundled-assets/pack/armor/textures/trims/color_palettes/trim_palette.png",
                "bundled-assets/pack/armor/textures/misc/enchanted_glint_armor.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/chest.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/ender_chest.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/black_shulker_box.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/white_bed.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/black_bed.png",
                "bundled-assets/pack/themes/faithful32x/special-variants/minecraft/trapped_chest_christmas.png",
                "bundled-assets/pack/themes/faithful32x/runtime-composites/items/minecraft/trident.png",
                "bundled-assets/pack/themes/faithful32x/runtime-composites/items/minecraft/potion_overlay.png",
                "bundled-assets/pack/themes/faithful32x/runtime-composites/items/minecraft/tipped_arrow_base.png",
                "bundled-assets/pack/themes/faithful32x/overrides/items/minecraft/shield.png",
                "bundled-assets/pack/themes/faithful32x/ender-chest-background.png"
            ) + bundledThemeResources).forEach { required ->
                check(required in entries) { "Addon JAR is missing $required" }
            }
            val bundledIcons = entries.count {
                it.startsWith("bundled-assets/pack/vanilla/$bundledVanillaCacheKey/generated-icons/") &&
                    it.endsWith(".png")
            }
            check(bundledIcons == 1413) { "Expected 1413 bundled MB7 icons, found $bundledIcons" }
            check("cn/huohuas001/huhobot/api/HuHoBotService.class" in entries) {
                "Official-compatible Inventory JAR must embed huhobot-api"
            }
            check(entries.none {
                it.startsWith("cn/huohuas001/bot/") ||
                    it.startsWith("io/github/kloping/") ||
                    it.startsWith("cn/huohuas001/huhobotPenguin/") ||
                    it.startsWith("net/skinsrestorer/")
            }) {
                "Addon JAR must not bundle Core/QQ SDK/SkinsRestorer implementation classes"
            }
            check(entries.none {
                it.startsWith("data/") ||
                    it.startsWith("cache/") ||
                    it.startsWith("runtime-assets/") ||
                    (it.contains("generated-icons/") && !it.startsWith("bundled-assets/pack/vanilla/")) ||
                    it.contains("visual-audit") ||
                    it.contains("contact-sheet") ||
                    it.endsWith(".zip") ||
                    it.endsWith("26.1.2.jar") ||
                    it.startsWith("docs/")
            }) {
                "Addon JAR contains development/private material outside the managed runtime pack"
            }
        }
    }
}

val verifyVanillaAssetsToolJar by tasks.registering {
    group = "verification"
    description = "Checks that the offline importer is standalone and contains no imported assets."
    dependsOn(vanillaAssetsToolJar)

    doLast {
        val toolFile = vanillaAssetsToolJar.get().archiveFile.get().asFile
        ZipFile(toolFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            check("cn/huohuas001/huhobot/inventory/asset/VanillaAssetImporter.class" in entries)
            check(entries.none {
                it.startsWith("org/bukkit/") ||
                    it.startsWith("cn/huohuas001/huhobot/api/") ||
                    it.startsWith("data/") ||
                    it.contains("generated-icons/")
            }) {
                "Offline assets tool must contain only importer code, never server APIs or imported assets"
            }
        }
    }
}

tasks.build {
    dependsOn(verifyAddonJar, verifyVanillaAssetsToolJar)
}
