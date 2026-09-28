object ProjectConfig {
    const val APP_NAME = "Stelliberty"
    const val PACKAGE_NAME = "com.stelliberty.android"
    const val VERSION_NAME = "1.0.0"

    object Android {
        const val TARGET_SDK = 37
        const val MIN_SDK = 31
        const val COMPILE_SDK = 37
        const val COMPILE_SDK_MINOR = 0
    }
}

fun versionCodeFor(version: String): Int {
    val match = Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-beta([1-9]\\d*))?")
        .matchEntire(version) ?: error("Invalid app version: $version")
    val (major, minor, patch) = match.groupValues.drop(1).take(3).map(String::toInt)
    val beta = match.groupValues[4].takeIf(String::isNotEmpty)?.toInt()
    require(major in 0..20 && minor in 0..99 && patch in 0..99 && (beta == null || beta in 1..9998)) {
        "App version exceeds Android versionCode limits: $version"
    }
    // 同一版本的稳定版排在全部 beta 后面，分支提交数量不参与升级顺序。
    return major * 100_000_000 + minor * 1_000_000 + patch * 10_000 + (beta ?: 9999)
}
