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
# SHA-256 of the launcher, pinned here because Maven Central only publishes SHA-1 and MD5;
# update it together with project/build.properties
declare -A LAUNCHER_SHA256=(
  [1.11.7]=f92a2095ac75008764fe3b2b793ffe624c4fbef5bfd9b0022e4bc2daf668c651
)
HTTPS_ONLY="=https"
CURL=(curl -sSfL --proto "$HTTPS_ONLY" --proto-redir "$HTTPS_ONLY" --retry 3)

if [[ ! -s "$LAUNCHER" ]]; then
  EXPECTED=${LAUNCHER_SHA256[$SBT_VERSION]:-}
  if [[ -z "$EXPECTED" ]]; then
    echo "no pinned SHA-256 for sbt launcher $SBT_VERSION in $0" >&2
    exit 1
  fi
  "${CURL[@]}" -o "$LAUNCHER.tmp" \
    "$MIRROR/org/scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch-$SBT_VERSION.jar"
  if ! echo "$EXPECTED  $LAUNCHER.tmp" | sha256sum --check --status; then
    echo "sbt launcher $SBT_VERSION does not match its pinned SHA-256" >&2
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
