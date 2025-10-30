package io.github.umutcansu.gradleartisan.toolwindow.tabs

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import io.github.umutcansu.gradleartisan.services.FavoriteTasksService
import io.github.umutcansu.gradleartisan.services.GradleTaskRepository
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatus
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatusCellRenderer
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AllTasksTab(
    private val project: Project,
    private val taskStatusMap: MutableMap<String, TaskStatus>,
    private val taskRepository: GradleTaskRepository,
    private val favoriteTasksService: FavoriteTasksService,
    private val onFavoritesChanged: () -> Unit,
    private val updateTaskStatus: (String, TaskStatus) -> Unit
) {

    private val allTasksMasterList = mutableListOf<String>()
    private val allTasksListModel = DefaultListModel<String>()
    private val allTasksList = JBList(allTasksListModel)
    private val searchField = SearchTextField(false)

    private val scrollPane = JBScrollPane(allTasksList)
    private val loadingPanel = JBLoadingPanel(BorderLayout(), project)

    private val animationTimer: javax.swing.Timer

    init {
        animationTimer = javax.swing.Timer(100) {
            if (taskStatusMap.values.any { it == TaskStatus.RUNNING }) {
                allTasksList.repaint()
            } else {
                animationTimer.stop()
            }
        }
    }


    fun getPanel(): JPanel {
        allTasksList.cellRenderer = TaskStatusCellRenderer(taskStatusMap, favoriteTasksService)
        val panel = JPanel(BorderLayout())
        val controlsPanel = JPanel(BorderLayout())
        val refreshButton = JButton("Refresh")
        controlsPanel.add(searchField, BorderLayout.CENTER)
        controlsPanel.add(refreshButton, BorderLayout.EAST)

        searchField.addDocumentListener(createSwingListener { filterTasks() })

        refreshButton.addActionListener { refreshGradleTasks() }
        allTasksList.addMouseListener(createMouseListener(allTasksList))
        panel.add(controlsPanel, BorderLayout.NORTH)

        loadingPanel.add(scrollPane, BorderLayout.CENTER)
        panel.add(loadingPanel, BorderLayout.CENTER)

        return panel
    }

    fun refreshGradleTasks() {
        loadingPanel.startLoading()
        allTasksListModel.clear()

        object : Task.Backgroundable(project, "Loading Gradle Tasks...", false) {
            private var newTasks: List<String> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                newTasks = taskRepository.getAllTasksStable(project)
            }

            override fun onSuccess() {
                taskStatusMap.clear()
                allTasksMasterList.clear()
                allTasksMasterList.addAll(newTasks)
                filterTasks()
                loadingPanel.stopLoading()
            }

            override fun onCancel() {
                loadingPanel.stopLoading()
            }

            override fun onThrowable(error: Throwable) {
                loadingPanel.stopLoading()
            }
        }.queue()
    }

    fun repaintList() {
        allTasksList.repaint()
    }

    fun isTaskInList(taskName: String): Boolean {
        return allTasksMasterList.contains(taskName)
    }

    private fun filterTasks() {
        val searchTerm = searchField.text.lowercase()
        val filteredTasks = allTasksMasterList.filter { it.lowercase().contains(searchTerm) }
        allTasksListModel.clear()
        filteredTasks.forEach { allTasksListModel.addElement(it) }
    }

    private fun createSwingListener(action: () -> Unit): DocumentListener {
        return object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = action()
            override fun removeUpdate(e: DocumentEvent?) = action()
            override fun changedUpdate(e: DocumentEvent?) = action()
        }
    }

    private fun createMouseListener(list: JBList<String>): MouseAdapter {
        return object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (index == -1) return
                list.selectedIndex = index
                val taskName = list.selectedValue
                val cellBounds = list.getCellBounds(index, index)

                val starIconWidth = 40

                if (e.point.x > cellBounds.width - starIconWidth) {
                    toggleFavorite(taskName)
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    createDynamicPopupMenu(taskName).show(e.component, e.x, e.y)
                } else if (e.clickCount == 2) {
                    runGradleTask(taskName)
                }
            }
        }
    }

    private fun toggleFavorite(taskName: String) {
        if (favoriteTasksService.isFavorite(taskName)) {
            favoriteTasksService.removeFavorite(taskName)
        } else {
            favoriteTasksService.addFavorite(taskName)
        }
        onFavoritesChanged()
        allTasksList.repaint()
    }

    private fun createDynamicPopupMenu(taskName: String): JPopupMenu {
        val group = DefaultActionGroup()
        val runAction = object : DumbAwareAction("Run") {
            override fun actionPerformed(e: AnActionEvent) {
                runGradleTask(taskName)
            }
        }
        group.add(runAction)
        group.addSeparator()

        val toggleFavoriteAction = object : DumbAwareAction() {
            override fun update(e: AnActionEvent) {
                val presentation = e.presentation
                if (favoriteTasksService.isFavorite(taskName)) {
                    presentation.text = "Remove from Favorites"
                } else {
                    presentation.text = "Add to Favorites"
                }
            }

            override fun actionPerformed(e: AnActionEvent) {
                toggleFavorite(taskName)
            }
        }
        group.add(toggleFavoriteAction)

        val actionPopupMenu: ActionPopupMenu = ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.UNKNOWN, group)

        return actionPopupMenu.component
    }

    private fun runGradleTask(taskName: String) {
        taskRepository.logDebug("UI: Running task $taskName...")
        updateTaskStatus(taskName, TaskStatus.RUNNING)
        allTasksList.repaint()

        if (!animationTimer.isRunning) {
            animationTimer.start()
        }

        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.basePath
            taskNames = listOf(taskName)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }

        ExternalSystemUtil.runTask(
            settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            null,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC, false
        )
    }
}