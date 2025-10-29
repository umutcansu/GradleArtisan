package io.github.umutcansu.gradleartisan.services

import com.intellij.openapi.components.*

data class FavoritesState(
    var favoriteTasks: MutableSet<String> = mutableSetOf()
)

@Service(Service.Level.PROJECT)
@State(
    name = "GradleArtisanFavoritesState",
    storages = [Storage("gradleArtisan.xml")]
)
class FavoriteTasksService : PersistentStateComponent<FavoritesState> {

    private var internalState = FavoritesState()

    override fun getState(): FavoritesState = internalState

    override fun loadState(state: FavoritesState) {
        internalState = state
    }

    fun addFavorite(taskName: String) {
        internalState.favoriteTasks.add(taskName)
    }

    fun removeFavorite(taskName: String) {
        internalState.favoriteTasks.remove(taskName)
    }

    fun isFavorite(taskName: String): Boolean {
        return internalState.favoriteTasks.contains(taskName)
    }

    fun getFavorites(): Set<String> {
        return internalState.favoriteTasks
    }
}