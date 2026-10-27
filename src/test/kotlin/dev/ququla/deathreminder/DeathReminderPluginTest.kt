package dev.ququla.deathreminder

import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

class DeathReminderPluginTest {
    private class Fixture {
        val plugin = mock(DeathReminderPlugin::class.java)
        val server = mock(Server::class.java)
        val player = mock(Player::class.java)
        val scheduler = mock(EntityScheduler::class.java)
        val death = mock(PlayerDeathEvent::class.java)
        val respawn = mock(PlayerRespawnEvent::class.java)
        val callbacks = mutableListOf<Consumer<ScheduledTask>>()

        init {
            DeathReminderPlugin::class.java
                .getDeclaredField("deathLocations")
                .apply {
                    isAccessible = true
                }.set(plugin, ConcurrentHashMap<UUID, Any>())
            `when`(plugin.server).thenReturn(server)
            `when`(player.uniqueId).thenReturn(UUID.randomUUID())
            `when`(player.name).thenReturn("QuQuLa89")
            `when`(player.scheduler).thenReturn(scheduler)
            `when`(player.isOnline).thenReturn(true)
            val world = mock(World::class.java)
            `when`(world.name).thenReturn("world_nether")
            `when`(player.location).thenReturn(Location(world, -1.2, 63.8, 20.9))
            `when`(death.player).thenReturn(player)
            `when`(respawn.player).thenReturn(player)
            doCallRealMethod().`when`(plugin).onPlayerDeath(death)
            doCallRealMethod().`when`(plugin).onPlayerRespawn(respawn)
            doAnswer {
                callbacks += it.getArgument<Consumer<ScheduledTask>>(1)
                mock(ScheduledTask::class.java)
            }.`when`(scheduler).run(eq(plugin), any(), isNull())
        }
    }

    @Test
    fun `nonop death coordinates broadcast only after the entity scheduler runs and only once`() {
        val f = Fixture()
        f.plugin.onPlayerDeath(f.death)
        f.plugin.onPlayerRespawn(f.respawn)
        verifyNoInteractions(f.server)
        assertEquals(1, f.callbacks.size)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))
        verify(f.server).broadcast(Component.text("* QuQuLa89 died at world=world_nether x=-2 y=63 z=20"))
        verify(f.player, never()).performCommand(any(String::class.java))
        verify(f.player, never()).hasPermission("minecraft.command.me")
        f.plugin.onPlayerRespawn(f.respawn)
        assertEquals(1, f.callbacks.size)
    }

    @Test
    fun `respawning without a recorded death does not schedule a broadcast`() {
        val f = Fixture()
        f.plugin.onPlayerRespawn(f.respawn)
        verifyNoInteractions(f.scheduler, f.server)
    }

    @Test
    fun `disconnecting before the next tick suppresses the broadcast`() {
        val f = Fixture()
        f.plugin.onPlayerDeath(f.death)
        f.plugin.onPlayerRespawn(f.respawn)
        `when`(f.player.isOnline).thenReturn(false)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))
        verifyNoInteractions(f.server)
    }
}
