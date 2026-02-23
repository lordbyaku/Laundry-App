-- Laundry Komersial Indonesia - Supabase Schema (Multi Tenant)
-- Timezone default: Asia/Jakarta

create extension if not exists pgcrypto;

-- =========================
-- ENUMS
-- =========================
create type public.app_role as enum ('owner', 'kasir', 'operator', 'admin');
create type public.license_package as enum ('bulanan', 'tahunan');
create type public.license_status as enum ('aktif', 'masa_tenggang', 'kedaluwarsa');
create type public.order_status as enum ('pesanan_masuk', 'sedang_dicuci', 'selesai_dicuci', 'sudah_diambil');
create type public.payment_status as enum ('menunggu_verifikasi', 'lunas', 'ditolak');

-- =========================
-- MASTER TENANT & USER
-- =========================
create table if not exists public.tenants (
  id uuid primary key default gen_random_uuid(),
  kode text unique not null,
  nama text not null,
  alamat text,
  no_telepon text,
  timezone text not null default 'Asia/Jakarta',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nama text not null,
  no_telepon text,
  global_role public.app_role not null default 'operator',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- user bisa akses banyak tenant
create table if not exists public.user_tenants (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role public.app_role not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, user_id)
);

-- =========================
-- LISENSI + PEMBAYARAN
-- =========================
create table if not exists public.licenses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  package public.license_package not null,
  start_at timestamptz not null,
  end_at timestamptz not null,
  grace_days integer not null default 3,
  status public.license_status not null default 'aktif',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  license_id uuid references public.licenses(id) on delete set null,
  amount_idr bigint not null,
  metode text not null default 'manual_transfer',
  bukti_url text,
  status public.payment_status not null default 'menunggu_verifikasi',
  paid_at timestamptz,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- =========================
-- OPERASIONAL LAUNDRY
-- =========================
create table if not exists public.services (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  nama_layanan text not null,
  satuan text not null default 'kg',
  harga_default_idr bigint not null,
  estimasi_jam integer,
  aktif boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, nama_layanan)
);

create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  nama text not null,
  no_telepon text not null,
  alamat text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, no_telepon)
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  kode text not null,
  customer_id uuid not null references public.customers(id) on delete restrict,
  service_id uuid references public.services(id) on delete set null,
  berat_kg numeric(10,2),
  total_idr bigint not null,
  status public.order_status not null default 'pesanan_masuk',
  barcode_value text not null,
  catatan text,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  selesai_dicuci_at timestamptz,
  diambil_at timestamptz,
  unique (tenant_id, kode),
  unique (tenant_id, barcode_value)
);

create table if not exists public.order_status_logs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  order_id uuid not null references public.orders(id) on delete cascade,
  status_lama public.order_status,
  status_baru public.order_status not null,
  catatan text,
  changed_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);

create table if not exists public.wa_message_logs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  order_id uuid references public.orders(id) on delete set null,
  customer_id uuid references public.customers(id) on delete set null,
  destination_phone text not null,
  message_body text not null,
  api_url text,
  response_code integer,
  response_body text,
  is_success boolean not null default false,
  created_at timestamptz not null default now()
);

-- =========================
-- FUNCTIONS
-- =========================
create or replace function public.is_global_admin()
returns boolean
language sql
stable
as $$
  select coalesce((select p.global_role = 'admin' from public.profiles p where p.id = auth.uid()), false);
$$;

create or replace function public.has_tenant_access(target_tenant uuid)
returns boolean
language sql
stable
as $$
  select
    public.is_global_admin()
    or exists (
      select 1
      from public.user_tenants ut
      where ut.user_id = auth.uid()
        and ut.tenant_id = target_tenant
        and ut.is_active = true
    );
$$;

create or replace function public.can_manage_tenant_users(target_tenant uuid)
returns boolean
language sql
stable
as $$
  select
    public.is_global_admin()
    or exists (
      select 1
      from public.user_tenants ut
      where ut.user_id = auth.uid()
        and ut.tenant_id = target_tenant
        and ut.is_active = true
        and ut.role in ('owner', 'admin')
    );
$$;

create or replace function public.license_effective_status(target_tenant uuid)
returns public.license_status
language sql
stable
as $$
  with latest as (
    select l.*
    from public.licenses l
    where l.tenant_id = target_tenant
      and l.is_active = true
    order by l.end_at desc
    limit 1
  )
  select case
    when exists(select 1 from latest where now() <= end_at) then 'aktif'::public.license_status
    when exists(select 1 from latest where now() > end_at and now() <= (end_at + (grace_days || ' days')::interval)) then 'masa_tenggang'::public.license_status
    else 'kedaluwarsa'::public.license_status
  end;
$$;

create or replace function public.update_order_status(
  p_order_id uuid,
  p_new_status public.order_status,
  p_note text default null
)
returns public.orders
language plpgsql
security definer
as $$
declare
  v_order public.orders;
  v_old_status public.order_status;
begin
  select * into v_order from public.orders where id = p_order_id;
  if not found then
    raise exception 'Order tidak ditemukan';
  end if;

  if not public.has_tenant_access(v_order.tenant_id) then
    raise exception 'Tidak punya akses tenant';
  end if;

  if public.license_effective_status(v_order.tenant_id) <> 'aktif' then
    raise exception 'Lisensi tidak aktif, mode baca saja';
  end if;

  v_old_status := v_order.status;

  update public.orders
  set status = p_new_status,
      updated_at = now(),
      selesai_dicuci_at = case when p_new_status = 'selesai_dicuci' then now() else selesai_dicuci_at end,
      diambil_at = case when p_new_status = 'sudah_diambil' then now() else diambil_at end
  where id = p_order_id
  returning * into v_order;

  insert into public.order_status_logs (tenant_id, order_id, status_lama, status_baru, catatan, changed_by)
  values (v_order.tenant_id, v_order.id, v_old_status, p_new_status, p_note, auth.uid());

  return v_order;
end;
$$;

-- =========================
-- TRIGGERS
-- =========================
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger trg_tenants_updated_at before update on public.tenants
for each row execute procedure public.set_updated_at();

create trigger trg_profiles_updated_at before update on public.profiles
for each row execute procedure public.set_updated_at();

create trigger trg_licenses_updated_at before update on public.licenses
for each row execute procedure public.set_updated_at();

create trigger trg_payments_updated_at before update on public.payments
for each row execute procedure public.set_updated_at();

create trigger trg_customers_updated_at before update on public.customers
for each row execute procedure public.set_updated_at();

create trigger trg_orders_updated_at before update on public.orders
for each row execute procedure public.set_updated_at();

-- =========================
-- RLS
-- =========================
alter table public.tenants enable row level security;
alter table public.profiles enable row level security;
alter table public.user_tenants enable row level security;
alter table public.licenses enable row level security;
alter table public.payments enable row level security;
alter table public.services enable row level security;
alter table public.customers enable row level security;
alter table public.orders enable row level security;
alter table public.order_status_logs enable row level security;
alter table public.wa_message_logs enable row level security;

-- Profiles
create policy "profiles_read_self_or_admin" on public.profiles
for select using (id = auth.uid() or public.is_global_admin());

create policy "profiles_update_self_or_admin" on public.profiles
for update using (id = auth.uid() or public.is_global_admin())
with check (id = auth.uid() or public.is_global_admin());

-- Tenants
create policy "tenants_select_by_access" on public.tenants
for select using (public.has_tenant_access(id));

-- User-tenants
create policy "user_tenants_select_scoped" on public.user_tenants
for select using (public.has_tenant_access(tenant_id));

create policy "user_tenants_manage_owner_admin" on public.user_tenants
for all using (public.can_manage_tenant_users(tenant_id))
with check (public.can_manage_tenant_users(tenant_id));

-- Generic tenant-scoped policies
create policy "licenses_tenant_scoped" on public.licenses
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "payments_tenant_scoped" on public.payments
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "services_tenant_scoped" on public.services
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "customers_tenant_scoped" on public.customers
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "orders_tenant_scoped" on public.orders
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "order_status_logs_tenant_scoped" on public.order_status_logs
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

create policy "wa_message_logs_tenant_scoped" on public.wa_message_logs
for all using (public.has_tenant_access(tenant_id))
with check (public.has_tenant_access(tenant_id));

-- =========================
-- REPORTING VIEWS
-- =========================
create or replace view public.v_laporan_harian as
select
  o.tenant_id,
  (o.created_at at time zone 'Asia/Jakarta')::date as tanggal,
  count(*) as jumlah_order,
  sum(o.total_idr) as omzet_idr,
  count(*) filter (where o.status = 'sudah_diambil') as jumlah_selesai_diambil
from public.orders o
group by o.tenant_id, (o.created_at at time zone 'Asia/Jakarta')::date;

create or replace view public.v_order_belum_diambil as
select
  o.tenant_id,
  o.id,
  o.kode,
  o.status,
  o.total_idr,
  c.nama as customer_nama,
  c.no_telepon as customer_phone,
  o.created_at
from public.orders o
join public.customers c on c.id = o.customer_id
where o.status <> 'sudah_diambil';

create or replace view public.v_repeat_customer as
select
  o.tenant_id,
  o.customer_id,
  c.nama,
  c.no_telepon,
  count(*) as total_order,
  sum(o.total_idr) as total_belanja_idr,
  max(o.created_at) as terakhir_order
from public.orders o
join public.customers c on c.id = o.customer_id
group by o.tenant_id, o.customer_id, c.nama, c.no_telepon;
