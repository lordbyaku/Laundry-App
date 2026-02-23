# Laundry Komersial Indonesia (Android + Supabase)

Aplikasi Android untuk operasional laundry komersial Indonesia dengan Bahasa Indonesia dan mata uang IDR/Rp.

## Fitur yang sudah di aplikasi Android

- Lisensi komersial bulanan (30 hari) & tahunan (365 hari).
- Masa tenggang lisensi 3 hari dalam mode **read-only**.
- Input pesanan + registrasi pelanggan baru (nama, no telepon, alamat).
- Barcode order, scan barcode untuk update status:
  - Pesanan masuk → Sedang dicuci → Selesai dicuci → Sudah diambil
- Struk teks untuk printer Bluetooth POS 58mm.
- Notifikasi WhatsApp saat status berubah (via API).

## Keamanan konfigurasi

Kunci API **tidak** lagi di-hardcode di source code. Konfigurasi diambil dari Gradle property.

Tambahkan di `~/.gradle/gradle.properties` (direkomendasikan):

```properties
SUPABASE_URL=https://azfapaanqsurkreczxwh.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
WA_REMINDER_API_URL=https://reminder.famstory.my.id/api/send-message
WA_REMINDER_API_KEY=YOUR_WA_API_KEY
OWNER_EMAIL=lordbyaku@gmail.com
```

> Jangan pernah menaruh `service_role` di aplikasi Android.

## Setup Supabase (multi-tenant production)

File SQL lengkap sudah disiapkan di:

- `supabase/schema.sql`

Mencakup:

- Multi-tenant isolation dengan RLS.
- Role user: `owner`, `kasir`, `operator`, `admin`.
- Mapping akses user ke banyak tenant (`user_tenants`).
- Lisensi + grace period + pembayaran manual transfer.
- Tabel layanan yang bisa didefinisikan owner/operator/admin.
- Log WhatsApp sukses/gagal (`wa_message_logs`).
- View laporan operasional.

### Cara eksekusi

1. Buka **Supabase SQL Editor**.
2. Jalankan isi `supabase/schema.sql`.
3. Siapkan user pertama sebagai `admin` di tabel `profiles`.
4. Assign tenant via `user_tenants`.


### Seed data demo

Untuk membuat data demo tenant + admin sesuai permintaan, jalankan:

- `supabase/seed_demo_laundry.sql`

Script ini akan:
- membuat tenant `DEMO LAUNDRY`,
- menambahkan layanan default,
- menambahkan lisensi bulanan aktif,
- mengatur user `na_grow@yahoo.com` sebagai global `admin` + admin tenant.

## Laporan lengkap + download Excel/PDF

Untuk kebutuhan laporan lengkap dan export Excel/PDF, gunakan kombinasi:

1. View SQL (`v_laporan_harian`, `v_order_belum_diambil`, `v_repeat_customer`) yang sudah dibuat.
2. Supabase Edge Function / backend API untuk:
   - ambil data laporan,
   - generate file `.xlsx` dan `.pdf`,
   - simpan ke Supabase Storage,
   - return URL download terproteksi.

## Build

```bash
./gradlew assembleDebug
```

