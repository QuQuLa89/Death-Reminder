package dev.ququla.deathreminder

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DeathReminderPlugin :
    JavaPlugin(),
    Listener {
    private val deathLocations = ConcurrentHashMap<UUID, DeathPosition>()

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        deathLocations.clear()
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        deathLocations[player.uniqueId] = DeathPosition.from(player.location)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val deathPosition = deathLocations.remove(player.uniqueId) ?: return

        player.scheduler.run(
            this,
            { _ ->
                if (!player.isOnline) return@run

                server.broadcast(Component.text("* ${player.name} died at ${deathPosition.toMessage()}"))
            },
            null,
        )
    }
}

private data class DeathPosition(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun toMessage(): String = "world=$worldName x=$x y=$y z=$z"

    companion object {
        fun from(location: Location): DeathPosition =
            DeathPosition(
                worldName = location.world?.name ?: "unknown",
                x = location.blockX,
                y = location.blockY,
                z = location.blockZ,
            )
    }
}
