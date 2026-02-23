# Checklist Setup Supabase - Laundry Komersial Indonesia

## 1) Secrets & konfigurasi
- [ ] Simpan `SUPABASE_URL` dan `SUPABASE_ANON_KEY` untuk aplikasi Android.
- [ ] Simpan `service_role` hanya di server/Edge Function (bukan di APK).
- [ ] Simpan WA API key hanya di server/Edge Function.

## 2) Multi-tenant isolation
- [ ] Jalankan `supabase/schema.sql`.
- [ ] Pastikan RLS aktif di semua tabel tenant-scoped.
- [ ] Set role global admin pada `profiles.global_role='admin'` untuk super admin.
- [ ] Mapping akses tenant per user di `user_tenants`.

## 3) Lisensi
- [ ] Paket lisensi: bulanan (30) / tahunan (365).
- [ ] Grace period: 3 hari.
- [ ] Saat lisensi habis: mode read-only.
- [ ] Pembayaran awal: manual transfer (`payments.metode = manual_transfer`).

## 4) Operasional order
- [ ] Definisikan layanan per tenant di tabel `services`.
- [ ] Gunakan status default: pesanan_masuk → sedang_dicuci → selesai_dicuci → sudah_diambil.
- [ ] Gunakan `update_order_status()` untuk audit log otomatis di `order_status_logs`.

## 5) WhatsApp log
- [ ] Simpan sukses/gagal pengiriman ke `wa_message_logs`.
- [ ] Tidak perlu retry otomatis (sesuai requirement).

## 6) Laporan
- [ ] Gunakan timezone `Asia/Jakarta`.
- [ ] Gunakan view:
  - `v_laporan_harian`
  - `v_order_belum_diambil`
  - `v_repeat_customer`
- [ ] Tambahkan Edge Function untuk export `.xlsx` & `.pdf`.
