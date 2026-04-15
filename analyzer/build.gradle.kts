plugins {
	id("java")
	id("org.springframework.boot") version "3.1.5"
	id("io.spring.dependency-management") version "1.1.4"
}

group = "com.effectivnost"
version = "1.0.0"

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// CSV Processing
	implementation("org.apache.commons:commons-csv:1.10.0")

	// Excel Processing
	implementation("org.apache.poi:poi:5.2.5")
	implementation("org.apache.poi:poi-ooxml:5.2.5")

	// PDF Export
	implementation("com.itextpdf:itext7-core:7.2.5")

	// Statistics
	implementation("org.apache.commons:commons-math3:3.6.1")

	// WebJars
	implementation("org.webjars:chartjs:3.9.1")
	implementation("org.webjars:bootstrap:5.3.2")
	implementation("org.webjars:jquery:3.7.1")
	implementation("org.webjars:webjars-locator:0.52")

	// File upload
	implementation("commons-fileupload:commons-fileupload:1.5")

	// Jackson JSON
	implementation("com.fasterxml.jackson.core:jackson-databind")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")

	// DevTools
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}