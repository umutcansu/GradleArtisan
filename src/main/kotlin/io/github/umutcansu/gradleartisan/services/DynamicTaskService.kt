package io.github.umutcansu.gradleartisan.services

import com.intellij.openapi.components.*

data class DynamicTask(
    var name: String = "",
    var template: String = "",
    var variables: MutableMap<String, String> = mutableMapOf(),
    var readFromFileOnRun: MutableMap<String, Boolean> = mutableMapOf()
)

data class DynamicTaskState(
    var tasks: MutableList<DynamicTask> = mutableListOf()
)

@Service(Service.Level.PROJECT)
@State(
    name = "GradleArtisanDynamicTasks",
    storages = [Storage("gradleArtisan_dynamicTasks.xml")]
)
class DynamicTaskService : PersistentStateComponent<DynamicTaskState> {

    private var internalState = DynamicTaskState()

    override fun getState(): DynamicTaskState = internalState

    override fun loadState(state: DynamicTaskState) {
        internalState = state
    }

    fun getTasks(): List<DynamicTask> = internalState.tasks.toList()

    fun addTask(task: DynamicTask) {
        val existing = internalState.tasks.find { it.name == task.name }
        if (existing != null) {
            existing.template = task.template
            existing.variables = task.variables
            existing.readFromFileOnRun = task.readFromFileOnRun
        } else {
            internalState.tasks.add(task)
        }
    }

    fun removeTask(taskName: String) {
        internalState.tasks.removeIf { it.name == taskName }
    }
}