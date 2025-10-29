package io.github.umutcansu.gradleartisan.toolwindow.util

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
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

    private val scaledStarIcon: Icon = IconUtil.scale(AllIcons.Ide.Rating, null, 1.5f)
    private val grayStar: Icon = IconUtil.desaturate(scaledStarIcon)
    private val vibrantStar: Icon = IconUtil.colorize(scaledStarIcon, JBColor.ORANGE, false)

    private val runningIcon: Icon = AnimatedIcon.Default()

    init {
        val leftPanel = JPanel(BorderLayout(5, 0))
        leftPanel.isOpaque = false
        leftPanel.add(statusIcon, BorderLayout.WEST)
        leftPanel.add(taskNameLabel, BorderLayout.CENTER)
        panel.add(leftPanel, BorderLayout.CENTER)
        panel.add(favoriteStarIcon, BorderLayout.EAST)
        panel.isOpaque = true
        favoriteStarIcon.border = JBUI.Borders.empty(0, 4, 0, 8)
    }

    override fun getListCellRendererComponent(
        list: JList<out String>, value: String, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean
    ): Component {

        taskNameLabel.text = value

        statusIcon.icon = when (taskStatusMap.getOrDefault(value, TaskStatus.IDLE)) {
            TaskStatus.RUNNING -> runningIcon
            TaskStatus.SUCCESS -> AllIcons.Status.Success
            TaskStatus.FAILED -> AllIcons.General.Error
            TaskStatus.IDLE -> null
        }

        val isFavorite = favoriteTasksService.isFavorite(value)

        if (isSelected) {
            panel.background = list.selectionBackground
            taskNameLabel.foreground = list.selectionForeground

            val selectionColor = list.selectionForeground
            val starToUse = if (isFavorite) vibrantStar else grayStar
            favoriteStarIcon.icon = IconUtil.colorize(starToUse, selectionColor, false)

        } else {
            panel.background = list.background
            taskNameLabel.foreground = list.foreground

            favoriteStarIcon.icon = if (isFavorite) vibrantStar else grayStar
        }

        (panel.getComponent(0) as JComponent).isOpaque = false
        favoriteStarIcon.isOpaque = false
        return panel
    }
}