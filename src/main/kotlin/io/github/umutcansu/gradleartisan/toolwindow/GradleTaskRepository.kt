package io.github.umutcansu.gradleartisan.services

import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

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

    fun getExtPropertiesFromPsi(): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        runReadAction {
            logDebug("Repository (PSI): Read action started.")
            val psiManager = PsiManager.getInstance(project)
            val projectBaseDir = project.baseDir ?: return@runReadAction

            val rootBuildFile = projectBaseDir.findChild("build.gradle") ?: projectBaseDir.findChild("build.gradle.kts")
            rootBuildFile?.let { psiManager.findFile(it)?.let { file -> parsePsiFile(file, properties) } }

            val appModuleDir = projectBaseDir.findChild("app")
            val appBuildFile = appModuleDir?.findChild("build.gradle") ?: appModuleDir?.findChild("build.gradle.kts")
            appBuildFile?.let { psiManager.findFile(it)?.let { file -> parsePsiFile(file, properties) } }
        }
        logDebug("Repository (PSI): Analysis completed. A total of ${properties.size} variables were found.")
        return properties
    }

    fun parsePsiFile(psiFile: PsiFile, properties: MutableMap<String, String>) {
        when (psiFile) {
            is GroovyFile -> {
                logDebug("Repository (PSI): Analyzing Groovy file: ${psiFile.name}")
                psiFile.statements.forEach { statement ->
                    if (statement is GrMethodCall && statement.invokedExpression.text == "ext") {
                        logDebug("Repository (PSI): Found block 'ext' in '${psiFile.name}'.")
                        statement.closureArguments.firstOrNull()?.let { closure ->
                            closure.statements.forEach { innerStatement ->
                                if (innerStatement is GrAssignmentExpression) {
                                    val name = innerStatement.lValue.text
                                    val value = (innerStatement.rValue as? GrLiteral)?.value as? String
                                    if (name != null && value != null) {
                                        logDebug("Repository (PSI): Variable found -> $name = $value")
                                        properties[name] = value
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is KtFile -> {
                logDebug("Repository (PSI): Analyzing Kotlin file: ${psiFile.name}")
                psiFile.accept(object : KtTreeVisitorVoid() {
                    override fun visitBinaryExpression(expression: KtBinaryExpression) {
                        super.visitBinaryExpression(expression)

                        if (expression.operationReference.getReferencedName() == "=") {
                            val left = expression.left
                            if (left is KtArrayAccessExpression && left.arrayExpression?.text == "extra") {
                                val name = left.indexExpressions.firstOrNull()?.text?.trim('"')
                                val value = (expression.right as? KtStringTemplateExpression)?.text?.trim('"')

                                if (name != null && value != null) {
                                    logDebug("Repository (PSI): Variable found -> $name = $value")
                                    properties[name] = value
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    fun getAllTasks(): List<String> {
        logDebug("Repository: Searching for all Gradle tasks...")
        val projectBasePath = project.basePath ?: return emptyList()
        val projectInfo = ExternalSystemApiUtil.findProjectInfo(project, GradleConstants.SYSTEM_ID, projectBasePath) ?: return emptyList()
        val projectStructure = projectInfo.externalProjectStructure ?: return emptyList()
        val modules = ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.MODULE)
        val allTasks = mutableSetOf<String>()
        for (module in modules) {
            val standardTasks = ExternalSystemApiUtil.findAll(module, ProjectKeys.TASK)
            standardTasks.mapTo(allTasks) { it.data.name }
            val androidModel = module.children.find { it.data is GradleAndroidModelData }?.data as? GradleAndroidModelData
            if (androidModel != null) {
                androidModel.androidProject.variantsBuildInformation.forEach { variantInfo ->
                    variantInfo.buildInformation.let { buildInfo ->
                        allTasks.add(buildInfo.assembleTaskName)
                        buildInfo.bundleTaskName?.let { allTasks.add(it) }
                        buildInfo.apkFromBundleTaskName?.let { allTasks.add(it) }
                    }
                }
                androidModel.variants.forEach { variant ->
                    variant.mainArtifact.let { artifact ->
                        allTasks.add(artifact.compileTaskName)
                        artifact.assembleTaskName.let { allTasks.add(it) }
                    }
                    variant.hostTestArtifacts.forEach { artifact ->
                        allTasks.add(artifact.compileTaskName)
                        artifact.assembleTaskName?.let { allTasks.add(it) }
                    }
                    variant.deviceTestArtifacts.forEach { artifact ->
                        allTasks.add(artifact.compileTaskName)
                        artifact.assembleTaskName?.let { allTasks.add(it) }
                    }
                }
            }
        }
        logDebug("Repository: A total of ${allTasks.size} unique tasks were found.")
        return allTasks.sorted()
    }
}