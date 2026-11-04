# NaviTH v0.3 – Android Auto integration preview (Source)

โปรเจกต์ Android ใหม่ เขียนด้วย **Java + Android SDK + AMAP SDK** สร้างจากคู่มือ
https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-map/show-map

> **สถานะ:** โปรเจกต์ Source สำหรับ Build โดยใช้ AMAP SDK เวอร์ชันจาก ZIP ที่ผู้ใช้อัปโหลด ยังไม่ได้คอมไพล์เป็น APK หรือทดสอบบนมือถือจริง แพ็กเกจนี้ไม่มี API Key ฝังอยู่ เพื่อให้อัปโหลดเข้า Repository แบบ Private ได้อย่างปลอดภัยกว่า

## ฟีเจอร์ที่ลงโค้ดแล้ว

- แผนที่ AMAP แบบ Vector, เลือกสลับดาวเทียม
- UI ภาษาไทย, พยายามตั้งค่าแสดงชื่อบนแผนที่ภาษาไทย และ region ประเทศไทย
- ขอความยินยอมก่อนเริ่ม SDK พร้อมสิทธิ์ตำแหน่ง Android แยกจากกัน
- รับตำแหน่งปัจจุบันเมื่อผู้ใช้อนุญาต (ขณะใช้งานแอปเท่านั้น)
- ค้นหาสถานที่ (AMAP POI Search), เลือกจุดหมายจากผลค้นหา
- เลือกจุดหมายด้วยการแตะค้างบนแผนที่
- คำนวณและวาดเส้นทางรถยนต์ / เดินเท้า จาก GPS ไปยังจุดหมาย พร้อมระยะทางและเวลาประมาณ
- ปุ่มกลับไปตำแหน่งปัจจุบัน และล้างเส้นทาง

**ยังไม่รองรับจริง:** เสียงแนะนำการเลี้ยว / ระบบนำทาง Turn-by-turn, แสดงช่องจราจร 3D / ทางด่วนแบบละเอียด, การนำทางตามรถระหว่างขับ, Android Automotive OS (ติดตั้งในรถโดยตรง), แผนที่ Offline. เวอร์ชัน 0.3 เพิ่มเฉพาะโครงสร้าง Android Auto และหน้าจอตัวอย่างเส้นทางบนจอรถ (ยังไม่ทดสอบใน DHU/รถจริง). อย่าใช้เวอร์ชันนี้เป็นระบบนำทางหลักขณะขับรถ; ความครอบคลุมข้อมูลประเทศไทยและสิทธิ์ใช้งาน API ต้องตรวจสอบกับ AMAP เอง

## สิ่งที่ต้องทำก่อน Build

1. ลงทะเบียนและสร้าง **Android API Key** ที่ https://console.opnavi.com/ ตามคู่มือ https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-project/get-key
2. ใช้ข้อมูล **Package name**: `com.navith.amap`
3. ใช้ **Debug SHA1** (จาก test-only signing key ที่แนบมาสำหรับต้นแบบเท่านั้น):

   `A6:E8:18:DB:34:52:CF:0C:2E:68:25:F1:79:B6:F4:9E:DB:57:75:08`

4. ตั้งค่า Gradle property `AMAP_KEY` หรือสร้างไฟล์ `local.properties` ในเครื่องพร้อมบรรทัด `AMAP_KEY=คีย์ของคุณ` โดย Gradle จะส่งค่าเข้า AndroidManifest; สำหรับ GitHub Actions ให้ใช้ Repository Secret `AMAP_KEY`. อย่าคอมมิตคีย์ใน Repository

**สำคัญ:** Keystore ใน `demo-signing/TEST_ONLY.jks` เป็น **กุญแจทดสอบที่แจกใน ZIP** และใครก็นำไปใช้เซ็น APK ปลอมได้ ห้ามใช้สำหรับเวอร์ชันที่เผยแพร่จริง ต้องสร้าง signing key ใหม่ที่เป็นความลับเองและสมัคร SHA1 ใหม่

## สำคัญ: API key ของคุณ

- โปรเจกต์ ZIP นี้ไม่รวม `local.properties` หรือคีย์จริง (ตั้งค่าแยกบนมือถือ/CI ตามขั้นตอน)
- **ห้ามอัปโหลด `local.properties` ขึ้น GitHub แม้จะใช้ GitHub Web อัปโหลดไฟล์เอง** เพราะอาจเปิดเผย key; `.gitignore` ป้องกันเฉพาะ `git add` ไม่ได้ป้องกันการลากไฟล์ขึ้นเว็บ
- ใน GitHub Actions ต้องตั้ง Repository Secret ชื่อ `AMAP_KEY` แยกหนึ่งครั้ง (workflow เตรียมไว้แล้ว)
- คีย์นี้เป็น Android client key ซึ่งจะปรากฏภายใน APK ที่แจกจ่าย ควรจำกัดด้วย PackageName + SHA1 บน AMAP Console และอย่าใช้ test signing key สำหรับเผยแพร่จริง

## วิธี Build ด้วยมือถืออย่างเดียว (GitHub Actions)

1. สร้าง GitHub repository ใหม่ชนิด **Private** ผ่านเว็บ GitHub (ควรใช้ Private ขณะทดลอง)
2. แตก ZIP นี้ในมือถือ แล้วนำไฟล์ทั้งหมดไปใส่ repository ใหม่ วิธี Termux ตัวอย่าง:

```sh
pkg update
pkg install git unzip gh
unzip NaviTH-AMAP.zip -d /sdcard/Download/navith-project
cd /sdcard/Download/navith-project/NaviTH-AMAP
# หาก Termux ยังไม่มีสิทธิ์อ่านไฟล์ให้รัน termux-setup-storage ก่อน
# ล็อกอิน GitHub:
gh auth login
# ถ้าสร้าง repo เปล่าไว้แล้ว:
git init -b main
git add .
git commit -m 'Initial NaviTH AMAP project'
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

3. เปิด GitHub repository > **Settings > Secrets and variables > Actions > New repository secret**, ชื่อ `AMAP_KEY` ใส่ key ที่ได้จาก AMAP Console **(จำเป็นสำหรับ GitHub Actions เพราะ `local.properties` จะไม่ถูกอัปโหลดด้วยคำสั่ง git ปกติ)**
4. เปิด **Actions > Build NaviTH Android APK > Run workflow** (หรือ push อีกครั้งหลังตั้ง key)
5. เมื่อ Build สำเร็จ เปิด workflow run > **Artifacts > NaviTH-debug-APK** ดาวน์โหลด ZIP artifact จาก GitHub แล้วแตกไฟล์ `.apk` ติดตั้งลงโทรศัพท์
6. ยอมรับการเปิดใช้งาน SDK และสิทธิ์ GPS หากต้องการค้นหาเส้นทางจากตำแหน่งตัวเอง

*ถ้ารันครั้งแรกก่อนตั้ง AMAP_KEY จะได้ APK ที่ compile ได้ แต่แผนที่จะไม่แสดงข้อมูลจาก AMAP จนกว่าจะ Build ด้วย key ที่ถูกต้อง*

> ถ้า `git` ใน Termux บน `/sdcard/` มีปัญหา permission/symlink ให้แตก ZIP ลง `~/navith` (โฟลเดอร์หลักของ Termux) แทน

## วิธี Build ผ่าน Android Studio บนคอม (หากมี)

- ติดตั้ง Android Studio รุ่นที่รองรับ AGP 8.7.3 และ JDK 17
- `Open` โฟลเดอร์ `NaviTH-AMAP`, รอ Gradle Sync
- สร้าง Android API key โดยใช้ package name + SHA1 ด้านบน
- ตั้งค่า `AMAP_KEY` ใน Environment Variables (หรือ Gradle project property) แล้ว Build > Build APK(s)
- โปรเจกต์ไม่แถม Gradle wrapper; ตัวอย่าง GitHub Actions ใช้ Gradle 8.9 แยกต่างหาก

## การทำงานของแอป

1. เปิดแอปและอ่านหน้าขอความยินยอมก่อนอนุญาต (ไม่ยอมรับ = ไม่โหลด AMAP SDK)
2. เมื่อยอมรับแล้ว หน้าจอจะเปิดที่ **แผนที่กรุงเทพฯ เป็นค่าเริ่มต้น** เท่านั้น ไม่ใช่ข้อมูลพิกัดอุปกรณ์
3. หากอนุญาต GPS แผนที่จะเลื่อนไปตำแหน่งเครื่องเมื่ออ่านสำเร็จ
4. ค้นหาสถานที่ หรือแตะค้างบนแผนที่เพื่อกำหนดจุดหมาย
5. เลือก รถยนต์ หรือ เดินเท้า จากนั้นแตะ แสดงเส้นทาง

## แก้ปัญหาพื้นฐาน

| ปัญหา | ตรวจสอบ |
|---|---|
| แผนที่ขาว/ไม่มีภาพ | `AMAP_KEY`, SHA1 ที่ผูกกับ key, package name, อินเทอร์เน็ต, privacy consent และสิทธิ์ใช้บริการของ AMAP |
| ค้นหาไม่เจอ | สิทธิ์ API/Search, พื้นที่บริการ, คำค้นภาษาไทย/อังกฤษและจังหวัด |
| เส้นทางหาไม่ได้ | ต้นทางจาก GPS พร้อมหรือยัง, สิทธิ์ routing, coverage ประเทศไทย (อย่าเดาเส้นทางขึ้นเอง) |
| Build บน GitHub ไม่ผ่าน | เปิด log ขั้นตอน Build APK เพื่อดูข้อผิดพลาดของ dependency/plugin; SDK นี้ใช้ ARM64/ARMv7; ใช้ไลบรารีท้องถิ่นจากไฟล์ ZIP ที่อัปโหลด จึงไม่ต้องดาวน์โหลด AMAP SDK จาก Maven |
| ตำแหน่งไม่ตรง | เปิด GPS, ให้สิทธิ์ Precision หากต้องการ, ตรวจ coordinate scheme/region ตามข้อกำหนด AMAP |

## โครงสร้าง

- `app/src/main/java/com/navith/amap/MainActivity.java` — Map, GPS, Search, Routing, privacy
- `app/src/main/res/layout/activity_main.xml` — UI ภาษาไทย
- `app/src/main/AndroidManifest.xml` — permissions และ API key placeholder
- `app/build.gradle` — Android build และอ้างอิง AMAP SDK จาก `app/libs/`
- `app/libs/amap-native-android.zip` — ไฟล์ Native ARM64/ARMv7 (Gradle แตกเข้า build/generated/ ก่อน package โดยอัตโนมัติ)
- `reference/` — ตาราง adcode ที่แนบมา (ข้อมูลอ้างอิงเท่านั้น ยังไม่เชื่อมกับการค้นหาในแอป)
- `docs/NaviTH-AMAP-Demo.html` — ต้นแบบ UI ที่กดทดลองในเบราว์เซอร์ได้ (ไม่ใช้ API/GPS จริง)
- `.github/workflows/build-android.yml` — CI สร้าง Debug APK จากมือถือผ่าน GitHub
- `PRIVACY.md` — ข้อมูล privacy สำหรับต้นแบบ (ต้องปรับก่อนเผยแพร่จริง)

## แหล่งอ้างอิง

- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-project/android-studio-create-project
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-project/get-key
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-project/dev-attention
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/map-data/poi
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/route-plan/drive
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/route-plan/walk


## เพิ่มเติมจากไฟล์ที่อัปโหลดล่าสุด

- นำ `AMap3DMap_11.2.1001_AMapSearch_9.8.1_AMapLocation_11.2.1001_20260805.jar` และ `.so` ทั้งสองสถาปัตยกรรมมาวางในโปรเจกต์แล้ว
- ตัด dependency AMAP บน Maven เดิมออกเพื่อไม่ให้คลาสซ้ำเมื่อ Build
- บันทึกไฟล์ต้นแบบ HTML เป็น `docs/NaviTH-AMAP-Demo.html`
- เก็บชุดตาราง adcode ใน `reference/world-admin-adcode-original.zip` โดย **ไม่** ใส่ไว้ใน APK และยัง **ไม่** นำไปใช้ค้นหาสถานที่อัตโนมัติ
- SDK เป็นไลบรารีที่มีลิขสิทธิ์ของผู้ให้บริการ ตรวจสอบสิทธิ์การแจกจ่ายก่อนเผยแพร่ repository สาธารณะ (ใช้ Private ระหว่างทดสอบ)
- ตรวจสอบชื่อแพ็กเกจ `com.navith.amap` และ SHA1 ให้ตรงกับ API Key ใน Console; ฟังก์ชันจริงอาจขึ้นกับพื้นที่บริการ/สิทธิ์บัญชี

### เช็กว่าใช้ local SDK จริง

`app/build.gradle` ต้องใช้ `implementation files('libs/amap-combined-20260805.jar')` และ **ไม่** ใช้ dependency `com.amap.api:3dmap-location-search` ควบคู่กัน


## Android Auto ใหม่ใน v0.3 (ยังเป็น Preview)

- `app/src/main/java/com/navith/amap/car/NaviCarAppService.java`: ลงทะเบียนแอปประเภท Navigation กับ Android Auto host พร้อมตรวจผู้เชื่อมต่อ
- `NaviCarScreen.java`: `MapWithContentTemplate` สำหรับ Car API 7+ และ `NavigationTemplate` สำหรับโฮสต์รุ่นเก่า, map SurfaceCallback, VirtualDisplay/Presentation + AMAP MapView, ดูเส้นทางจากรถยนต์
- `NaviSearchScreen.java`: ค้นหา AMAP POI ด้วย SearchTemplate ของ Android Auto เลือกปลายทางจากจอรถ
- `TripStore.java`: แชร์ปลายทาง ตำแหน่งล่าสุดและ polyline ที่คำนวณจาก AMAP ระหว่างมือถือกับจอรถ
- `app/src/main/res/xml/automotive_app_desc.xml`: ประกาศความสามารถ template และเพิ่ม `CarAppService` ใน Manifest
- AndroidX Car App Library **1.7.0 (Stable)** และ `app-projected:1.7.0` (รองรับ Android Auto แบบเชื่อมมือถือเท่านั้น)
- AMAP native libraries ถูกบีบอัดใน `app/libs/amap-native-android.zip` เพื่อแยกไฟล์ได้ขนาดต่ำกว่า 25MB; Gradle แตกไปที่ `app/build/generated/amapNative` ก่อน package

### ขอบเขตและความจริงเกี่ยวกับฟีเจอร์

| ความสามารถ | สถานะ v0.3 |
|---|---|
| ลงทะเบียนและค้นพบแอปบน Android Auto | เขียน Service/Manifest แล้ว แต่ต้องทดสอบ host จริงและติดตั้งจากช่องทางที่เชื่อถือได้ |
| ค้นหาสถานที่จากจอรถ | เขียน SearchTemplate + AMAP POI แล้ว ต้องทดสอบจริง |
| แผนที่ AMAP บนจอรถ | เขียน SurfaceCallback + VirtualDisplay/MapView แล้ว **การ render AMAP บน VirtualDisplay ยังไม่ยืนยันว่ารองรับ** |
| ดูและคำนวณเส้นทางรถยนต์ | เขียนโดยใช้ AMAP Search Route SDK, แสดง preview **ไม่ใช่ turn-by-turn** |
| Share จุดหมายจากมือถือไปจอรถและกลับกัน | เขียน SharedPreferences listener แล้ว ต้องทดสอบ lifecycle |
| GPS อัปเดตขณะจอรถเชื่อมต่อ | เขียน LocationManager foreground listener แล้ว ต้องทดสอบสิทธิ์ขณะใช้ Android Auto |
| เสียงนำทาง, rerouting, ETA สด, speed-limit, lane guidance, tollgate, 3D intersections | **ยังไม่ได้ทำ**; ต้องใช้ AMAP Navigation SDK หรือ navigation engine อื่น + data feed/การทดสอบจริง |
| Android Automotive OS ติดตั้งในรถแบบไม่มีมือถือ | **ยังไม่ใช่แพ็กเกจ AAOS** (ต้องทำ application module แยก) |
| Android Auto รถทุกรุ่น | **ไม่รับประกัน**; โฮสต์กำหนดข้อจำกัดจอ/การเชื่อมต่อ/ระดับ API |

**ข้อจำกัด AMAP SDK:** ไฟล์ที่คุณแนบมี Map/Search/Location เท่านั้น ไม่พบไฟล์ Navigation SDK. จึงไม่มีข้อมูลการเลี้ยว เลน หรือภาพทางด่วนละเอียดแบบภาพตัวอย่าง ห้ามเดาคำสั่งเลี้ยวแล้วแสดงเหมือนเป็นระบบนำทางจริง

**ก่อนใช้ในรถ:** เข้าแอป NaviTH บนมือถือ ยินยอมความเป็นส่วนตัวและให้สิทธิ์ GPS ก่อน แล้วติดตั้งรุ่นทดสอบจาก **Google Play Internal Testing / Internal App Sharing** (แอป Car App Library ที่ sideload ผ่าน APK โดยทั่วไปจะไม่ปรากฏบนจอรถตามข้อกำหนด Trusted Source). ทดสอบด้วย Desktop Head Unit และบนรถจริง โดยห้ามนำเวอร์ชัน Preview นี้ใช้เป็นระบบนำทางหลักระหว่างขับรถ

**หมายเหตุ Build:** คีย์ AMAP ใส่ผ่าน `AMAP_KEY` GitHub Secret เช่นเดิม, ไม่ฝังใน ZIP. ไม่มี Gradle wrapper ให้ใช้ GitHub Actions. ABI support จำกัด arm64-v8a และ armeabi-v7a. การ Build ยังไม่ได้ทดลองในสภาพแวดล้อมนี้เพราะไม่มี Android SDK/Gradle

### เอกสารสำหรับ Android Auto

- https://developer.android.com/training/cars/apps/auto
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/training/cars/apps/library/draw-maps
- https://developer.android.com/jetpack/androidx/releases/car-app
- https://developer.android.com/training/cars/testing
- https://developer.android.com/training/cars/distribute
