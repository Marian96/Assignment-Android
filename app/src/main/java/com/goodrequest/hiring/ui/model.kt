package com.goodrequest.hiring.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodrequest.hiring.PokemonApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.Serializable

class PokemonViewModel(
    private val state: SavedStateHandle,
    private val api: PokemonApi
) : ViewModel() {

    private val _pokemons: MutableLiveData<Result<List<PokemonItemType>>?> = state.getLiveData(SAVED_STATE_POKEMONS, null)
    val pokemons: LiveData<Result<List<PokemonItemType>>?> = _pokemons
    private val _showRefreshError = MutableLiveData<Unit>()
    val showRefreshError: LiveData<Unit> = _showRefreshError
    private var pageToLoad = state.get<Int>(SAVED_STATE_LOADED_PAGE) ?: 1
    private var isLoadingNextPage = false
    private var hasError = false

    init {
        if (getLoadedPokemons() == null) {
            load(true)
        }
    }

    fun load(loadNextPage: Boolean) {
        viewModelScope.launch {
            if (loadNextPage && (hasError || isLoadingNextPage)) {
                return@launch
            }
            if (pageToLoad > 1) {
                addLoadingItem()
            }
            getPokemonsWithDetails(pageToLoad).fold(
                onSuccess = { fetchedPokemons ->
                    state[SAVED_STATE_POKEMONS] = Result.success(getLoadedPokemons()?.plus(fetchedPokemons) ?: fetchedPokemons)
                    state[SAVED_STATE_LOADED_PAGE] = ++pageToLoad
                    hasError = false
                },
                onFailure = {
                    val loadedPokemons = getLoadedPokemons()
                    if (!loadedPokemons.isNullOrEmpty()) {
                        addErrorItem(loadedPokemons)
                        hasError = true
                    } else {
                        state[SAVED_STATE_POKEMONS] = Result.failure<List<PokemonItemType>>(it)
                    }
                }
            )
            isLoadingNextPage = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            getPokemonsWithDetails(pageToLoad).fold(
                onSuccess = {
                    state[SAVED_STATE_POKEMONS] = Result.success(it)
                },
                onFailure = {
                    if (!getLoadedPokemons().isNullOrEmpty()) {
                        _showRefreshError.postValue(Unit)
                    } else {
                        state[SAVED_STATE_POKEMONS] = Result.failure<List<PokemonItemType>>(it)
                    }
                }
            )
        }
    }

    private suspend fun getPokemonsWithDetails(page: Int): Result<List<PokemonItemType>> {
        val result = api.getPokemons(page = page)
        result.fold(
            onSuccess = { pokemons ->
                val updatedPokemons = pokemons.map { pokemon ->
                    viewModelScope.async { pokemon to api.getPokemonDetail(pokemon) }
                }.awaitAll().map {
                    val (pokemon, pokemonDetail) = it
                    PokemonItemType.Data(pokemon = pokemon.copy(detail = pokemonDetail.getOrNull()))
                }

                return Result.success(updatedPokemons)
            },
            onFailure = {
                return Result.failure(it)
            }
        )
    }

    private fun getLoadedPokemons(): List<PokemonItemType.Data>? = pokemons.value?.getOrDefault(emptyList())?.filterIsInstance<PokemonItemType.Data>()

    private fun addErrorItem(loadedPokemons: List<PokemonItemType>) {
        _pokemons.postValue(Result.success(loadedPokemons.plus(PokemonItemType.Error)))
    }

    private fun addLoadingItem() {
        val loadedPokemons = getLoadedPokemons()
        if (!loadedPokemons.isNullOrEmpty() && !isLoadingNextPage) {
            _pokemons.postValue(Result.success(loadedPokemons.plus(PokemonItemType.Loading)))
            isLoadingNextPage = true
        }
    }

    companion object {
        private const val SAVED_STATE_POKEMONS = "saved_state_pokemons"
        private const val SAVED_STATE_LOADED_PAGE = "saved_state_loaded_page"
    }
}

data class Pokemon(
    val id: String,
    val name: String,
    val detail: PokemonDetail? = null
): Serializable

data class PokemonDetail(
    val image: String,
    val move: String,
    val weight: Int
): Serializable