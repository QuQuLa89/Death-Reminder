# DeathReminder

Kotlin 製の Paper / Folia 対応プラグインです。

プレイヤーが死亡した地点を記録し、リスポーンしたタイミングで死亡座標と現在地からの距離・方角をチャットへ通知します。記録された死亡履歴はコマンドで後から確認することもできます。

通知例:

```text
* Player died at world=world x=123 y=64 z=-456 (142m NE)
```

## Features

- リスポーン時に最新の死亡地点をチャットへ自動通知します（同一ワールドの場合は距離と8方位を表示します）。
- プレイヤーごとの死亡履歴を保持し、`/lastdeath` コマンドでいつでも再確認できます。
- `config.yml` で通知の公開範囲（全員 / 本人のみ）や履歴の保存上限を設定できます。

## Commands

| コマンド | 説明 |
| --- | --- |
| `/lastdeath` | 最新の死亡地点を表示します。 |
| `/lastdeath <数値>` | 最新から指定件数分の死亡履歴を表示します。 |

## Configuration (`config.yml`)

```yaml
# broadcast: サーバー全体に通知 / private: 本人のみに通知
notification-visibility: broadcast

# プレイヤーごとに保持する死亡履歴の最大件数
max-history: 10
```

## Build

```powershell
gradle build
```

## Requirements

- Paper / Folia 1.21.x
- Java 21
