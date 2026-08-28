# SMS Verification Gateway

Backend **Java Spring Boot** untuk memverifikasi kepemilikan nomor handphone dengan pola **reverse SMS verification**.
Semua data disimpan **in-memory** selama service hidup dan akan hilang saat service restart.

1. Sistem membuat kode unik yang berelasi dengan nomor pengguna.
2. Pengguna mengirim SMS `VERIF <KODE>` ke SIM yang terpasang pada Android gateway.
3. Aplikasi Android **Incoming SMS to URL Forwarder** meneruskan SMS ke webhook backend.
4. Backend mencocokkan nomor pengirim dan kode unik.
5. Frontend atau backend-for-frontend melakukan polling status sampai `VERIFIED` atau `EXPIRED`.

Proyek ini tidak membutuhkan langganan SMS provider maupun PostgreSQL. Pengguna mengirim SMS ke nomor SIM milik sistem. Infrastruktur yang dibutuhkan adalah Android, SIM aktif, internet pada Android, aplikasi SMS Forwarder, dan backend ini.

## Fitur

- Generate kode verifikasi acak dengan alfabet tanpa karakter ambigu.
- Normalisasi nomor Indonesia ke format E.164, misalnya `0812...` menjadi `+62812...`.
- Webhook inbound SMS yang kompatibel dengan `bogkonstantin/android_income_sms_gateway_webhook`.
- Pencocokan wajib berdasarkan **kode dan nomor pengirim**.
- API status untuk polling.
- API daftar maksimum 50 SMS yang berelasi dengan satu sesi verifikasi.
- HMAC-SHA-256 untuk seluruh request.
- Timestamp dan nonce untuk mencegah replay pada API aplikasi.
- Idempotensi webhook Android untuk menangani retry payload yang sama.
- Penyimpanan in-memory, Docker, Docker Compose, Render Blueprint, dan GitHub Actions.
- Unit test, Spring integration test, dan smoke test Docker.

## Arsitektur

```text
Frontend / BFF
   │
   │ POST /api/v1/verifications
   │ GET  /api/v1/verifications/{id}/status
   │ GET  /api/v1/verifications/{id}/sms
   │ GET  /api/v1/debug/storage
   │ X-Timestamp + X-Nonce + X-Signature
   ▼
Spring Boot API + In-Memory Storage
   ▲
   │ POST /internal/sms/incoming
   │ X-Signature atas raw JSON body
   │
Android SMS Forwarder + SIM
   ▲
   │ SMS: VERIF ABCD2345
   │
Pengguna
```

## Stack

- Java 21
- Spring Boot 3.5.16
- Spring Web MVC
- Maven 3.9.16
- Docker / Docker Compose
- GitHub Actions
- Render Blueprint dan Deploy Hook

## Struktur proyek

```text
src/main/java/com/smsverification/gateway/
├── api/             Penanganan error API
├── config/          Configuration properties
├── core/            HMAC, normalisasi nomor, generator/parser kode
├── debug/           Endpoint debug untuk melihat state storage
├── security/        Filter HMAC, nonce, replay protection
├── sms/             Webhook dan pembacaan SMS masuk
├── storage/         Penyimpanan in-memory
└── verification/    Generate kode dan check status

src/main/resources/
└── application.yml

scripts/
├── hmac-request.js  Client API bertanda tangan
└── smoke_test.py    Uji alur end-to-end
```

---

# API

Semua endpoint membutuhkan header HMAC. Skema HMAC untuk webhook Android berbeda dari API aplikasi karena harus kompatibel dengan aplikasi SMS Forwarder.

## 1. Generate kode unik

```http
POST /api/v1/verifications
Content-Type: application/json
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-value>
X-Signature: <hmac-hex>
```

Request:

```json
{
  "phoneNumber": "0812 3456 7890"
}
```

Response `201 Created`:

```json
{
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "phoneNumber": "+6281234567890",
  "code": "K7P4M9QX",
  "smsText": "VERIF K7P4M9QX",
  "destinationNumber": "081100009999",
  "status": "PENDING",
  "expiresAt": "2026-08-28T08:10:00Z"
}
```

`code` hanya dikembalikan ketika sesi dibuat. Service menyimpan hash SHA-256 kode, bukan kode plaintext.

Membuat sesi baru untuk nomor yang sama akan mengubah sesi `PENDING` sebelumnya menjadi `EXPIRED`.

## 2. Webhook SMS masuk

Endpoint ini diisi pada aplikasi Android SMS Forwarder:

```http
POST /internal/sms/incoming
Content-Type: application/json
X-Signature: <HMAC-SHA-256 dari raw JSON body>
```

Payload:

```json
{
  "from": "+6281234567890",
  "text": "VERIF K7P4M9QX",
  "sentStamp": 1787904000000,
  "receivedStamp": 1787904001000,
  "sim": "SIM1"
}
```

Response:

```json
{
  "smsId": "c6b093bd-f833-437c-82e8-ffec2a2ed540",
  "duplicate": false,
  "matchStatus": "MATCHED",
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "verificationStatus": "VERIFIED"
}
```

Nilai `matchStatus`:

| Status | Arti |
|---|---|
| `MATCHED` | Kode valid, nomor pengirim cocok, sesi menjadi `VERIFIED` |
| `NO_VERIFICATION_CODE` | Format SMS bukan `VERIF <KODE>` |
| `UNKNOWN_CODE` | Kode tidak ditemukan |
| `PHONE_MISMATCH` | Kode ada tetapi nomor pengirim berbeda |
| `EXPIRED_CODE` | Sesi kode sudah kedaluwarsa |
| `ALREADY_VERIFIED` | Kode tersebut sebelumnya sudah berhasil diverifikasi |
| `INVALID_SENDER` | Nomor pengirim tidak dapat dinormalisasi |

Retry payload Android yang identik menghasilkan `duplicate: true` dan tidak mengubah status untuk kedua kalinya.

## 3. Check status

```http
GET /api/v1/verifications/{verificationId}/status
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-value>
X-Signature: <hmac-hex>
```

Response:

```json
{
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "phoneNumber": "+6281234567890",
  "status": "VERIFIED",
  "createdAt": "2026-08-28T08:00:00Z",
  "expiresAt": "2026-08-28T08:10:00Z",
  "verifiedAt": "2026-08-28T08:02:15Z"
}
```

Status sesi:

- `PENDING`: masih menunggu SMS.
- `VERIFIED`: nomor pengirim dan kode cocok.
- `EXPIRED`: melewati TTL atau digantikan sesi baru untuk nomor yang sama.

Polling yang disarankan: setiap 2–5 detik, lalu berhenti setelah `VERIFIED` atau `EXPIRED`. Setiap polling wajib membuat `X-Nonce` baru.

## 4. Get SMS yang berelasi dengan verifikasi

```http
GET /api/v1/verifications/{verificationId}/sms
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-value>
X-Signature: <hmac-hex>
```

Response:

```json
{
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "count": 1,
  "items": [
    {
      "smsId": "c6b093bd-f833-437c-82e8-ffec2a2ed540",
      "senderRaw": "+6281234567890",
      "senderPhone": "+6281234567890",
      "text": "VERIF K7P4M9QX",
      "sentAt": "2026-08-28T08:02:14Z",
      "receivedAt": "2026-08-28T08:02:15Z",
      "sim": "SIM1",
      "matchStatus": "MATCHED"
    }
  ]
}
```

Endpoint ini hanya mengembalikan maksimum 50 SMS yang sudah dapat direlasikan ke `verificationId`. Untuk sekadar mengetahui keberhasilan, gunakan endpoint status.

## 5. Debug storage in-memory

```http
GET /api/v1/debug/storage
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-value>
X-Signature: <hmac-hex>
```

Response:

```json
{
  "verificationsCount": 1,
  "incomingSmsCount": 1,
  "noncesCount": 2,
  "verifications": [
    {
      "id": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
      "phoneNumber": "+6281234567890",
      "codeHash": "<sha256>",
      "status": "VERIFIED",
      "createdAt": "2026-08-28T08:00:00Z",
      "expiresAt": "2026-08-28T08:10:00Z",
      "verifiedAt": "2026-08-28T08:02:15Z"
    }
  ],
  "incomingSms": [],
  "nonces": []
}
```

Endpoint ini ditujukan untuk debugging saat service hidup. Karena storage bersifat in-memory, seluruh isi endpoint ini akan kosong kembali setelah service restart.

---

# HMAC-SHA-256

## A. Webhook Android

Aplikasi Android menghitung:

```text
signature = HEX(HMAC-SHA-256(SMS_FORWARDER_HMAC_SECRET, RAW_HTTP_BODY))
```

Header:

```http
X-Signature: 64-karakter-hex
```

Yang ditandatangani harus berupa **byte raw JSON yang benar-benar dikirim**, termasuk spasi dan urutan field. Backend menghitung signature sebelum JSON diparsing.

Secret Android harus sama persis dengan environment variable:

```text
SMS_FORWARDER_HMAC_SECRET
```

Webhook tidak memakai timestamp/nonce karena aplikasi Android tersebut hanya menyediakan signature raw-body. Efek replay ditahan menggunakan fingerprint/idempotensi payload pada database.

## B. API aplikasi

API `/api/v1/**` memakai tiga header:

```http
X-Timestamp: 1787904000
X-Nonce: 8ed3e35d-11c2-43e0-a14c-56665aa17956
X-Signature: <hex>
```

Canonical request:

```text
METHOD\n
REQUEST_TARGET\n
X_TIMESTAMP\n
X_NONCE\n
SHA256_HEX(RAW_BODY)
```

Tanpa baris kosong tambahan. Contoh untuk POST:

```text
POST
/api/v1/verifications
1787904000
8ed3e35d-11c2-43e0-a14c-56665aa17956
8b81...<sha256 body>
```

Signature:

```text
HEX(HMAC-SHA-256(API_HMAC_SECRET, UTF8(CANONICAL_REQUEST)))
```

Ketentuan:

- `METHOD` harus uppercase.
- `REQUEST_TARGET` adalah path ditambah raw query string bila ada, misalnya `/api/v1/items?page=1`.
- `X-Timestamp` memakai Unix epoch detik.
- Default toleransi waktu adalah ±5 menit.
- `X-Nonce` harus unik dan panjang 16–128 karakter.
- Body GET adalah byte kosong.
- Signature dibandingkan secara constant-time.

Kode error autentikasi yang mungkin muncul:

| Code | Penyebab |
|---|---|
| `HMAC_HEADERS_REQUIRED` | Header wajib tidak lengkap |
| `HMAC_TIMESTAMP_INVALID` | Timestamp salah atau terlalu jauh |
| `HMAC_NONCE_INVALID` | Format nonce tidak valid |
| `HMAC_SIGNATURE_INVALID` | Signature tidak cocok |
| `HMAC_REPLAY_DETECTED` | Nonce sudah pernah dipakai |

> **Peringatan:** jangan menanam `API_HMAC_SECRET` di JavaScript browser/SPA karena pengguna dapat mengekstraknya. Frontend browser sebaiknya memanggil backend-for-frontend (BFF) Anda, lalu BFF menandatangani request menuju service ini. Direct signing layak untuk backend, native application dengan secure storage yang memadai, atau alat administrasi tepercaya.

## Client helper

Node.js 20+:

```bash
API_HMAC_SECRET='change-this-api-secret-at-least-32-characters' \
node scripts/hmac-request.js \
  POST http://localhost:8080/api/v1/verifications \
  '{"phoneNumber":"081234567890"}'
```

Check status:

```bash
API_HMAC_SECRET='change-this-api-secret-at-least-32-characters' \
node scripts/hmac-request.js \
  GET http://localhost:8080/api/v1/verifications/VERIFICATION_ID/status
```

---

# Setup aplikasi Android SMS Forwarder

Repository aplikasi:

```text
https://github.com/bogkonstantin/android_income_sms_gateway_webhook
```

Nama menu dapat sedikit berbeda menurut versi aplikasi, tetapi nilai konfigurasinya sebagai berikut.

## 1. Permission Android

Berikan izin:

- Receive/read SMS sesuai permintaan aplikasi.
- Notification, agar status foreground terlihat.
- Autostart/background activity pada Xiaomi, Oppo, Vivo, Realme, Samsung, dan vendor lain yang membatasi aplikasi background.
- Kecualikan aplikasi dari battery optimization.

Pastikan Android selalu terhubung ke internet, dapat menerima SMS, dan tidak mematikan aplikasi saat layar padam.

## 2. Tambah forwarding rule

Gunakan:

```text
Sender filter: *
```

Text filter:

```regex
(?i)^\s*VERIF\s+[A-Z0-9]{8}\s*$
```

Jika `VERIFICATION_CODE_LENGTH` diubah, ubah `{8}` pada regex sesuai panjang kode.

Webhook URL production:

```text
https://NAMA-SERVICE.onrender.com/internal/sms/incoming
```

Untuk pengujian Android ke komputer lokal, gunakan URL HTTPS yang benar-benar dapat dijangkau Android. `localhost` pada Android menunjuk ke Android itu sendiri, bukan laptop Anda.

## 3. JSON payload

Isi payload persis:

```json
{
  "from": "%from%",
  "text": "%text%",
  "sentStamp": %sentStamp%,
  "receivedStamp": %receivedStamp%,
  "sim": "%sim%"
}
```

## 4. HMAC

Aktifkan opsi HMAC-SHA-256/sign request, lalu isi secret yang sama dengan:

```text
SMS_FORWARDER_HMAC_SECRET
```

Backend mengharapkan header:

```http
X-Signature: <hex HMAC-SHA-256>
```

## 5. Retry

Aktifkan penyimpanan pesan gagal/retry. Backend harus mengembalikan HTTP `2xx` ketika payload diterima. Jika Android mengirim ulang payload identik, backend mengembalikan data lama dengan `duplicate: true`.

## 6. Test

1. Tekan tombol test pada aplikasi dan periksa log. Payload test yang tidak mengikuti regex mungkin hanya menguji koneksi rule.
2. Generate kode melalui API.
3. Dari HP lain, kirim SMS sesuai nilai `smsText`, misalnya `VERIF K7P4M9QX`.
4. Poll endpoint status menggunakan `verificationId`.
5. Periksa `/sms` bila perlu melihat hasil pencocokan.

---

# Menjalankan secara lokal

## Opsi 1 — Docker Compose

Prasyarat:

- Docker Engine atau Docker Desktop dengan Docker Compose v2.

Salin konfigurasi:

```bash
cp .env.example .env
```

Ubah minimal:

```dotenv
GATEWAY_PHONE_NUMBER=081100009999
API_HMAC_SECRET=<secret-random-minimal-32-karakter>
SMS_FORWARDER_HMAC_SECRET=<secret-random-minimal-32-karakter>
```

Generate secret dengan OpenSSL:

```bash
openssl rand -base64 48
```

Jalankan:

```bash
docker compose up --build
```

API tersedia di:

```text
http://localhost:8080
```

Stop:

```bash
docker compose down
```

Hapus container lokal:

```bash
docker compose down
```

Smoke test setelah container aktif:

```bash
python3 scripts/smoke_test.py
```

## Opsi 2 — Maven lokal

Prasyarat:

- JDK 21
- Maven 3.9+

Set environment:

```bash
export GATEWAY_PHONE_NUMBER='081100009999'
export API_HMAC_SECRET='change-this-api-secret-at-least-32-characters'
export SMS_FORWARDER_HMAC_SECRET='change-this-sms-secret-at-least-32-characters'
```

Jalankan:

```bash
mvn spring-boot:run
```

Test:

```bash
mvn -B -ntp verify
```

Build JAR:

```bash
mvn -B -ntp clean package
java -jar target/sms-verification-gateway.jar
```

---

# Environment variables

| Variable | Wajib | Default | Keterangan |
|---|---:|---|---|
| `GATEWAY_PHONE_NUMBER` | Ya | kosong | Nomor SIM Android yang ditampilkan kepada pengguna |
| `API_HMAC_SECRET` | Ya* | kosong | Secret HMAC API aplikasi, minimal 32 karakter |
| `SMS_FORWARDER_HMAC_SECRET` | Ya* | kosong | Secret HMAC Android, minimal 32 karakter |
| `HMAC_SECRET` | Opsional | kosong | Fallback bersama jika dua secret spesifik tidak diisi; secret terpisah lebih disarankan |
| `DEFAULT_COUNTRY_CODE` | Tidak | `62` | Country code untuk nomor lokal berawalan `0` |
| `VERIFICATION_PREFIX` | Tidak | `VERIF` | Prefix isi SMS |
| `VERIFICATION_CODE_LENGTH` | Tidak | `8` | Panjang kode, 6–32 |
| `VERIFICATION_TTL` | Tidak | `PT10M` | TTL format ISO-8601 Duration |
| `HMAC_ALLOWED_CLOCK_SKEW` | Tidak | `PT5M` | Toleransi timestamp API |
| `HMAC_NONCE_CLEANUP_MILLIS` | Tidak | `600000` | Interval pembersihan nonce expired |
| `PORT` | Render | `8080` | Port HTTP; Render mengisinya otomatis |
| `JAVA_OPTS` | Tidak | konfigurasi Docker | Opsi JVM runtime |

`*` Salah satu dari secret spesifik atau fallback `HMAC_SECRET` harus tersedia. Untuk production gunakan dua secret berbeda.

Secret tidak ditulis pada Docker image. Semua nilai di-bind saat container dijalankan melalui environment variables.

---

# Deploy ke Render

Proyek menyediakan `render.yaml` untuk membuat:

- satu Render Web Service berbasis Docker di region Singapore;
- secret yang diminta saat Blueprint dibuat;
- auto-deploy Render dimatikan agar deploy dilakukan setelah GitHub Actions lulus.

## 1. Push ke GitHub

```bash
git init
git add .
git commit -m "Initial SMS verification gateway"
git branch -M main
git remote add origin git@github.com:USERNAME/NAMA-REPO.git
git push -u origin main
```

## 2. Buat Blueprint pada Render

1. Login ke Render.
2. Pilih **New > Blueprint**.
3. Hubungkan repository GitHub ini.
4. Render membaca `render.yaml`.
5. Isi nilai yang diminta:
   - `GATEWAY_PHONE_NUMBER`
   - `API_HMAC_SECRET`
   - `SMS_FORWARDER_HMAC_SECRET`
6. Apply Blueprint.

## 3. Siapkan GitHub Actions deploy hook

Setelah service Render terbentuk:

1. Buka service **sms-verification-gateway** pada Render.
2. Buka **Settings > Deploy Hook**.
3. Salin URL deploy hook.
4. Pada GitHub buka **Settings > Secrets and variables > Actions**.
5. Tambahkan repository secret:

```text
Name: RENDER_DEPLOY_HOOK_URL
Value: <URL deploy hook Render>
```

Workflow `.github/workflows/ci-deploy.yml` menjalankan:

1. `mvn verify`.
2. Build Docker image aplikasi.
3. Smoke test bertanda tangan: membuat sesi pengganti, inbound SMS, retry idempotent, status, dan daftar SMS.
4. Deploy hook Render hanya setelah seluruh test lulus pada push ke `main`. Parameter `ref` memastikan Render men-deploy commit yang benar-benar diuji, bukan sekadar commit terbaru saat hook dipanggil.

Jika secret deploy hook belum diisi, CI tetap berjalan dan tahap deploy dilewati dengan pesan yang jelas.

## 4. Konfigurasi Android ke Render

Gunakan URL:

```text
https://NAMA-SERVICE.onrender.com/internal/sms/incoming
```

Secret HMAC Android harus sama dengan nilai `SMS_FORWARDER_HMAC_SECRET` pada Render.

---

# Keamanan dan batasan

1. **Kode dan nomor harus cocok.** Mengetahui kode milik orang lain tidak cukup bila SMS dikirim dari nomor berbeda.
2. **Kode tersimpan sebagai hash.** Plaintext hanya muncul pada response generate dan SMS yang masuk.
3. **HMAC bukan otorisasi pengguna akhir.** Ia mengautentikasi sistem pemanggil/API client yang memegang shared secret.
4. **Jangan taruh secret dalam browser.** Gunakan BFF untuk web frontend.
5. **Gunakan HTTPS.** Render menyediakan HTTPS pada URL publik. Jangan expose endpoint production lewat HTTP plaintext.
6. **Rotasi secret.** Update environment variable Render dan Android secara terkoordinasi.
7. **Jaga Android tetap hidup.** Nonaktifkan battery optimization, gunakan charger yang aman, koneksi internet stabil, dan monitoring perangkat.
8. **Pahami sifat data.** Status, nonce, dan audit SMS hanya hidup selama proses aplikasi berjalan dan akan hilang setelah restart.
9. **Free tier bukan SLA production.** Gunakan plan/infrastruktur sesuai kebutuhan availability.
10. **Perhatikan privasi.** Nomor dan isi SMS adalah data pribadi. Terapkan retensi, akses, logging, dan kebijakan perlindungan data sesuai kebutuhan organisasi.

## Catatan operasional

- Jangan log `API_HMAC_SECRET` atau `SMS_FORWARDER_HMAC_SECRET`.
- Jangan log request body webhook pada production bila tidak diperlukan.
- Batasi akses repository dan dashboard Render.
- Gunakan secret acak minimal 32 karakter; 48 byte random lebih baik.
- Sinkronkan waktu server dan client penandatangan karena API memakai timestamp.
- Kode default memiliki alfabet 32 karakter dan panjang 8; ruang kode sangat besar, tetapi TTL tetap wajib.

---

# Troubleshooting

## Android menerima SMS tetapi backend tidak menerima request

Periksa:

- URL webhook benar dan memakai HTTPS.
- Android memiliki internet.
- aplikasi tidak dihentikan battery optimization.
- rule forwarding aktif.
- text filter cocok dengan panjang kode.
- Render service tidak sedang cold-start. Free web service dapat berhenti setelah idle dan startup berikutnya dapat memerlukan sekitar satu menit; retry Android membantu menutup jeda ini.
- log/syslog aplikasi Android.

## `401 HMAC_SIGNATURE_INVALID` pada webhook Android

- Secret aplikasi Android berbeda dari `SMS_FORWARDER_HMAC_SECRET`.
- Payload yang ditandatangani berbeda dengan payload yang dikirim.
- JSON payload dimodifikasi oleh proxy.
- Jangan menghitung HMAC dari JSON yang sudah diparse/reformat; gunakan raw body.

## `401 HMAC_TIMESTAMP_INVALID`

- Timestamp harus Unix epoch **detik**, bukan milidetik.
- Jam client terlalu berbeda dari server.
- Atur NTP atau tingkatkan `HMAC_ALLOWED_CLOCK_SKEW` secara hati-hati.

## `401 HMAC_REPLAY_DETECTED`

Client menggunakan nonce yang sama lebih dari sekali. Generate UUID/random nonce baru untuk setiap request, termasuk setiap polling GET.

## `PHONE_MISMATCH`

Nomor yang didaftarkan berbeda dari caller ID SMS yang diterima Android. Bandingkan hasil normalisasi E.164 dan pastikan pengguna mengirim dari SIM yang didaftarkan.

## Render deploy tidak terpicu

- Periksa secret GitHub `RENDER_DEPLOY_HOOK_URL`.
- Pastikan workflow pada branch `main` lulus.
- Periksa tab **Actions** dan log step deploy.
- `autoDeployTrigger` sengaja `off` untuk mencegah deploy sebelum CI selesai.

---

# Pengembangan

Jalankan test inti:

```bash
mvn -B -ntp test
```

Verifikasi lengkap:

```bash
mvn -B -ntp verify
```

Smoke test container:

```bash
docker compose up --build --detach
python3 scripts/smoke_test.py
docker compose down
```

## Lisensi

MIT. Lihat [LICENSE](LICENSE).
