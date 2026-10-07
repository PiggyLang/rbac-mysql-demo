#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"
JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
allowed_value_regex='^[A-Za-z0-9._:/?&=+-]*$'

if [[ ! -r "$ENV_FILE" ]]; then
  echo "缺少 .env，请先复制 .env.example 并在本机填写连接凭据。" >&2
  exit 1
fi

while IFS='=' read -r key value || [[ -n "${key:-}" ]]; do
  [[ -z "${key:-}" || "$key" == \#* ]] && continue
  case "$key" in
    MYSQL_DATABASE|MYSQL_USER|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|SERVER_PORT) ;;
    *) echo "不支持 .env 中的变量名：$key" >&2; exit 1 ;;
  esac
  [[ "$value" =~ $allowed_value_regex ]] || { echo ".env 值包含不支持的字符。" >&2; exit 1; }
  export "$key=$value"
done < "$ENV_FILE"

if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  echo ".env 缺少 SPRING_DATASOURCE_PASSWORD。" >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
cd "$PROJECT_DIR"
exec mvn -Dmaven.repo.local="$PROJECT_DIR/target/m2" "$@"
