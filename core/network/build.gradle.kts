plugins {
    id("yomu.android.library")
}
dependencies {
    implementation(project(":core:common"))
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    testImplementation(libs.okhttp.mockwebserver)
}
