package com.goodrequest.hiring.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.goodrequest.hiring.R
import com.goodrequest.hiring.databinding.ItemDataBinding
import com.goodrequest.hiring.databinding.ItemErrorBinding
import com.goodrequest.hiring.databinding.ItemLoadingBinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.Serializable

class PokemonAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val ITEM_DATA = 0
        private const val ITEM_LOADING = 1
        private const val ITEM_ERROR = 2
    }

    private val items = ArrayList<PokemonItemType>()

    private val _onRetryButtonClick = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val onRetryButtonClick: Flow<Unit> = _onRetryButtonClick

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ITEM_DATA -> DataItem(ItemDataBinding.inflate(inflater, parent, false))
            ITEM_LOADING -> LoadingItem(ItemLoadingBinding.inflate(inflater, parent, false))
            ITEM_ERROR -> ErrorItem(ItemErrorBinding.inflate(inflater, parent, false))
            else -> throw IllegalStateException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DataItem -> holder.show(items[position] as PokemonItemType.Data)
            else -> Unit // do nothing
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PokemonItemType.Data -> ITEM_DATA
            is PokemonItemType.Loading -> ITEM_LOADING
            is PokemonItemType.Error -> ITEM_ERROR
        }
    }

    override fun getItemCount(): Int =
        items.size

    fun show(pokemons: List<PokemonItemType>) {
        items.clear()
        items.addAll(pokemons)
        notifyDataSetChanged()
    }

    inner class DataItem(private val binding: ItemDataBinding): RecyclerView.ViewHolder(binding.root) {
        fun show(pokemonData: PokemonItemType.Data) {
            val pokemon = pokemonData.pokemon
            binding.name.text = pokemon.name
            pokemon.detail?.let { detail ->
                binding.image.load(detail.image) {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)
                }
                binding.move.text = detail.move
                binding.weight.text = detail.weight.toString()
            } ?: binding.image.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    inner class ErrorItem(binding: ItemErrorBinding): RecyclerView.ViewHolder(binding.root) {
        init {
            binding.retry.setOnClickListener {
                _onRetryButtonClick.tryEmit(Unit)
            }
        }
    }

    inner class LoadingItem(binding: ItemLoadingBinding): RecyclerView.ViewHolder(binding.root)
}

sealed interface PokemonItemType: Serializable {
    data class Data(val pokemon: Pokemon): PokemonItemType
    data object Loading: PokemonItemType
    data object Error: PokemonItemType
}