plugins {
    kotlin("jvm") version "1.9.24"
    application
}

group = "com.bytedance.tools"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.9.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.bytedance.tools.codelocator.adapter.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
