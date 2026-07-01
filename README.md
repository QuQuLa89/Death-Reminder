# DeathReminder

Kotlin 製の Paper プラグインです。

プレイヤーが死亡した地点を記録し、復活したタイミングで本人に `/me` を実行させて死亡座標をチャットへ送信します。

例:

```text
* Player died at world=world x=123 y=64 z=-456
```

## Build

```powershell
gradle build
```

## Requirements

- Paper 1.21.x
- Java 21
