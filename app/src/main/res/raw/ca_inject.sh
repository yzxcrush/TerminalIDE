#!/system/bin/sh
# Terminal IDE CA 注入脚本（由 App 通过 root 执行）
# 占位符 __HASH__ / __PEM__ 由 Kotlin 替换
H=__HASH__
PEM=__PEM__
T=/data/local/tmp/terminalide-ca-apex
SRC=""

if [ -d /apex/com.android.conscrypt@360499999/cacerts ] && [ $(ls /apex/com.android.conscrypt@360499999/cacerts/*.0 2>/dev/null | wc -l) -gt 100 ]; then
  SRC=/apex/com.android.conscrypt@360499999/cacerts
elif [ $(ls /apex/com.android.conscrypt/cacerts/*.0 2>/dev/null | wc -l) -gt 100 ]; then
  SRC=/apex/com.android.conscrypt/cacerts
fi
[ -z "$SRC" ] && { echo NO_SOURCE; exit 1; }

nsenter -t 1 -m -- sh -c "
  umount $T 2>/dev/null
  rm -rf $T
  mkdir -p $T || exit 1
  mount -t tmpfs tmpfs $T || exit 2
  cp -a $SRC/*.0 $T/ 2>/dev/null
  cp -f $PEM $T/$H.0 2>/dev/null
  chmod 644 $T/* 2>/dev/null
  chcon -R u:object_r:system_security_cacerts_file:s0 $T 2>/dev/null
  C=\$(ls $T/*.0 2>/dev/null | wc -l)
  [ \$C -lt 100 ] && exit 3
  mount --bind $T /apex/com.android.conscrypt/cacerts
"
[ $? -ne 0 ] && { echo INJECT_FAIL; exit 1; }

for z in zygote zygote64; do
  for pid in $(pidof $z); do
    nsenter -t $pid -m -- mount --bind $T /apex/com.android.conscrypt/cacerts 2>/dev/null
  done
done

if ls /apex/com.android.conscrypt/cacerts/$H.0 >/dev/null 2>&1; then
  echo CA_OK
else
  echo CA_MISSING
fi
