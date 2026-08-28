# Spesifikasi Integrasi Frontend — SMS Verification Gateway

Dokumen ini diturunkan dari implementasi aktual service Spring Boot `sms-verification-gateway`.

## 1. Tujuan

Frontend digunakan untuk:

1. menerima nomor handphone pengguna;
2. meminta service membuat kode verifikasi yang berelasi dengan nomor tersebut;
3. menampilkan nomor tujuan SMS dan teks SMS yang harus dikirim;
4. mengecek status verifikasi secara berkala;
5. menampilkan status `PENDING`, `VERIFIED`, atau `EXPIRED`;
6. secara opsional menampilkan SMS yang sudah direlasikan ke sesi verifikasi untuk kebutuhan diagnosis/admin.

Endpoint `/internal/sms/incoming` hanya untuk aplikasi Android SMS Forwarder dan **tidak boleh dipanggil oleh browser/frontend**.

---

## 2. Arsitektur keamanan yang wajib

Service Spring menggunakan `API_HMAC_SECRET` untuk seluruh endpoint `/api/v1/**`. Secret ini tidak boleh ditanam dalam JavaScript browser, bundle SPA, local storage, atau environment variable yang diekspos ke client.

Gunakan arsitektur:

```text
Browser / Mobile Web
        |
        | request tanpa secret service
        v
BFF / Server Route milik frontend
        |
        | membuat X-Timestamp, X-Nonce, X-Signature
        v
SMS Verification Gateway (Spring Boot)
```

Pilihan implementasi:

- Next.js: Route Handler atau Server Action;
- Nuxt: server route;
- SvelteKit: server endpoint;
- React/Vite SPA: wajib memakai backend/BFF terpisah;
- frontend lain: gunakan komponen server-side yang dapat menjaga secret.

Environment variable server-side:

```dotenv
SMS_VERIFICATION_API_BASE_URL=https://nama-service.onrender.com
SMS_VERIFICATION_API_HMAC_SECRET=secret-yang-sama-dengan-API_HMAC_SECRET-service
```

Jangan menggunakan prefix environment yang mengekspos nilai ke browser, misalnya `NEXT_PUBLIC_`, `VITE_`, atau ekuivalennya.

---

## 3. Base URL dan format umum

```text
Base URL: {SMS_VERIFICATION_API_BASE_URL}
Content-Type: application/json
Accept: application/json
Timestamp: ISO-8601 UTC pada response
ID: UUID
Nomor ter-normalisasi: E.164, contoh +6281234567890
```

Default konfigurasi service:

```text
Prefix SMS       : VERIF
Panjang kode     : 8 karakter
Masa berlaku     : 10 menit
Country code     : 62
Clock skew HMAC  : ±5 menit
```

Nilai tersebut dapat diubah melalui environment service. Frontend harus menggunakan data dari response (`smsText`, `destinationNumber`, dan `expiresAt`) dan tidak melakukan hard-code.

---

# 4. HMAC untuk API aplikasi

Berlaku untuk:

```text
POST /api/v1/verifications
GET  /api/v1/verifications/{verificationId}/status
GET  /api/v1/verifications/{verificationId}/sms
```

## Header wajib

```http
X-Timestamp: <Unix epoch dalam detik>
X-Nonce: <nilai unik 16–128 karakter>
X-Signature: <64 karakter hexadecimal HMAC-SHA-256>
```

`X-Nonce` yang direkomendasikan adalah UUID acak baru pada setiap request.

## Canonical request

```text
METHOD\nREQUEST_TARGET\nX_TIMESTAMP\nX_NONCE\nSHA256_HEX(RAW_BODY)
```

Definisi:

- `METHOD`: uppercase, misalnya `POST` atau `GET`;
- `REQUEST_TARGET`: path dan raw query string bila ada, tanpa scheme dan hostname;
- `X_TIMESTAMP`: nilai header persis seperti yang dikirim;
- `X_NONCE`: nilai header persis seperti yang dikirim;
- `RAW_BODY`: byte request body yang benar-benar dikirim;
- request GET menggunakan body kosong;
- tidak ada newline tambahan di akhir canonical request.

Signature:

```text
HEX_LOWERCASE(
  HMAC-SHA-256(
    API_HMAC_SECRET,
    UTF8(CANONICAL_REQUEST)
  )
)
```

Contoh canonical request POST:

```text
POST
/api/v1/verifications
1787904000
8ed3e35d-11c2-43e0-a14c-56665aa17956
<sha256-hex-dari-raw-json-body>
```

Contoh algoritma server-side TypeScript:

```ts
import crypto from "node:crypto";

export function createSignedHeaders(input: {
  method: string;
  requestTarget: string;
  rawBody?: string;
  secret: string;
}) {
  const method = input.method.toUpperCase();
  const rawBody = input.rawBody ?? "";
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const nonce = crypto.randomUUID();

  const bodyHash = crypto
    .createHash("sha256")
    .update(rawBody, "utf8")
    .digest("hex");

  const canonicalRequest = [
    method,
    input.requestTarget,
    timestamp,
    nonce,
    bodyHash,
  ].join("\n");

  const signature = crypto
    .createHmac("sha256", input.secret)
    .update(canonicalRequest, "utf8")
    .digest("hex");

  return {
    "X-Timestamp": timestamp,
    "X-Nonce": nonce,
    "X-Signature": signature,
  };
}
```

**Penting:** serialisasi JSON hanya dilakukan satu kali. String JSON yang dihitung hash-nya harus sama persis dengan body yang dikirim melalui `fetch`.

---

# 5. Endpoint service

## 5.1 Generate kode unik

```http
POST /api/v1/verifications
Content-Type: application/json
Accept: application/json
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-nonce>
X-Signature: <hmac-hex>
```

### Request

```json
{
  "phoneNumber": "0812 3456 7890"
}
```

### Aturan field

| Field | Tipe | Wajib | Aturan |
|---|---|---:|---|
| `phoneNumber` | string | Ya | Tidak kosong, maksimum 32 karakter |

Format input yang lazim diterima:

```text
081234567890
+6281234567890
6281234567890
0812 3456 7890
(0812) 3456-7890
```

Response mengembalikan nomor dalam format E.164.

### Response `201 Created`

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

### Semantik

- `verificationId` menjadi identifier utama untuk polling;
- `code` hanya dikembalikan saat pembuatan sesi;
- `smsText` adalah teks final yang harus dikirim pengguna;
- `destinationNumber` adalah nomor SIM pada Android gateway;
- `expiresAt` harus menjadi sumber countdown frontend;
- jika dibuat sesi baru untuk nomor yang sama, sesi `PENDING` sebelumnya otomatis menjadi `EXPIRED`.

---

## 5.2 Check status verifikasi

```http
GET /api/v1/verifications/{verificationId}/status
Accept: application/json
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-nonce-baru>
X-Signature: <hmac-hex>
```

Body request kosong.

### Response `200 OK` — PENDING

```json
{
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "phoneNumber": "+6281234567890",
  "status": "PENDING",
  "createdAt": "2026-08-28T08:00:00Z",
  "expiresAt": "2026-08-28T08:10:00Z"
}
```

### Response `200 OK` — VERIFIED

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

### Response `200 OK` — EXPIRED

```json
{
  "verificationId": "02c73bc5-1fa0-4daa-b72d-769f22a85373",
  "phoneNumber": "+6281234567890",
  "status": "EXPIRED",
  "createdAt": "2026-08-28T08:00:00Z",
  "expiresAt": "2026-08-28T08:10:00Z"
}
```

`verifiedAt` tidak selalu ada karena response service menghilangkan property bernilai `null`.

### Status

| Status | Arti | Perilaku frontend |
|---|---|---|
| `PENDING` | Menunggu SMS yang valid | Lanjutkan polling dan countdown |
| `VERIFIED` | Nomor pengirim dan kode cocok | Hentikan polling, tampilkan sukses |
| `EXPIRED` | TTL habis atau sesi digantikan | Hentikan polling, tawarkan generate ulang |

### Strategi polling

- interval default: 3 detik;
- setiap polling wajib memakai nonce baru;
- hentikan polling saat `VERIFIED` atau `EXPIRED`;
- hentikan polling saat component unmount;
- saat tab tidak aktif, polling boleh dihentikan;
- saat tab aktif kembali, lakukan request status segera;
- jangan menggunakan endpoint daftar SMS sebagai polling utama.

---

## 5.3 Get SMS yang berelasi dengan sesi

```http
GET /api/v1/verifications/{verificationId}/sms
Accept: application/json
X-Timestamp: <epoch-seconds>
X-Nonce: <unique-nonce-baru>
X-Signature: <hmac-hex>
```

Body request kosong.

### Response `200 OK`

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

### Semantik

- maksimum 50 SMS;
- urutan terbaru ke terlama berdasarkan `receivedAt`;
- hanya SMS yang dapat direlasikan ke `verificationId`;
- `sentAt`, `senderPhone`, atau `sim` dapat tidak ada bila sumber tidak menyediakannya;
- endpoint ini cocok untuk panel diagnosis/admin, bukan syarat alur utama pengguna.

### Nilai `matchStatus`

| Status | Arti |
|---|---|
| `MATCHED` | Kode dan nomor cocok; sesi berhasil diverifikasi |
| `PHONE_MISMATCH` | Kode ditemukan tetapi pengirim berbeda |
| `EXPIRED_CODE` | Kode ditemukan tetapi sesi kedaluwarsa |
| `ALREADY_VERIFIED` | Kode telah diverifikasi sebelumnya |
| `NO_VERIFICATION_CODE` | Format SMS tidak sesuai; umumnya tidak memiliki relasi sesi |
| `UNKNOWN_CODE` | Kode tidak ditemukan; umumnya tidak memiliki relasi sesi |
| `INVALID_SENDER` | Nomor pengirim tidak valid; umumnya tidak memiliki relasi sesi |

---

## 5.4 Webhook Android — bukan endpoint frontend

```http
POST /internal/sms/incoming
Content-Type: application/json
X-Signature: <HMAC-SHA-256 raw body>
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

Endpoint ini hanya dipakai oleh Android SMS Forwarder. Jangan membuat UI atau client browser yang memanggil endpoint ini.

---

# 6. Format error

Semua error JSON menggunakan format:

```json
{
  "timestamp": "2026-08-28T08:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/verifications",
  "fieldErrors": {
    "phoneNumber": "must not be blank"
  }
}
```

## Kode error aplikasi

| HTTP | `code` | Arti | Respons UI |
|---:|---|---|---|
| 400 | `VALIDATION_ERROR` | Field tidak valid | Tampilkan error pada field terkait |
| 400 | `INVALID_JSON` | JSON request tidak valid | Error integrasi; log server-side |
| 400 | `INVALID_PATH_PARAMETER` | `verificationId` bukan UUID | Hapus sesi lokal dan minta ulang |
| 400 | `INVALID_REQUEST` | Nomor HP atau input lain tidak valid | Tampilkan `message` secara aman |
| 404 | `RESOURCE_NOT_FOUND` | Sesi tidak ditemukan | Hapus sesi lokal dan tawarkan generate baru |
| 409 | `DATA_CONFLICT` | Konflik constraint/data | Tampilkan gagal sementara dan izinkan retry |
| 500 | `INTERNAL_ERROR` | Error service | Tampilkan pesan generik dan tombol coba lagi |

## Kode error HMAC

| HTTP | `code` | Arti |
|---:|---|---|
| 401 | `HMAC_HEADERS_REQUIRED` | Header HMAC belum lengkap |
| 401 | `HMAC_TIMESTAMP_INVALID` | Jam server BFF salah atau timestamp di luar toleransi |
| 401 | `HMAC_NONCE_INVALID` | Nonce tidak memenuhi pola/panjang |
| 401 | `HMAC_SIGNATURE_INVALID` | Canonical request, body, path, atau secret tidak cocok |
| 401 | `HMAC_REPLAY_DETECTED` | Nonce pernah digunakan |

Error HMAC adalah masalah integrasi server-side. Browser sebaiknya menerima pesan generik seperti “Layanan verifikasi sedang tidak tersedia”, sedangkan detail kode dicatat pada log BFF tanpa menampilkan secret/signature.

---

# 7. TypeScript contract

```ts
export type VerificationStatus = "PENDING" | "VERIFIED" | "EXPIRED";

export type SmsMatchStatus =
  | "MATCHED"
  | "NO_VERIFICATION_CODE"
  | "UNKNOWN_CODE"
  | "PHONE_MISMATCH"
  | "EXPIRED_CODE"
  | "ALREADY_VERIFIED"
  | "INVALID_SENDER";

export interface CreateVerificationRequest {
  phoneNumber: string;
}

export interface CreateVerificationResponse {
  verificationId: string;
  phoneNumber: string;
  code: string;
  smsText: string;
  destinationNumber: string;
  status: VerificationStatus;
  expiresAt: string;
}

export interface VerificationStatusResponse {
  verificationId: string;
  phoneNumber: string;
  status: VerificationStatus;
  createdAt: string;
  expiresAt: string;
  verifiedAt?: string;
}

export interface SmsItem {
  smsId: string;
  senderRaw: string;
  senderPhone?: string;
  text: string;
  sentAt?: string;
  receivedAt: string;
  sim?: string;
  matchStatus: SmsMatchStatus;
}

export interface SmsListResponse {
  verificationId: string;
  count: number;
  items: SmsItem[];
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: Record<string, string>;
}
```

---

# 8. BFF API yang direkomendasikan untuk browser

Agar browser tidak mengetahui secret service, frontend generator sebaiknya membuat endpoint lokal/BFF berikut:

```text
POST /api/phone-verifications
GET  /api/phone-verifications/{verificationId}/status
GET  /api/phone-verifications/{verificationId}/sms   (opsional/admin)
```

BFF melakukan:

1. validasi input dari browser;
2. serialisasi raw body satu kali;
3. membuat timestamp dan nonce baru;
4. menghitung HMAC;
5. memanggil Spring service;
6. meneruskan status HTTP dan JSON yang telah disanitasi;
7. tidak pernah mengembalikan secret, canonical request, atau signature ke browser.

Untuk route GET, BFF harus menandatangani path service yang sebenarnya, misalnya:

```text
/api/v1/verifications/{verificationId}/status
```

bukan path publik BFF `/api/phone-verifications/...`.

---

# 9. Alur UX frontend

## State machine

```text
IDLE
  -> CREATING
  -> WAITING/PENDING
  -> VERIFIED
  -> EXPIRED

Error sementara dari CREATING atau WAITING
  -> ERROR_RETRYABLE
```

## Layar 1 — Input nomor

Komponen:

- judul “Verifikasi Nomor Handphone”;
- input `tel`;
- contoh format `0812 3456 7890`;
- tombol “Buat Kode Verifikasi”;
- validasi kosong dan panjang maksimum;
- submit dengan Enter;
- loading state dan pencegahan double-submit.

## Layar 2 — Instruksi pengiriman SMS

Tampilkan dari response API:

- nomor pengguna hasil normalisasi;
- `smsText` dengan tombol salin;
- `destinationNumber` dengan tombol salin;
- countdown berdasarkan `expiresAt`;
- status “Menunggu SMS…”;
- tombol/link “Buka Aplikasi SMS” menggunakan URI `sms:` bila perangkat mendukung;
- jangan menyusun sendiri teks dari `code`; gunakan `smsText` dari server.

Contoh:

```text
Kirim SMS berikut:
VERIF K7P4M9QX

Ke nomor:
081100009999

Berlaku selama 09:42
```

## Layar 3 — Berhasil

- ikon sukses;
- teks “Nomor handphone berhasil diverifikasi”;
- nomor hasil normalisasi;
- waktu verifikasi bila `verifiedAt` tersedia;
- tombol lanjut sesuai aplikasi induk.

## Layar 4 — Kedaluwarsa

- teks bahwa kode sudah tidak berlaku;
- tombol “Buat Kode Baru”;
- saat generate ulang, gunakan nomor sebelumnya;
- sesi lama tidak perlu dihapus melalui API karena service otomatis membuatnya `EXPIRED`.

## Persistensi client

Simpan hanya data sesi yang diperlukan di `sessionStorage`, bukan secret:

```ts
{
  verificationId,
  phoneNumber,
  smsText,
  destinationNumber,
  expiresAt
}
```

Saat reload:

1. baca sesi;
2. validasi UUID dan tanggal;
3. panggil status melalui BFF;
4. lanjutkan state berdasarkan response;
5. hapus sesi bila 404 atau data lokal rusak.

---

# 10. Acceptance criteria frontend

1. Secret HMAC tidak pernah masuk bundle browser.
2. Generate mengirim nomor ke BFF dan menampilkan seluruh nilai berdasarkan response service.
3. Polling status berjalan setiap sekitar 3 detik dengan nonce berbeda pada setiap request dari BFF ke service.
4. Polling berhenti pada `VERIFIED`, `EXPIRED`, unmount, atau pembatalan request.
5. Countdown menggunakan `expiresAt` dari server.
6. Refresh halaman dapat memulihkan sesi dari `sessionStorage`.
7. Generate ulang untuk nomor sama mengganti sesi aktif.
8. Semua loading, empty, error, verified, dan expired state terlihat jelas.
9. UI responsif dan dapat digunakan pada mobile karena pengguna perlu berpindah ke aplikasi SMS.
10. Endpoint `/internal/sms/incoming` tidak dipanggil oleh frontend.
11. GET daftar SMS tidak digunakan sebagai indikator utama sukses; gunakan endpoint status.
12. Unit test mencakup HMAC helper server-side, state transition, polling stop condition, error mapping, dan restore session.

---

# 11. Prompt siap pakai untuk membuat frontend

Salin prompt berikut ke AI coding agent:

```text
Buat frontend lengkap untuk alur verifikasi nomor handphone menggunakan SMS inbound. Pengguna tidak menerima OTP dari server. Pengguna harus mengirim SMS dengan kode unik ke nomor gateway yang ditampilkan sistem.

PILIHAN ARSITEKTUR
- Bila framework belum ditentukan, gunakan Next.js dengan TypeScript agar HMAC dapat dibuat pada server route/BFF.
- Browser tidak boleh memanggil Spring service secara langsung.
- Browser memanggil BFF/server route milik aplikasi frontend.
- BFF memanggil SMS Verification Gateway menggunakan HMAC-SHA-256.
- Jangan pernah mengekspos API_HMAC_SECRET ke browser, bundle JavaScript, localStorage, sessionStorage, response API, atau variable environment publik.

ENVIRONMENT SERVER-SIDE
SMS_VERIFICATION_API_BASE_URL=<base URL Spring service>
SMS_VERIFICATION_API_HMAC_SECRET=<nilai yang sama dengan API_HMAC_SECRET pada Spring service>

API SPRING SERVICE

1. Generate verification
POST /api/v1/verifications
Request JSON:
{
  "phoneNumber": "0812 3456 7890"
}
Response 201:
{
  "verificationId": "UUID",
  "phoneNumber": "+6281234567890",
  "code": "K7P4M9QX",
  "smsText": "VERIF K7P4M9QX",
  "destinationNumber": "081100009999",
  "status": "PENDING",
  "expiresAt": "ISO-8601 UTC"
}

2. Check status
GET /api/v1/verifications/{verificationId}/status
Response 200:
{
  "verificationId": "UUID",
  "phoneNumber": "+6281234567890",
  "status": "PENDING | VERIFIED | EXPIRED",
  "createdAt": "ISO-8601 UTC",
  "expiresAt": "ISO-8601 UTC",
  "verifiedAt": "ISO-8601 UTC, optional"
}

3. Get related SMS, opsional untuk panel diagnosis/admin
GET /api/v1/verifications/{verificationId}/sms
Response 200:
{
  "verificationId": "UUID",
  "count": 1,
  "items": [
    {
      "smsId": "UUID",
      "senderRaw": "+6281234567890",
      "senderPhone": "+6281234567890, optional",
      "text": "VERIF K7P4M9QX",
      "sentAt": "ISO-8601 UTC, optional",
      "receivedAt": "ISO-8601 UTC",
      "sim": "SIM1, optional",
      "matchStatus": "MATCHED | NO_VERIFICATION_CODE | UNKNOWN_CODE | PHONE_MISMATCH | EXPIRED_CODE | ALREADY_VERIFIED | INVALID_SENDER"
    }
  ]
}

JANGAN panggil POST /internal/sms/incoming dari frontend. Endpoint tersebut hanya untuk Android SMS Forwarder.

HMAC API
Untuk setiap request BFF ke /api/v1/**, buat header:
X-Timestamp: Unix epoch detik
X-Nonce: UUID baru pada setiap request
X-Signature: HMAC-SHA-256 hex

Canonical request persis:
METHOD + "\\n" +
REQUEST_TARGET + "\\n" +
X_TIMESTAMP + "\\n" +
X_NONCE + "\\n" +
SHA256_HEX(RAW_BODY)

METHOD harus uppercase. REQUEST_TARGET adalah path service beserta raw query string. GET memakai raw body kosong. Signature adalah lowercase hex HMAC-SHA-256 menggunakan SMS_VERIFICATION_API_HMAC_SECRET. Serialisasi JSON satu kali; body yang di-hash harus identik dengan body yang dikirim.

BUAT BFF ROUTES
POST /api/phone-verifications
GET /api/phone-verifications/{verificationId}/status
GET /api/phone-verifications/{verificationId}/sms

BFF harus meneruskan status HTTP dan response JSON yang aman, memiliki timeout, AbortController, validasi UUID, dan logging server-side tanpa secret/signature.

UI/UX
- Buat halaman responsif dan mobile-first.
- State: IDLE, CREATING, PENDING, VERIFIED, EXPIRED, ERROR.
- Form input nomor menggunakan type=tel.
- Setelah generate, tampilkan phoneNumber, smsText, destinationNumber, dan countdown dari expiresAt.
- Sediakan tombol salin teks SMS dan nomor tujuan.
- Sediakan tombol “Buka Aplikasi SMS” dengan URI sms: dan body yang telah di-URL-encode, tetapi tetap sediakan fallback copy manual.
- Poll endpoint status setiap 3 detik selama PENDING.
- Gunakan nonce baru untuk setiap request service melalui BFF.
- Hentikan polling saat VERIFIED, EXPIRED, tab/component ditutup, atau request dibatalkan.
- Saat tab kembali aktif, cek status segera.
- Pada VERIFIED tampilkan success state.
- Pada EXPIRED tampilkan tombol generate kode baru menggunakan nomor sebelumnya.
- Jangan menggunakan endpoint daftar SMS sebagai polling utama.
- Simpan verificationId, phoneNumber, smsText, destinationNumber, expiresAt di sessionStorage agar reload dapat dipulihkan. Jangan simpan secret.
- Jika restore menghasilkan 404 atau data sesi invalid, hapus sessionStorage dan kembali ke form.
- Gunakan pesan error yang dapat dipahami pengguna. Detail error HMAC hanya dicatat server-side.
- Jangan hard-code prefix VERIF, panjang kode, nomor tujuan, atau TTL. Gunakan response API.

ERROR RESPONSE
{
  "timestamp": "ISO-8601",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/verifications",
  "fieldErrors": {
    "phoneNumber": "must not be blank"
  }
}

Tangani code:
VALIDATION_ERROR, INVALID_JSON, INVALID_PATH_PARAMETER, INVALID_REQUEST,
RESOURCE_NOT_FOUND, DATA_CONFLICT, INTERNAL_ERROR,
HMAC_HEADERS_REQUIRED, HMAC_TIMESTAMP_INVALID, HMAC_NONCE_INVALID,
HMAC_SIGNATURE_INVALID, HMAC_REPLAY_DETECTED.

TYPE SAFETY DAN KUALITAS
- Gunakan TypeScript strict.
- Pisahkan API client server-side, BFF routes, domain types, polling hook/state machine, UI components, dan utilities.
- Hindari komponen besar dengan banyak tanggung jawab.
- Tambahkan unit test untuk pembentukan canonical HMAC, signature, error mapping, countdown, polling stop condition, restore session, dan state transition.
- Tambahkan integration test untuk generate -> pending -> verified/expired menggunakan mock service.
- Tambahkan README berisi konfigurasi environment, cara menjalankan, arsitektur security, dan cara menguji.

OUTPUT
Berikan struktur proyek lengkap, source code yang dapat dijalankan, .env.example tanpa secret nyata, test, dan README. Jangan membuat implementasi mock sebagai hasil akhir; mock hanya untuk test/development.
```
