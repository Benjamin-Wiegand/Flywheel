import javax.inject.Inject
import org.gradle.process.ExecOperations
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
}

abstract class CreatePrivdJarTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputFiles
    abstract val classDirs: ConfigurableFileCollection

    @get:InputFile
    abstract val androidJar: RegularFileProperty

    @get:InputFile
    abstract val d8Executable: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun create() {
        val classFiles = classDirs
            .flatMap { Files.walk(it.toPath()).toList() }
            .filter(Files::isRegularFile)
            .toTypedArray();

        execOperations.exec {
            commandLine(
                d8Executable.get().asFile.absolutePath,
                "--release",
                "--output", outputJar.get().asFile.absolutePath,
                "--classpath", androidJar.get().asFile.absolutePath,
                *classFiles
            )
        }
    }
}

android {
    namespace = "io.benwiegand.projection.geargrinder.privd"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.name;
        val buildTypeUpper = buildType.replaceFirstChar { it.uppercase() }

        tasks.register<CreatePrivdJarTask>("create${buildTypeUpper}PrivdJar") {
            description = "privileged daemon jar asset"
            dependsOn(":libprivd:compile${buildTypeUpper}JavaWithJavac")
            dependsOn("compile${buildTypeUpper}JavaWithJavac")

            var intermediatesClassesRelative = "intermediates/javac/${buildType}/compile${buildTypeUpper}JavaWithJavac/classes";

            classDirs.from(
                layout.buildDirectory.dir(intermediatesClassesRelative),
                project(":libprivd").layout.buildDirectory.dir(intermediatesClassesRelative))

            d8Executable.set(file("${android.sdkDirectory.path}/build-tools/${android.buildToolsVersion}/d8"))

            androidJar.set(file("${android.sdkDirectory.path}/platforms/android-${android.defaultConfig.targetSdk}/android.jar"))

            outputJar.set(rootProject.layout.projectDirectory.file("app/src/main/assets/privd.jar"))

        }
    }
}

dependencies {
    implementation(project(":libprivd"))
}
