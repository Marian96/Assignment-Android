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

    private val _pokemons: MutableLiveData<Result<List<Pokemon>>?> = state.getLiveData(SAVED_STATE_POKEMONS, null)
    val pokemons: LiveData<Result<List<Pokemon>>?> = _pokemons
    private val _showRefreshError = MutableLiveData<Unit>()
    val showRefreshError: LiveData<Unit> = _showRefreshError

    init {
        if (pokemons.value?.getOrNull() == null) {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            state[SAVED_STATE_POKEMONS] = getPokemonsWithDetails(page = 1)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            api.getPokemons(page = 1).fold(
                onSuccess = {
                    state[SAVED_STATE_POKEMONS] = Result.success(it)
                },
                onFailure = {
                    if (!pokemons.value?.getOrDefault(emptyList()).isNullOrEmpty()) {
                        _showRefreshError.postValue(Unit)
                    } else {
                        state[SAVED_STATE_POKEMONS] = Result.failure<List<Pokemon>>(it)
                    }
                }
            )
        }
    }

    private suspend fun getPokemonsWithDetails(page: Int): Result<List<Pokemon>> {
        val result = api.getPokemons(page = page)
        result.fold(
            onSuccess = { pokemons ->
                val updatedPokemons = pokemons.map { pokemon ->
                    viewModelScope.async { pokemon to api.getPokemonDetail(pokemon) }
                }.awaitAll().map {
                    val (pokemon, pokemonDetail) = it
                   pokemon.copy(detail = pokemonDetail.getOrNull())
                }

                return Result.success(updatedPokemons)
            },
            onFailure = {
                return Result.failure(it)
            }
        )
    }

    companion object {
        private const val SAVED_STATE_POKEMONS = "saved_state_pokemons"
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