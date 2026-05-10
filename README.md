# Instant Daily Memo

起動したらすぐ今日の日記を書ける Android アプリです。端末の Calendar Provider を使い、Google Calendar の今日の `memo` 予定のメモ欄に本文を保存します。

## 動き

- 起動直後に入力欄を表示します。
- カレンダー権限を許可すると、Google アカウントのカレンダーから今日の `memo` 予定を探します。
- 既存の `memo` 予定があれば、その `description` を表示します。
- 入力後 0.9 秒で自動保存し、アプリを閉じる時にも保存します。
- 今日の `memo` 予定がなければ、保存時に新規作成します。

## ビルド

Android SDK を入れ、ネットワーク経由で Android Gradle Plugin を取得できる状態で、次を実行します。

```powershell
gradle assembleDebug
```

この環境では Android SDK が未設定だったため、APK 生成までは未完了です。`ANDROID_HOME` を設定するか、`local.properties` に `sdk.dir=...` を追加してください。
