package io.github.umutcansu.gradleartisan.toolwindow.tabs

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatus
import io.github.umutcansu.gradleartisan.services.FavoriteTasksService
import io.github.umutcansu.gradleartisan.services.GradleTaskRepository
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatusCellRenderer
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

class FavoritesTab(
    private val project: Project,
    private val taskStatusMap: MutableMap<String, TaskStatus>,
    private val taskRepository: GradleTaskRepository,
    private val favoriteTasksService: FavoriteTasksService,
    private val onFavoritesChanged: () -> Unit,
    private val updateTaskStatus: (String, TaskStatus) -> Unit
) {
    private val favoritesListModel = DefaultListModel<String>()
    private val favoritesList = JBList(favoritesListModel)

    fun getPanel(): JPanel {
        favoritesList.cellRenderer = TaskStatusCellRenderer(taskStatusMap, favoriteTasksService)
        val panel = JPanel(BorderLayout())
        favoritesList.addMouseListener(createMouseListener(favoritesList))
        panel.add(JBScrollPane(favoritesList), BorderLayout.CENTER)
        return panel
    }

    fun refreshFavoritesList() {
        val selectedValue = favoritesList.selectedValue
        favoritesListModel.clear()
        favoriteTasksService.getFavorites().sorted().forEach {
            favoritesListModel.addElement(it)
        }
        favoritesList.setSelectedValue(selectedValue, true)
    }

    fun repaintList() {
        favoritesList.repaint()
    }

    fun isTaskInList(taskName: String): Boolean {
        return (0 until favoritesListModel.size()).any { favoritesListModel.getElementAt(it) == taskName }
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
        taskRepository.logDebug("UI: Running task '$taskName'...")
        updateTaskStatus(taskName, TaskStatus.RUNNING)
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.basePath
            taskNames = listOf(taskName)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        ExternalSystemUtil.runTask(
            settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            null, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false
        )
    }

}