-- Prepared locally. Applying this migration is a separate production action.
-- Uses the signed-in user's JWT; no service-role credential is needed by Prismet.
begin;

create table if not exists public.prismet_arcade_saves (
  user_id uuid not null references auth.users(id) on delete cascade,
  game_id text not null check (game_id in ('2048', 'minesweeper', 'lights-out')),
  slot text not null check (slot in ('free', 'daily', 'challenge')),
  revision bigint not null check (revision > 0 and revision <= 9007199254740991),
  envelope jsonb not null check (
    jsonb_typeof(envelope) = 'object'
    and octet_length(envelope::text) <= 65536
    and envelope->>'version' = '1'
    and envelope->>'stateVersion' = '1'
    and envelope->>'gameID' = game_id
    and envelope->>'mode' = slot
  ),
  updated_at timestamptz not null default now(),
  primary key (user_id, game_id, slot)
);

-- Checking auth.users also protects direct PostgREST callers; an incomplete JWT
-- or a device-only anonymous account cannot become a cross-device identity.
create or replace function public.prismet_arcade_account_confirmed()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from auth.users u
    where u.id = (select auth.uid()) and u.is_anonymous = false
      and u.email is not null and u.email <> '' and u.email_confirmed_at is not null
  );
$$;
revoke all on function public.prismet_arcade_account_confirmed() from public, anon;
grant execute on function public.prismet_arcade_account_confirmed() to authenticated;

alter table public.prismet_arcade_saves enable row level security;
revoke all on public.prismet_arcade_saves from anon, authenticated;
grant select on public.prismet_arcade_saves to authenticated;
drop policy if exists arcade_read_own on public.prismet_arcade_saves;
create policy arcade_read_own on public.prismet_arcade_saves for select to authenticated
  using (user_id = (select auth.uid()) and (select public.prismet_arcade_account_confirmed()));

-- No user ID parameter: the verified JWT determines ownership. Direct writes are
-- denied, so callers cannot bypass the revision check through the table API.
create or replace function public.prismet_save_arcade_progress(
  p_game_id text, p_slot text, p_expected_revision bigint, p_envelope jsonb
) returns setof public.prismet_arcade_saves
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
  saved public.prismet_arcade_saves;
begin
  if caller is null or not public.prismet_arcade_account_confirmed() then
    raise sqlstate '42501' using message = 'A confirmed Arcade account is required';
  end if;
  if p_game_id is null or p_game_id not in ('2048', 'minesweeper', 'lights-out')
     or p_slot is null or p_slot not in ('free', 'daily', 'challenge')
     or p_expected_revision is null or p_expected_revision < 0 or p_expected_revision >= 9007199254740991
     or p_envelope is null or jsonb_typeof(p_envelope) <> 'object'
     or octet_length(p_envelope::text) > 65536
     or p_envelope->>'version' is distinct from '1'
     or p_envelope->>'stateVersion' is distinct from '1'
     or p_envelope->>'gameID' is distinct from p_game_id
     or p_envelope->>'mode' is distinct from p_slot then
    raise sqlstate '22023' using message = 'Invalid Arcade save';
  end if;
  if p_expected_revision = 0 then
    insert into public.prismet_arcade_saves(user_id, game_id, slot, revision, envelope)
      values (caller, p_game_id, p_slot, 1, p_envelope)
      on conflict (user_id, game_id, slot) do nothing returning * into saved;
  else
    update public.prismet_arcade_saves set revision = revision + 1,
      envelope = p_envelope, updated_at = now()
      where user_id = caller and game_id = p_game_id and slot = p_slot and revision = p_expected_revision
      returning * into saved;
  end if;
  if saved.user_id is null then
    raise sqlstate 'PT409' using message = 'A newer save exists';
  end if;
  return next saved;
end;
$$;
revoke all on function public.prismet_save_arcade_progress(text,text,bigint,jsonb) from public, anon;
grant execute on function public.prismet_save_arcade_progress(text,text,bigint,jsonb) to authenticated;
commit;
