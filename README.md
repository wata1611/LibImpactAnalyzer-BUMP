Java/Maven プロジェクトから外部ライブラリを除去した際の影響を定量的に分析するツール。  
[BUMP ベンチマーク](https://github.com/chains-project/BUMP) の各コミットに対して、ライブラリ除去がソースコードとテスト結果に与える影響をCSVで出力する。

---

## 概要

### 研究の問い

> Java プロジェクトから外部ライブラリを除去すると、どれだけのコードが影響を受けるか？

### 分析の流れ

```
1. BUMP の -breaking Docker イメージ（ライブラリ更新後の状態）を使用
2. xmlstarlet で pom.xml から対象ライブラリの <dependency> を削除
   → mvn compile がコンパイルエラーになる
3. BUMP-LibImpactAnalyzer.jar を実行
   → コンパイルエラーを Spoon AST で自動修正しながら削除量を計測
4. mvn test を実行して機能への影響を定量化
```

---

## 特徴

- **Spoon ライブラリ**を使った AST レベルのソースコード自動修正
- メインコード（Phase 1）とテストコード（Phase 2）を分けて処理
- シングルモジュール・**マルチモジュール** Maven プロジェクト両対応
- 修正前後のファイルを `original/` / `loop1/` ... `loopN/` に逐次保存
- 結果を CSV に出力（削除行数・通過率・実行時間など）

---

## ファイル構成

```
LibImpactAnalyzer-BUMP/
├── dep_analysis_pipeline.sh   # 1件分のパイプライン実行スクリプト（メインエントリ）
├── edit_pom.sh                # xmlstarlet で pom.xml の依存を削除するスクリプト
├── pom_paths.csv              # SHA → pom.xml パスの対応表
├── pom.xml                    # ツール本体のビルド定義
└── src/main/java/iwata/LibImpactAnalyzer_BUMP/
    ├── CompilationErrorAutoFixer.java  # Phase1/2 ループ制御・メインクラス
    ├── SourceCodeFixer.java            # Spoon AST 修正ロジックのコア
    ├── MavenCommandExecutor.java       # mvn compile/test 実行・エラー正規表現パース
    ├── ApplicationConfig.java          # 設定・環境変数・マルチモジュール検出
    ├── CompilationError.java           # コンパイルエラー情報モデル
    ├── CsvWriter.java                  # CSV 出力
    ├── FixMetrics.java                 # 計測メトリクスモデル
    ├── JavaFileSearcher.java           # ファイル検索ユーティリティ
    ├── LineCounter.java                # ソースコード行数カウント
    ├── ModuleInfo.java                 # マルチモジュール情報モデル
    └── PomParser.java                  # pom.xml のモジュール構成解析
```

---

## ビルド

```bash
cd LibImpactAnalyzer-BUMP
mvn package -q
# target/BUMP-LibImpactAnalyzer.jar が生成される
```

Java 11 以上が必要。依存ライブラリは `spoon-core 10.4.2`。

---

## 実行方法

### 1 件実行

```bash
./dep_analysis_pipeline.sh <JSON_PATH> [OUTPUT_DIR] [JAR_PATH] [POM_PATHS_CSV] [EDIT_POM_SCRIPT]
```

| 引数 | デフォルト | 説明 |
|---|---|---|
| `JSON_PATH` | 必須 | BUMP ベンチマークの JSON ファイルパス |
| `OUTPUT_DIR` | `./output` | CSV・修正ファイルの出力先ディレクトリ |
| `JAR_PATH` | `./BUMP-LibImpactAnalyzer.jar` | ツール本体 JAR |
| `POM_PATHS_CSV` | `./pom_paths.csv` | SHA → pom.xml パスの対応表 |
| `EDIT_POM_SCRIPT` | `./edit_pom.sh` | pom.xml 編集スクリプト |

**実行例**:

```bash
./dep_analysis_pipeline.sh \
  /path/to/BUMP_benchmark/3ff575ae202cdf76ddfa8a4228a1711a6fa1e921.json \
  ./output
```

### 内部動作

1. JSON から `breakingCommit`（SHA）・`dependencyGroupID`・`dependencyArtifactID`・`newVersion` を取得
2. `pom_paths.csv` から SHA に対応する pom.xml パスを取得
3. `-breaking` Docker イメージを起動
4. `edit_pom.sh` を Docker コンテナ内で実行し、`xmlstarlet` で対象 `<dependency>` を削除
5. `BUMP-LibImpactAnalyzer.jar` を実行して分析・CSV 出力

---

## 内部処理の詳細

### Phase 1: メインコードの修正（`fixMainCodeErrors`）

1. `mvn clean compile` を実行してコンパイルエラーを抽出
2. エラーが発生したファイルを Spoon で AST 解析
3. エラー行の要素を削除（import 文・フィールド・メソッド・クラス宣言など）
4. エラーがなくなるまで最大 `MAX_ITERATIONS=20` 回繰り返す

### Phase 2: テストコードの修正（`fixTestCodeErrors`）

1. `mvn test-compile` を実行してコンパイルエラーを抽出
2. テストファイルのエラー行を含むメソッド本体をクリアし `Assert.fail(...)` を挿入
3. エラーがなくなるまで繰り返す

### Spoon による AST 修正（`SourceCodeFixer`）

| エラー種別 | 修正方法 |
|---|---|
| 通常のコンパイルエラー | エラー行の AST ノードを `element.delete()` で削除 |
| `extends` / `implements` 宣言エラー | `setSuperclass(null)` / `superInterfaces.clear()` |
| `missing return statement` | メソッド末尾にデフォルト値の `return` 文を追加（`boolean`→`false`、数値型→`0`、参照型→`null`） |
| テストファイルのエラー | エラー行を含むメソッド本体をクリアして `Assert.fail(...)` を挿入 |
| import 文 | エラー行番号に一致する import のみ明示的に `remove()` |

修正のたびに `original/`（初回のみ）と `loop{N}/` ディレクトリへファイルをコピー。

### pom.xml の依存削除（`edit_pom.sh`）

```bash
xmlstarlet ed -L \
  -N pom="http://maven.apache.org/POM/4.0.0" \
  -d "//pom:dependency[pom:groupId=\"$GROUP_ID\" and pom:artifactId=\"$ARTIFACT_ID\" and pom:version=\"$VER\"]" \
  "$pom_file"
```

`groupId` + `artifactId` + **`version`** の完全一致で削除する。  
`xmlstarlet` が未インストールの場合は `apt-get` / `yum` / `apk` で自動インストール。

---

## 出力

### ディレクトリ構成

```
output/
└── <SHA>/
    ├── <SHA>.csv          # 分析結果 CSV（1行）
    ├── console_output.txt # ツール実行ログ
    ├── original/          # 修正前の各ファイル（最初にエラーが出たタイミングで保存）
    ├── loop1/             # ループ1回目で修正されたファイル
    ├── loop2/             # ループ2回目で修正されたファイル
    └── ...
```

### CSV 出力項目（主要フィールド）

| フィールド | 説明 |
|---|---|
| `Main Code Total Lines` | 処理後のメインコード総行数 |
| `Main Code Deleted Lines` | メインコード削除行数（累計） |
| `Test Code Deleted Lines` | テストコード削除行数（累計） |
| `Tests Run` | テスト実行件数 |
| `Failures` / `Errors` | テスト失敗・エラー件数 |
| `Test Pass Rate (%)` | テスト通過率 |
| `Removed Testcase` | 削除されたテストメソッド名 |
| `Modified Main Files` | 修正されたメインコードファイル名 |
| `Total Execution Time (seconds)` | 全体実行時間 |

---

## LibScope（JavaParser 版）との主な違い

本ツールは Spoon ライブラリを使った旧実装。  
現行の研究ツールは `LibScopeAnalyzer`（JavaParser + LexicalPreservingPrinter 版）。

| 項目 | LibImpactAnalyzer-BUMP（本ツール） | LibScopeAnalyzer（現行） |
|---|---|---|
| Docker イメージ | `-breaking`（ライブラリ更新後） | `-pre`（ライブラリ正常状態） |
| AST ライブラリ | Spoon 10.4.2 | JavaParser + LexicalPreservingPrinter |
| pom.xml 依存削除 | `xmlstarlet`（groupId + artifactId + **version**） | `awk`（groupId + artifactId のみ） |
| 対象 pom.xml | `pom_paths.csv` 記載の特定ファイルのみ | プロジェクト内全 pom.xml を走査 |
| テストメソッド修正 | ボディクリア + `Assert.fail(...)` 挿入 | **宣言ごと削除**（連鎖削除を促す） |

---

## 依存関係

| ライブラリ | バージョン | 用途 |
|---|---|---|
| `spoon-core` | 10.4.2 | Java AST 解析・修正 |
| Java | 11+ | 実行環境 |
| Docker | - | BUMP ベンチマーク環境の隔離実行 |
| `xmlstarlet` | - | pom.xml の XML 編集（コンテナ内で自動インストール） |
| `jq` | - | JSON パース（ホスト側シェルスクリプトで使用） |
