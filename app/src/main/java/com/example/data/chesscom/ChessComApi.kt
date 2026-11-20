package com.example.data.chesscom

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChessComProfileDto(
    @Json(name = "username") val username: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "followers") val followers: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class ChessComArchivesDto(
    @Json(name = "archives") val archives: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChessComPlayerSideDto(
    @Json(name = "username") val username: String = "",
    @Json(name = "rating") val rating: Int? = null,
    @Json(name = "result") val result: String = ""
)

@JsonClass(generateAdapter = true)
data class ChessComAccuraciesDto(
    @Json(name = "white") val white: Double? = null,
    @Json(name = "black") val black: Double? = null
)

@JsonClass(generateAdapter = true)
data class ChessComGameDto(
    @Json(name = "url") val url: String = "",
    @Json(name = "pgn") val pgn: String? = null,
    @Json(name = "time_control") val timeControl: String? = null,
    @Json(name = "end_time") val endTime: Long? = null,
    @Json(name = "rated") val rated: Boolean? = null,
    @Json(name = "time_class") val timeClass: String? = null,
    @Json(name = "rules") val rules: String? = null,
    @Json(name = "white") val white: ChessComPlayerSideDto? = null,
    @Json(name = "black") val black: ChessComPlayerSideDto? = null,
    @Json(name = "accuracies") val accuracies: ChessComAccuraciesDto? = null
)

@JsonClass(generateAdapter = true)
data class ChessComMonthlyGamesDto(
    @Json(name = "games") val games: List<ChessComGameDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChessComRatingRecordDto(
    @Json(name = "rating") val rating: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChessComStatBucketDto(
    @Json(name = "last") val last: ChessComRatingRecordDto? = null
)

@JsonClass(generateAdapter = true)
data class ChessComStatsDto(
    @Json(name = "chess_bullet") val bullet: ChessComStatBucketDto? = null,
    @Json(name = "chess_blitz") val blitz: ChessComStatBucketDto? = null,
    @Json(name = "chess_rapid") val rapid: ChessComStatBucketDto? = null,
    @Json(name = "chess_daily") val daily: ChessComStatBucketDto? = null
)

interface ChessComApi {

    @GET("pub/player/{username}")
    suspend fun getPlayerProfile(@Path("username") username: String): Response<ChessComProfileDto>

    @GET("pub/player/{username}/stats")
    suspend fun getPlayerStats(@Path("username") username: String): Response<ChessComStatsDto>

    @GET("pub/player/{username}/games/archives")
    suspend fun getGameArchives(@Path("username") username: String): Response<ChessComArchivesDto>

    @GET
    suspend fun getGamesFromArchiveUrl(@Url archiveUrl: String): Response<ChessComMonthlyGamesDto>

    companion object {
        private const val BASE_URL = "https://api.chess.com/"

        fun create(): ChessComApi {
            val userAgentInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ChessAnalystAndroid/1.0 (https://github.com/chess-analyst-android)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(ChessComApi::class.java)
        }
    }
}
