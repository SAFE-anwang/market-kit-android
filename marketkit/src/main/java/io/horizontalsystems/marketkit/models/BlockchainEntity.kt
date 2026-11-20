package io.horizontalsystems.marketkit.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = arrayOf("uid"))
    ]
)
data class BlockchainEntity(
    @PrimaryKey
    val uid: String,
    val name: String,
    val eip3091url: String?
) {

    val blockchain: Blockchain
        get() {
            val type = BlockchainType.fromUid(uid)
            // Always surface Safe4 chain as "SAFE" in UI, even if older DB rows still say "Safe4".
            val displayName = if (type == BlockchainType.SafeFour) "SAFE" else name
            return Blockchain(type, displayName, eip3091url)
        }

}
