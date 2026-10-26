# Stable private APK signing and updating

**Never commit or upload the keystore to the public or private source repository.** A private GitHub *encrypted secret* is the intended mechanism. A user-controlled encrypted backup of the keystore and its passwords is essential; losing the key can make in-place APK upgrades impossible.

From an offline, trusted computer with JDK installed, create a long-lived 2048/3072-bit RSA signing keystore:

```
keytool -genkeypair -v -keystore stridelab-privatealpha.jks -alias stridelab -keyalg RSA -keysize 3072 -validity 10000
```

Create repository Actions secrets in `Isshhin/stridelab-development`:

- `STRIDELAB_KEYSTORE_B64`: Base64 of the entire JKS file (not the file path).
- `STRIDELAB_STORE_PASSWORD`: keystore password.
- `STRIDELAB_KEY_ALIAS`: e.g. `stridelab`.
- `STRIDELAB_KEY_PASSWORD`: signing private-key password.

On Windows PowerShell, encode using `[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\secure\stridelab-privatealpha.jks'))` and paste into the GitHub secret field. Do not paste any of these values into chat.

In the final GitHub Actions workflow, set `version_code` initially `1` and increment this each accepted APK update. Stable package name remains `io.stridelab.privatealpha`. Download release APK through private workflow artifact and install manually. `apksigner verify` and fingerprint check should be done before installation. Back up data before every update. The CI-generated **debug** APK uses an ephemeral debug signing key and is intended only for an initial disposable test.

If the release signing secrets are absent, the workflow still builds/tests a debug APK, but **does not pretend that it is safely updatable**.
