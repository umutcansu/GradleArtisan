package io.github.umutcansu.gradleartisan.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.github.umutcansu.gradleartisan.services.DynamicTaskService
import io.github.umutcansu.gradleartisan.services.FavoriteTasksService
import io.github.umutcansu.gradleartisan.services.GradleTaskRepository
import io.github.umutcansu.gradleartisan.toolwindow.tabs.DynamicRunnerTab
import io.github.umutcansu.gradleartisan.toolwindow.tabs.AllTasksTab
import io.github.umutcansu.gradleartisan.toolwindow.tabs.FavoritesTab
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatus
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

class ArtisanWindow(private val project: Project) {

    private val taskStatusMap = mutableMapOf<String, TaskStatus>()
    private val taskRepository = GradleTaskRepository(project)
    private val favoriteTasksService = project.service<FavoriteTasksService>()
    private val dynamicTaskService = project.service<DynamicTaskService>()

    private val allTasksTab: AllTasksTab
    private val favoritesTab: FavoritesTab
    private val dynamicRunnerTab: DynamicRunnerTab

    init {
        val onFavoritesChanged: () -> Unit = {
            favoritesTab.refreshFavoritesList()
            allTasksTab.repaintList()
        }

        val updateStatusCallback: (String, TaskStatus) -> Unit = this::updateTaskStatus

        allTasksTab = AllTasksTab(
            project,
            taskStatusMap,
            taskRepository,
            favoriteTasksService,
            onFavoritesChanged,
            updateStatusCallback
        )
        favoritesTab = FavoritesTab(
            project,
            taskStatusMap,
            taskRepository,
            favoriteTasksService,
            onFavoritesChanged,
            updateStatusCallback
        )
        dynamicRunnerTab = DynamicRunnerTab(
            project,
            taskStatusMap,
            taskRepository,
            favoriteTasksService,
            dynamicTaskService,
            onFavoritesChanged,
            updateStatusCallback
        )

        DumbService.getInstance(project).runWhenSmart {
            allTasksTab.refreshGradleTasks()
            favoritesTab.refreshFavoritesList()
        }
    }

    fun getPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout())
        val tabbedPane = JTabbedPane()

        tabbedPane.addTab("All Tasks", allTasksTab.getPanel())
        tabbedPane.addTab("Favorites", favoritesTab.getPanel())
        tabbedPane.addTab("Dynamic Runner", dynamicRunnerTab.getPanel())

        tabbedPane.addChangeListener {
            if (tabbedPane.selectedIndex == 1) {
                favoritesTab.refreshFavoritesList()
            }
        }

        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        return mainPanel
    }

    fun updateTaskStatus(taskName: String, status: TaskStatus) {
        taskRepository.logDebug("UI: Status of '$taskName' updated to: $status")
        SwingUtilities.invokeLater {
            taskStatusMap[taskName] = status
            allTasksTab.repaintList()
            favoritesTab.repaintList()
            dynamicRunnerTab.updatePreviewStatus(taskName,status)
        }
    }

    fun isTaskInOurList(taskName: String) : Boolean {
        return allTasksTab.isTaskInList(taskName)
                || favoritesTab.isTaskInList(taskName)
                || dynamicRunnerTab.getCurrentTaskString() == taskName
    }

    fun getTaskStatus(taskName: String): TaskStatus? {
        return taskStatusMap[taskName]
    }
}