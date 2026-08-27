---
description: Generate and review ProjectS image assets through the official PixelLab API without automatic adoption
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
   `test -r "$HOME/.config/projects/pixellab-token" && test -s "$HOME/.config/projects/pixellab-token"` だけを実行して、ファイルの存在・読取可否・空でないことを確認してください。token本文を読んだり、引数・出力・metadata・ログへ入れたりしないでください。確認に失敗した場合は、次の一文だけを返して終了してください。API、request作成、画像処理は実行しません。

   `PixelLabトークンが未設定です。トークン本文をチャットに貼らず、~/.config/projects/pixellab-token に保存して権限を0600に設定し、OpenCodeを再起動してから再実行してください。`

2. **入力の整理**
   free-form request から短い normalized prompt、目的（static icon/object、UI element、既存画像のpixel-art/edit、animation等）、サイズ、候補数を読み取ってください。`references:` や入力中の絶対/相対パスは local reference として抽出し、各パスを先に存在確認してください。存在しない場合はその正確なパスを報告し、APIを呼ばずに終了してください。referenceは必要なtool入力にそのまま使い、不要なコピーや変換をしないでください。

3. **公式PixelLab APIで生成**
   OpenCode 1.18.16ではremote MCP transportは接続できてもPixelLab tool catalogが登録されず、公式SDK 1.0.5は現行generation responseをparseできないことを確認済みです。PixelLab Web UIや別画像AIへfallbackせず、公式v2 APIを次のlocal runnerから使用してください。runnerが元のrequest、normalized prompt、reference path、サイズ、候補数を保存してから生成します。

   ```sh
   uv run scripts/pixellab_generate.py \
     --request "$ARGUMENTS" \
     --normalized-prompt "<整理したprompt>" \
     --width <width> \
     --height <height> \
     --count <candidate count> \
     --transparent \
     --reference "<reference path>"
   ```

   referenceが無い場合は `--reference` を省略し、透明背景が不要な依頼だけ `--transparent` を省略します。runnerはtokenをcanonical fileからprocess内部でだけ読み、CLI引数、chat、metadata、logへ出しません。text-onlyは公式APIのPixflux、reference付きはBitforgeを使い、候補PNGとnumbered contact sheetを `.projects-local/pixellab/results/<request-id>/` に保存します。成功時はrequest id、候補数、result path、contact sheet pathをCreatorへ返してください。失敗時は安全な要約だけを返し、raw API errorを表示しないでください。

4. **採用は明示指示のときだけ**
   生成・previewだけではProjectS resourceへ何もcopyしません。「candidate 7を `<target path>` に採用して」のようにcandidate番号とtargetが明確なCreator requestが来た場合だけ、次を実行できます。

   ```sh
   python3 scripts/pixellab_pipeline.py adopt \
     --request-id "<request id>" \
     --candidate 7 \
     --target "<target path>" \
     --confirm-adopt
   ```

   candidate番号、request id、targetのいずれかが曖昧・欠落している場合は確認を返して停止し、推測で採用しないでください。candidateが存在しない場合は拒否してください。targetが既に存在する場合は、Creatorが明示的にoverwrite/replaceを要求した場合に限り `--overwrite` を追加します。それ以外は拒否します。採用後もKotlin/runtime編集、Git commit、push、mergeは自動実行しません。source request idとcandidate番号はlocal result metadataへ残ります。

5. **follow-up**
   Creatorが既存候補の比較や再生成を依頼した場合は、既存result metadataを読み、対象candidate/referenceを明示的に使って新しいrequest folderを作ってください。以前の候補を上書きせず、新しい生成も同じ公式API routeとpreview手順を通します。
