# NaviTH — เป้าหมายรองรับ Android Auto แบบครบระบบ

สถานะ ณ 4 พฤศจิกายน 2026: v0.3 เป็น Android Auto **โครงสร้างและ route preview สำหรับทดสอบ** ไม่ใช่แอปนำทางขณะขับที่พร้อมใช้งานจริง

## สิ่งที่ทำในโค้ดนี้

- Android Auto `CarAppService` category NAVIGATION + metadata template
- Host validation เคร่งครัดใน release / เปิดสำหรับเครื่องทดสอบเฉพาะ debug
- AndroidX Car App Library 1.7.0 (stable): NavigationTemplate fallback / MapWithContentTemplate สำหรับโฮสต์ API 7+
- AMAP MapView พยายาม render ลง Surface ผ่าน VirtualDisplay + Presentation (ยังต้องพิสูจน์จริงว่า SDK รองรับ)
- SearchTemplate และ POI search บนจอรถ
- Route preview จาก AMAP Search SDK ไม่ใช่ Turn-by-turn
- Share ข้อมูล GPS/จุดหมาย/เส้นทางระหว่างมือถือและจอรถ
- Android GPS foreground updates โดยไม่ขอสิทธิ์ตำแหน่งเบื้องหลัง
- ARM64 + ARMv7 และ Build ผ่าน GitHub Actions ด้วย key จาก Secret

## สิ่งที่ต้องเพิ่มก่อนเรียกว่ารองรับทั้งหมด

1. ต้องได้รับ AMAP Navigation SDK **สำหรับ Android และ region ประเทศไทย** ที่มีสิทธิ์ใช้งานจริง (Map/Search/Location zip ที่ได้รับยังไม่มี) และเลือก dependencies ไม่ให้ชน Map SDK เดิม
2. เขียน navigation engine lifecycle (start/stop/pause/reroute/background/arrived) และต่อ GPS real-time / map matching
3. นำข้อมูลจริงจาก navigation engine มาแปลงเป็น `RoutingInfo`, `Step`, `Maneuver`, `TravelEstimate`, lane info และ alerts; **ห้ามเดาคำสั่งเลี้ยวหรือสร้างข้อมูลเลนเอง**
4. ฝั่งโทรศัพท์ทำหน้าจอเริ่ม/หยุดนำทาง, เสียง TTS ภาษาไทยที่ปรับระดับได้ และ audio focus ที่ถูกต้อง; ต้องซิงค์คำสั่งจาก Android Auto
5. Render นำทางแบบ 3D: ทดสอบ map native render บน VirtualDisplay; หาก AMAP ไม่รองรับ ให้ทำ renderer compatible ใหม่โดยได้รับสิทธิ์และ data feed ที่ถูกต้อง
6. เมื่อ AMAP มีข้อมูลจริง ค่อยเพิ่มทางแยกเสมือนจริง ภาพ toll booth, ป้ายทางออก, การบอกเลน และการแสดงจราจรตามเวลาจริง (ไม่รับประกันครบทุกประเทศ/ทุกถนน)
7. รองรับ voice intents แบบ `geo:` และ `google.navigation:` + Google Assistant/Gemini integration ตามคุณสมบัติจริง, รวมถึง share destination จากแอปอื่น
8. ทดสอบ DHU ขนาด 800x480, 1280x720, ultrawide, day/night, rotary/touch, split screen, DPI ต่างกัน; ใช้ Surface stable/visible-area และ fallback โฮสต์ API ต่ำ
9. ทดสอบ Android Auto ผ่าน USB และ Wireless (เป็นการเชื่อมต่อที่ระบบ/รถจัดการ ไม่ใช่ฟีเจอร์พิเศษของแอป)
10. หากต้องการ Android Automotive OS (แอปลงในรถโดยไม่ใช้มือถือ) ต้องทำ module แยก app-automotive, CarAppActivity, packaging และทดสอบตามนโยบาย Google Play
11. อาจเพิ่ม Cluster Instrument Panel เฉพาะรถที่รองรับ Car App API 6+; ไม่สามารถรับประกันทุกคัน
12. ระบบ privacy, mapping license, crash testing, safety, battery use, foreground service, trusted host and Play Console car app review ก่อนเผยแพร่จริง

## เงื่อนไขเผยแพร่ / การทดสอบ

- ทดสอบ Android Auto บนรถจริงด้วย Google Play Internal Testing หรือ Internal App Sharing; การติดตั้ง APK ผ่านไฟล์โดยตรงและเปิด Unknown Sources ไม่พอสำหรับ Car App Library apps ตามเอกสารของ Google
- Android Auto ไม่ใช่ Android Automotive OS; โปรเจกต์ v0.3 รองรับฝั่ง projected only
- ชุดทดสอบนี้ยังไม่ได้ Build ด้วย Android SDK ในสภาพแวดล้อมนี้ จึงอาจต้องแก้บั๊ก compile/runtime หลัง Build ครั้งแรก

อ้างอิง: https://developer.android.com/training/cars/apps/navigation
https://developer.android.com/training/cars/testing
https://developer.android.com/training/cars/distribute
