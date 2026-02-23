-- Seed data DEMO LAUNDRY
-- Jalankan setelah: supabase/schema.sql
--
-- Penting:
-- 1) Buat user auth dulu di Supabase Authentication:
--    email: na_grow@yahoo.com
--    password: Hun090609
-- 2) Setelah user auth terbentuk, jalankan script ini di SQL Editor.

begin;

-- 1) Tenant demo
insert into public.tenants (kode, nama, alamat, no_telepon, timezone)
values ('DEMO-LAUNDRY', 'DEMO LAUNDRY', 'Indonesia', '08562717803', 'Asia/Jakarta')
on conflict (kode) do update
set
  nama = excluded.nama,
  alamat = excluded.alamat,
  no_telepon = excluded.no_telepon,
  timezone = excluded.timezone;

-- 2) Layanan default demo
insert into public.services (tenant_id, nama_layanan, satuan, harga_default_idr, estimasi_jam, aktif)
select t.id, svc.nama_layanan, svc.satuan, svc.harga_default_idr, svc.estimasi_jam, true
from public.tenants t
cross join (
  values
    ('Cuci Kering', 'kg', 7000::bigint, 24),
    ('Cuci + Setrika', 'kg', 9000::bigint, 24),
    ('Setrika Saja', 'kg', 6000::bigint, 12),
    ('Express 6 Jam', 'kg', 15000::bigint, 6)
) as svc(nama_layanan, satuan, harga_default_idr, estimasi_jam)
where t.kode = 'DEMO-LAUNDRY'
on conflict (tenant_id, nama_layanan) do update
set
  satuan = excluded.satuan,
  harga_default_idr = excluded.harga_default_idr,
  estimasi_jam = excluded.estimasi_jam,
  aktif = excluded.aktif;

-- 3) Lisensi demo aktif 30 hari + grace 3 hari
insert into public.licenses (tenant_id, package, start_at, end_at, grace_days, status, is_active)
select t.id,
       'bulanan'::public.license_package,
       now(),
       now() + interval '30 days',
       3,
       'aktif'::public.license_status,
       true
from public.tenants t
where t.kode = 'DEMO-LAUNDRY'
  and not exists (
    select 1
    from public.licenses l
    where l.tenant_id = t.id
      and l.is_active = true
  );

-- 4) Provision user ADMIN sesuai data yang diminta
-- User harus sudah ada di auth.users (dibuat via dashboard Authentication)
do $$
declare
  v_user_id uuid;
  v_tenant_id uuid;
begin
  select id into v_user_id
  from auth.users
  where email = 'na_grow@yahoo.com'
  limit 1;

  if v_user_id is null then
    raise exception 'User auth dengan email na_grow@yahoo.com belum ada. Buat dulu di Authentication > Users.';
  end if;

  select id into v_tenant_id
  from public.tenants
  where kode = 'DEMO-LAUNDRY'
  limit 1;

  if v_tenant_id is null then
    raise exception 'Tenant DEMO-LAUNDRY tidak ditemukan.';
  end if;

  -- global admin
  insert into public.profiles (id, nama, no_telepon, global_role)
  values (v_user_id, 'TRISNA', '08562717803', 'admin')
  on conflict (id) do update
  set
    nama = excluded.nama,
    no_telepon = excluded.no_telepon,
    global_role = 'admin';

  -- akses tenant sebagai admin
  insert into public.user_tenants (tenant_id, user_id, role, is_active)
  values (v_tenant_id, v_user_id, 'admin', true)
  on conflict (tenant_id, user_id) do update
  set
    role = 'admin',
    is_active = true;
end $$;

commit;
