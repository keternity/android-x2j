package androidx2j

import android.databinding.tool.ext.toCamelCase
import androidx2j.parser.X2JTranslator
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.utils.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

@Suppress("UnstableApiUsage")
class X2JPlugin : Plugin<Project> {
    private val layoutFiles = hashMapOf<String, File>()
    private var isAndroidLibrary = false

    override fun apply(project: Project) {
        MyLogger.log("hello, android-x2j")
        try {
            val androidApp = project.extensions.findByType(AppExtension::class.java)
            val androidLib = project.extensions.findByType(LibraryExtension::class.java)
            val android = androidApp ?: androidLib ?: run {
                MyLogger.error("not a android module")
                return
            }
            isAndroidLibrary = androidLib != null

            android.sourceSets.getByName("main").res.srcDirs.asSequence()
                    .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
                    .filter { it.name.startsWith("layout") }
                    .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
                    .forEach { layoutFiles[it.nameWithoutExtension] = it }

            android.registerTransform(X2JTransform(android))

            project.afterEvaluate {
                if (android is AppExtension) {
                    android.applicationVariants.forEach {
                        generateX2J(project, android, it)
                    }
                } else if (android is LibraryExtension) {
                    android.libraryVariants.forEach {
                        generateX2J(project, android, it)
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun generateX2J(project: Project, android: BaseExtension, variant: BaseVariant) {
        val variantName = variant.name
        val applicationId = variant.applicationId
        val outputRootDir = File(project.buildDir, "generated/source/apt/$variantName")

        val genX2JTask = project.tasks.create("generate${variantName.toCamelCase()}X2J") {
            group = "build"
            doLast {
                MyLogger.log("generate X2J file")
                X2JTranslator.start(android, variant, outputRootDir)

                val x2jFile = File(outputRootDir, "dev/android/x2j/X2J.java")
                x2jFile.parentFile.mkdirs()
                x2jFile.delete()
                x2jFile.outputStream().writer().use { writer ->
                    val layouts = layoutFiles.keys.joinToString(", ") { "\"$it\"" }
                    writer.write(X2J_CODE
                            .replace("\"o_0_layouts\"", layouts)
                            .replace("o_0_applicationId", applicationId)
                            .replace("o_0_isAndroidLibrary", "" + isAndroidLibrary))
                }
            }
        }
        variant.registerJavaGeneratingTask(genX2JTask, outputRootDir)

        project.tasks.getByName("generate${variantName.toCamelCase()}Sources").doLast {
            MyLogger.log("generate R file")
            val rFilePath = applicationId.replace(".", "/") + "/R.java"
            val rFile = File(project.buildDir, "generated/source/r/$variantName/$rFilePath")
            val rFile2 = File(project.buildDir, "generated/not_namespaced_r_class_sources/$variantName/r/$rFilePath")
            if (rFile.exists() || rFile2.exists()) {
                if (!rFile.exists()) {
                    rFile.parentFile.mkdirs()
                    FileUtils.copyFile(rFile2, rFile)
                }
                if (isAndroidLibrary) {
                    val r2File = File(outputRootDir, rFilePath.replace("R.java", "R2.java"))
                    rFile.inputStream().reader().use { reader ->
                        r2File.parentFile.mkdirs()
                        r2File.outputStream().writer().use { writer ->
                            writer.write(reader.readText()
                                    .replace("class R {", "class R2 {"))
                        }
                    }
                }
                MyLogger.log("create R file success")
            } else {
                MyLogger.log("R file not found, $rFile; $rFile2")
            }
        }
    }
}

