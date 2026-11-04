# NaviTH 1.0 (UI experiment): AMap-inspired Android navigation

**อัปเดต 5 พ.ย. 2026 — Draft / ทดลองเท่านั้น**

ปรับรูปแบบงานออกแบบและ interaction โดยอ้างอิงจาก **5 ภาพตัวอย่างที่ผู้ใช้ส่งให้** (หน้าแผนที่, ทั่วไป, การตั้งค่าภาพ, การตั้งค่าการนำทาง, เพิ่มเติม) โดยออกแบบสี ปุ่ม และตัวอักษรใหม่สำหรับ **NaviTH** ไม่คัดลอก asset, ไอคอนแอปหรือเครื่องหมายการค้า AMap.

## Screen mapping / สิ่งที่ลงโค้ดจริง

| หน้าตามภาพ | งานที่ทำ | สถานะ |
|---|---|---|
| Map home | แผนที่ AMAP เดิม, bottom search, quick travel modes, saved shortcuts, 5 navigation tabs, floating layers/GPS/zoom | UI wired |
| General | Search top/bottom, theme, keep screen on, 2D rotation lock, 3D tilt, marker image, indoor layer toggle | Settings wired where supported |
| Display | Auto/portrait/landscape, north-up/heading-up, auto/day/night, 2D/3D | UI + persisted prefs |
| Navigation | Car + unavailable vehicle modes, route priorities (recommended/shortest/fastest), avoid tolls/highways, traffic, marker selection, voice modes, units | Mapbox/AMAP applicable settings |
| Additional | Off-route warnings, battery-aware keep-awake, lane/AI traffic/Android Auto information | Useful switches implemented; missing providers explicitly unavailable |
| Explore/Search | Existing POI search, recents, favorites, home/work and voice search (existing) | Existing AMAP features preserved |
| Thailand routing | Mapbox Directions traffic/driving/walk/cycle, alternatives, preview + experimental GPS/TTS | Optional public token; not full SDK |
| Offline | Existing AMAP SDK offline maps activity | Dependent on region/provider |
| Android Auto | Existing Car App map-and-route preview | Not certified full active guidance |

## การกดปุ่ม
- หน้า Home: ค้นหา, รถยนต์, เดิน, จักรยาน (requires Mapbox), สำรวจ, ชั้นข้อมูล, รายการโปรด, ตั้งค่า
- Transit/Ride/Motorcycle/Truck/EV/Bus: มี dialog อธิบายว่าไม่มี provider; **ไม่ทำเส้นทางรถยนต์แอบอ้าง**
- การตั้งค่าจะถูกบันทึกไว้ใน `navith_view` และนำไปใช้ใน `MainActivity` หรือ Mapbox request เมื่อเกี่ยวข้อง
- เปลี่ยนไอคอนรถและไฟล์ภาพส่วนตัวได้จาก Settings; ใช้ bitmap ภายในโปรเจกต์ ไม่ดึงภาพจาก AMap ทางการ
- แผนที่ AMAP ยังคงใช้เพื่อ rendering/search; Mapbox Directions จะใช้ **เมื่อกำหนด `MAPBOX_PUBLIC_TOKEN`** และผู้ใช้ให้ consent ก่อน

## ข้อจำกัดสำคัญ (ห้ามอ้างว่าเสร็จแล้ว)
- Mapbox Navigation SDK v3 ยังไม่ได้เชื่อม; ตัวอย่างเสียงใช้ Android TTS และ GPS ระดับเบื้องต้น ยังไม่มี map matching, background navigation, lane guidance จริง
- Android Auto ยังเป็น route preview, ไม่มีเสียงเลี้ยวจริง; ต้องใช้ SDK + DHU + head unit testing
- สถานที่/จราจร/อาคาร/แผนที่ภายใน/ออฟไลน์ขึ้นกับข้อมูลที่ AMAP มีให้ในไทย
- รถบรรทุก, EV, bus, transit, motorcycle, ride-hailing และ AI traffic light: ยังไม่มี backend รองรับ
- หน้าตาได้รับการแมปจากภาพ แต่ **ยังไม่ได้ทำ visual regression test บน Emulator/อุปกรณ์จริง**
- การแสดงผลเส้นทาง Mapbox บน AMAP basemap เป็น **ต้นแบบ** ต้องตรวจสอบเงื่อนไขสิทธิ์/attribution ของผู้ให้บริการทั้งสองก่อนใช้งานเชิงพาณิชย์
- Build สำเร็จยังไม่ได้แปลว่าผ่านการทดสอบนำทางในประเทศไทย

## Checklist ก่อนปล่อยใช้จริง
1. Build Debug APK ผ่าน GitHub Actions (Java 17 / Android SDK 35)
2. ใส่ `MAPBOX_PUBLIC_TOKEN` เป็น GitHub Actions Secret; AMAP_KEY ยังต้องมี
3. ตรวจขนาดจอเล็ก/ใหญ่, gesture tap targets, กลางคืน/กลางวัน, orientation auto/portrait/landscape
4. ทดสอบ navigation flow ด้วย simulated location (ไม่ทดลองขับเสี่ยงอันตราย)
5. ทดสอบ permission deny, offline, timeout, failed token, unsupported region
6. ตรวจ SHA1 กับ AMAP Key และ update privacy policy สำหรับ Mapbox
7. ตรวจ Mapbox API attribution/terms และ AMAP SDK map-term compatibility
8. Mapbox Navigation SDK v3 + Android Auto SDK ต้องเชื่อมแยก ก่อนนำไปใช้นำทางจริง

Official references:
- https://docs.mapbox.com/api/navigation/directions/
- https://docs.mapbox.com/android/navigation/guides/
- https://docs.mapbox.com/android/navigation/guides/androidauto/
