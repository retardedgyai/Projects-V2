# ProjectS v2 開発環境

## リライト開始時の固定値

- Minecraft Java Edition: **26.2**
- Java: **JDK 25**
- Server: **Minestom 26.2系**
- Client: **Fabric 26.2**
- Language: **Kotlin-first**
- Build: **Gradle Kotlin DSL**
- IDE候補: **IntelliJ IDEA**
- AI実装支援: Codex / OpenCode 等

## なぜGradleか

Mavenが不可能だからではない。

ProjectSはMinestom ServerだけでなくFabric Clientも同じrepoで扱うため、Fabric Loomを含めてGradleへ統一する方が運用しやすい。

目標:

```text
Projects-V2/
├─ domain/
├─ content/
├─ protocol/
├─ server-minestom/
├─ client-fabric/
├─ mob-editor/
├─ integration-tests/
└─ docs/
```

Server/Client/Protocolを一つの変更で同時更新できるモノレポ構成を使う。

## バージョン更新方針

「常に最新」ではなく「開発開始時の最新安定版を一定期間固定」。

- Snapshot / Pre-releaseは追わない。
- 新安定版が出てもmainを即更新しない。
- 更新BranchでBuild / Combat / Client / Protocolを検証する。
- 問題なければまとめてmainへ入れる。

## Client描画

Minecraft 26.2の現行描画基盤へ合わせる。

- 生OpenGLへ強く依存するProjectS VFX基盤は作らない。
- Client側の独自描画はMinecraft/Fabricの現行Render Pipelineを利用する。
- 将来のRenderer変更へ追従しやすい構造を優先する。

## 起動体験

開発者が毎回複数の複雑な手順を踏まなくて済むようにする。

目標例:

```bash
./gradlew :server-minestom:run
./gradlew :client-fabric:runClient
```

可能なら開発用にServer+Clientをまとめて準備/起動しやすいタスクも用意する。

## Javaの扱い

基本はKotlin。

Javaを許容する例:
- Fabric Mixin等でJavaの方がInterop上明確に安全。
- 外部API境界でKotlin化するメリットが薄い。

「ゼロJava」自体を目標にはしない。
