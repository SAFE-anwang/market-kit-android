package io.horizontalsystems.marketkit

import io.horizontalsystems.marketkit.models.Coin

object SafeExtend {

    fun Coin.isSafeCoin(): Boolean{
        return this.uid == "safe-coin" || this.uid == "safe4-coin" || this.uid == "custom-safe4-coin"
    }

    fun String?.isSafeCoin(): Boolean {
        return this == "safe-coin" || this == "safe4-coin" || this == "custom-safe4-coin"
    }

    fun String?.isSafeFourCoin(): Boolean {
        return this == "safe4-coin"
    }

    fun String?.isSafeIcon(): Boolean {
        return this?.endsWith("safe-coin@3x.png") ==true
                || this?.endsWith("safe4-coin@3x.png") ==true
                || this?.contains("custom-safe4-coin") ==true
    }

    fun Coin.isSafeFour(): Boolean {
        return this.uid == "safe4-coin" || this.uid == "custom-safe4-coin"
    }
}