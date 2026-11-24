package dev.ququla.deathreminder

import org.bukkit.Location
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// 死亡地点の「ワールド名」と「整数座標 (x, y, z)」をまとめて持つためのデータクラス。
// - internal: 同じモジュール（このプラグインとテスト）内からだけ参照できる。
// - data class: データを保持するための専用クラス（Python の @dataclass とほぼ同じ）。
internal data class DeathPosition(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    // 基準地点（reference）が同じワールドにあれば、距離と方角もあわせて文字列にする。
    // デフォルト引数「= null」があるので、引数を省略して toMessage() だけでも呼べる。
    fun toMessage(reference: Location? = null): String {
        val base = "world=$worldName x=$x y=$y z=$z"
        // 基準地点が未指定、または別ワールドにいるときは座標だけを返す。
        if (reference == null || reference.world?.name != worldName) {
            return base
        }

        val dx = (x - reference.blockX).toDouble()
        val dy = (y - reference.blockY).toDouble()
        val dz = (z - reference.blockZ).toDouble()
        val distance = sqrt(dx * dx + dy * dy + dz * dz).roundToInt()

        // 水平方向（X/Z）にズレがないときは方角を省略し、距離だけを表示する。
        return if (dx == 0.0 && dz == 0.0) {
            "$base (${distance}m)"
        } else {
            "$base (${distance}m ${compassDirection(dx, dz)})"
        }
    }

    // companion object は「インスタンスを作らずに呼べるメソッド・定数」を置く場所（Python の @classmethod 等に近い）。
    companion object {
        private val DIRECTIONS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        // Minecraft の Location（小数点を含む座標）から、ブロック単位の整数座標だけを抜き出して DeathPosition を作る。
        fun from(location: Location): DeathPosition =
            DeathPosition(
                // 「?.」はセーフコール、「?:」はエルビス演算子。world が null なら "unknown" を使う。
                worldName = location.world?.name ?: "unknown",
                x = location.blockX,
                y = location.blockY,
                z = location.blockZ,
            )

        // Minecraft の座標系（北 = -Z、東 = +X、南 = +Z、西 = -X）に合わせて 8方位を返す。
        internal fun compassDirection(
            dx: Double,
            dz: Double,
        ): String {
            val angle = Math.toDegrees(atan2(dx, -dz))
            val normalized = (angle + 360.0) % 360.0
            val index = ((normalized + 22.5) / 45.0).toInt() % DIRECTIONS.size
            return DIRECTIONS[index]
        }
    }
}
