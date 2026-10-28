#!/usr/bin/env bash
#
# Stages the documentation site from the checked-out sources of a tag.
#
#   etc/site/stage.sh <tag> <output-dir> [<apidocs-dir>]
#
# Run from the repository root. The site is an explicit list - README.md as the
# home page, docs/ and the Jekyll configuration - so nothing else in the
# repository is published by accident. A Markdown link to a repository file the
# site does not carry would break once published, so it is rewritten to that
# file on GitHub at the tag; anchors, absolute URLs and links into docs/ are
# left alone, since they resolve on the site as they are.
set -euo pipefail

tag="${1:?usage: stage.sh <tag> <output-dir> [<apidocs-dir>]}"
out="${2:?usage: stage.sh <tag> <output-dir> [<apidocs-dir>]}"
apidocs="${3:-}"

rm -rf "$out"
mkdir -p "$out"
cp README.md "$out/"
cp -r docs "$out/"
cp etc/site/_config.yml "$out/"
if [ -n "$apidocs" ]; then
    cp -r "$apidocs" "$out/apidocs"
fi

for page in README.md docs/*.md; do
    TAG="$tag" PAGE="$page" perl -i -pe '
        BEGIN { ($dir = $ENV{PAGE}) =~ s{[^/]*$}{}; }
        s{\]\((?![a-z][a-z0-9+.-]*:|#|/)([^)\s#]+)(#[^)\s]*)?\)}{
            my ($path, $anchor) = ($1, $2 // "");
            my @parts;
            for (split m{/}, $dir . $path) {
                next if $_ eq "" || $_ eq ".";
                if ($_ eq "..") { pop @parts } else { push @parts, $_ }
            }
            my $target = join "/", @parts;
            $target =~ m{^docs/}
                ? "]($path$anchor)"
                : "](https://github.com/matteobaccan/vmjms/blob/$ENV{TAG}/$target$anchor)";
        }ge;
    ' "$out/$page"
done
