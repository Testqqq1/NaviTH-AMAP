# AMap-Web source workspace / คลังอ้างอิง AMap-Web

Repository ต้นทาง: https://github.com/AMap-Web

ตรวจรายการ ณ วันที่ 2026-11-04: **10 Repository สาธารณะ** — 9 มี Commit และเพิ่มเป็น Git Submodule แล้ว; อีก 1 เป็น Repository ว่างจึงบันทึกชื่อไว้แทน

## Install / ติดตั้งทุก Repository ที่มีโค้ด

จาก Root ของ NaviTH-AMAP:

```bash
bash tools/install-amap-web.sh
# equivalent:
git submodule update --init --recursive --jobs 4
```

Source Code จะอยู่ใน `third_party/amap-web/<repo>` และปักหมุด Commit ที่ตรวจพบในวันที่ติดตั้ง มีประวัติ/License จากต้นทางแยกไว้ เพื่อให้อัปเดตแบบตรวจสอบได้

**หมายเหตุ:** การ checkout Git Submodules ไม่ใช่การติดตั้ง npm packages และ **ไม่ทำให้ Android APK รองรับฟังก์ชันของ Web SDK อัตโนมัติ** GitHub Actions ของ NaviTH จะไม่ Build หรือนำ JavaScript/TypeScript เหล่านี้เข้าไปใน APK

| Repository | ประเภท | โอกาสใช้กับ NaviTH |
|---|---|---|
| [amap-skills](https://github.com/AMap-Web/amap-skills) | คู่มือ/AI skills สำหรับ JSAPI 2.0 | อ้างอิงการออกแบบและ Web prototype |
| [amap-lbs-skill](https://github.com/AMap-Web/amap-lbs-skill) | Node.js เรียก Web Service API, POI/Route | ใช้ใน tooling/เซิร์ฟเวอร์แยก เมื่อมี Web Service Key |
| [amap-screenshot](https://github.com/AMap-Web/amap-screenshot) | ปลั๊กอิน Screenshot JS | ใช้กับเว็บ ไม่ใช่ Android MapView |
| [layer-3dtiles](https://github.com/AMap-Web/layer-3dtiles) | ปลั๊กอิน 3D Tiles บน JSAPI | ใช้เว็บโดยพึ่ง @amap/three-layer |
| [amap-jsapi-types](https://github.com/AMap-Web/amap-jsapi-types) | TypeScript declarations สำหรับ JSAPI | Type hints สำหรับเว็บ ไม่ใช่ Android Java |
| [amap-three](https://github.com/AMap-Web/amap-three) | Three.js layer บน JSAPI | ตัวอย่าง 3D บนเว็บ ไม่สามารถลง Android Native โดยตรง |
| [amap-map-agent-skills](https://github.com/AMap-Web/amap-map-agent-skills) | AI agent skills | ใช้กับ coding agent, ไม่ใช่ APK |
| [RealMapBench](https://github.com/AMap-Web/RealMapBench) | Benchmark ประเมิน map agents | ทดสอบ AI agent ไม่ใช่ library นำทาง |
| [amap-loca-types](https://github.com/AMap-Web/amap-loca-types) | TypeScript definitions สำหรับ Loca 2.0 | สำหรับเว็บ/ข้อมูลเชิงพื้นที่ |
| [amap-loca-types-](https://github.com/AMap-Web/amap-loca-types-) | **Repository ว่าง** | ไม่มี Commit ให้ทำเป็น Submodule (ยังไม่สามารถติดตั้ง Source) |

## ดาวน์โหลดทุก Source จาก GitHub Actions

ไปที่ `Actions → Prepare AMap-Web source workspace` จากนั้นเปิด Artifact `AMap-Web-repositories-snapshot` ซึ่งเป็น ZIP/TAR snapshot ของ 9 Repo ใน `third_party/amap-web` พร้อมรายงาน pin SHA ไว้แต่ละรายการ การดาวน์โหลดนี้เป็นเพื่อ **อ้างอิงและพัฒนาในแยกพื้นที่เท่านั้น** ไม่รันโค้ดภายนอก

## ความปลอดภัย / Compatibility

- AMap-Web เป็น repo โค้ดเว็บ/AI skills **ไม่ใช่ AMAP Android Navigation SDK**; Android ใช้ชุด AMAP Native SDK แยกต่างหากตาม `NAVIGATION_INTEGRATION_TH.md`.
- หากต้องการใช้งาน Web Service API จาก `amap-lbs-skill` ต้องมี Web Service Key แยกต่างหาก: ห้ามนำ Android Key ที่ผูก SHA-1 มาใช้แทนหรือบันทึกใน Repository
- ตรวจ LICENSE ของแต่ละต้นทางก่อนแก้ไข/แจกจ่าย โดย CI จะเก็บลิงก์ต้นทางและ SHA ไว้
- Submodule pinned commit; ไม่อัปเดตเองโดยไม่ตรวจสอบ เพื่อหลีกเลี่ยง supply-chain drift
- Repository ที่ว่างยังไม่ถูกแทนที่ด้วย dummy code
