#!/bin/bash
# Installs sbt for Claude Code on the web, so sessions can compile and run tests before pushing.
# sbt and all dependencies come from Google's mirror of Maven Central: the default sbt repository
# (repo.scala-sbt.org) is not reachable from the web sandbox, and repo1.maven.org rate-limits its
# shared egress address (HTTP 429).
set -euo pipefail

if [[ "${CLAUDE_CODE_REMOTE:-}" != "true" ]]; then
  exit 0
fi

MIRROR=https://maven-central.storage-download.googleapis.com/maven2
PROJECT_DIR=${CLAUDE_PROJECT_DIR:-$(pwd)}
SBT_VERSION=$(sed -n 's/^sbt\.version=//p' "$PROJECT_DIR/project/build.properties")
LAUNCHER="$HOME/.sbt/launchers/$SBT_VERSION/sbt-launch.jar"
WRAPPER="$HOME/.local/bin/sbt"

mkdir -p "$(dirname "$LAUNCHER")" "$(dirname "$WRAPPER")"
if [[ ! -s "$LAUNCHER" ]]; then
  JAR_URL="$MIRROR/org/scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch-$SBT_VERSION.jar"
  # HTTPS only, also on redirects; the launcher must match the checksum Maven Central publishes
  curl -sSfL --proto '=https' --proto-redir '=https' --retry 3 -o "$LAUNCHER.tmp" "$JAR_URL"
  EXPECTED_SHA1=$(curl -sSfL --proto '=https' --proto-redir '=https' --retry 3 "$JAR_URL.sha1")
  if [[ "$(sha1sum "$LAUNCHER.tmp" | cut -d' ' -f1)" != "${EXPECTED_SHA1:0:40}" ]]; then
    echo "sbt launcher checksum mismatch" >&2
    rm -f "$LAUNCHER.tmp"
    exit 1
  fi
  mv "$LAUNCHER.tmp" "$LAUNCHER"
fi

# read by the launcher and, with sbt.override.build.repos, by the build's own resolution
cat > "$HOME/.sbt/repositories" <<REPOS
[repositories]
  local
  google-maven-central: $MIRROR/
REPOS

# minimal replacement for the sbt script: the launcher does not know script options like -batch
cat > "$WRAPPER" <<WRAP
#!/bin/bash
args=()
for arg in "\$@"; do
  case "\$arg" in
    -batch|--batch) ;;
    *) args+=("\$arg") ;;
  esac
done
exec java -Dsbt.log.noformat=true -Dsbt.ci=true -Dsbt.override.build.repos=true -jar "$LAUNCHER" "\${args[@]}"
WRAP
chmod +x "$WRAPPER"

# download dependencies and compile once, so the cached container starts with a warm build
cd "$PROJECT_DIR"
"$WRAPPER" Test/compile < /dev/null > /tmp/sbt-session-start.log 2>&1 || {
  tail -20 /tmp/sbt-session-start.log >&2
  exit 1
}
