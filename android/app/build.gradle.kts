import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.baselineProfile)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    baselineProfile(projects.baselineprofile)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.navigationevent)
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.miuix)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.hiddenapibypass)
    implementation(libs.quickie.bundled)
    implementation(libs.scripta.editor)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val appVersionName = providers.gradleProperty("stelliberty.versionName").orElse(ProjectConfig.VERSION_NAME).get()
val appVersionCode = versionCodeFor(appVersionName)

val supportedAbis: List<String> =
    providers.gradleProperty("stelliberty.abis").orNull
        ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        ?: listOf("arm64-v8a")

val properties = Properties()
runCatching { project.rootProject.file("local.properties").inputStream().use { properties.load(it) } }
val keystorePath: String? = System.getenv("KEYSTORE_PATH") ?: properties.getProperty("KEYSTORE_PATH")
val keystorePwd: String? = System.getenv("KEYSTORE_PASS") ?: properties.getProperty("KEYSTORE_PASS")
val alias: String? = System.getenv("KEY_ALIAS") ?: properties.getProperty("KEY_ALIAS")
val pwd: String? = System.getenv("KEY_PASSWORD") ?: properties.getProperty("KEY_PASSWORD")

@Suppress("UnstableApiUsage")
android {
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePwd
                keyAlias = alias
                keyPassword = pwd
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            optimization.enable = true
            vcsInfo.include = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileSdk {
        version = release(ProjectConfig.Android.COMPILE_SDK) {
            minorApiLevel = ProjectConfig.Android.COMPILE_SDK_MINOR
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        applicationId = ProjectConfig.PACKAGE_NAME
        minSdk = ProjectConfig.Android.MIN_SDK
        targetSdk = ProjectConfig.Android.TARGET_SDK
        versionName = appVersionName
        versionCode = appVersionCode
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    externalNativeBuild {
        cmake {
            version = libs.versions.cmake.get()
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    namespace = ProjectConfig.PACKAGE_NAME
    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include(*supportedAbis.toTypedArray())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

androidComponents {
    finalizeDsl { ext ->
        ext.buildTypes.findByName("nonMinifiedRelease")?.optimization?.enable = false
        if (keystorePath == null) {
            val debugSigning = ext.signingConfigs.getByName("debug")
            listOf("nonMinifiedRelease", "benchmarkRelease").forEach { name ->
                ext.buildTypes.findByName(name)?.signingConfig = debugSigning
            }
        }
    }
}

abstract class DownloadGeoFilesTask : DefaultTask() {
    @get:Input
    abstract val sources: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        sources.get().forEach { (downloadUrl, fileName) ->
            val target = File(dir, fileName)
            val partial = File(dir, "$fileName.part")
            val connection = (URI(downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
            }
            try {
                val status = connection.responseCode
                check(status == HttpURLConnection.HTTP_OK) { "$downloadUrl responded HTTP $status" }
                connection.inputStream.use {
                    Files.copy(it, partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                connection.disconnect()
            }
            val size = partial.length()
            if (size < MIN_FILE_BYTES) {
                partial.delete()
                error("$fileName is only $size bytes, expected a data file (bad URL or rate limited?)")
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("$fileName downloaded to $target ($size bytes)")
        }
    }

    private companion object {
        const val HTTP_TIMEOUT_MS = 30_000
        const val MIN_FILE_BYTES = 512 * 1024L
    }
}

val downloadGeoFiles = tasks.register<DownloadGeoFilesTask>("downloadGeoFiles") {
    description = "Download the prebuilt GeoIP/GeoSite data files into the assets source set"
    sources.set(
        mapOf(
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
        )
    )
    outputDir.set(layout.projectDirectory.dir("src/main/assets"))
    // 上游的地理数据包是原地重新发布的，地址和本地文件名都不变，按常规判断会永远认为是最新的，
    // 本地就一直停在第一次抓到的那份。这个任务只在被点名时才跑，「调了就去取最新」正是该有的行为。
    outputs.upToDateWhen { false }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    // 下载任务的输出目录同时是资源合并任务的输入源，不声明先后顺序，两者出现在同一次构建里时
    // 会因为「用了没声明依赖的产物」而中止。
    mustRunAfter(downloadGeoFiles)
}

val mihomoSubmoduleDir = rootProject.layout.projectDirectory.dir("../third_party/mihomo")
val stellibertyCoreSourceDir = layout.projectDirectory.dir("src/main/native/stelliberty_core")
// mishka 这个标签名不能跟着项目改名走：它被子模块自己的源码引用（几处文件名和编译约束里都写着），
// 改了直接编译不过。
val mihomoBuildTags = listOf("cmfa", "mishka", "with_gvisor")
val mihomoVersion = providers.gradleProperty("mihomo.version").orElse("dev").get()
val mihomoVersionPath = "github.com/metacubex/mihomo/constant.Version"
val ndkDirectoryProvider = androidComponents.sdkComponents.ndkDirectory

val buildMihomoTasks = supportedAbis.map { abi ->
    val suffix = abi.replace("-", "_")
    tasks.register<GoBuildTask>("buildMihomo_$suffix") {
        group = "mihomo"
        description = "Build unified mihomo + JNI c-shared library (libmihomo.so) for $abi"
        goSourceDir.set(stellibertyCoreSourceDir)
        goVersion.set(providers.gradleProperty("stelliberty.goVersion"))
        replacedModuleSources.from(
            mihomoSubmoduleDir.asFileTree.matching {
                exclude(".git", ".github/**", "docs/**", "test/**", "**/*_test.go")
            }
        )
        this.abi.set(abi)
        versionName.set(mihomoVersion)
        buildTags.set(mihomoBuildTags)
        cgoEnabled.set(true)
        buildMode.set(GoBuildTask.BuildMode.CShared)
        ndkDirectory.set(ndkDirectoryProvider)
        minSdk.set(ProjectConfig.Android.MIN_SDK)
        moduleVersionPath.set(mihomoVersionPath)
        outputFile.set(layout.projectDirectory.file("src/main/jniLibs/$abi/libmihomo.so"))
        headerFile.set(layout.projectDirectory.file("src/main/jniLibs/$abi/libmihomo.h"))
    }
}

tasks.named("preBuild") {
    dependsOn(buildMihomoTasks)
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(buildMihomoTasks)
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.add("**")
    }
}

base {
    archivesName.set(
        "${ProjectConfig.APP_NAME}-v${appVersionName}",
    )
}
