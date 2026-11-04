# NaviTH — ทดลองเส้นทางในประเทศไทยด้วย Mapbox

**สถานะ: Prototype / ยังไม่ผ่านการทดสอบในรถยนต์** (5 พฤศจิกายน 2026)

## เป้าหมาย
- เก็บ AMAP map / POI / icon / camera / Android Auto route-preview เดิมไว้
- ใช้ Mapbox Directions API v5 เพื่อสร้างเส้นทางในประเทศไทยเมื่อผู้พัฒนาตั้งค่า public access token
- รองรับ driving-traffic, walking, เส้นทางสำรอง, ระยะทาง/ETA และรายการ maneuver ภาษาไทย
- คำสั่งเลี้ยว + TTS จาก Android + GPS ในหน้าจอมือถือ *เฉพาะ foreground* และทดลอง rerouting เมื่อ GPS หลุดจากแนวเส้นทางหลายครั้ง
- ไม่มีการใช้ AMAP Navigation SDK เมื่อเลือกเส้นทาง Mapbox ดังนั้นไม่ต้องพึ่งสิทธิ์นำทางภายในประเทศจีน

## ตั้งค่า
1. สมัคร/เข้าสู่ระบบที่ https://account.mapbox.com/access-tokens/
2. คัดลอก **public** token ที่ขึ้นต้นด้วย `pk.`; *ห้ามใช้ `sk.`* และห้ามใส่ในไฟล์ GitHub ที่ commit
3. GitHub Repo Settings → Secrets and variables → Actions → New repository secret:
   - Name: `MAPBOX_PUBLIC_TOKEN`
   - Value: Mapbox public token
4. เก็บ `AMAP_KEY` เดิมไว้ เพื่อให้หน้าตาและการค้นหา AMAP ยังทำงาน
5. สำหรับ Android Studio: ตั้งค่า `MAPBOX_PUBLIC_TOKEN=pk....` ใน `local.properties` ที่ไม่ถูกติดตามด้วย Git
6. Build `Build NaviTH Android APK` เป็นรุ่นทดสอบ และติดตั้งบนโทรศัพท์ของผู้พัฒนา

**หมายเหตุ**: Public token จะถูกฝังใน APK และถูกอ่านได้โดยผู้ใช้; จำกัดสิทธิ์/ตรวจสอบปริมาณใช้งานและค่าใช้จ่ายกับบัญชี Mapbox ของคุณตามแนวทางผู้ให้บริการ ไม่มีการสร้างบัญชี Mapbox หรือเติม Token ให้อัตโนมัติ

## การใช้งานทดลอง
1. เปิดแผนที่ AMAP และยอมรับ consent
2. อนุญาตตำแหน่งที่ตั้งแบบแม่นยำ
3. ค้นหาและเลือกจุดหมาย → ดูเส้นทาง
4. ยอมรับ Mapbox consent ก่อนส่งพิกัดไปยัง Mapbox
5. เมื่อ route สำเร็จ สามารถเลือกเส้นทางสำรองได้
6. ปุ่ม **เริ่มนำทาง** เปิดคำเตือนก่อนเริ่ม *foreground experimental guidance*; ระบบจะหยุดเมื่อแอปเปลี่ยนไปอยู่เบื้องหลัง
7. ทดสอบด้วย mock location หรือขณะจอดรถ และตรวจสอบคำเลี้ยว ระยะทาง และการ re-route อย่างรอบคอบ

## ข้อจำกัดที่ต้องแก้ก่อน Production
- **ไม่ใช่ Mapbox Navigation SDK**: เป็น REST Directions + prototype guidance เท่านั้น ไม่ได้ใช้ Map Matching / Mapbox route progress / lane-level assistance / background foreground-service tracking
- ไม่รับประกันความแม่นยำขณะขับรถจริง; ไม่ควรใช้แทนระบบนำทางที่ผ่านการทดสอบ
- Android Auto ยังคงแสดงแผนที่และเส้นทางจาก TripStore; **ยังไม่ส่ง turn-by-turn guidance ของ Mapbox ให้ Android Auto**
- หากไม่ได้ตั้ง `MAPBOX_PUBLIC_TOKEN` จะยังใช้ AMAP RouteSearch เดิม ซึ่งอาจมีข้อจำกัดนอกประเทศจีน
- ชื่อสถานที่และผลค้นหาเดิมมาจาก AMAP; ถ้าผลค้นหาในไทยไม่ครบ ต้องมี global search provider เพิ่มต่างหาก
- ต้องตรวจสอบค่าบริการ, เงื่อนไข API, attribution และ privacy policy ก่อนปล่อยใช้จริง

## ขั้นถัดไปสำหรับระบบนำทางเต็มรูปแบบ
- Mapbox Navigation SDK v3: requires Mapbox public token **and Maven download secret token**; แยก navigation screen/engine ออกจาก AMAP map เพื่อลด class conflicts
- เชื่อม Car App NavigationTemplate ด้วยคำเลี้ยวที่ได้จาก SDK จริง พร้อมตรวจ DHU/จอรถ
- บริการ foreground location และเสียงขณะจอดับตามนโยบาย Android
- ทดสอบตำแหน่งจริง, tunnels, GPS drift, wrong way, reroute, Android Auto และภาษาไทยบนอุปกรณ์

## อ้างอิง
- https://docs.mapbox.com/api/navigation/directions/
- https://docs.mapbox.com/android/navigation/guides/install/
- https://docs.mapbox.com/help/dive-deeper/attribution/
