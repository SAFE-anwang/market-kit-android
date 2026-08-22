package io.horizontalsystems.marketkit.models

import android.os.Parcelable
import io.horizontalsystems.marketkit.SafeExtend.isSafe3Coin
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.Objects

@Parcelize
@Serializable
data class Blockchain(
    val type: BlockchainType,
    val name: String,
    val eip3091url: String?
) : Parcelable {

    val uid: String
        get() = type.uid

    override fun equals(other: Any?): Boolean =
        other is Blockchain && other.type == type

    override fun hashCode(): Int =
        Objects.hash(type, name)

    @IgnoredOnParcel
    val coinName = name + if (type.uid.isSafe3Coin()) "3" else ""
}
