#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export LC_ALL=C
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1790294400}"
mkdir -p dist/Notesy
tar --format=gnu --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner \
    --transform='s,^,notesy-1.0/,' \
    -cf - pom.xml Makefile README.md LICENSE docs packaging scripts src .gitignore .vscode \
    | gzip -n > dist/Notesy/notesy-1.0.tar.gz
cp packaging/notesy.spec packaging/notesy.desktop \
    packaging/notesy.appdata.xml packaging/notesy.changes dist/Notesy/
touch -d "@$SOURCE_DATE_EPOCH" dist/Notesy/notesy-1.0.tar.gz \
    dist/Notesy/notesy.{spec,desktop,appdata.xml,changes}
sha256sum dist/Notesy/notesy-1.0.tar.gz