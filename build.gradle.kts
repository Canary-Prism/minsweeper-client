/*
 *    Copyright 2025 Canary Prism <canaryprsn@gmail.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "canaryprism"
version = "1.9.0"

application {
    mainClass = "canaryprism.minsweeperclient.Main"
    mainModule = "canaryprism.minsweeperclient"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("io.github.canary-prism:minsweeper-java:4.0.0")
    implementation("org.apache.xmlgraphics:batik-swing:1.18")
    implementation("commons-io:commons-io:2.14.0")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.18")

    // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // https://mvnrepository.com/artifact/tools.jackson.core/jackson-databind
    implementation("tools.jackson.core:jackson-databind:3.0.0-rc10")

    // https://mvnrepository.com/artifact/dev.dirs/directories
    implementation("dev.dirs:directories:26")

    implementation("com.formdev:flatlaf:3.6.1")
    implementation("com.github.weisj:darklaf-core:3.1.0")
}

apply("jpackage.gradle")

tasks.shadowJar {
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
