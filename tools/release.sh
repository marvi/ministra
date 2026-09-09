#!/usr/bin/env bash
#
# Släpper en ny version av Ministra.
#
# Versionen i pom-filen är den som står på tur. Skriptet tar bort
# -SNAPSHOT, kör igenom bygget, taggar, och höjer sedan pom-filen ett
# steg för nästa gång. Taggen är det som utlöser publiceringen av
# containeravbilden: se .github/workflows/release.yml
#
# Versionerna är X.Y.Z. Utan flaggor släpps det pom-filen står på, och efteråt
# höjs tredje siffran: 1.1.1 → 1.1.2-SNAPSHOT. Ett större steg anges för hand,
# och därefter räknar tredje siffran vidare därifrån: --version 1.2 ger 1.2 och
# sedan 1.2.1-SNAPSHOT.
#
#   tools/release.sh              # versionen enligt pom-filen
#   tools/release.sh --version 1.2
#   tools/release.sh --dry-run    # visa vad som skulle hända
#
set -euo pipefail

BRANCH=main
REMOTE=origin
dry_run=false
assume_yes=false
version=""

die() {
  printf '\033[31mFel:\033[0m %s\n' "$*" >&2
  exit 1
}
info() { printf '\033[1m%s\033[0m\n' "$*"; }
step() { printf '  %s\n' "$*"; }

run() {
  if $dry_run; then
    printf '  \033[2m[torrkörning] %s\033[0m\n' "$*"
  else
    "$@"
  fi
}

usage() {
  sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//; $d'
  exit 0
}

while [ $# -gt 0 ]; do
  case "$1" in
  --version)
    version="${2:-}"
    shift 2
    ;;
  --dry-run)
    dry_run=true
    shift
    ;;
  --yes | -y)
    assume_yes=true
    shift
    ;;
  --help | -h) usage ;;
  *) die "Okänd flagga: $1" ;;
  esac
done

cd "$(dirname "$0")/.."
[ -f pom.xml ] || die "Hittar inte pom.xml, står jag i rätt katalog?"

# ---------- Kontroller före release ----------
info "Kontrollerar arbetskopian"

[ -z "$(git status --porcelain)" ] || die "Arbetskopian har oincheckade ändringar."

current_branch=$(git branch --show-current)
[ "$current_branch" = "$BRANCH" ] || die "Står på grenen '$current_branch', förväntade '$BRANCH'."

git fetch --quiet --tags "$REMOTE"
if [ -n "$(git rev-list "HEAD..$REMOTE/$BRANCH" 2>/dev/null)" ]; then
  die "Grenen ligger efter $REMOTE/$BRANCH. Hämta hem först."
fi
step "ren, på $BRANCH, i fas med $REMOTE"

# ---------- Vilken version ----------
pom_version=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version | tail -1)
if [ -z "$version" ]; then
  version="${pom_version%-SNAPSHOT}"
fi

printf '%s' "$version" | grep -Eq '^[0-9]+\.[0-9]+(\.[0-9]+)?$' ||
  die "Versionen '$version' ser inte ut som X.Y.Z eller X.Y"

# Nästa version: tredje siffran plus ett. En X.Y räknas som X.Y.0.
IFS=. read -r major minor patch <<EOF
$version
EOF
next_version="$major.$minor.$((${patch:-0} + 1))"

# Versionen måste vara högre än allt som redan släppts, annars räknar Maven
# den nya artefakten som äldre än en befintlig.
highest_tag=$(git tag -l | sed 's/^v//' |
  grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1 || true)
if [ -n "$highest_tag" ]; then
  lower=$(printf '%s\n%s\n' "$version" "$highest_tag" | sort -V | head -1)
  if [ "$version" = "$highest_tag" ] || [ "$lower" = "$version" ]; then
    die "Version $version är inte högre än den befintliga taggen $highest_tag."
  fi
fi

git rev-parse -q --verify "refs/tags/v$version" >/dev/null &&
  die "Taggen v$version finns redan."

info "Släpper version $version"
step "pom-filen står på:    $pom_version"
step "högsta befintliga tagg: ${highest_tag:-ingen}"
step "efter release:        $next_version-SNAPSHOT"

if ! $dry_run && ! $assume_yes; then
  printf '\nFortsätta? [j/N] '
  read -r answer
  case "$answer" in j | J | ja | JA) ;; *) die "Avbrutet." ;; esac
fi

# ---------- Bygg och tagga ----------
info "Sätter version $version"
run ./mvnw -B -q versions:set -DnewVersion="$version" -DgenerateBackupPoms=false

info "Bygger och kör testerna"
run ./mvnw -B --no-transfer-progress clean verify

info "Taggar v$version"
run git commit -am "Version $version"
run git tag -a "v$version" -m "Version $version"

info "Höjer till $next_version-SNAPSHOT"
run ./mvnw -B -q versions:set -DnewVersion="$next_version-SNAPSHOT" -DgenerateBackupPoms=false
run git commit -am "Fortsätt på $next_version-SNAPSHOT"

info "Skickar till $REMOTE"
run git push "$REMOTE" "$BRANCH"
run git push "$REMOTE" "v$version"

if $dry_run; then
  info "Torrkörning: ingenting ändrades."
else
  info "Klart. Taggen v$version utlöser bygge, publicering och utrullning."
  step "följ den på: https://github.com/marvi/ministra/actions"
fi
