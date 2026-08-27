---
description: Generate and review ProjectS image assets through the native PixelLab MCP without automatic adoption
agent: build
model: openai/gpt-5.6-luna
variant: max
---

ProjectS の `/pixellab` asset pipeline を実行します。Creator の入力は次のとおりです。

```text
$ARGUMENTS
```

以下を順番どおりに行ってください。

1. **接続前のtoken確認**
   `test -r "$HOME/.config/projects/pixellab-token" && test -s "$HOME/.config/projects/pixellab-token"` だけを実行して、ファイルの存在・読取可否・空でないことを確認してください。token本文を読んだり、引数・出力・metadata・ログへ入れたりしないでください。確認に失敗した場合は、次の一文だけを返して終了してください。MCP、request作成、画像処理は実行しません。

   `PixelLabトークンが未設定です。トークン本文をチャットに貼らず、~/.config/projects/pixellab-token に保存して権限を0600に設定し、OpenCodeを再起動してから再実行してください。`

2. **入力の整理**
   free-form request から短い normalized prompt、目的（static icon/object、UI element、既存画像のpixel-art/edit、animation等）、サイズ、候補数を読み取ってください。`references:` や入力中の絶対/相対パスは local reference として抽出し、各パスを先に存在確認してください。存在しない場合はその正確なパスを報告し、MCPを呼ばずに終了してください。referenceは必要なtool入力にそのまま使い、不要なコピーや変換をしないでください。

3. **requestの保存**
   MCPを呼ぶ前に、次のlocal helperで元のrequest、normalized prompt、reference path、安全なサイズ/候補数を保存してください。helperの出力に含まれる request id を後続で使います。

   ```sh
   python3 scripts/pixellab_pipeline.py init \
     --request "$ARGUMENTS" \
     --normalized-prompt "<整理したprompt>" \
     --reference "<reference path>"
   ```

   referenceが無い場合は `--reference` を省略します。サイズや候補数が明示されている場合だけ `--width`、`--height`、`--count` を追加してください。保存先は既定の `.projects-local/pixellab/` で、requestごとに固有folderを使います。

4. **PixelLab native MCPのtool routing**
   OpenCodeに読み込まれた `pixellab` MCP serverの実際のtool一覧とschemaを先に確認し、requestの目的に合うtoolを選んでください。tool名を推測して固定したり、PixelLab Web UIへ誘導したりしないでください。画像生成・edit・style/subject reference・透明背景・animationなど、公開されているtoolの入力schemaに従ってnative MCPだけを呼びます。

   `curl`、`requests`、独自PixelLab API client、別画像AIへのfallbackは使いません。MCPが利用不能または生成失敗ならendpoint/statusを報告し、request folderは残してください。エラー本文をmetadataへ丸ごとコピーせず、tokenらしき値は絶対に保存・表示しないでください。

5. **候補の保存とpreview**
   native MCPが返した全候補を、画素を変更せずlocal image fileへmaterializeできたものだけ、次のhelperへ渡してください。レスポンスにlocal file pathがある場合はそれを使います。attachment/resourceをlocal fileにできない場合は、独自HTTP取得をせず、partial resultとして報告してください。

   ```sh
   python3 scripts/pixellab_pipeline.py save-candidates \
     --request-id "<request id>" \
     --tool-name "<実際に呼んだtool名>" \
     --candidate "<candidate file 1>" \
     --candidate "<candidate file 2>"
   ```

   seed、実際の出力サイズがnative MCPから返る場合だけ `--seed`、`--width`、`--height` を追加します。helperはcandidate metadata、numbered `contact-sheet.png`、light/dark別preview、small asset用のactual-size previewを同じrequest folderへ保存します。候補番号とpathをCreatorへ返してください。

   生成に失敗した場合は次でrequestをretry可能な状態として残します。詳細なMCP errorやtokenは渡しません。

   ```sh
   python3 scripts/pixellab_pipeline.py record-failure \
     --request-id "<request id>" \
     --message "generation failed"
   ```

6. **採用は明示指示のときだけ**
   生成・previewだけではProjectS resourceへ何もcopyしません。「candidate 7を `<target path>` に採用して」のようにcandidate番号とtargetが明確なCreator requestが来た場合だけ、次を実行できます。

   ```sh
   python3 scripts/pixellab_pipeline.py adopt \
     --request-id "<request id>" \
     --candidate 7 \
     --target "<target path>" \
     --confirm-adopt
   ```

   candidate番号、request id、targetのいずれかが曖昧・欠落している場合は確認を返して停止し、推測で採用しないでください。candidateが存在しない場合は拒否してください。targetが既に存在する場合は、Creatorが明示的にoverwrite/replaceを要求した場合に限り `--overwrite` を追加します。それ以外は拒否します。採用後もKotlin/runtime編集、Git commit、push、mergeは自動実行しません。source request idとcandidate番号はlocal result metadataへ残ります。

7. **follow-up**
   Creatorが既存候補の比較や再生成を依頼した場合は、既存result metadataを読み、対象candidate/referenceを明示的に使って新しいrequest folderを作ってください。以前の候補を上書きせず、新しい生成も同じnative MCP routingとpreview手順を通します。
