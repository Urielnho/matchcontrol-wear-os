import java.util.Properties
plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
val localConfig=Properties().apply {rootProject.file("local.properties").takeIf {it.exists()}?.inputStream()?.use {load(it)}}
fun clientConfig(name: String)=localConfig.getProperty(name,"").replace("\\","\\\\").replace("\"","\\\"")
android {
 namespace = "com.matchcontrol.mobile"
 compileSdk = 36
 defaultConfig {
  applicationId="com.matchcontrol";minSdk=30;targetSdk=36;versionCode=21;versionName="2.1.0"
  buildConfigField("String","SUPABASE_URL","\"${clientConfig("SUPABASE_URL")}\"")
  buildConfigField("String","SUPABASE_PUBLISHABLE_KEY","\"${clientConfig("SUPABASE_PUBLISHABLE_KEY")}\"")
 }
 buildFeatures { compose=true;buildConfig=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(project(":core"))
 implementation(platform("androidx.compose:compose-bom:2025.08.00"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
 implementation("androidx.navigation:navigation-compose:2.9.3")
 implementation("io.coil-kt:coil-compose:2.7.0")
}
