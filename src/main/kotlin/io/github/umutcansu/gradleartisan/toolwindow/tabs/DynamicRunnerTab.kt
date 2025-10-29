package io.github.umutcansu.gradleartisan.toolwindow.tabs

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import io.github.umutcansu.gradleartisan.toolwindow.util.TaskStatus
import io.github.umutcansu.gradleartisan.services.DynamicTask
import io.github.umutcansu.gradleartisan.services.DynamicTaskService
import io.github.umutcansu.gradleartisan.services.FavoriteTasksService
import io.github.umutcansu.gradleartisan.services.GradleTaskRepository
import io.github.umutcansu.gradleartisan.toolwindow.util.GradleVariableCompletionProvider
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer

class DynamicRunnerTab(
    private val project: Project,
    private val taskStatusMap: MutableMap<String, TaskStatus>,
    private val taskRepository: GradleTaskRepository,
    private val favoriteTasksService: FavoriteTasksService,
    private val dynamicTaskService: DynamicTaskService,
    private val onFavoritesChanged: () -> Unit,
    private val updateTaskStatus: (String, TaskStatus) -> Unit
) {

    private lateinit var templateTextField: TextFieldWithCompletion
    private lateinit var templateNameField: JTextField
    private val variablesPanel = JPanel()
    private val variablesScrollPane = JBScrollPane(variablesPanel)
    private val previewLabel = JLabel("Result: ")
    private val statusIconLabel = JBLabel()
    private val savedTasksListModel = DefaultListModel<String>()
    private val savedTasksList = JBList(savedTasksListModel)
    private lateinit var updateButton: JButton
    private lateinit var saveNewButton: JButton

    private var readFromFileMap: MutableMap<String, Boolean> = mutableMapOf()
    private var variablesValueMap: MutableMap<String, String> = mutableMapOf()
    private var hasUnsavedChanges = false

    fun getPanel(): JComponent {
        val mainSplitPane =
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createSavedTasksPanel(), createEditorPanel())
        mainSplitPane.dividerLocation = 250
        return mainSplitPane
    }

    private fun createSavedTasksPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("Saved Templates")
        savedTasksList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        savedTasksList.addListSelectionListener {
            if (!it.valueIsAdjusting) loadSelectedDynamicTask()
        }
        panel.add(JBScrollPane(savedTasksList), BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val newButton = JButton(AllIcons.General.Add)
        newButton.toolTipText = "Create New Template"
        newButton.addActionListener {
            if (hasUnsavedChanges) {
                val res = Messages.showOkCancelDialog(
                    project,
                    "Current changes are not saved. Proceed?",
                    "Unsaved Changes",
                    "Yes",
                    "No",
                    Messages.getQuestionIcon()
                )
                if (res != Messages.OK) return@addActionListener
            }
            savedTasksList.clearSelection()
            templateNameField.text = ""
            templateTextField.text = ""
            variablesPanel.removeAll()
            variablesPanel.revalidate()
            variablesPanel.repaint()
            readFromFileMap.clear()
            variablesValueMap.clear()
            hasUnsavedChanges = false
            setEditingMode(false)
            statusIconLabel.icon = null
        }

        val deleteButton = JButton(AllIcons.General.Remove)
        deleteButton.toolTipText = "Delete Selected Template"
        deleteButton.addActionListener { deleteSelectedDynamicTask() }

        buttonPanel.add(newButton)
        buttonPanel.add(deleteButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        refreshSavedTasksList()
        return panel
    }

    private fun createEditorPanel(): JPanel {
        val editorPanel = JPanel(BorderLayout(10, 10))
        editorPanel.border = JBUI.Borders.empty(10)

        templateNameField = JTextField()
        templateNameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            fun update() {
                hasUnsavedChanges = true
            }

            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
        })

        val provider = GradleVariableCompletionProvider(taskRepository)
        templateTextField =
            TextFieldWithCompletion(project, provider, "", true, true, true, true)
        templateTextField.setPlaceholder("""For groovy Use '$' to insert available Gradle ext variables (e.g.for Groovy ${'$'}versionName). for KTS , DSL extra["ENV"]""")
        SwingUtilities.invokeLater {
            val editor: Editor = templateTextField.editor ?: return@invokeLater



            editor.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        if (editor.selectionModel.hasSelection()) {
                            val selectionEnd = editor.selectionModel.selectionEnd
                            editor.selectionModel.removeSelection()
                            editor.caretModel.moveToOffset(selectionEnd)
                        }
                    }

                    ApplicationManager.getApplication().invokeLater {
                        updateDynamicUIAndPreview()
                        hasUnsavedChanges = true
                    }
                }
            })


            editor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyChar == '$') {
                        val caretOffset = editor.caretModel.offset
                        editor.caretModel.moveToOffset(caretOffset)
                        CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor)
                    }
                }
            })
        }


        val topPanel = JPanel(GridLayout(4, 1, 5, 5))
        topPanel.add(JLabel("Template Name:"))
        topPanel.add(templateNameField)
        topPanel.add(JLabel("Task Template:"))
        topPanel.add(templateTextField)

        variablesPanel.layout = GridBagLayout()
        variablesPanel.border = BorderFactory.createTitledBorder("Variables")

        val runButton = JButton("Run")
        updateButton = JButton("Update")
        saveNewButton = JButton("Save as New")
        val addToFavoritesButton = JButton("Add to Favorites")

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(addToFavoritesButton)
        buttonPanel.add(updateButton)
        buttonPanel.add(saveNewButton)
        buttonPanel.add(runButton)

        val bottomPanel = JPanel(BorderLayout(10, 0))
        val previewPanel = JPanel(BorderLayout(5, 0))
        previewPanel.add(statusIconLabel, BorderLayout.WEST)
        previewPanel.add(previewLabel, BorderLayout.CENTER)
        bottomPanel.add(previewPanel, BorderLayout.CENTER)
        bottomPanel.add(buttonPanel, BorderLayout.EAST)

        runButton.addActionListener { runTask(getCurrentTaskString()) }
        updateButton.addActionListener { updateSelectedDynamicTask() }
        saveNewButton.addActionListener { saveNewDynamicTask() }
        addToFavoritesButton.addActionListener { addPreviewToFavorites() }

        editorPanel.add(topPanel, BorderLayout.NORTH)
        editorPanel.add(variablesScrollPane, BorderLayout.CENTER)
        editorPanel.add(bottomPanel, BorderLayout.SOUTH)

        setEditingMode(false)
        updateDynamicUIAndPreview()
        return editorPanel
    }

    private fun setEditingMode(isEditing: Boolean) {
        updateButton.isVisible = isEditing
        saveNewButton.isVisible = !isEditing
    }

    fun getCurrentTaskString(): String {
        var templateResult = templateTextField.text
        val latestProps = taskRepository.getExtPropertiesFromPsi()
        val knownVariableNames = latestProps.keys
        val variableNamesInTemplate = findVariablesInTemplate(templateResult, knownVariableNames)

        variableNamesInTemplate.forEach { varName ->
            val textField = findTextFieldForVariable(varName)
            val value = if (readFromFileMap[varName] == true) {
                latestProps.getOrDefault(varName, "")
            } else {
                textField?.text ?: ""
            }
            templateResult = templateResult.replace("\$$varName", value)
        }
        return templateResult
    }

    private fun updateDynamicUIAndPreview() {
        val template = templateTextField.text
        val latestProps = taskRepository.getExtPropertiesFromPsi()
        val knownVariableNames = latestProps.keys
        val variableNames = findVariablesInTemplate(template, knownVariableNames)
        variablesPanel.removeAll()

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
        }

        variableNames.forEachIndexed { index, varName ->
            gbc.gridy = index

            val label = JLabel(varName)
            val textField = JTextField()
            val checkBox = JBCheckBox("Read From File")

            val shouldReadFromFile = readFromFileMap[varName] ?: false
            checkBox.isSelected = shouldReadFromFile

            val value = if (shouldReadFromFile)
                latestProps.getOrDefault(varName, "")
            else
                variablesValueMap[varName] ?: latestProps.getOrDefault(varName, "")

            textField.text = value
            textField.isEnabled = !shouldReadFromFile

            readFromFileMap[varName] = shouldReadFromFile
            variablesValueMap[varName] = value

            val rowPanel = JPanel(BorderLayout(5, 0))
            rowPanel.add(label, BorderLayout.WEST)
            rowPanel.add(textField, BorderLayout.CENTER)
            rowPanel.add(checkBox, BorderLayout.EAST)

            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                fun update() {
                    variablesValueMap[varName] = textField.text
                    hasUnsavedChanges = true
                    updatePreview()
                }

                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
            })

            checkBox.addActionListener {
                val isChecked = checkBox.isSelected
                readFromFileMap[varName] = isChecked
                textField.isEnabled = !isChecked
                if (isChecked) {
                    textField.text = latestProps.getOrDefault(varName, "")
                    variablesValueMap[varName] = textField.text
                }
                hasUnsavedChanges = true
                updatePreview()
            }

            variablesPanel.add(rowPanel, gbc)
        }

        gbc.gridy = variableNames.size
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.VERTICAL
        variablesPanel.add(JPanel(), gbc)

        variablesPanel.revalidate()
        variablesPanel.repaint()
        updatePreview()
    }

    private fun updatePreview() {
        previewLabel.text = "Result: ${getCurrentTaskString()}"
    }

    private fun findVariablesInTemplate(template: String, knownVariables: Set<String>): Set<String> {
        val foundVariables = mutableSetOf<String>()
        val regex = "\\\$([a-zA-Z0-9_]+)".toRegex()

        regex.findAll(template).forEach { matchResult ->
            val potentialVar = matchResult.groupValues[1]

            val prefixVar = knownVariables
                .filter { potentialVar.startsWith(it) }
                .maxByOrNull { it.length }

            if (prefixVar != null) {
                foundVariables.add(prefixVar)
            }
        }
        return foundVariables
    }

    private fun findTextFieldForVariable(varName: String): JTextField? {
        for (comp in variablesPanel.components) {
            val row = comp as? JPanel ?: continue
            var foundLabel: JLabel? = null
            var foundText: JTextField? = null
            for (child in row.components) {
                if (child is JLabel && child.text == varName) {
                    foundLabel = child
                }
                if (child is JTextField) {
                    foundText = child
                }
            }
            if (foundLabel != null) return foundText
        }
        return null
    }

    fun updatePreviewStatus(taskName: String, status: TaskStatus) {
        val currentPreview = getCurrentTaskString()
        if (taskName == currentPreview) {
            statusIconLabel.icon = when (status) {
                TaskStatus.RUNNING -> AnimatedIcon.Default()
                TaskStatus.SUCCESS -> AllIcons.Status.Success
                TaskStatus.FAILED -> AllIcons.General.Error
                TaskStatus.IDLE -> null
            }
        } else {
            if (status != TaskStatus.RUNNING) {
                statusIconLabel.icon = null
            }
        }
    }

    private fun runTask(taskName: String) {
        taskRepository.logDebug("UI: Running task '$taskName'...")
        updateTaskStatus(taskName, TaskStatus.RUNNING)

        if (taskName == getCurrentTaskString()) {
            statusIconLabel.icon = AnimatedIcon.Default()
        }

        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.basePath
            taskNames = listOf(taskName)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        ExternalSystemUtil.runTask(
            settings,
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            GradleConstants.SYSTEM_ID,
            null,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            false
        )
    }


    private fun refreshSavedTasksList() {
        val selected = savedTasksList.selectedValue
        savedTasksListModel.clear()
        dynamicTaskService.getTasks().sortedBy { it.name }
            .forEach { savedTasksListModel.addElement(it.name) }
        if (selected != null) savedTasksList.setSelectedValue(selected, true)
    }

    private fun loadSelectedDynamicTask() {
        val selectedName = savedTasksList.selectedValue ?: return
        val task = dynamicTaskService.getTasks().find { it.name == selectedName } ?: return

        templateNameField.text = task.name
        templateTextField.text = task.template
        readFromFileMap = task.readFromFileOnRun.toMutableMap()
        variablesValueMap = task.variables.toMutableMap()

        SwingUtilities.invokeLater { updateDynamicUIAndPreview() }
        setEditingMode(true)
        hasUnsavedChanges = false
        statusIconLabel.icon = null
    }

    private fun deleteSelectedDynamicTask() {
        val selectedName = savedTasksList.selectedValue ?: return
        val confirmation = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to delete '$selectedName'?",
            "Confirm Deletion",
            "Delete",
            "Cancel",
            Messages.getQuestionIcon()
        )
        if (confirmation == Messages.OK) {
            dynamicTaskService.removeTask(selectedName)
            refreshSavedTasksList()
            templateTextField.text = ""
            templateNameField.text = ""
            variablesPanel.removeAll()
            variablesPanel.revalidate()
            variablesPanel.repaint()
            readFromFileMap.clear()
            variablesValueMap.clear()
            setEditingMode(false)
            hasUnsavedChanges = false
            statusIconLabel.icon = null
        }
    }

    private fun saveNewDynamicTask() {
        val name = templateNameField.text.ifBlank {
            Messages.showInputDialog(
                project,
                "Enter template name:",
                "Save Template",
                Messages.getQuestionIcon()
            ) ?: return
        }
        if (dynamicTaskService.getTasks().any { it.name.equals(name, ignoreCase = true) }) {
            Messages.showErrorDialog(
                project,
                "A template with this name already exists.",
                "Error"
            )
            return
        }
        saveOrUpdateTask(name)
    }

    private fun updateSelectedDynamicTask() {
        val selectedName = savedTasksList.selectedValue ?: return
        val newName = templateNameField.text.trim()
        if (newName.isBlank()) {
            Messages.showErrorDialog(project, "Template name cannot be empty.", "Error")
            return
        }

        if (!newName.equals(selectedName, ignoreCase = true)) {
            if (dynamicTaskService.getTasks().any { it.name.equals(newName, ignoreCase = true) }) {
                Messages.showErrorDialog(project, "A template with this name already exists.", "Error")
                return
            }
            dynamicTaskService.removeTask(selectedName)
        }

        saveOrUpdateTask(newName)
    }

    private fun saveOrUpdateTask(name: String) {
        val template = templateTextField.text
        val taskToSave =
            DynamicTask(name, template, variablesValueMap.toMutableMap(), readFromFileMap.toMutableMap())
        dynamicTaskService.addTask(taskToSave)
        refreshSavedTasksList()
        savedTasksList.setSelectedValue(name, true)
        hasUnsavedChanges = false
        setEditingMode(true)

        statusIconLabel.icon = AllIcons.Actions.Commit
        val timer = Timer(2000) { statusIconLabel.icon = null }
        timer.isRepeats = false
        timer.start()
    }

    private fun addPreviewToFavorites() {
        val taskToAdd = getCurrentTaskString()
        if (taskToAdd.isNotEmpty() && !favoriteTasksService.isFavorite(taskToAdd)) {
            favoriteTasksService.addFavorite(taskToAdd)
            onFavoritesChanged()
            Messages.showInfoMessage("'$taskToAdd' added to favorites.", "Success")
        } else if (taskToAdd.isNotEmpty()) {
            Messages.showInfoMessage("'$taskToAdd' already in favorites.", "Info")
        }
    }
}