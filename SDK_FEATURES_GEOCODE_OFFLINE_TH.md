# NaviTH — AMAP Map/Search/Location/Offline Integration

วันที่ตรวจ 4 พ.ย. 2026

## สิ่งที่เชื่อมต่อแล้ว

1. **MapView** — `activity_main.xml` ใช้ `com.amap.api.maps.MapView` และ `MainActivity` เรียก `onCreate`, `getMap`, `onResume`, `onPause`, `onSaveInstanceState`, `onDestroy`
2. **ประเภทแผนที่** — มาตรฐาน / กลางคืน / ดาวเทียม และตัวเลือกธีมอัตโนมัติผ่าน Settings
3. **Marker / Polyline** — จุดหมาย, หมุดตำแหน่งและเส้นทางตัวอย่าง, POI ผลค้นหาพร้อมการจัดกลุ่ม
4. **ตำแหน่งปัจจุบัน** — ใช้ AMap Location SDK + Android `LocationManager` fallback, แสดงวงกลมความคลาดเคลื่อนและสถานะ; **ไม่เปิด MyLocationStyle ซ้ำ** เพราะอาจให้สองระบบตำแหน่งและหมุดตำแหน่งที่แย่งกัน
5. **POI Search** — ค้นหาด้วย Keyword แบบ Async และแสดงผลในหน้าค้นหาแยก
6. **Geocode** — ถ้า Keyword POI ไม่มีผล ค้นที่อยู่แบบมีโครงสร้างด้วย `GeocodeSearch.getFromLocationNameAsyn`, ส่งกลับผลแยกจาก POI และให้ผู้ใช้ตรวจสอบพิกัด
7. **Reverse Geocode** — เมื่อกดค้างบนแผนที่ หรือกด `ดูที่อยู่จากพิกัด` ในการ์ดเส้นทางที่ขยาย จะเรียก `GeocodeSearch.getFromLocationAsyn`; แสดงที่อยู่ที่ผู้ให้บริการคืนมา ไม่สร้างที่อยู่ขึ้นเอง
8. **Offline Maps (ทดลอง)** — Settings > แผนที่ออฟไลน์ > เมืองที่ดาวน์โหลดได้ ดึง **รายการเมืองจาก AMAP** ผ่าน `OfflineMapManager`, เลือกดาวน์โหลดเมืองที่ปรากฏ, Pause, Stop, และลบเมืองที่ติดตั้งแล้ว หลังได้รับ callback ตรวจสอบข้อมูล

## ข้อจำกัดการนำไปใช้จริง

- แผนที่ Offline **ไม่ใช่** Offline Navigation (ไม่มีคำนวณเส้นทางออฟไลน์และเสียงนำทางเมื่อไม่ต่อเน็ต)
- ไม่กำหนดรายชื่อเมืองหรือประเทศด้วยตนเอง; ถ้าเซิร์ฟเวอร์/สิทธิ์ของ API Key ไม่ส่งรายการที่ดาวน์โหลดได้ UI จะแสดงว่าไม่พร้อม ไม่ได้แอบใช้ข้อมูลเดา
- Maps SDK 3D รองรับ Offline Map ใน API แต่เอกสาร AMAP ประเทศจีนอัปเดต 24 ก.ย. 2026 ระบุ Offline Map สำหรับรุ่น 11.0+ เป็นบริการขั้นสูง ต้องขอสิทธิ์บริการ ทั้งนี้ยังต้องทดสอบสิทธิ์ใน **AMAP Global Console** และการมีแพ็กประเทศไทย
- การเปลี่ยน `MyLocationStyle` ไปใช้เอนจินใน Map โดยตรงต้องประเมินการซ้ำกับ `FastLocationProvider` และเส้นทาง Android Auto ก่อน
- Geocoding และ Reverse Geocoding อาจไม่คืนที่อยู่สำหรับบางประเทศ/รหัส API หรือข้อมูลไม่ครบ และอาจต้องได้รับสิทธิ์ที่แยกสำหรับบริการ Overseas
- Android Auto เวอร์ชันนี้ยังเป็นแผนที่และ Route Preview ไม่ใช่ Navigation Template เต็มรูปแบบของการนำทางจริง
- `AMAP_KEY` อ่านจาก GitHub Actions Secret หรือ local.properties ไม่ฝังคีย์ใน Repo

## การตรวจรับ

1. Build Debug APK ใน GitHub Actions แล้วดูว่าผ่าน Java compile
2. บนมือถือจริง: ค้นหาที่อยู่เช่นชื่อถนน/บ้านเลขที่โดยไม่พบ POI → ดู Address match
3. แตะค้างพิกัด → เปิด Route Card Expanded → ดูข้อความที่อยู่จริงหรือเหตุผลที่ไม่มีข้อมูล
4. ไป Settings → แผนที่ออฟไลน์ → ดูรายการเมืองที่ AMAP อนุญาต หากไม่มีรายการไม่สามารถดาวน์โหลดได้
5. ทดสอบ Offline Map หลังดาวน์โหลดสำเร็จด้วยการปิดอินเทอร์เน็ต เพื่อยืนยันว่าสามารถแสดง Tiles และไม่มีการแอบคำนวณเส้นทางออฟไลน์
6. ตรวจ Android Auto ผ่าน Desktop Head Unit หรือจอรถที่รองรับก่อนใช้งานขับจริง

## อ้างอิง
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-map/show-map
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/map-data/geo
- https://mapsplatform.opnavi.com/api/maps-sdk-for-android/guide/create-map/offline-map
- https://lbs.amap.com/api/android-sdk/guide/create-map/offline-map
