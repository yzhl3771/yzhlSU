# yzhlSU identity and storage isolation

This branch is a private KernelSU protocol instance named `yzhlSU`. It is
designed so that the stock KernelSU Manager can remain installed but reports
KernelSU as unavailable while a yzhlSU-patched kernel is running.

## Isolation boundaries

| Boundary | KernelSU upstream | yzhlSU |
| --- | --- | --- |
| Manager application ID | `me.weishu.kernelsu` | `me.yzhl.su` |
| Control FD | `[ksu_driver]` | `[yzhlsu_driver]` |
| su-session FD | `[ksu_driver_su]` | `[yzhlsu_driver_su]` |
| FD wrapper | `[ksu_fdwrapper]` | `[yzhlsu_fdwrapper]` |
| Reboot handshake | upstream magic pair | yzhlSU magic pair |
| ioctl type | `K` | `Y` |
| Daemon | `/data/adb/ksud` | `/data/adb/yzhlsud` |
| Working data | `/data/adb/ksu` | `/data/adb/yzhlsu` |
| Modules | `/data/adb/modules` | `/data/adb/modules` (shared for compatibility) |
| SELinux domain | `u:r:ksu:s0` | `u:r:yzhlsu:s0` |
| Deep-link scheme | `ksu` | `yzhlsu` |

The package name is pinned by the kernel build. A stock KernelSU Manager is
therefore not selected as the manager and does not inherit the yzhlSU control
FD. Even if an FD is passed accidentally, upstream ioctl command numbers do
not match yzhlSU's private ioctl type.

## Signing requirement

The kernel verifies the Manager APK signing certificate. Build the Manager
with a key you control, export its signing certificate in DER form, and pass
its exact byte length and SHA-256 digest to the kernel build:

```sh
keytool -exportcert -keystore yzhlsu.jks -alias yzhlsu \
  -storepass:env YZHLSU_KEYSTORE_PASSWORD -file yzhlsu-cert.der

wc -c < yzhlsu-cert.der
sha256sum yzhlsu-cert.der
```

Example kernel build arguments:

```text
KSU_MANAGER_PACKAGE=me.yzhl.su
KSU_EXPECTED_SIZE=<decimal-or-0x-certificate-length>
KSU_EXPECTED_HASH=<lowercase-sha256>
```

The Manager build must use the matching keystore through `KEYSTORE_FILE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

The yzhlSU kernel build intentionally stops with an error when
`KSU_EXPECTED_SIZE` or `KSU_EXPECTED_HASH` is missing. This prevents creating
an image whose Manager can never authenticate. Do not restore KernelSU's
upstream certificate defaults.

Do not ship a shared private signing key in the repository. Every independent
root instance should use a different application ID, signing key, handshake,
ioctl type, FD names, data paths, and SELinux types.

## GitHub Actions build

Create the signing material once on a trusted Windows machine with a JDK:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/create-yzhlsu-keystore.ps1 `
  -OutputDirectory .private-yzhlsu
```

The command creates `yzhlsu.jks` and `github-secrets.txt`. Keep both files out
of Git and back them up securely. In the GitHub repository, open **Settings →
Secrets and variables → Actions** and create the four repository secrets copied
from `github-secrets.txt`:

- `KEYSTORE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Push the `yzhlSU` branch, then open **Actions → Build yzhlSU → Run workflow**.
The workflow performs these operations with one certificate identity:

1. Decode the private keystore without uploading it as an artifact.
2. Extract the public certificate length and SHA-256 digest.
3. Build all supported arm64 KMI modules exclusively for the local Windows
   image patcher. x86_64 remains disabled.
4. Build `ksuinit` and the private-protocol `yzhlsud` binaries without an
   embedded KMI for the Android Manager.
5. Build, repack, and sign the `me.yzhl.su` Manager APK.
6. Build the single-file Windows local image patcher and verify that every
   supported arm64 KMI is embedded.
7. Upload the final APK as `manager` and the local tool as
   `yzhlSU-local-image-patcher-windows-x64`.

Pull requests use a one-day temporary signing key. Those APKs are disposable
and cannot update a Manager signed by the persistent repository key.

## Scope

This design supports multiple Manager APKs being installed at the same time,
with only the Manager paired to the currently running kernel instance showing
root. It does not make it safe to load several independent KernelSU-derived
kernel modules simultaneously. Those modules hook the same kernel execution,
credential, and filesystem paths and need a single shared dispatcher before
true simultaneous operation can be supported.

## Verification

After installing the yzhlSU Manager and booting a yzhlSU-patched image:

1. yzhlSU reports the kernel version and can grant/revoke one test app.
2. The stock KernelSU Manager reports not installed or unsupported.
3. yzhlSU keeps its daemon and non-module working data in `/data/adb/yzhlsu`
   and `/data/adb/yzhlsud`.
4. Module installation follows upstream KernelSU and uses the shared
   `/data/adb/modules` and `/data/adb/modules_update` directories.
5. Because the module directory is shared, do not let two Root managers mutate
   modules concurrently, and back up modules before permanently uninstalling.
