# Build Toolchain

The shared build baseline uses Gradle 9.8.2, Android Gradle Plugin 9.5.0,
Kotlin/Compose compiler 2.4.21 and KSP 2.3.13. The Gradle daemon requires a locally
installed Microsoft JDK 25. Set `JAVA_HOME` to that installation before invoking
the wrapper; CI provisions the matching JDK family with `setup-java`.

The daemon criteria intentionally contain no stale JDK 21 download URLs. They
select the runtime, not the application's Java/Kotlin bytecode level. Application
output remains Java/Kotlin 21; Android compile/target SDK 37 and minimum SDK 24
are unchanged. Room, Compose UI and all unrelated dependency pins are unchanged.

Kotlin's published fully tested range ends before Gradle 9.8.2 / AGP 9.5.0.
That is a validation risk, not a claim that this combination cannot work. The
manual pilot evaluates the actual music host tests, generated Room code, native
library tests and signed release packaging; final reader alignment and integrated
delivery remain a separate coordinated gate.

Windows builds/tests must use the installed shared Gradle build gate. Do not
replace its queue or lifecycle supervision with a direct wrapper invocation.
