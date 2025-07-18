package io.horizontalsystems.marketkit

import io.horizontalsystems.marketkit.SafeExtend.isSafeCoin
import io.horizontalsystems.marketkit.models.Coin

object SafeExtend {

    const val SAFE_ERC_COIN_UID = "ethereum|eip20:0xee9c1ea4dcf0aaf4ff2d78b6ff83aa69797b65eb"
    const val SAFE_MATIC_COIN_UID = "polygon-pos|eip20:0xb7dd19490951339fe65e341df6ec5f7f93ff2779"
    const val SAFE_BEP20_COIN_UID = "binance-smart-chain|eip20:0x4d7fa587ec8e50bd0e9cd837cb4da796f47218a1"

    const val SAFE4_ERC_COIN_UID = "ethereum|eip20:0x96f59c9d155d598d4f895f07dd6991ccb5fa7dc7"
    const val SAFE4_MATIC_COIN_UID = "polygon-pos|eip20:0xe0d3ff9b473976855b2242a1a022ac66f980ce50"
    const val SAFE4_BEP20_COIN_UID = "binance-smart-chain|eip20:0x3a5557ad6fa16699dd56fd0e418c70c83e42240a"

    fun Coin.isSafeCoin(): Boolean{
        return this.uid == "safe-coin" || this.uid == "safe4-coin" || this.uid == "custom-safe4-coin"
                || this.uid == SAFE4_ERC_COIN_UID
                || this.uid == SAFE4_MATIC_COIN_UID
                || this.uid == SAFE4_BEP20_COIN_UID
    }

    fun String?.isSafeCoin(): Boolean {
        return this == "safe-coin" || this == "safe4-coin" || this?.startsWith("custom-safe4-coin") == true
                || this == SAFE4_ERC_COIN_UID
                || this == SAFE4_MATIC_COIN_UID
                || this == SAFE4_BEP20_COIN_UID
    }

    fun String?.isSafeFourCoin(): Boolean {
        return this == "safe4-coin"
    }

    fun String.isSafeFourCustomCoin(): Boolean {
        return this.startsWith("custom-safe4-coin")
    }

    fun String?.isSafeIcon(): Boolean {
        return this?.endsWith("safe-coin@3x.png") ==true
                || this?.endsWith("safe4-coin@3x.png") ==true
                || this?.contains("custom-safe4-coin") ==true
                || this?.contains("0x96f59c9d155d598d4f895f07dd6991ccb5fa7dc7", true) ==true
                || this?.contains("0x3a5557ad6fa16699dd56fd0e418c70c83e42240a", true) ==true
                || this?.contains("0xe0d3ff9b473976855b2242a1a022ac66f980ce50", true) ==true
    }

    fun Coin.isSafeFour(): Boolean {
        return this.uid == "safe4-coin" || this.uid == "custom-safe4-coin"
    }
}