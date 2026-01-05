#!/usr/bin/env bash
set -euo pipefail

JSON_PATH="${1:-}"
OUTPUT_DIR="${2:-./output}"
JAR_PATH="${3:-./BUMP-LibImpactAnalyzer.jar}"
POM_PATHS_CSV="${4:-./pom_paths.csv}"
EDIT_POM_SCRIPT="${5:-./edit_pom.sh}"

# 出力ディレクトリの作成
mkdir -p "$OUTPUT_DIR"

# JSONからライブラリ情報の取得
SHA="$(jq -r '.breakingCommit' "$JSON_PATH")"
GROUP_ID="$(jq -r '.updatedDependency.dependencyGroupID' "$JSON_PATH")"
ARTIFACT_ID="$(jq -r '.updatedDependency.dependencyArtifactID' "$JSON_PATH")"
VER="$(jq -r '.updatedDependency.newVersion' "$JSON_PATH")"
IMAGE="$(jq -r '.breakingUpdateReproductionCommand' "$JSON_PATH" | awk '{print $3}')"

# --- CSV から SHA に対応する pom.xml パスを取得 ---
if [[ ! -f "$POM_PATHS_CSV" ]]; then
  echo "error: pom_paths.csv not found: $POM_PATHS_CSV" >&2
  exit 1
fi

POM_PATHS="$(grep "^${SHA}," "$POM_PATHS_CSV" | cut -d',' -f2- || true)"

# 何も見つからない場合は stderr に出して終了
if [[ -z "$POM_PATHS" ]]; then
  echo "error: no pom.xml paths found for SHA: $SHA in $POM_PATHS_CSV" >&2
  exit 2
fi

# edit_pom.sh の存在確認
if [[ ! -f "$EDIT_POM_SCRIPT" ]]; then
  echo "error: edit_pom.sh not found: $EDIT_POM_SCRIPT" >&2
  exit 1
fi

# 出力ファイル名
OUTPUT_FILE="${SHA}.csv"

echo "SHA: $SHA"
echo "GROUP_ID: $GROUP_ID"
echo "ARTIFACT_ID: $ARTIFACT_ID"
echo "VER: $VER"
echo "POM_PATHS:"
echo "$POM_PATHS"

PATHS_INFO=$(docker run --rm "$IMAGE" sh -c '
  # コンテナ起動時のカレントディレクトリをプロジェクトルートとして扱う
  PROJECT_ROOT="$(pwd)"

  echo "PROJECT_ROOT=$PROJECT_ROOT"
')

# パス情報をパース
eval "$PATHS_INFO"

echo "Project Root: $PROJECT_ROOT"

# JARファイル、edit_pom.sh、出力ディレクトリをマウントしてコンテナ実行
docker run --rm \
  -e SHA="$SHA" \
  -e GROUP_ID="$GROUP_ID" \
  -e ARTIFACT_ID="$ARTIFACT_ID" \
  -e VER="$VER" \
  -e POM_PATHS="$POM_PATHS" \
  -e PROJECT_ROOT="$PROJECT_ROOT" \
  -v "$(realpath "$JAR_PATH")":/tool/BUMP-LibImpactAnalyzer.jar:ro \
  -v "$(realpath "$EDIT_POM_SCRIPT")":/tool/edit_pom.sh:ro \
  -v "$(realpath "$OUTPUT_DIR")":/output \
  "$IMAGE" \
  sh -lc '
    # POM編集スクリプトの実行（shで実行）
    sh /tool/edit_pom.sh "$GROUP_ID" "$ARTIFACT_ID" "$VER" "$POM_PATHS" "$PROJECT_ROOT"
    
    # CSV出力ツールの実行
    java -jar /tool/BUMP-LibImpactAnalyzer.jar /output/'"$OUTPUT_FILE"