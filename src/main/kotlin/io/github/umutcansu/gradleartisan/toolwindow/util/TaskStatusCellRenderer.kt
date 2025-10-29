package io.github.umutcansu.gradleartisan.toolwindow.util

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import io.github.umutcansu.gradleartisan.services.FavoriteTasksService
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class TaskStatusCellRenderer(
    private val taskStatusMap: MutableMap<String, TaskStatus>,
    private val favoriteTasksService: FavoriteTasksService
) : ListCellRenderer<String> {
    private val panel = JPanel(BorderLayout(5, 0))
    private val statusIcon = JBLabel()
    private val taskNameLabel = JBLabel()
    private val favoriteStarIcon = JBLabel()

    private val favoriteIcon: Icon = try {
        AllIcons.Nodes.Favorite
    } catch (_: Throwable) {
        EmptyIcon.create(16)
    }

    private val notFavoriteIcon: Icon = try {
        AllIcons.Nodes.NotFavoriteOnHover
    } catch (_: Throwable) {
        EmptyIcon.create(16)
    }

    private val successIcon: Icon = try {
        AllIcons.Status.Success
    } catch (_: Throwable) {
        try {
            AllIcons.General.SuccessLogin
        } catch (_: Throwable) {
            EmptyIcon.create(16)
        }
    }

    private val failIcon: Icon = try {
        AllIcons.General.Error
    } catch (_: Throwable) {
        try {
            AllIcons.General.Close
        } catch (_: Throwable) {
            EmptyIcon.create(16)
        }
    }
    private val runningIcon: Icon = AnimatedIcon.Default()

    init {
        val leftPanel = JPanel(BorderLayout(5, 0))
        leftPanel.isOpaque = false
        leftPanel.add(statusIcon, BorderLayout.WEST)
        leftPanel.add(taskNameLabel, BorderLayout.CENTER)
        panel.add(leftPanel, BorderLayout.CENTER)
        panel.add(favoriteStarIcon, BorderLayout.EAST)
        panel.isOpaque = true
        favoriteStarIcon.border = JBUI.Borders.empty(0, 8, 0, 16)
    }

    override fun getListCellRendererComponent(
        list: JList<out String>, value: String, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean
    ): Component {

        taskNameLabel.text = value

        statusIcon.icon = when (taskStatusMap.getOrDefault(value, TaskStatus.IDLE)) {
            TaskStatus.RUNNING -> runningIcon
            TaskStatus.SUCCESS -> successIcon
            TaskStatus.FAILED -> failIcon
            TaskStatus.IDLE -> null
        }

        val isFavorite = favoriteTasksService.isFavorite(value)
        favoriteStarIcon.icon = if (isFavorite) favoriteIcon else notFavoriteIcon

        if (isSelected) {
            panel.background = list.selectionBackground
            taskNameLabel.foreground = list.selectionForeground
        } else {
            panel.background = list.background
            taskNameLabel.foreground = list.foreground
        }

        (panel.getComponent(0) as JComponent).isOpaque = false
        favoriteStarIcon.isOpaque = false
        return panel
    }
}