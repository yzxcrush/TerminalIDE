# Terminal IDE CA — 系统证书安装

把 Terminal IDE 抓包用的自签 CA 安装为**系统级信任证书**，这样所有 App（含浏览器）都能被 HTTPS 解密。

> ⚠️ 仅用于**自己的设备 / 已授权目标**。证书固定（微信、支付宝、银行等）的 App 依然无法解密，这是 MITM 的固有限制。

---

## 方式一：App 内一键安装（推荐）

1. 打开 App → 抽屉 ☰ → 📡 抓包
2. 点「**开始抓包**」（会自动生成 CA）
3. 点「**装系统证书**」→ 自动完成 APEX 注入
4. 重启相关 App（微信等）使其重新加载信任库

> 重启手机后失效，需重新点一次。

---

## 方式二：KernelSU / Magisk 模块（持久化，开机自动生效）

### 1. 取出 App 生成的 CA

```bash
# 需要 root
cp /data/data/com.terminalide/files/capture/ca.crt /sdcard/Download/terminalide-ca.der
```

### 2. 计算证书哈希并转 PEM

```bash
# 在电脑或 Termux 中
HASH=$(openssl x509 -inform DER -in terminalide-ca.der -noout -subject_hash)
openssl x509 -inform DER -in terminalide-ca.der -out "$HASH.0"
echo "证书文件名: $HASH.0"
```

### 3. 放进模块目录

```bash
# 需要 root
cp "$HASH.0" /data/adb/modules/terminalide_ca/system/etc/security/cacerts/
```

### 4. 安装模块

```bash
# 需要 root，把本目录内容复制过去
mkdir -p /data/adb/modules/terminalide_ca
cp -r module.prop post-fs-data.sh system /data/adb/modules/terminalide_ca/
chmod 755 /data/adb/modules/terminalide_ca/post-fs-data.sh
```

### 5. 重启手机

开机时 `post-fs-data.sh` 会自动：
- 从 APEX 读取系统原有证书（Android 14+ 的真实信任库）
- 加上我们的 CA
- **安全校验**：证书数 < 50 时中止（不会破坏系统信任库）
- 挂载到 init / zygote 命名空间

---

## 方式三：手动安装（无 root）

1. App 内点「**导出 CA**」→ 生成 `/sdcard/Download/terminalide-ca.crt`
2. 系统设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书 → 选择该文件
3. 此方式只有**信任用户证书**的 App 生效（浏览器可用，多数 App 不行）

---

## 卸载

```bash
# 需要 root
rm -rf /data/adb/modules/terminalide_ca
# 然后重启，系统证书恢复原样
```

---

## 原理

Android 14+ 的证书信任库不在 `/system`，而在 **APEX**：

```
/apex/com.android.conscrypt/cacerts/
```

且每个进程（init / zygote / app）有**独立的挂载命名空间**，所以需要用 `nsenter` 注入到每个命名空间：

```bash
mount --bind <新目录> /apex/com.android.conscrypt/cacerts
nsenter --mount=/proc/1/ns/mnt -- mount --bind ...
nsenter --mount=/proc/<zygote-pid>/ns/mnt -- mount --bind ...
```

部分厂商（如本机 OnePlus）把证书放在独立 f2fs 分区，需要从 erofs 挂载点
`/apex/com.android.conscrypt@<版本>/cacerts` 读取原始证书，脚本已做兼容。
