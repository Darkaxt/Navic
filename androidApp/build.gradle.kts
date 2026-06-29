import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import java.security.KeyStore
import java.security.MessageDigest

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

val releaseBuildRequested = gradle.startParameter.taskNames
	.map { it.substringAfterLast(':') }
	.any { task ->
		task.contains("Release") ||
			task == "assemble" ||
			task == "build" ||
			task == "package"
	}

val expectedReleaseCertificateSha256 =
	"ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7"

if (
	gradle.startParameter.taskNames
		.map { it.substringAfterLast(':') }
		.any { it.startsWith("install") && it.contains("Debug") } &&
	System.getenv("NAVIC_ALLOW_DEBUG_INSTALL")?.equals("true", ignoreCase = true) != true
) {
	throw GradleException(
		"Refusing to install Navic (Dev). Install a release APK, or set " +
			"NAVIC_ALLOW_DEBUG_INSTALL=true for an explicit one-off debug install."
	)
}

extensions.configure<ApplicationExtension> {
	namespace = "paige.navic.androidApp"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	buildFeatures {
		buildConfig = true
		resValues = true
	}

	defaultConfig {
		applicationId = "darkaxt.navic"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 444
		versionName = "v1.0.11-theta16"
		buildConfigField("boolean", "NAVIC_READER_DEV", "false")

		ndk {
			abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
			val isRelease = System.getenv("RELEASE")?.toBoolean() ?: false
			if (!isRelease) {
				abiFilters.add("x86_64")
			}
		}
	}

	signingConfigs {
		create("release") {
			keyAlias = System.getenv("SIGNING_KEY_ALIAS")
			keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
			storeFile = System.getenv("SIGNING_STORE_FILE")?.let(::File)
			storePassword = System.getenv("SIGNING_STORE_PASSWORD")
		}
	}

	buildTypes {
		val isRelease = System.getenv("RELEASE")?.toBoolean() ?: false
		val signingStoreFile = System.getenv("SIGNING_STORE_FILE")?.let(::File)
		val releaseSigningEnv = listOf(
			"SIGNING_KEY_ALIAS",
			"SIGNING_KEY_PASSWORD",
			"SIGNING_STORE_PASSWORD",
			"SIGNING_STORE_FILE"
		)
		val hasReleaseSigning =
			releaseSigningEnv.all { System.getenv(it)?.isNotEmpty() == true } &&
				signingStoreFile?.isFile == true &&
				signingStoreFile.length() > 0
		val releaseSigningCertificateSha256 = if (hasReleaseSigning) {
			val storeType = System.getenv("SIGNING_STORE_TYPE")?.takeIf { it.isNotBlank() } ?: "PKCS12"
			val keyStore = KeyStore.getInstance(storeType)
			signingStoreFile.inputStream().use { input ->
				keyStore.load(input, System.getenv("SIGNING_STORE_PASSWORD").toCharArray())
			}
			val certificate = keyStore.getCertificate(System.getenv("SIGNING_KEY_ALIAS"))
				?: throw GradleException("Release signing keystore does not contain SIGNING_KEY_ALIAS.")
			MessageDigest.getInstance("SHA-256")
				.digest(certificate.encoded)
				.joinToString("") { byte -> "%02x".format(byte) }
		} else {
			null
		}

		if ((isRelease || releaseBuildRequested) && !hasReleaseSigning) {
			throw GradleException(
				"Release builds require SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, " +
					"SIGNING_STORE_PASSWORD, and a non-empty SIGNING_STORE_FILE."
			)
		}
		if (
			(isRelease || releaseBuildRequested) &&
			releaseSigningCertificateSha256 != null &&
			releaseSigningCertificateSha256 != expectedReleaseCertificateSha256
		) {
			throw GradleException(
				"Release signing certificate mismatch. Expected " +
					"$expectedReleaseCertificateSha256 but found $releaseSigningCertificateSha256."
			)
		}

		getByName("release") {
			isMinifyEnabled = true
			isDebuggable = false
			isProfileable = false
			isJniDebuggable = false
			isShrinkResources = true
			if (hasReleaseSigning) {
				signingConfig = signingConfigs.getByName("release")
			}
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}

		getByName("debug") {
			applicationIdSuffix = ".debug"
			resValue("string", "app_name", "Navic (Dev)")
		}

		create("readerDev") {
			initWith(getByName("debug"))
			applicationIdSuffix = ".readerdev"
			isDebuggable = true
			isProfileable = false
			isJniDebuggable = true
			isMinifyEnabled = false
			isShrinkResources = false
			buildConfigField("boolean", "NAVIC_READER_DEV", "true")
			resValue("string", "app_name", "Navic Reader Lab")
		}
	}

	packaging {
		resources {
			excludes += "/okhttp3/**"
			excludes += "/*.properties"
			excludes += "/org/antlr/**"
			excludes += "/com/android/tools/smali/**"
			excludes += "/org/eclipse/jgit/**"
			excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
			excludes += "/org/bouncycastle/**"
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
}

extensions.configure<ApplicationAndroidComponentsExtension> {
	onVariants { variant ->
		variant.outputs.forEach { output ->
			if (output is VariantOutputImpl) {
				output.outputFileName = "Navic.apk"
			}
		}
	}
	onVariants(selector().withBuildType("release")) {
		it.packaging.resources.excludes.apply {
			add("/**/*.version")
			add("/kotlin-tooling-metadata.json")
			add("/DebugProbesKt.bin")
			add("/**/*.kotlin_builtins")
		}
	}
}

dependencies {
	implementation(projects.composeApp)
	implementation(libs.androidx.activity.compose)
	implementation(libs.cmp.material3)
	implementation(libs.koin.android)
	implementation(libs.koin.core)
	implementation(libs.bundles.glance)
	implementation(libs.bundles.coil)
	implementation(libs.bundles.media3)
}
