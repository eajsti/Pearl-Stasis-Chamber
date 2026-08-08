plugins {
    id("java")
}

group = "com.stasis"
version = "1.0.6"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Latest stable 26.2 build. Use 26.2.build.+ if you want Gradle to always
    // resolve the newest 26.2 build automatically instead of pinning one.
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
}
