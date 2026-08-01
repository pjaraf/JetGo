package com.jetgo.tv.data.remote

import com.google.gson.annotations.SerializedName

// ---- Respuestas crudas del endpoint player_api.php de Xtream Codes ----

data class CategoryDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

data class LiveStreamDto(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("epg_channel_id") val epgChannelId: String?
)

data class VodStreamDto(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("rating") val rating: String?
)

data class SeriesDto(
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("plot") val plot: String?,
    @SerializedName("rating") val rating: String?
)

data class UserInfoResponse(
    @SerializedName("user_info") val userInfo: UserInfoDto?
)

data class UserInfoDto(
    @SerializedName("auth") val auth: Int,
    @SerializedName("status") val status: String?,
    @SerializedName("exp_date") val expDate: String?
)

// ---- get_series_info: episodios agrupados por temporada ----

data class EpisodeDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("episode_num") val episodeNum: Int?,
    @SerializedName("season") val season: Int?
)

data class SeriesInfoResponse(
    @SerializedName("info") val info: SeriesInfoDetailsDto?,
    @SerializedName("episodes") val episodes: Map<String, List<EpisodeDto>>?
)

data class SeriesInfoDetailsDto(
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?
)
