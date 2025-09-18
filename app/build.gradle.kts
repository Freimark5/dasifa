import org.gradle.jvm.toolchain.JavaLanguageVersion
plugins {
	id("com.android.application") version "8.13.0" apply true
	id("org.jetbrains.kotlin.android") version "2.2.20" apply true
}
android {
	namespace = "com.dasifa"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.dasifa"
		minSdk = 24
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		jvmTarget.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
	}
}
dependencies {
	implementation("androidx.core:core-ktx:1.17.0")
	implementation("androidx.appcompat:appcompat:1.7.1")
	implementation("androidx.constraintlayout:constraintlayout:2.2.1")
	implementation("com.google.android.material:material:1.13.0")
	implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
	implementation("androidx.documentfile:documentfile:1.1.0")
	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.3.0")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}