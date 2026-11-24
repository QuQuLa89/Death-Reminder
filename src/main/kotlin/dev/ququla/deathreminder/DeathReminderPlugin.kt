package dev.ququla.deathreminder

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 通知の公開範囲を表す列挙型（Python の Enum に相当）。
internal enum class NotificationVisibility {
    BROADCAST,
    PRIVATE,
    ;

    companion object {
        // config.yml の文字列を安全に NotificationVisibility に変換する。
        fun fromConfig(raw: String?): NotificationVisibility =
            when (raw?.trim()?.lowercase()) {
                "private", "self" -> PRIVATE
                else -> BROADCAST
            }
    }
}

// クラス定義。コロン「:」は「継承」や「実装」を表す（Python の class Foo(Bar, Baz): と同じイメージ）。
// - JavaPlugin(): Paper プラグインの親クラス（CommandExecutor / TabCompleter の機能も内包している）。
// - Listener: イベント（プレイヤーの死亡など）を受け取れるようにするためのインターフェース（型・約束事）。
class DeathReminderPlugin :
    JavaPlugin(),
    Listener {
    // 死亡したプレイヤーごとの履歴リスト（インデックス 0 が最新）。
    private val deathHistory = ConcurrentHashMap<UUID, List<DeathPosition>>()

    // リスポーン時に「1回だけ」自動通知するための未通知死亡地点。
    private val pendingRespawnDeaths = ConcurrentHashMap<UUID, DeathPosition>()

    // config.yml から読み込む設定値（var は再代入できる変数）。
    private var notificationVisibility: NotificationVisibility = NotificationVisibility.BROADCAST
    private var maxHistory: Int = DEFAULT_MAX_HISTORY

    // プラグインがサーバーに読み込まれて有効になるときに自動で1回だけ呼ばれる処理。
    override fun onEnable() {
        saveDefaultConfig()
        loadSettings()
        server.pluginManager.registerEvents(this, this)
        // plugin.yml に登録した /lastdeath コマンドの実行・タブ補完をこのクラスに紐づける。
        getCommand("lastdeath")?.let { cmd ->
            cmd.setExecutor(this)
            cmd.tabCompleter = this
        }
    }

    // プラグインが無効化（サーバー停止など）されるときに自動で呼ばれる後片付け処理。
    override fun onDisable() {
        deathHistory.clear()
        pendingRespawnDeaths.clear()
    }

    // config.yml から公開範囲と最大履歴件数を読み込む。
    internal fun loadSettings() {
        notificationVisibility = NotificationVisibility.fromConfig(config.getString(CONFIG_VISIBILITY_KEY))
        maxHistory = config.getInt(CONFIG_MAX_HISTORY_KEY, DEFAULT_MAX_HISTORY).coerceAtLeast(1)
    }

    // プレイヤーが死んだ瞬間に呼ばれ、履歴の先頭へ追加しつつリスポーン通知予約にも記録する。
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val position = DeathPosition.from(player.location)
        val limit = maxHistory.coerceAtLeast(1)

        // compute でスレッド安全に「新しい死亡地点を先頭に足して上限件数で切る」更新を行う。
        deathHistory.compute(player.uniqueId) { _, existing ->
            listOf(position) + (existing ?: emptyList()).take(limit - 1)
        }
        pendingRespawnDeaths[player.uniqueId] = position
    }

    // プレイヤーがリスポーン（復活）した瞬間に自動で呼ばれる関数。
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        // リスポーン通知用の予約だけを取り出して消す（履歴リスト deathHistory 自体は残る）。
        val deathPosition = pendingRespawnDeaths.remove(player.uniqueId) ?: return

        // リスポーン直後はプレイヤーの準備が完全に終わっていないことがあるため、
        // プレイヤー専用のスケジューラ（Paper / Folia の安全なタスク実行の仕組み）に処理を予約する。
        player.scheduler.run(
            this,
            { _ ->
                if (!player.isOnline) return@run

                val message = Component.text("* ${player.name} died at ${deathPosition.toMessage(player.location)}")
                when (notificationVisibility) {
                    NotificationVisibility.BROADCAST -> server.broadcast(message)
                    NotificationVisibility.PRIVATE -> player.sendMessage(message)
                }
            },
            null,
        )
    }

    // /lastdeath [count] コマンドが実行されたときに呼ばれる処理。
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!command.name.equals("lastdeath", ignoreCase = true)) return false
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players."))
            return true
        }
        if (args.size > 1) {
            sender.sendMessage(Component.text("Usage: /lastdeath [count]"))
            return true
        }

        // 引数が無ければ 1件、あれば整数に変換する（数字以外や 0 以下なら使い方エラーを返す）。
        val count = if (args.isEmpty()) 1 else args[0].toIntOrNull()
        if (count == null || count < 1) {
            sender.sendMessage(Component.text("Usage: /lastdeath [count] (count must be a positive number)"))
            return true
        }

        val history = deathHistory[sender.uniqueId].orEmpty()
        if (history.isEmpty()) {
            sender.sendMessage(Component.text("No death history recorded yet."))
            return true
        }

        val entries = history.take(count)
        if (count == 1) {
            sender.sendMessage(Component.text("Last death: ${entries[0].toMessage(sender.location)}"))
        } else {
            sender.sendMessage(Component.text("Death history (${entries.size}/${history.size}):"))
            entries.forEachIndexed { index, entry ->
                sender.sendMessage(Component.text("#${index + 1} ${entry.toMessage(sender.location)}"))
            }
        }
        return true
    }

    // /lastdeath の引数入力中に Tab キーで件数候補を補完する。
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> {
        if (!command.name.equals("lastdeath", ignoreCase = true) || sender !is Player || args.size != 1) {
            return emptyList()
        }
        val size = deathHistory[sender.uniqueId]?.size ?: 0
        if (size == 0) return emptyList()
        return (1..size).map(Int::toString).filter { it.startsWith(args[0]) }
    }

    companion object {
        internal const val DEFAULT_MAX_HISTORY = 10
        private const val CONFIG_VISIBILITY_KEY = "notification-visibility"
        private const val CONFIG_MAX_HISTORY_KEY = "max-history"
    }
}
