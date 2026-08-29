# Bruno Collection

Koleksi Bruno ini dibuat untuk mengetes semua endpoint utama backend:

- `POST /api/v1/verifications`
- `GET /api/v1/verifications/{verificationId}/status`
- `GET /api/v1/verifications/{verificationId}/sms`
- `GET /api/v1/debug/storage`
- `POST /internal/sms/incoming`

## Cara pakai

1. Buka folder [docs/api/bruno](/Users/andreyantopratama/Downloads/phone-verification/phone-verification-backend/docs/api/bruno) sebagai collection di Bruno.
2. Salin `.env.example` menjadi `.env` di folder collection yang sama.
3. Isi `API_HMAC_SECRET` dan `SMS_FORWARDER_HMAC_SECRET` dengan nilai yang sama seperti backend.
4. Pilih environment `local`.
5. Aktifkan `Developer Mode` untuk scripting Bruno karena collection ini memakai `require("crypto")`.

## Urutan request yang disarankan

1. `Create Verification`
2. `Incoming SMS Webhook`
3. `Get Verification Status`
4. `List Verification SMS`
5. `Debug Storage`

`Create Verification` akan menyimpan `verificationId` dan `verificationCode` ke runtime variable Bruno, jadi request berikutnya bisa langsung dipakai tanpa copy-paste manual.
