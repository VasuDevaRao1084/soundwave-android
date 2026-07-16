plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Applied conditionally in app/build.gradle.kts only if google-services.json
    // is present, so the build never breaks for anyone who hasn't set up
    // Firebase yet — see the push-notifications setup notes in the handoff doc.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
