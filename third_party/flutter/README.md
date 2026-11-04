# best-flutter/flutter_amap (Flutter AMap legacy reference)

Source: https://github.com/best-flutter/flutter_amap

**Requested URL:** https://github.com/best-flutter/flutter_ama (not found / 404)
**Actual repository:** `best-flutter/flutter_amap` (final `p` added)
**Pinned commit:** `a1e3386c41b21b4f5f67872aff2159512bf6db36` (2018-07-16; `master`)

This source is retained as a **read-only development reference** for NaviTH, next to the separate AMap-Web source repositories. It is NOT an Android Java dependency.

- Language: Dart/Flutter plus native Android and iOS code.
- `pubspec.yaml` version: `0.0.1`, Flutter `>=0.1.4 <3.0.0`, Dart `>=2.0.0-dev.48.0 <3.0.0`.
- Scope stated by upstream README: original AMap 3D map display and basic map options; README warns about limitations of embedding the map in a standalone controller/activity.
- **Do not add it to `app/build.gradle`**, the AMAP combined Navigation SDK JAR, Android Manifest or Flutter dependencies. Mixing this old plugin into the existing native Java app risks SDK conflicts and build failures.
- This does **not** add lane navigation, new offline maps, or Android Auto functionality.
- For a future Flutter-based NaviTH prototype, review the plugin API, update for modern Flutter embedding, request location and privacy consent as required, and test native dependencies first.

## Fetch source

From the NaviTH project root:

```bash
git submodule update --init --recursive third_party/flutter/flutter_amap
```

Or for all reference sources at once:

```bash
bash tools/install-map-references.sh
```

GitHub Actions: **Prepare NaviTH map reference workspace** verifies and packages all ten nonempty source submodules; the Android app build remains separate.
