# yzhlSU GitHub Actions 构建

此分支已经配置为使用同一份签名证书构建内核模块、`yzhlsud` 和 Manager
APK。请勿为后续版本更换签名，否则已安装 APK 无法直接升级，并且旧内核也不会
认可新 Manager。

## 1. 生成个人签名

在装有 JDK 的可信 Windows 电脑上，于仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/create-yzhlsu-keystore.ps1
```

脚本会在 `.private-yzhlsu` 中创建：

- `yzhlsu.jks`：需要永久备份的签名私钥。
- `github-secrets.txt`：需要添加到 GitHub 的四项 Secrets。

这两个文件已被 `.gitignore` 排除。不要提交、公开或发送给其他人。

## 2. 配置 GitHub Secrets

打开仓库的 **Settings → Secrets and variables → Actions**，按
`github-secrets.txt` 的内容创建以下 Repository secrets：

- `KEYSTORE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## 3. 运行构建

把 `yzhlSU` 分支推送至 GitHub，进入 **Actions → Build yzhlSU → Run
workflow**。也可以在推送修改后自动触发。

成功后下载 `manager` artifact，其中的 APK 已包含：

- arm64-v8a 与 x86_64 的 `yzhlsud`。
- 当前工作流支持的全部 GKI KMI 内核模块。
- 与内核模块完全匹配的 Manager 签名。

## 4. 设备测试

首次测试不要直接覆盖正常启动槽。先备份原厂 `boot.img`，在 Manager 内选择并
修补镜像；能够使用 `fastboot boot <patched.img>` 的设备应先临时启动验证。

验证项目：

1. yzhlSU 显示已安装并能给测试应用授权。
2. 官方 KernelSU Manager 显示未安装或不支持。
3. 数据仅写入 `/data/adb/yzhlsu`，守护进程为 `/data/adb/yzhlsud`。
4. 安装模块只改变 `/data/adb/yzhlsu/modules`。
5. 重启、撤销授权和永久卸载均不会删除其他 Root 实现的数据。

当前设计允许多个 Manager APK 同时安装，但不支持同时加载多套独立的
KernelSU 派生内核模块。
