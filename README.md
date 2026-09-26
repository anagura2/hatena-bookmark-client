# はてなブックマーク Android クライアント

Pixel 8 Proで発生している公式アプリの横幅・横スクロール問題を避けるための、独立したAndroidネイティブクライアントです。

## 現在の状態

- Kotlin + Jetpack Composeのプロジェクト雛形
- 人気エントリー画面のUIプロトタイプ
- WebView/CSSのviewportに依存しない幅制約
- はてなAPIとの接続、OAuth、ブックマーク投稿は次の実装段階

## OAuth開発設定

Consumer KeyとConsumer Secretは、Git管理対象外の`local.properties`に設定します。

```properties
hatena.consumerKey=YOUR_CONSUMER_KEY
hatena.consumerSecret=YOUR_CONSUMER_SECRET
```

アプリ内のメニューアイコンからOAuth認証を開始し、認証後、はてな画面に表示されたPINコードをアプリへ入力します（OAuthの`oob`方式）。

Consumer SecretをAPKに含める方式は個人利用・試作向けです。一般公開する場合は、
SecretをAndroidアプリに置かないバックエンド方式へ移行してください。

## 開発環境

- Android Studio
- JDK 17
- Android SDK 35

Android Studioでプロジェクトルートを開き、Gradle同期後に実行してください。
