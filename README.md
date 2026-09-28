# はてなブックマーク Android クライアント

はてなブックマークを閲覧・検索・管理する、個人利用向けの独立したAndroidクライアントです。
KotlinとJetpack Composeで実装しています。

このアプリは、はてな株式会社の公式アプリではありません。
私のスマホでは公式アプリで横幅が広く(innerWdthが900px)なってしまい、使いづらいので開発しました。

## 主な機能

- 人気・新着・カテゴリ別の記事一覧
- 記事詳細のWebView表示
- 記事の既読履歴
- 記事コメント一覧、コメント詳細、スター数表示
- ログインユーザーのマイブックマーク一覧
- ブックマークの登録・編集、タグ選択
- 通知一覧と未読バッジ
- 記事一覧の全文検索
- 検索画面の話題のキーワード表示
- 記事一覧、カテゴリ、スクロール位置、WebViewの状態保持
- Androidシステム設定に追従するライト／ダークテーマ

## 必要な環境

- Android Studio
- JDK 17
- Android SDK 35
- minSdk 26

## OAuth開発設定

OAuthのConsumer KeyとConsumer Secretは、Git管理対象外の`local.properties`に設定します。

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
hatena.consumerKey=YOUR_CONSUMER_KEY
hatena.consumerSecret=YOUR_CONSUMER_SECRET
```

アプリ内のメニューからログインを開始し、ブラウザのはてな認証画面に表示されたPINコードをアプリへ入力します。
認証方式はOAuth 1.0aの`oob`フローです。

### セキュリティ上の注意

このプロジェクトは個人利用・試作を目的としています。
Consumer Secretはビルド時にアプリへ埋め込まれるため、APKを配布すると解析される可能性があります。
一般公開用のアプリとして配布する場合は、Consumer Secretをアプリに含めないバックエンド方式へ移行してください。

`local.properties`、OAuthトークン、署名鍵、APKなどの秘密情報・生成物はコミットしないでください。

## ビルド

プロジェクトルートで以下を実行します。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug --no-daemon
```

生成されるデバッグAPK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

接続中の端末を確認するには、次を実行します。

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

インストールと起動:

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s <device-id> shell monkey -p jp.hatena.bookmarkclient 1
```

このプロジェクトでは、リリース署名設定は用意していません。上記はデバッグ版です。

## GitHub Releases

`v*`形式のタグを`main`へpushすると、GitHub Actionsが署名付きRelease APKをビルドし、
GitHub Releasesへ自動で添付します。

リリース用keystoreはリポジトリへコミットせず、GitHub ActionsのSecretsへ登録してください。
必要なSecretsは次の4つです。

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

最初のリリースに使ったkeystoreは、今後の更新でも同じものを使う必要があります。
keystoreとパスワードは安全な場所にバックアップしてください。

## API

主に以下のはてなブックマークAPI・フィードを利用しています。

- 人気・カテゴリ別RSS
  - `https://b.hatena.ne.jp/hotentry.rss`
  - `https://b.hatena.ne.jp/entrylist/all.rss?sort=new`
  - `https://b.hatena.ne.jp/hotentry/{category}.rss`
- 記事・コメント情報
  - `https://b.hatena.ne.jp/entry/json/`
  - `https://b.hatena.ne.jp/entry/jsonlite/`
- 公開記事検索RSS
  - `https://b.hatena.ne.jp/q/{query}?target=text&mode=rss&sort=popular`
- コメントのスター数
  - `https://s.hatena.com/entry.json`
- 認証が必要なマイブックマーク、タグ、ブックマーク登録
  - Hatena Bookmark OAuth／REST API

通知取得には、公式ドキュメントで一般公開されていないHatenaの通知エンドポイントを利用しています。
仕様変更により動作しなくなる可能性があります。

## 既知の制限

- 個人利用・試作向けであり、一般配布向けのConsumer Secret保護は行っていません。
- WebViewを画面遷移後も保持するため、長時間利用時はメモリ使用量が増える可能性があります。
- はてなの非公式・公開APIの仕様変更に影響を受ける機能があります。
- リリース署名や自動配布の設定はありません。

## ライセンス

このリポジトリのコードおよび独自に作成したアプリアイコンは、MIT Licenseの下で公開します。

Hatenaの商標・ロゴ、外部サービスのAPI、依存ライブラリなど、第三者に帰属するものは
それぞれの利用条件・ライセンスに従います。MIT Licenseは、はてな株式会社との提携や公式な許諾を意味しません。
