#!/usr/bin/env bash
# Compute per-locale translation completion and update/check the README table.
#
# Usage:
#   bash scripts/translation-status.sh           # rewrite the table in README.md
#   bash scripts/translation-status.sh --check   # exit 1 if the table is stale
#
# Requires: bash, grep, sed, sort, comm, awk, find  (standard on Ubuntu CI)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_XML="$REPO_ROOT/app/src/main/res/values/strings.xml"
README="$REPO_ROOT/README.md"

# Locale code -> endonym; keep in sync with LanguageDialog.kt
display_name() {
    case "$1" in
        en) echo "English"    ;;
        ru) echo "Русский"    ;;
        es) echo "Español"    ;;
        uk) echo "Українська" ;;
        *)  echo "$1"         ;;
    esac
}

# Percentage -> 12-char Unicode block progress bar (e.g. ███████████░)
progress_bar() {
    local pct=$1
    awk -v p="$pct" 'BEGIN {
        w = 12
        f = int(p * w / 100 + 0.5)
        e = w - f
        bar = ""
        for (i = 0; i < f; i++) bar = bar "█"
        for (i = 0; i < e; i++) bar = bar "░"
        print bar
    }'
}

# Sorted translatable key names from the source strings.xml
source_keys() {
    grep '<string name=' "$SOURCE_XML" \
        | grep -v 'translatable="false"' \
        | grep -o 'name="[^"]*"' \
        | sed 's/name="//;s/"//' \
        | sort
}

# Sorted key names from a locale strings.xml (path as $1)
locale_keys() {
    grep '<string name=' "$1" \
        | grep -o 'name="[^"]*"' \
        | sed 's/name="//;s/"//' \
        | sort
}

# Print the Markdown table to stdout
compute_table() {
    local src_keys total
    src_keys="$(source_keys)"
    total="$(echo "$src_keys" | wc -l | tr -d '[:space:]')"

    echo "| Language | Progress |"
    echo "| --- | --- |"
    echo "| English | $(progress_bar 100) 100% (source) |"

    find "$REPO_ROOT/app/src/main/res" -maxdepth 1 -name 'values-*' -type d \
        | sort \
        | while IFS= read -r locale_dir; do
            local code lkeys matched pct name bar
            code="$(basename "$locale_dir" | sed 's/^values-//')"
            lkeys="$(locale_keys "$locale_dir/strings.xml")"
            matched="$(comm -12 <(echo "$src_keys") <(echo "$lkeys") | wc -l | tr -d '[:space:]')"
            pct="$(awk "BEGIN { printf \"%d\", int($matched * 100 / $total + 0.5) }")"
            name="$(display_name "$code")"
            bar="$(progress_bar "$pct")"
            echo "| $name | $bar ${pct}% |"
        done
}

# Print the full marker block (start marker + table + end marker) to stdout
generate_block() {
    echo "<!-- translations:start -->"
    compute_table
    echo "<!-- translations:end -->"
}

# ── main ──────────────────────────────────────────────────────────────────────

CHECK=false
[[ "${1:-}" == "--check" ]] && CHECK=true

if $CHECK; then
    if ! grep -q '<!-- translations:start -->' "$README"; then
        echo "ERROR: README is missing <!-- translations:start --> marker."
        echo "Run: bash scripts/translation-status.sh"
        exit 1
    fi
    new="$(generate_block)"
    current="$(awk '/<!-- translations:start -->/,/<!-- translations:end -->/' "$README")"
    if [[ "$current" == "$new" ]]; then
        echo "Translation table is up to date."
        exit 0
    else
        echo "ERROR: README translation table is out of date."
        echo "Run: bash scripts/translation-status.sh"
        echo ""
        diff <(echo "$current") <(echo "$new") || true
        exit 1
    fi
fi

if grep -q '<!-- translations:start -->' "$README"; then
    # Replace content between existing markers
    {
        awk '/<!-- translations:start -->/{exit} {print}' "$README"
        generate_block
        awk 'found{print} /<!-- translations:end -->/{found=1}' "$README"
    } > "${README}.tmp" && mv "${README}.tmp" "$README"
    echo "README.md: translation table updated."
else
    # Insert a full Translations section before ## Contributing
    tmpblock="$(mktemp)"
    {
        echo "## Translations"
        echo ""
        generate_block
        echo ""
        echo "Want to add a language? See [CONTRIBUTING.md](CONTRIBUTING.md)."
        echo ""
    } > "$tmpblock"

    awk -v blk="$tmpblock" '
        /^## Contributing/ {
            while ((getline line < blk) > 0) printf "%s\n", line
            close(blk)
        }
        { print }
    ' "$README" > "${README}.tmp" && mv "${README}.tmp" "$README"
    rm -f "$tmpblock"
    echo "README.md: Translations section inserted."
fi
