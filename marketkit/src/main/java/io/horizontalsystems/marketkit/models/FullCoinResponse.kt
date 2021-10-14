package io.horizontalsystems.marketkit.models

import com.google.gson.annotations.SerializedName

data class FullCoinResponse(
    override val uid: String,
    override val name: String,
    override val code: String,
    @SerializedName("market_cap_rank")
    override val marketCapRank: Int?,
    @SerializedName("coingecko_id")
    override val coinGeckoId: String,

    val platforms: List<PlatformResponse>,
) : CoinResponse {

    fun fullCoin() = FullCoin(coin(), platforms.mapNotNull { it.platform(uid) })
}