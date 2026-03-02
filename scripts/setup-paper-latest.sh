#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.paper-local"
API_BASE="https://api.papermc.io/v2/projects/paper"
mkdir -p "$RUN_DIR/plugins"

latest_version=$(python3 - "$API_BASE" <<'PY'
import json,re,sys,urllib.request
api_base = sys.argv[1]
with urllib.request.urlopen(api_base) as resp:
    j = json.load(resp)
vers = [v for v in j.get("versions", []) if re.fullmatch(r"1\.\d+(?:\.\d+)?", v)]
if not vers:
    raise SystemExit("No stable versions found")
vers.sort(key=lambda s: [int(x) for x in s.split(".")])
print(vers[-1])
PY
)

latest_build=$(python3 - "$API_BASE" "$latest_version" <<'PY'
import json,sys,urllib.request
api_base = sys.argv[1]
version = sys.argv[2]
with urllib.request.urlopen(f"{api_base}/versions/{version}/builds") as resp:
    j = json.load(resp)
builds = [x["build"] for x in j.get("builds", []) if x.get("channel") == "default"]
if not builds:
    builds = [x["build"] for x in j.get("builds", [])]
if not builds:
    raise SystemExit("No builds found for version")
print(max(builds))
PY
)

jar_name="paper-${latest_version}-${latest_build}.jar"
jar_path="$RUN_DIR/$jar_name"

if [[ ! -f "$jar_path" ]]; then
  echo "Downloading Paper $latest_version build $latest_build..."
  curl -fL "$API_BASE/versions/$latest_version/builds/$latest_build/downloads/$jar_name" -o "$jar_path"
else
  echo "Paper already present: $jar_name"
fi

ln -sfn "$jar_name" "$RUN_DIR/paper-latest.jar"

if [[ ! -f "$RUN_DIR/eula.txt" ]]; then
  echo "eula=true" > "$RUN_DIR/eula.txt"
fi

echo "Paper ready: $RUN_DIR/paper-latest.jar"
