plugins {
 id("com.android.library")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.serialization")
}
android {
 namespace = "com.matchcontrol.core"
 compileSdk = 36
 defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 // Synthetic fixtures exist only in verification builds, never in the applications.
 sourceSets.getByName("test").java.srcDir("src/testData/java")
 sourceSets.getByName("androidTest").java.srcDir("src/testData/java")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
 api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
 api("com.google.android.gms:play-services-wearable:19.0.0")
 // Wearable includes an old Fragment transitively; Activity Result needs >= 1.3.
 api("androidx.fragment:fragment:1.8.9")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
