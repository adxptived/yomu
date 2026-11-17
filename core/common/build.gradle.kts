plugins { id("yomu.android.library") }
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.vintage)
}
