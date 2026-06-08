buildscript {
	configurations.classpath {
		resolutionStrategy.force(
			"io.netty:netty-codec:4.1.135.Final",
			"io.netty:netty-codec-http:4.1.135.Final",
			"io.netty:netty-codec-http2:4.1.135.Final",
			"io.netty:netty-common:4.1.135.Final",
			"io.netty:netty-handler:4.1.135.Final",
			"io.netty:netty-handler-proxy:4.1.135.Final",
			"io.opentelemetry:opentelemetry-api:1.62.0",
			"io.opentelemetry:opentelemetry-context:1.62.0",
			"org.apache.commons:commons-lang3:3.18.0",
			"org.apache.httpcomponents:httpclient:4.5.14",
			"org.bitbucket.b_c:jose4j:0.9.6",
			"org.bouncycastle:bcpkix-jdk18on:1.84",
			"org.bouncycastle:bcprov-jdk18on:1.84",
			"org.bouncycastle:bcutil-jdk18on:1.84",
			"org.jdom:jdom2:2.0.6.1"
		)
	}
}

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlinMultiplatform) apply false
	alias(libs.plugins.kotlinMultiplatformLibrary) apply false
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.composeCompiler) apply false
	alias(libs.plugins.aboutLibraries) apply false
	alias(libs.plugins.valkyrie) apply false
}

val securityPatchedDependencies = listOf(
	"io.netty:netty-codec:4.1.135.Final",
	"io.netty:netty-codec-http:4.1.135.Final",
	"io.netty:netty-codec-http2:4.1.135.Final",
	"io.netty:netty-common:4.1.135.Final",
	"io.netty:netty-handler:4.1.135.Final",
	"io.netty:netty-handler-proxy:4.1.135.Final",
	"io.opentelemetry:opentelemetry-api:1.62.0",
	"io.opentelemetry:opentelemetry-context:1.62.0",
	"org.apache.commons:commons-lang3:3.18.0",
	"org.apache.httpcomponents:httpclient:4.5.14",
	"org.bitbucket.b_c:jose4j:0.9.6",
	"org.bouncycastle:bcpkix-jdk18on:1.84",
	"org.bouncycastle:bcprov-jdk18on:1.84",
	"org.bouncycastle:bcutil-jdk18on:1.84",
	"org.jdom:jdom2:2.0.6.1"
)

allprojects {
	configurations.configureEach {
		resolutionStrategy.force(securityPatchedDependencies)
	}
}
