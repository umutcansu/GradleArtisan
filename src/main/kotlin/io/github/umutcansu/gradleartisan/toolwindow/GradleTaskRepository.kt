package io.github.umutcansu.gradleartisan.services

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import java.nio.file.Paths

class GradleTaskRepository(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(GradleTaskRepository::class.java)
        private const val IS_LOGGING_ENABLED = true
    }

    fun logDebug(message: String) {
        if (IS_LOGGING_ENABLED) {
            LOG.warn("[Gradle Artisan] $message")
        }
    }
    fun getAllGradleExtProperties(): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        runReadAction {
            logDebug("Repository (PSI): Scanning all Gradle files...")
            val psiManager = PsiManager.getInstance(project)

            val projectBaseDir = project.basePath
                ?.let { VirtualFileManager.getInstance().findFileByNioPath(Paths.get(it)) } ?: return@runReadAction


            val gradleFiles = mutableListOf<VirtualFile>()
            VfsUtilCore.visitChildrenRecursively(projectBaseDir, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && (file.name == "build.gradle" || file.name == "build.gradle.kts")) {
                        gradleFiles.add(file)
                    }
                    return true
                }
            })

            logDebug("Repository (PSI): Found ${gradleFiles.size} Gradle files to analyze.")


            for (file in gradleFiles) {
                psiManager.findFile(file)?.let { psiFile ->
                    parsePsiGradleFile(psiFile, properties)
                }
            }
        }

        logDebug("Repository (PSI): Analysis complete. Found ${properties.size} variables.")
        return properties
    }

    private fun parsePsiGradleFile(psiFile: PsiFile, properties: MutableMap<String, String>) {
        when (psiFile) {
            is GroovyFile -> parseGroovyGradleFile(psiFile, properties)
            is KtFile -> parseKotlinGradleFile(psiFile, properties)
        }
    }

    private fun parseGroovyGradleFile(file: GroovyFile, properties: MutableMap<String, String>) {
        logDebug("Repository (PSI): Analyzing Groovy Gradle file: ${file.name}")

        file.accept(object : GroovyRecursiveElementVisitor() {
            override fun visitMethodCall(call: GrMethodCall) {
                super.visitMethodCall(call)
                val invokedText = call.invokedExpression.text

                // ext { key = value }
                if (invokedText == "ext") {
                    call.closureArguments.firstOrNull()?.let { closure ->
                        closure.statements.forEach { stmt ->
                            if (stmt is GrAssignmentExpression) {
                                val name = stmt.lValue.text
                                val value = (stmt.rValue as? GrLiteral)?.value?.toString()
                                if (name != null && value != null) {
                                    logDebug("ext block -> $name = $value")
                                    properties[name] = value
                                }
                            }
                        }
                    }
                }

                // project.ext.set("key", "value")
                if (invokedText.endsWith("ext.set") || invokedText == "ext.set") {
                    val args = call.argumentList.allArguments
                    if (args.size >= 2) {
                        val name = (args[0] as? GrLiteral)?.value?.toString()
                        val value = (args[1] as? GrLiteral)?.value?.toString()
                        if (name != null && value != null) {
                            logDebug("ext.set -> $name = $value")
                            properties[name] = value
                        }
                    }
                }
            }

            override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
                super.visitAssignmentExpression(expression)

                // project.ext["key"] = "value"
                val lValue = expression.lValue
                if (lValue is GrIndexProperty && (lValue.invokedExpression.text.endsWith("ext"))) {
                    val name = (lValue.argumentList.allArguments.firstOrNull() as? GrLiteral)?.value?.toString()
                    val value = (expression.rValue as? GrLiteral)?.value?.toString()
                    if (name != null && value != null) {
                        logDebug("ext[\"$name\"] = $value")
                        properties[name] = value
                    }
                }
            }
        })
    }

    private fun parseKotlinGradleFile(file: KtFile, properties: MutableMap<String, String>) {
        logDebug("Repository (PSI): Analyzing Kotlin Gradle file: ${file.name}")

        file.accept(object : KtTreeVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)

                if (expression.operationReference.getReferencedName() == "=") {
                    val left = expression.left
                    val right = expression.right

                    // extra["key"] = "value"
                    if (left is KtArrayAccessExpression && (left.arrayExpression?.text == "extra" || left.arrayExpression?.text?.endsWith(".extra") == true)) {
                        val name = left.indexExpressions.firstOrNull()?.text?.trim('"')
                        val value = (right as? KtStringTemplateExpression)?.entries?.joinToString("") { it.text }?.trim('"')
                        if (name != null && value != null) {
                            logDebug("extra[\"$name\"] = $value")
                            properties[name] = value
                        }
                    }
                }
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val callee = expression.calleeExpression?.text
                if (callee == "set" && (expression.getQualifiedExpressionForSelector()?.receiverExpression?.text == "extra"
                            || expression.getQualifiedExpressionForSelector()?.receiverExpression?.text?.endsWith(".extra") == true)
                ) {
                    val args = expression.valueArguments
                    if (args.size >= 2) {
                        val name = args[0].text.trim('"')
                        val value = args[1].text.trim('"')
                        logDebug("extra.set(\"$name\", \"$value\")")
                        properties[name] = value
                    }
                }
            }
        })
    }



    fun getAllTasksStable(): List<String> {
        logDebug("Repository: Searching for all Gradle tasks...")
        val allTasks = mutableSetOf<String>()

        try {
            val projectBasePath = project.basePath ?: return emptyList()
            val projectInfo = ExternalSystemApiUtil.findProjectInfo(project, GradleConstants.SYSTEM_ID, projectBasePath)
                ?: return emptyList()

            val projectStructure = projectInfo.externalProjectStructure ?: return emptyList()
            val modules = ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.MODULE)

            for (module in modules) {
                try {
                    ExternalSystemApiUtil.findAll(module, ProjectKeys.TASK)
                        .mapTo(allTasks) { it.data.name }
                } catch (e: Exception) {
                    logDebug("Repository: Error fetching standard tasks for module ${module.data.id}: ${e.message}")
                }

                processAndroidTasksWithReflection(module, allTasks)
            }

        } catch (e: Throwable) {
            logDebug("Repository: Critical error during task search: ${e.message}")
        }

        logDebug("Repository: A total of ${allTasks.size} unique tasks were found.")
        return allTasks.sorted()
    }

    private fun processAndroidTasksWithReflection(module: DataNode<ModuleData>, allTasks: MutableSet<String>) {
        try {
            val modelClassName = "com.android.tools.idea.gradle.project.model.GradleAndroidModelData"
            val modelClass = Class.forName(modelClassName) // ClassNotFoundException fırlatabilir

            val androidModelDataNode = module.children.find { modelClass.isInstance(it.data) }
            val androidModel: Any? = androidModelDataNode?.data // Tip 'Any?' oldu

            if (androidModel != null) {

                val androidProject = safeInvoke(androidModel, "getAndroidProject")
                (androidProject?.let { safeInvoke(it, "getVariantsBuildInformation") } as? Collection<*>)?.forEach { variantInfo ->
                    val buildInfo = variantInfo?.let { safeInvoke(it, "getBuildInformation") }
                    if (buildInfo != null) {
                        addTask(safeInvoke(buildInfo, "getAssembleTaskName"), allTasks)
                        addTask(safeInvoke(buildInfo, "getBundleTaskName"), allTasks)
                        addTask(safeInvoke(buildInfo, "getApkFromBundleTaskName"), allTasks)
                    }
                }

                (safeInvoke(androidModel, "getVariants") as? Collection<*>)?.forEach { variant ->
                    if (variant == null) return@forEach

                    val mainArtifact = safeInvoke(variant, "getMainArtifact")
                    if (mainArtifact != null) {
                        addTask(safeInvoke(mainArtifact, "getCompileTaskName"), allTasks)
                        addTask(safeInvoke(mainArtifact, "getAssembleTaskName"), allTasks)
                    }

                    (safeInvoke(variant, "getHostTestArtifacts") as? Collection<*>)?.forEach { artifact ->
                        if (artifact != null) {
                            addTask(safeInvoke(artifact, "getCompileTaskName"), allTasks)
                            addTask(safeInvoke(artifact, "getAssembleTaskName"), allTasks)
                        }
                    }

                    (safeInvoke(variant, "getDeviceTestArtifacts") as? Collection<*>)?.forEach { artifact ->
                        if (artifact != null) {
                            addTask(safeInvoke(artifact, "getCompileTaskName"), allTasks)
                            addTask(safeInvoke(artifact, "getAssembleTaskName"), allTasks)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logDebug("Repository: Error processing Android model (via Reflection) for module ${module.data.id}. " +
                    "This is safely ignored. Error: ${e.message}")
        }
    }

    private fun safeInvoke(receiver: Any, methodName: String): Any? {
        return try {
            val method = receiver.javaClass.getMethod(methodName)
            method.invoke(receiver)
        } catch (e: Exception) {
            logDebug("safeInvoke failed for '$methodName' on ${receiver.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun addTask(task: Any?, allTasks: MutableSet<String>) {
        (task as? String)?.let {
            if (it.isNotBlank()) {
                allTasks.add(it)
            }
        }
    }

}