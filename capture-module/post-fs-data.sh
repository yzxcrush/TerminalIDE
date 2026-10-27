#!/system/bin/sh
# Terminal IDE CA - Android 14+ APEX 证书注入
# 安全校验：证书数不足 50 时坚决不挂载，避免破坏系统信任库
MODDIR=${0%/*}
CERT_DIR="$MODDIR/system/etc/security/cacerts"
TEMP_DIR="/data/local/tmp/terminalide-ca-apex"
APEX_CACERTS="/apex/com.android.conscrypt/cacerts"
EROFS_CACERTS="/apex/com.android.conscrypt@360499999/cacerts"

SRC=""
if [ -d "$EROFS_CACERTS" ] && [ "$(ls "$EROFS_CACERTS"/*.0 2>/dev/null | wc -l)" -gt 50 ]; then
  SRC="$EROFS_CACERTS"
elif [ "$(ls "$APEX_CACERTS"/*.0 2>/dev/null | wc -l)" -gt 50 ]; then
  SRC="$APEX_CACERTS"
fi
[ -z "$SRC" ] && exit 0

umount "$TEMP_DIR" 2>/dev/null
rm -rf "$TEMP_DIR" 2>/dev/null
mkdir -p "$TEMP_DIR"
mount -t tmpfs tmpfs "$TEMP_DIR" || exit 1
cp -a "$SRC"/*.0 "$TEMP_DIR/" 2>/dev/null
cp -f "$CERT_DIR"/*.0 "$TEMP_DIR/" 2>/dev/null

CNT=$(ls "$TEMP_DIR"/*.0 2>/dev/null | wc -l)
[ "$CNT" -lt 50 ] && { umount "$TEMP_DIR" 2>/dev/null; exit 0; }

chown -R 0:0 "$TEMP_DIR"
chmod 755 "$TEMP_DIR"
chmod 644 "$TEMP_DIR"/* 2>/dev/null
CTX=$(ls -Zd "$APEX_CACERTS" 2>/dev/null | awk "{print \$1}")
[ -n "$CTX" ] && chcon -R "$CTX" "$TEMP_DIR" 2>/dev/null

mount --bind "$TEMP_DIR" "$APEX_CACERTS" 2>/dev/null
nsenter --mount=/proc/1/ns/mnt -- mount --bind "$TEMP_DIR" "$APEX_CACERTS" 2>/dev/null
for z in zygote zygote64; do
  for pid in $(pidof "$z" 2>/dev/null); do
    nsenter --mount=/proc/$pid/ns/mnt -- mount --bind "$TEMP_DIR" "$APEX_CACERTS" 2>/dev/null
  done
done
