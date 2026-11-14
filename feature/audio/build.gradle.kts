plugins {
    id("yomu.android.feature")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":core:network"))
    api(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.telephoto.zoomable.coil)
}
