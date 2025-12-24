#!/usr/bin/env bash
set -euo pipefail

JSON_PATH="${1:-}"
OUTPUT_DIR="${2:-./output}"
JAR_PATH="${3:-./BUMP-LibImpactAnalyzer.jar}"

# 出力ディレクトリの作成
mkdir -p "$OUTPUT_DIR"

# JSONからライブラリ情報の取得
SHA="$(jq -r '.breakingCommit' "$JSON_PATH")"
GROUP_ID="$(jq -r '.updatedDependency.dependencyGroupID' "$JSON_PATH")"
ARTIFACT_ID="$(jq -r '.updatedDependency.dependencyArtifactID' "$JSON_PATH")"
VER="$(jq -r '.updatedDependency.newVersion' "$JSON_PATH")"
IMAGE="$(jq -r '.breakingUpdateReproductionCommand' "$JSON_PATH" | awk '{print $3}')"
PR_URL="$(jq -r '.url' "$JSON_PATH")"

# --- PR URL から owner/repo と PR番号を抽出 ---
OWNER_REPO="$(echo "$PR_URL" | sed -nE 's#^https?://github\.com/([^/]+/[^/]+)/pull/([0-9]+).*$#\1#p')"
PR_NUMBER="$(echo "$PR_URL" | sed -nE 's#^https?://github\.com/([^/]+/[^/]+)/pull/([0-9]+).*$#\2#p')"

if [[ -z "$OWNER_REPO" || -z "$PR_NUMBER" ]]; then
  echo "error: invalid PR url in json: $PR_URL" >&2
  exit 1
fi

# --- GitHub API から PR の変更ファイル一覧を取り、pom.xml のパスだけ抜く ---
POM_PATHS="$(
  page=1
  while :; do
    api="https://api.github.com/repos/${OWNER_REPO}/pulls/${PR_NUMBER}/files?per_page=100&page=${page}"

    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
      res="$(curl -fsSL \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
        "$api")"
    else
      res="$(curl -fsSL \
        -H "Accept: application/vnd.github+json" \
        "$api")"
    fi

    cnt="$(echo "$res" | jq 'length')"
    [[ "$cnt" -eq 0 ]] && break

    echo "$res" | jq -r '.[].filename' | grep -E '(^|/)pom\.xml$' || true
    page=$((page+1))
  done | sort -u
)"

# 何も見つからない場合は stderr に出して終了
if [[ -z "$POM_PATHS" ]]; then
  echo "no pom.xml changed in PR: $PR_URL" >&2
  exit 2
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
  # コンテナ起動時のカレントディレクトリにあるpom.xml(マルチモジュールの場合は親pom.xml)
  PARENT_POM_PATH="$PROJECT_ROOT/pom.xml"

  echo "PROJECT_ROOT=$PROJECT_ROOT"
  echo "PARENT_POM_PATH=$PARENT_POM_PATH"
')

# パス情報をパース
eval "$PATHS_INFO"

echo "Project Root: $PROJECT_ROOT"
echo "PARENT_POM_PATH: $PARENT_POM_PATH"

# JARファイルと出力ディレクトリをマウントしてコンテナ実行
# 依存関係の追加・削除処理も含める
docker run --rm \
  -e SHA="$SHA" \
  -e GROUP_ID="$GROUP_ID" \
  -e ARTIFACT_ID="$ARTIFACT_ID" \
  -e VER="$VER" \
  -e PR_URL="$PR_URL" \
  -e POM_PATHS="$POM_PATHS" \
  -e PROJECT_ROOT="$PROJECT_ROOT" \
  -e PARENT_POM_PATH="$PARENT_POM_PATH" \
  -v "$(realpath "$JAR_PATH")":/tool/BUMP-LibImpactAnalyzer.jar:ro \
  -v "$(realpath "$OUTPUT_DIR")":/output \
  "$IMAGE" \
  sh -lc '
    # xmlstarletのインストール確認とインストール
    if ! command -v xmlstarlet >/dev/null 2>&1; then
      echo "Installing xmlstarlet..."
      
      # ディストリビューション判定してインストール
      if command -v apt-get >/dev/null 2>&1; then
        apt-get update -qq && apt-get install -y -qq xmlstarlet
      elif command -v yum >/dev/null 2>&1; then
        yum install -y -q xmlstarlet
      elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache xmlstarlet
      else
        echo "error: unable to install xmlstarlet (unsupported package manager)" >&2
        exit 1
      fi
    fi

    # 1. PARENT_POM_PATHにjunit依存関係を追加
    if [[ -f "$PARENT_POM_PATH" ]]; then
      echo "Adding junit dependency to: $PARENT_POM_PATH"
      
      # 既に同じ依存関係が存在するか確認
      if xmlstarlet sel -N pom="http://maven.apache.org/POM/4.0.0" \
         -t -v "//pom:dependency[pom:groupId=\"junit\" and pom:artifactId=\"junit\"]" \
         "$PARENT_POM_PATH" 2>/dev/null | grep -q .; then
        echo "junit dependency already exists in parent pom, skipping addition"
      else
        # dependenciesセクションが存在するか確認
        if xmlstarlet sel -N pom="http://maven.apache.org/POM/4.0.0" \
           -t -v "//pom:dependencies" "$PARENT_POM_PATH" 2>/dev/null | grep -q .; then
          # dependenciesセクションが存在する場合、そこに追加
          xmlstarlet ed -L \
            -N pom="http://maven.apache.org/POM/4.0.0" \
            -s "//pom:dependencies" -t elem -n "dependency" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "groupId" -v "junit" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "artifactId" -v "junit" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "version" -v "4.13.2" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "scope" -v "test" \
            "$PARENT_POM_PATH"
          echo "junit dependency added to parent pom"
        else
          # dependenciesセクションが存在しない場合、projectの下に作成
          xmlstarlet ed -L \
            -N pom="http://maven.apache.org/POM/4.0.0" \
            -s "//pom:project" -t elem -n "dependencies" \
            -s "//pom:project/pom:dependencies" -t elem -n "dependency" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "groupId" -v "junit" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "artifactId" -v "junit" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "version" -v "4.13.2" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "scope" -v "test" \
            "$PARENT_POM_PATH"
          echo "dependencies section created and junit dependency added to parent pom"
        fi
      fi
    else
      echo "warning: parent pom not found: $PARENT_POM_PATH" >&2
    fi

    # 2. POM_PATHSの各pom.xmlにjunit依存関係を追加して、対象の依存関係を削除
    echo "$POM_PATHS" | while IFS= read -r pom_file; do
      if [[ -z "$pom_file" ]]; then
        continue
      fi
      
      full_path="$PROJECT_ROOT/$pom_file"
      
      if [[ ! -f "$full_path" ]]; then
        echo "warning: pom.xml not found: $full_path" >&2
        continue
      fi
      
      echo "Processing: $full_path"
      
      # junit依存関係を追加
      if xmlstarlet sel -N pom="http://maven.apache.org/POM/4.0.0" \
         -t -v "//pom:dependency[pom:groupId=\"junit\" and pom:artifactId=\"junit\"]" \
         "$full_path" 2>/dev/null | grep -q .; then
        echo "junit dependency already exists in $full_path, skipping addition"
      else
        # dependenciesセクションが存在するか確認
        if xmlstarlet sel -N pom="http://maven.apache.org/POM/4.0.0" \
           -t -v "//pom:dependencies" "$full_path" 2>/dev/null | grep -q .; then
          # dependenciesセクションが存在する場合、そこに追加
          xmlstarlet ed -L \
            -N pom="http://maven.apache.org/POM/4.0.0" \
            -s "//pom:dependencies" -t elem -n "dependency" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "groupId" -v "junit" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "artifactId" -v "junit" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "version" -v "4.13.2" \
            -s "//pom:dependencies/pom:dependency[last()]" -t elem -n "scope" -v "test" \
            "$full_path"
          echo "junit dependency added to $full_path"
        else
          # dependenciesセクションが存在しない場合、projectの下に作成
          xmlstarlet ed -L \
            -N pom="http://maven.apache.org/POM/4.0.0" \
            -s "//pom:project" -t elem -n "dependencies" \
            -s "//pom:project/pom:dependencies" -t elem -n "dependency" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "groupId" -v "junit" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "artifactId" -v "junit" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "version" -v "4.13.2" \
            -s "//pom:project/pom:dependencies/pom:dependency" -t elem -n "scope" -v "test" \
            "$full_path"
          echo "dependencies section created and junit dependency added to $full_path"
        fi
      fi
      
      # 対象の依存関係が存在するか確認して削除
      xmlstarlet ed -L \
        -N pom="http://maven.apache.org/POM/4.0.0" \
        -d "//pom:dependency[pom:groupId=\"$GROUP_ID\" and pom:artifactId=\"$ARTIFACT_ID\" and pom:version=\"$VER\"]" \
        "$full_path" && echo "Removed dependency from: $full_path" || echo "No matching dependency found in: $full_path"
    done
    
    # 3. CSV出力ツールの実行
    java -jar /tool/BUMP-LibImpactAnalyzer.jar /output/'"$OUTPUT_FILE"