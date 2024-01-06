package com.goodrequest.hiring

import com.goodrequest.hiring.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import ru.gildor.coroutines.okhttp.await

object PokemonApi {
    suspend fun getPokemons(page: Int): Result<List<Pokemon>> = withContext(Dispatchers.IO) {
        client.httpGet(
            url   = "https://pokeapi.co/api/v2/pokemon/?limit=20&offset=${(page - 1) * 20}".toHttpUrl(),
            parse = ::parsePokemons)
    }

    suspend fun getPokemonDetail(pokemon: Pokemon): Result<PokemonDetail> = withContext(Dispatchers.IO) {
        client.httpGet(
            url = pokemon.id.toHttpUrl(),
            parse = ::parsePokemonDetail
        )
    }
}


private val client =
    OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

private suspend fun <T> OkHttpClient.httpGet(url: HttpUrl, parse: (String) -> T): Result<T> =
    try {
        val good: Request = Request.Builder().url(url).get().build()
        val result = newCall(good).await()
        val parsed = parse(result.body!!.string())
        Result.success(parsed)
    } catch (e: Exception) {
        Result.failure(e)
    }

private fun parsePokemons(json: String): List<Pokemon> =
    JSONObject(json).getJSONArray("results").map {
        Pokemon(
            id   = it.getString("url"),
            name = it.getString("name"))
    }

private fun parsePokemonDetail(json: String): PokemonDetail =
    JSONObject(json).let {
        PokemonDetail(
            move   = it.getJSONArray("moves").getJSONObject(0).getJSONObject("move").getString("name"),
            image  = it.getJSONObject("sprites").getString("front_default"),
            weight = it.getInt("weight"))
    }

private fun <T> JSONArray.map(action: (JSONObject) -> T): List<T> =
    (0 until length()).map { action(getJSONObject(it)) }