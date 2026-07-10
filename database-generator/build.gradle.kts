plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.jar {
    manifest { attributes["Main-Class"] = "ua.ukrtv.app.generator.DbGeneratorKt" }
    from(configurations.runtimeClasspath.map { it.map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
