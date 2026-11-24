package dev.ququla.deathreminder

import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeathReminderPluginTest {
    private class Fixture {
        val plugin = mock(DeathReminderPlugin::class.java)
        val server = mock(Server::class.java)
        val player = mock(Player::class.java)
        val scheduler = mock(EntityScheduler::class.java)
        val death = mock(PlayerDeathEvent::class.java)
        val respawn = mock(PlayerRespawnEvent::class.java)
        val command = mock(Command::class.java)
        val config = mock(FileConfiguration::class.java)
        val world: World = mock(World::class.java)
        val callbacks = mutableListOf<Consumer<ScheduledTask>>()

        init {
            setPluginField("deathHistory", ConcurrentHashMap<UUID, List<DeathPosition>>())
            setPluginField("pendingRespawnDeaths", ConcurrentHashMap<UUID, DeathPosition>())
            setPluginField("notificationVisibility", NotificationVisibility.BROADCAST)
            setPluginField("maxHistory", DeathReminderPlugin.DEFAULT_MAX_HISTORY)

            `when`(plugin.server).thenReturn(server)
            `when`(plugin.config).thenReturn(config)
            `when`(player.uniqueId).thenReturn(UUID.randomUUID())
            `when`(player.name).thenReturn("QuQuLa89")
            `when`(player.scheduler).thenReturn(scheduler)
            `when`(player.isOnline).thenReturn(true)
            `when`(world.name).thenReturn("world_nether")
            `when`(player.location).thenReturn(Location(world, -1.2, 63.8, 20.9))
            `when`(death.player).thenReturn(player)
            `when`(respawn.player).thenReturn(player)
            `when`(command.name).thenReturn("lastdeath")

            doCallRealMethod().`when`(plugin).onPlayerDeath(death)
            doCallRealMethod().`when`(plugin).onPlayerRespawn(respawn)
            doCallRealMethod().`when`(plugin).loadSettings()
            doAnswer {
                callbacks += it.getArgument<Consumer<ScheduledTask>>(1)
                mock(ScheduledTask::class.java)
            }.`when`(scheduler).run(eq(plugin), any(), isNull())
        }

        fun setVisibility(visibility: NotificationVisibility) {
            setPluginField("notificationVisibility", visibility)
        }

        fun setMaxHistory(limit: Int) {
            setPluginField("maxHistory", limit)
        }

        fun runLastDeath(
            sender: CommandSender,
            vararg args: String,
        ): Boolean {
            val argArray: Array<out String> = args
            doCallRealMethod().`when`(plugin).onCommand(sender, command, "lastdeath", argArray)
            return plugin.onCommand(sender, command, "lastdeath", argArray)
        }

        fun tabCompleteLastDeath(
            sender: CommandSender,
            vararg args: String,
        ): List<String> {
            val argArray: Array<out String> = args
            doCallRealMethod().`when`(plugin).onTabComplete(sender, command, "lastdeath", argArray)
            return plugin.onTabComplete(sender, command, "lastdeath", argArray)
        }

        private fun setPluginField(
            name: String,
            value: Any,
        ) {
            DeathReminderPlugin::class.java
                .getDeclaredField(name)
                .apply {
                    isAccessible = true
                }.set(plugin, value)
        }
    }

    @Test
    fun `broadcast mode sends coordinates without distance when respawning in another world and only once`() {
        val f = Fixture()
        f.plugin.onPlayerDeath(f.death)

        val overworld = mock(World::class.java)
        `when`(overworld.name).thenReturn("world")
        `when`(f.player.location).thenReturn(Location(overworld, 0.0, 64.0, 0.0))

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
    fun `broadcast mode includes distance and direction when respawning in the same world`() {
        val f = Fixture()
        `when`(f.player.location).thenReturn(Location(f.world, 10.0, 64.0, -10.0))
        f.plugin.onPlayerDeath(f.death)

        // Respawn 10 blocks south and 10 blocks west of death -> death is 14m NE
        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 64.0, 0.0))
        f.plugin.onPlayerRespawn(f.respawn)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))

        verify(f.server).broadcast(Component.text("* QuQuLa89 died at world=world_nether x=10 y=64 z=-10 (14m NE)"))
    }

    @Test
    fun `private mode sends message only to the player and does not broadcast`() {
        val f = Fixture()
        f.setVisibility(NotificationVisibility.PRIVATE)
        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 64.0, -20.0))
        f.plugin.onPlayerDeath(f.death)

        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 64.0, 0.0))
        f.plugin.onPlayerRespawn(f.respawn)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))

        verifyNoInteractions(f.server)
        verify(f.player).sendMessage(Component.text("* QuQuLa89 died at world=world_nether x=0 y=64 z=-20 (20m N)"))
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

    @Test
    fun `lastdeath shows no history message when player has never died`() {
        val f = Fixture()
        assertTrue(f.runLastDeath(f.player))
        verify(f.player).sendMessage(Component.text("No death history recorded yet."))
    }

    @Test
    fun `lastdeath without arguments returns latest death even after respawn notification`() {
        val f = Fixture()
        `when`(f.player.location).thenReturn(Location(f.world, 5.0, 70.0, 0.0))
        f.plugin.onPlayerDeath(f.death)

        // Respawn consumes pendingRespawnDeaths, but history remains queryable
        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 70.0, 0.0))
        f.plugin.onPlayerRespawn(f.respawn)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))

        assertTrue(f.runLastDeath(f.player))
        verify(f.player).sendMessage(Component.text("Last death: world=world_nether x=5 y=70 z=0 (5m E)"))
    }

    @Test
    fun `lastdeath with count shows newest deaths first and respects maxHistory`() {
        val f = Fixture()
        f.setMaxHistory(2)

        `when`(f.player.location).thenReturn(Location(f.world, 1.0, 64.0, 0.0))
        f.plugin.onPlayerDeath(f.death)
        `when`(f.player.location).thenReturn(Location(f.world, 2.0, 64.0, 0.0))
        f.plugin.onPlayerDeath(f.death)
        `when`(f.player.location).thenReturn(Location(f.world, 3.0, 64.0, 0.0))
        f.plugin.onPlayerDeath(f.death)

        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 64.0, 0.0))
        assertTrue(f.runLastDeath(f.player, "5"))

        verify(f.player).sendMessage(Component.text("Death history (2/2):"))
        verify(f.player).sendMessage(Component.text("#1 world=world_nether x=3 y=64 z=0 (3m E)"))
        verify(f.player).sendMessage(Component.text("#2 world=world_nether x=2 y=64 z=0 (2m E)"))
    }

    @Test
    fun `lastdeath rejects invalid arguments and non-player senders`() {
        val f = Fixture()
        val console = mock(CommandSender::class.java)

        assertTrue(f.runLastDeath(console))
        verify(console).sendMessage(Component.text("This command can only be used by players."))

        assertTrue(f.runLastDeath(f.player, "0"))
        verify(f.player).sendMessage(Component.text("Usage: /lastdeath [count] (count must be a positive number)"))

        assertTrue(f.runLastDeath(f.player, "abc"))
        assertTrue(f.runLastDeath(f.player, "1", "2"))
        verify(f.player).sendMessage(Component.text("Usage: /lastdeath [count]"))

        val otherCommand = mock(Command::class.java)
        `when`(otherCommand.name).thenReturn("other")
        doCallRealMethod().`when`(f.plugin).onCommand(f.player, otherCommand, "other", emptyArray())
        assertFalse(f.plugin.onCommand(f.player, otherCommand, "other", emptyArray()))
    }

    @Test
    fun `onTabComplete suggests counts matching recorded history`() {
        val f = Fixture()
        assertEquals(emptyList(), f.tabCompleteLastDeath(f.player, ""))

        f.plugin.onPlayerDeath(f.death)
        f.plugin.onPlayerDeath(f.death)
        f.plugin.onPlayerDeath(f.death)

        assertEquals(listOf("1", "2", "3"), f.tabCompleteLastDeath(f.player, ""))
        assertEquals(listOf("2"), f.tabCompleteLastDeath(f.player, "2"))
        assertEquals(emptyList(), f.tabCompleteLastDeath(f.player, "1", "2"))
    }

    @Test
    fun `loadSettings reads config visibility and clamps maxHistory`() {
        val f = Fixture()
        `when`(f.config.getString("notification-visibility")).thenReturn("private")
        `when`(f.config.getInt("max-history", DeathReminderPlugin.DEFAULT_MAX_HISTORY)).thenReturn(0)

        f.plugin.loadSettings()

        // Only 1 history entry retained because maxHistory is clamped to 1, and notification is private
        `when`(f.player.location).thenReturn(Location(f.world, 1.0, 64.0, 0.0))
        f.plugin.onPlayerDeath(f.death)
        `when`(f.player.location).thenReturn(Location(f.world, 2.0, 64.0, 0.0))
        f.plugin.onPlayerDeath(f.death)

        `when`(f.player.location).thenReturn(Location(f.world, 0.0, 64.0, 0.0))
        f.plugin.onPlayerRespawn(f.respawn)
        f.callbacks.single().accept(mock(ScheduledTask::class.java))
        verifyNoInteractions(f.server)
        verify(f.player).sendMessage(Component.text("* QuQuLa89 died at world=world_nether x=2 y=64 z=0 (2m E)"))

        assertEquals(listOf("1"), f.tabCompleteLastDeath(f.player, ""))
    }

    @Test
    fun `DeathPosition formats all eight compass directions and vertical-only offsets`() {
        val world = mock(World::class.java)
        `when`(world.name).thenReturn("world")
        val origin = Location(world, 0.0, 64.0, 0.0)

        assertEquals("world=world x=0 y=64 z=-10 (10m N)", DeathPosition("world", 0, 64, -10).toMessage(origin))
        assertEquals("world=world x=10 y=64 z=-10 (14m NE)", DeathPosition("world", 10, 64, -10).toMessage(origin))
        assertEquals("world=world x=10 y=64 z=0 (10m E)", DeathPosition("world", 10, 64, 0).toMessage(origin))
        assertEquals("world=world x=10 y=64 z=10 (14m SE)", DeathPosition("world", 10, 64, 10).toMessage(origin))
        assertEquals("world=world x=0 y=64 z=10 (10m S)", DeathPosition("world", 0, 64, 10).toMessage(origin))
        assertEquals("world=world x=-10 y=64 z=10 (14m SW)", DeathPosition("world", -10, 64, 10).toMessage(origin))
        assertEquals("world=world x=-10 y=64 z=0 (10m W)", DeathPosition("world", -10, 64, 0).toMessage(origin))
        assertEquals("world=world x=-10 y=64 z=-10 (14m NW)", DeathPosition("world", -10, 64, -10).toMessage(origin))

        assertEquals("world=world x=0 y=64 z=0 (0m)", DeathPosition("world", 0, 64, 0).toMessage(origin))
        assertEquals("world=world x=0 y=74 z=0 (10m)", DeathPosition("world", 0, 74, 0).toMessage(origin))

        val nether = mock(World::class.java)
        `when`(nether.name).thenReturn("world_nether")
        assertEquals("world=world x=10 y=64 z=0", DeathPosition("world", 10, 64, 0).toMessage(Location(nether, 0.0, 64.0, 0.0)))
    }

    @Test
    fun `NotificationVisibility parses config values with broadcast default`() {
        assertEquals(NotificationVisibility.BROADCAST, NotificationVisibility.fromConfig("broadcast"))
        assertEquals(NotificationVisibility.PRIVATE, NotificationVisibility.fromConfig("private"))
        assertEquals(NotificationVisibility.PRIVATE, NotificationVisibility.fromConfig(" PRIVATE "))
        assertEquals(NotificationVisibility.PRIVATE, NotificationVisibility.fromConfig("self"))
        assertEquals(NotificationVisibility.BROADCAST, NotificationVisibility.fromConfig(null))
        assertEquals(NotificationVisibility.BROADCAST, NotificationVisibility.fromConfig("unknown"))
    }
}
