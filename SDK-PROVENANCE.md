# Local AMAP SDK provenance

Original bundle: `AMap3DMap_AMapSearch_AMapLocation.zip` uploaded by user.

Combined Map 11.2.1001 / Search 9.8.1 / Location 11.2.1001 • package date 2026-08-05.

## Included files and SHA256

| Relative path | Bytes | SHA256 |
|---|---:|---|
| `app/libs/amap-combined-20260805.jar` | 11,186,854 | `c5020d25c0d72088210457ff4f8e3ccc775a9441fdad9d8794523bc38ea6a8ea` |
| `app/src/main/jniLibs/arm64-v8a/libAMapSDK_MAP_v11_2_100.so` | 25,224,832 | `aca0cb1404e006507bc339308bc852814a5f0be45ced1276e6bd8910e4e67060` |
| `app/src/main/jniLibs/arm64-v8a/libapssdk.so` | 858,944 | `a94c9e6c8750dd752c87f4ef97a1553e6980020b3b1a9dc6a775b7b695786a82` |
| `app/src/main/jniLibs/armeabi-v7a/libAMapSDK_MAP_v11_2_100.so` | 17,120,400 | `e3e18d2d1274291dc568c5f736d40225b8f106f8c5554937b11b63ad6894edde` |
| `app/src/main/jniLibs/armeabi-v7a/libapssdk.so` | 554,844 | `03ebf6f6154a6d6f643485603459928e02cca1b65f326a38ddfc2d6cea4f691a` |

No Maven AMAP dependencies are required; AMAP JAR is under app/libs/, while native .so files are stored compressed in `app/libs/amap-native-android.zip` and automatically unpacked into a Gradle build directory.

The presence of matching API classes in the JAR was checked, but **not** a full Android compile/runtime test.

Native library files are third-party binaries; ensure their license permits distribution before making the repository public.

The ZIP in `reference/` contains administrative division adcode lookup data only, not offline map tiles, POIs, or navigation routing.

Original native .so SHA256 values listed above refer to the expanded binaries inside the ZIP, not separate files in the repository.
