plugins {
	java
	id("org.springframework.boot") version "3.4.4"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "me.danb76"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	implementation("com.mysql:mysql-connector-j:8.3.0")

	implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.4.4")
	implementation("org.springframework.boot:spring-boot-starter-security:3.4.4")
	implementation("org.springframework.security:spring-security-core:6.4.4")

	implementation("io.minio:minio:8.5.17")

	implementation("commons-io:commons-io:2.19.0")

	implementation("org.imgscalr:imgscalr-lib:4.2")

	implementation("me.paulschwarz:spring-dotenv:4.0.0")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
