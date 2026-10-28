import java.util.Properties
plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
}
val privateConfig = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
// 2.0: credentials belong to the backend. The preserved adapters are disabled in DEMO.
val apiKey = ""
android {
 namespace = "com.matchcontrol"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.matchcontrol"
  minSdk = 30
  targetSdk = 36
  versionCode = 21
  versionName = "2.1.0"
  buildConfigField("String", "FOOTBALL_API_KEY", "\"$apiKey\"")
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
dependencies {
 implementation(project(":core"))
 implementation(platform("androidx.compose:compose-bom:2025.08.00"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.wear.compose:compose-material3:1.5.0")
 implementation("androidx.wear.compose:compose-foundation:1.5.0")
 implementation("androidx.wear.compose:compose-navigation:1.5.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
 implementation("androidx.datastore:datastore-preferences:1.1.7")
 implementation("androidx.core:core-ktx:1.16.0")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
 implementation("io.coil-kt:coil-compose:2.7.0")
 testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
