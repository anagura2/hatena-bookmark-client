# はてなブックマーク Android クライアント

Pixel 8 Proで発生している公式アプリの横幅・横スクロール問題を避けるための、独立したAndroidネイティブクライアントです。

## 現在の状態

- Kotlin + Jetpack Composeのプロジェクト雛形
- 人気エントリー画面のUIプロトタイプ
- WebView/CSSのviewportに依存しない幅制約
- はてなAPIとの接続、OAuth、ブックマーク投稿は次の実装段階

## 開発環境

- Android Studio
- JDK 17
- Android SDK 35

Android Studioでプロジェクトルートを開き、Gradle同期後に実行してください。
