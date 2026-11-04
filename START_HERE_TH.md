# NaviTH – วิธีสร้าง GitHub Repository และ APK ผ่านมือถือ

**สถานะ:** ZIP นี้เป็น SOURCE CODE เท่านั้น ยังไม่ได้คอมไพล์ APK หรือทดสอบการทำงานบนรถจริง

## วิธีใช้ด้วย Termux (สร้าง Repository + เริ่ม Build โดยอัตโนมัติ)

1. ติดตั้ง Termux (รุ่นที่ยังได้รับอัปเดต) แล้วเปิดแอป
2. รัน `pkg update -y && pkg install -y git gh unzip`
3. รัน `termux-setup-storage` และอนุญาตการเข้าถึงไฟล์
4. ดาวน์โหลด `NaviTH-AMAP-RepoReady.zip` ลง Download จากนั้นรัน:

```bash
mkdir -p ~/NaviTH-AMAP && cd ~/NaviTH-AMAP
unzip -o ~/storage/downloads/NaviTH-AMAP-RepoReady.zip
bash termux-create-and-build.sh
```

5. สคริปต์จะเปิด GitHub ให้ล็อกอินเอง ตรวจชื่อบัญชี `Natreethon` ถามให้ใส่ AMAP API Key (ไม่แสดงบนหน้าจอ), สร้าง `Natreethon/NaviTH-AMAP` แบบ **Private**, ตั้งค่า GitHub Actions Secret ชื่อ `AMAP_KEY` แล้ว Push โค้ดเพื่อเริ่ม Build
6. เปิด `https://github.com/Natreethon/NaviTH-AMAP/actions` ดูผล `Build NaviTH Android APK` ที่เริ่มจากการ push โค้ด แล้วดาวน์โหลด artifact `NaviTH-debug-APK` เฉพาะกรณี Build สำเร็จ

หาก Repository ชื่อนี้มีอยู่ก่อนแล้ว สคริปต์จะหยุดโดยไม่แก้ไฟล์/ประวัติเดิม หาก GitHub Actions เริ่มก่อนตั้งค่า secret ให้กด `Run workflow` อีกครั้งหลังตั้งค่าคีย์

**ความปลอดภัย:** ไม่เขียน API Key ลงใน source code หรือประวัติ Git สคริปต์ทำงานบนมือถือของผู้ใช้เอง ไม่มีการส่งข้อมูลล็อกอินให้ผู้ช่วย

## ทางเลือกแบบเว็บไซต์

1. เข้า `https://github.com/new` ล็อกอิน แล้วตั้ง Repository name เป็น `NaviTH-AMAP` เลือก Private
2. แตก ZIP แล้ว Upload ไฟล์/โฟลเดอร์ทั้งหมด (รวม `.github`) เข้า Repository โดยไม่ใส่โฟลเดอร์ชั้นนอกเพิ่ม
3. เข้า Settings > Secrets and variables > Actions สร้าง Secret `AMAP_KEY` แล้วใส่ AMAP Android API Key
4. เข้า Actions > Build NaviTH Android APK > Run workflow และดาวน์โหลด Artifact หลัง Build ผ่าน

**ข้อจำกัด**: v0.3 เป็น Android Auto map / route preview และยังไม่ใช่ระบบ turn-by-turn / lane-level 3D navigation พร้อมขับรถจริง AMAP MapView บน VirtualDisplay จำเป็นต้องทดสอบบน DHU หรือจอรถ และการติดตั้ง Debug APK ไม่ได้รับประกันว่าจะปรากฏใน Android Auto
