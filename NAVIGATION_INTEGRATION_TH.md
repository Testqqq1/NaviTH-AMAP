# NaviTH Navigation SDK — Experiment / เอกสารการทดสอบ

อัปเดต 4 พฤศจิกายน 2026

## สอง Build แยกกันโดยตั้งใจ

| Workflow | ผลลัพธ์ | วัตถุประสงค์ |
|---|---|---|
| `.github/workflows/build-android.yml` | NaviTH-debug-APK | Maps/Search/Location เดิม — เก็บเป็นทางสำรอง |
| `.github/workflows/build-amap-navigation.yml` | NaviTH-Navigation-Test-APK | ทดลอง Navigation SDK Global 11.2.1001 รวม Map/Navi/Search/Location |

### แหล่ง SDK อย่างเป็นทางการ
https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/download

- Official Global package `AMap3DMap_AMapNavi_AMapSearch_AMapLocation.zip`
- Map SDK 11.2.1001 + Navigation SDK 11.2.1001 + Search SDK 9.8.1 + Location SDK 11.2.1001
- Verification: Published MD5 `dedd330f7f3d916f2a9e95ad5b3cda4c`
- CI ดาวน์โหลดย้าย JAR ชุดรวมมาแทน `app/libs/amap-combined-20260805.jar` และแทน Native ZIP ทั้งชุดก่อน Build (โดยไม่ Commit ไฟล์ SDK ขนาดใหญ่เข้า Repository)
- **ไม่เพิ่ม Maps SDK ซ้อน** และไม่ใช้ JAR ใหม่ร่วมกับ Native เดิม

## การใช้งานบนมือถือ (เฉพาะ Navigation Test APK)
1. เปิด NaviTH ยินยอมการใช้งาน Maps/Search/Location ตามเดิม
2. อนุญาตให้แอปเข้าถึงตำแหน่งแบบแม่นยำ
3. ค้นหาสถานที่และเลือกจุดหมาย จากนั้นขยาย Route Card
4. กด `เริ่มนำทาง (ทดลอง)` ปุ่มนี้จะแสดงเมื่อ APK มี Navigation SDK
5. อ่านหน้าขอความยินยอมเพิ่มสำหรับการนำทาง ก่อนกดอนุญาต
6. แอปเปิด AMap Navigation Component ที่หน้าวางแผนเส้นทาง `ROUTE` โดยส่งปลายทางไปให้ SDK และใช้ตำแหน่งปัจจุบันเป็นจุดเริ่มต้น
7. ถ้า Navigation SDK ได้รับสิทธิ์และคำนวณได้ ผู้ใช้จึงเลือกนำทางจริงในหน้าของ SDK ได้เอง

## สิ่งที่ต้องตรวจให้ผ่านก่อนเรียกว่าพร้อมใช้
- **API Key / SHA1**: เช็กว่า AMAP Console เปิดใช้งาน Navigation SDK สำหรับ Key ที่ผูกกับ `com.navith.amap` และ DEMO signing SHA1 ด้วย
- **Coverage**: บริการทางถนน 3D / ทางด่วน / เลน / เสียงนำทาง ภาษาไทย ขึ้นกับพื้นที่จริงและบริการ AMAP Global; ยังไม่ได้พิสูจน์คุณภาพในประเทศไทย
- **JNI**: ทดสอบบน Android 64 บิต ทั้งตำแหน่ง ค้นหา และเปิด Navigation Component
- **Privacy**: ถ้าผู้ใช้ไม่ให้ความยินยอม ห้ามสร้าง Navigation SDK singleton และห้ามเริ่มบริการนำทาง
- **Drive safety**: ทดสอบที่หยุดรถหรือในสภาพแวดล้อมจำลองก่อน ไม่ควรใช้เป็นระบบนำทางหลักระหว่างขับจนกว่าจะผ่านการทดสอบจริง
- **Android Auto**: แอป Android Auto เดิมยังเป็น Map + Route Preview. Navigation Component ของมือถือ **ยังไม่เชื่อมข้อมูลเลี้ยวต่อเลี้ยวไปยัง Android Auto** การเขียน CarAppService แบบ NavigationTemplate และการส่งข้อมูล NaviInfo จำเป็นต้องทำต่างหาก
- **Simulation**: จุดหมายและเส้นทางต้องคำนวณสำเร็จก่อนเริ่ม `startNavi(2)`; ระบบจริงใช้ `startNavi(1)`; ไม่มีการสร้างคำสั่งเลี้ยวปลอม

## แนวทางเพิ่มฟีเจอร์ในระยะต่อไป (ยังไม่ได้ Implement)
- เปลี่ยนจาก Navigation Component ไป Custom Navigation Screen ที่ใช้ `AMapNavi`, `AMapNaviView`, `AMapNaviListener`, `onNaviInfoUpdate`
- แยก Engine จาก UI (มือถือ/Android Auto) พร้อมสถานะการนำทางและการยุติ session
- ความคืบหน้าใน Car App ผ่าน NavigationTemplate และ Surface ที่ถูกต้อง
- เสียงไทยแยกจาก TTS ของ SDK และทดสอบเสียงและภาษาที่แท้จริง
- โหมดจำลอง/นำทางจริง เลือกจากหน้า Dev Test โดยห้ามเริ่ม real GPS ขณะทดสอบแบบจำลอง

## แหล่งอ้างอิง
- https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navi-component/basic-functions
- https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navigation-map/gps-navi
- https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/create-project/configuration-considerations
- https://developer.amap.com/api/android-navi-sdk/download
