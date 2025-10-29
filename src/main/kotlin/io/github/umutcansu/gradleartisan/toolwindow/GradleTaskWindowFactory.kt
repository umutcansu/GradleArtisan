package io.github.umutcansu.gradleartisan.toolwindow

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatus

class GradleTaskWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val artisanWindow = ArtisanWindow(project)
        val content = ContentFactory.getInstance().createContent(artisanWindow.getPanel(), "", false)
        toolWindow.contentManager.addContent(content)

        project.messageBus.connect(toolWindow.disposable).subscribe(
            ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    val taskName = (env.runProfile as? ExternalSystemRunConfiguration)
                        ?.settings?.taskNames?.firstOrNull() ?: return

                    if (artisanWindow.isTaskInOurList(taskName)) {
                        handler.addProcessListener(object : ProcessAdapter() {
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                if (event.text.contains("BUILD FAILED")) {
                                    artisanWindow.updateTaskStatus(taskName, TaskStatus.FAILED)
                                }
                            }

                            override fun processTerminated(event: ProcessEvent) {
                                if (artisanWindow.getTaskStatus(taskName) == TaskStatus.RUNNING) {
                                    artisanWindow.updateTaskStatus(taskName, TaskStatus.SUCCESS)
                                }
                            }
                        })
                    }
                }
            }
        )
    }
}