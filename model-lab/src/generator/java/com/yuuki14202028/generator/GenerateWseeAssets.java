package com.yuuki14202028.generator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * bbmodel/*.bbmodel を走査して、モデル名とアニメーション名を Java 定数
 * {@code com.yuuki14202028.WseeAssets} として書き出す。モデル名の typo を
 * 実行時でなくコンパイル時に検出するためのもの。
 * args: [0]=bbmodel ディレクトリ, [1]=出力 java ルート。
 */
public final class GenerateWseeAssets {
    private GenerateWseeAssets() {}

    private static final String PACKAGE = "com.yuuki14202028";

    public static void main(String[] args) throws Exception {
        Path modelDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]).resolve(PACKAGE.replace('.', '/'));

        List<Path> files;
        try (Stream<Path> s = Files.list(modelDir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".bbmodel"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        if (files.isEmpty()) throw new IllegalStateException("bbmodel が1つも無い: " + modelDir);

        // モデルファイル名 -> アニメーション名（名前順）
        Map<String, List<String>> models = new LinkedHashMap<>();
        for (Path f : files) {
            models.put(f.getFileName().toString(), animationNames(f));
        }

        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("WseeAssets.java"), render(models), StandardCharsets.UTF_8);
        int anims = models.values().stream().mapToInt(List::size).sum();
        System.out.println("生成: " + models.size() + " モデル / " + anims + " アニメ -> " + outDir);
    }

    private static List<String> animationNames(Path file) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> names = new ArrayList<>();
        JsonElement anims = root.get("animations");
        if (anims != null && anims.isJsonArray()) {
            for (JsonElement a : anims.getAsJsonArray()) {
                if (!a.isJsonObject()) continue;
                JsonElement name = a.getAsJsonObject().get("name");
                if (name == null || !name.isJsonPrimitive()) continue;
                String n = name.getAsString();
                if (!names.contains(n)) names.add(n);
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String render(Map<String, List<String>> models) {
        StringBuilder sb = new StringBuilder(models.size() * 256);
        sb.append("package ").append(PACKAGE).append(";\n\n");
        sb.append("/**\n");
        sb.append(" * bbmodel/ の WSEE モデル名とアニメーション名の定数。\n");
        sb.append(" *\n");
        sb.append(" * <p>GenerateWseeAssets による自動生成物。手で書き換えても次のビルドで消える。\n");
        sb.append(" * 定数が無ければ bbmodel/ にそのモデルが無い。\n");
        sb.append(" */\n");
        sb.append("public final class WseeAssets {\n");
        sb.append("    private WseeAssets() {}\n\n");
        sb.append("    /** 全モデル名（ファイル名順）。実行時に組み立てた名前の検証用。 */\n");
        sb.append("    public static final String[] All = {\n");
        for (String fileName : models.keySet()) {
            sb.append("        ").append(quote(fileName)).append(",\n");
        }
        sb.append("    };\n");

        Map<String, String> classNames = new HashMap<>();
        for (Map.Entry<String, List<String>> e : models.entrySet()) {
            String fileName = e.getKey();
            String cls = identifier(fileName.substring(0, fileName.length() - ".bbmodel".length()));
            String prev = classNames.put(cls, fileName);
            if (prev != null) {
                throw new IllegalStateException("クラス名が衝突: " + prev + " と " + fileName + " -> " + cls);
            }
            sb.append("\n    public static final class ").append(cls).append(" {\n");
            sb.append("        private ").append(cls).append("() {}\n");
            sb.append("        public static final String Model = ").append(quote(fileName)).append(";\n");
            if (!e.getValue().isEmpty()) {
                sb.append("\n        public static final class Anim {\n");
                sb.append("            private Anim() {}\n");
                Map<String, String> fieldNames = new HashMap<>();
                for (String anim : e.getValue()) {
                    String field = identifier(anim);
                    String prevAnim = fieldNames.put(field, anim);
                    if (prevAnim != null) {
                        throw new IllegalStateException(
                                fileName + " のアニメ名が衝突: " + prevAnim + " と " + anim + " -> " + field);
                    }
                    sb.append("            public static final String ").append(field)
                            .append(" = ").append(quote(anim)).append(";\n");
                }
                sb.append("        }\n");
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** snake_case 等を CamelCase の Java 識別子へ機械変換する。英数字以外は区切りとして捨てる。 */
    private static String identifier(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        String name = sb.toString();
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) name = "_" + name;
        return name;
    }

    /** Java の文字列リテラルとして原文どおりの値になるようエスケープする。 */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
