plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // 构建脚本里的 downloadGeoFiles 用它把地理数据压成 xz。
    implementation(libs.xz)
}
