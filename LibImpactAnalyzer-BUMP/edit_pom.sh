#!/bin/sh
set -eu

# 引数の取得
GROUP_ID="$1"
ARTIFACT_ID="$2"
VER="$3"
POM_PATHS="$4"
PROJECT_ROOT="$5"

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

# POM_PATHSの各pom.xmlから対象の依存関係を削除
echo "$POM_PATHS" | while IFS= read -r pom_file; do
  # 空行をスキップ
  if [ -z "$pom_file" ]; then
    continue
  fi
  
  full_path="$PROJECT_ROOT/$pom_file"
  
  if [ ! -f "$full_path" ]; then
    echo "warning: pom.xml not found: $full_path" >&2
    continue
  fi
  
  echo "Processing: $full_path"
  
  # 対象の依存関係が存在するか確認
  # まず、該当する依存関係があるかチェック
  dep_count=$(xmlstarlet sel -N pom="http://maven.apache.org/POM/4.0.0" \
    -t -v "count(//pom:dependency[pom:groupId=\"$GROUP_ID\" and pom:artifactId=\"$ARTIFACT_ID\" and pom:version=\"$VER\"])" \
    "$full_path" 2>/dev/null || echo "0")
  
  if [ "$dep_count" -gt 0 ]; then
    echo "Found $dep_count matching dependency/dependencies in: $full_path"
    
    # 依存関係を削除
    xmlstarlet ed -L \
      -N pom="http://maven.apache.org/POM/4.0.0" \
      -d "//pom:dependency[pom:groupId=\"$GROUP_ID\" and pom:artifactId=\"$ARTIFACT_ID\" and pom:version=\"$VER\"]" \
      "$full_path"
    
    echo "Removed dependency from: $full_path"
  else
    echo "No matching dependency found in: $full_path"
  fi
done

echo "POM editing completed"