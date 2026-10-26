-- MatchControl sandbox: credits have no monetary value. No payment endpoints.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
create table public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 display_name text not null default 'Estudiante' check(length(display_name) between 1 and 80),
 data_mode text not null default 'API_FOOTBALL' check(data_mode in ('DEMO','API_FOOTBALL')),
 created_at timestamptz not null default now()
);
create table public.wallets (
 id uuid primary key default gen_random_uuid(), user_id uuid not null unique references public.profiles(id),
 balance numeric(18,2) not null default 10000 check(balance>=0), updated_at timestamptz not null default now()
);
create table public.matches (
 id text primary key, external_id text unique, home_team jsonb not null, away_team jsonb not null,
 league_id text not null, league_name text not null, status text not null check(status in ('SCHEDULED','LIVE','FINAL','VOID')),
 home_score integer check(home_score between 0 and 100), away_score integer check(away_score between 0 and 100),
 start_time timestamptz not null, source text not null check(source in ('DEMO','API_FOOTBALL')),
 verified boolean not null default false, events jsonb not null default '[]', statistics jsonb not null default '{}',
 check(status<>'FINAL' or (home_score is not null and away_score is not null and verified))
);
create index matches_start on public.matches(start_time,status);
create table public.markets (
 id uuid primary key default gen_random_uuid(), match_id text not null references public.matches(id),
 market_type text not null check(market_type in ('WINNER','DOUBLE_CHANCE','OVER_UNDER_15','OVER_UNDER_25','BOTH_SCORE')),
 status text not null default 'OPEN' check(status in ('OPEN','CLOSED')), unique(match_id,market_type)
);
create table public.market_outcomes (
 id uuid primary key default gen_random_uuid(), market_id uuid not null references public.markets(id),
 outcome_key text not null check(outcome_key in ('1','X','2','1X','X2','12','OVER','UNDER','YES','NO')),
 label text not null, decimal_odds numeric(12,4) not null check(decimal_odds>1 and decimal_odds<=100),
 updated_at timestamptz not null default now(), unique(market_id,outcome_key)
);
create table public.bets (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id),
 request_id uuid not null, request_payload jsonb not null, stake numeric(18,2) not null check(stake>=1),
 total_odds numeric not null check(total_odds>1), potential_return numeric(18,2) not null,
 status text not null default 'PENDING' check(status in ('PENDING','WON','LOST','VOID')),
 returned numeric(18,2) not null default 0 check(returned>=0), created_at timestamptz not null default now(), settled_at timestamptz,
 unique(user_id,request_id)
);
create index bets_user_status on public.bets(user_id,status,created_at desc);
create table public.bet_selections (
 id uuid primary key default gen_random_uuid(), bet_id uuid not null references public.bets(id),
 match_id text not null references public.matches(id), outcome_id uuid not null references public.market_outcomes(id),
 market_type text not null, outcome_key text not null, label text not null, fixture_label text not null,
 locked_odds numeric(12,4) not null check(locked_odds>1 and locked_odds<=100),
 status text not null default 'PENDING' check(status in ('PENDING','WON','LOST','VOID')), unique(bet_id,match_id)
);
create index selections_bet on public.bet_selections(bet_id);
create table public.wallet_transactions (
 id uuid primary key default gen_random_uuid(), wallet_id uuid not null references public.wallets(id),
 transaction_type text not null check(transaction_type in ('INITIAL','STAKE','SETTLEMENT','REFUND','RESET')),
 amount numeric(18,2) not null, reference_id text not null, created_at timestamptz not null default now(),
 unique(wallet_id,transaction_type,reference_id)
);
create index transactions_wallet_time on public.wallet_transactions(wallet_id,created_at desc);
create table public.favorite_teams (id uuid primary key default gen_random_uuid(),user_id uuid not null references public.profiles(id),team_id text not null,unique(user_id,team_id));
create table public.favorite_leagues (id uuid primary key default gen_random_uuid(),user_id uuid not null references public.profiles(id),league_id text not null,unique(user_id,league_id));
create table public.favorite_matches (id uuid primary key default gen_random_uuid(),user_id uuid not null references public.profiles(id),match_id text not null references public.matches(id),unique(user_id,match_id));
create table public.notification_preferences (user_id uuid primary key references public.profiles(id),enabled boolean not null default false);
create table private.provider_cache (cache_key text primary key,payload jsonb not null,updated_at timestamptz not null default now());
create table private.provider_budget (day date primary key,requests integer not null default 0 check(requests between 0 and 95));

-- Explicit privileges + ownership. Clients cannot mutate balances, odds or settlements.
do $$ declare t text; begin
 foreach t in array array['profiles','wallets','matches','markets','market_outcomes','bets','bet_selections','wallet_transactions','favorite_teams','favorite_leagues','favorite_matches','notification_preferences'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('revoke all on public.%I from anon, authenticated',t);
  execute format('grant select on public.%I to authenticated',t);
  execute format('grant all on public.%I to service_role',t);
 end loop;
end $$;
alter table private.provider_cache enable row level security;
alter table private.provider_budget enable row level security;
grant usage on schema private to service_role;
grant all on all tables in schema private to service_role;
create policy own_profiles on public.profiles for select to authenticated using ((select auth.uid())=id);
grant update(display_name) on public.profiles to authenticated;
create policy edit_own_profile on public.profiles for update to authenticated using ((select auth.uid())=id) with check ((select auth.uid())=id);
create policy own_wallet on public.wallets for select to authenticated using ((select auth.uid())=user_id);
create policy own_bets on public.bets for select to authenticated using ((select auth.uid())=user_id);
create policy own_selections on public.bet_selections for select to authenticated using (exists(select 1 from public.bets b where b.id=bet_id and b.user_id=(select auth.uid())));
create policy own_movements on public.wallet_transactions for select to authenticated using (exists(select 1 from public.wallets w where w.id=wallet_id and w.user_id=(select auth.uid())));
create policy read_matches on public.matches for select to authenticated using (true);
create policy read_markets on public.markets for select to authenticated using (true);
create policy read_outcomes on public.market_outcomes for select to authenticated using (true);
create policy own_teams on public.favorite_teams for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create policy own_leagues on public.favorite_leagues for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create policy own_matches on public.favorite_matches for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
grant insert,delete on public.favorite_teams,public.favorite_leagues,public.favorite_matches to authenticated;
create policy own_notifications on public.notification_preferences for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
grant insert,update(enabled) on public.notification_preferences to authenticated;

create function public.ensure_demo_user(p_user uuid) returns void language plpgsql security invoker set search_path='' as $$
declare w uuid;
begin
 insert into public.profiles(id) values(p_user) on conflict do nothing;
 insert into public.wallets(user_id) values(p_user) on conflict do nothing;
 insert into public.notification_preferences(user_id) values(p_user) on conflict do nothing;
 select id into w from public.wallets where user_id=p_user;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(w,'INITIAL',10000,'initial') on conflict do nothing;
end $$;

create function public.place_demo_bet(p_user uuid,p_request uuid,p_stake numeric,p_selections jsonb) returns uuid language plpgsql security invoker set search_path='' as $$
declare wallet public.wallets; existing public.bets; oid text; row record; ids text[]='{}'; product numeric=1; bid uuid=gen_random_uuid(); payload jsonb; event_source text;
begin
 if p_user is null or p_request is null or p_stake is null or p_stake<1 or p_stake>100000 or p_stake<>trunc(p_stake,2) or p_selections is null or jsonb_typeof(p_selections)<>'object' then
  raise exception 'Invalid stake or selections';
 end if;
 payload=jsonb_build_object('stake',p_stake,'outcomes',p_selections);
 select * into wallet from public.wallets where user_id=p_user for update;
 if not found then raise exception 'Wallet missing'; end if;
 select * into existing from public.bets where user_id=p_user and request_id=p_request;
 if found then if existing.request_payload<>payload then raise exception 'Idempotency conflict'; end if;return existing.id;end if;
 if wallet.balance<p_stake then raise exception 'Insufficient credits'; end if;
 if (select count(*) from jsonb_object_keys(p_selections)) not between 1 and 10 then raise exception 'Select 1 to 10 events';end if;
 for oid in select jsonb_object_keys(p_selections) order by 1 loop
  select o.*,m.match_id,m.market_type,m.status market_status,f.start_time,f.status fixture_status,f.source into row from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id=oid::uuid for update of o,m,f;
  if not found or row.market_status<>'OPEN' or row.fixture_status<>'SCHEDULED' or row.start_time<=now() or row.source not in ('DEMO','API_FOOTBALL') then raise exception 'Closed or unavailable event';end if;
  if event_source is not null and event_source<>row.source then raise exception 'Cannot combine real and demo events';end if;
  event_source=row.source;
  if row.match_id=any(ids) then raise exception 'Only one selection per event';end if;
  if row.decimal_odds*10000<>(p_selections->>oid)::numeric then raise exception 'Odds changed';end if;
  if not ((row.market_type='WINNER' and row.outcome_key in ('1','X','2')) or (row.market_type='DOUBLE_CHANCE' and row.outcome_key in ('1X','X2','12')) or (row.market_type in ('OVER_UNDER_15','OVER_UNDER_25') and row.outcome_key in ('OVER','UNDER')) or (row.market_type='BOTH_SCORE' and row.outcome_key in ('YES','NO'))) then raise exception 'Invalid market outcome';end if;
  ids=array_append(ids,row.match_id);product=product*row.decimal_odds;
 end loop;
 insert into public.bets(id,user_id,request_id,request_payload,stake,total_odds,potential_return) values(bid,p_user,p_request,payload,p_stake,product,trunc(p_stake*product,2));
 insert into public.bet_selections(bet_id,match_id,outcome_id,market_type,outcome_key,label,fixture_label,locked_odds)
 select bid,m.match_id,o.id,m.market_type,o.outcome_key,o.label,(f.home_team->>'code')||' · '||(f.away_team->>'code'),o.decimal_odds from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id::text in (select jsonb_object_keys(p_selections));
 update public.wallets set balance=balance-p_stake,updated_at=now() where id=wallet.id;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(wallet.id,'STAKE',-p_stake,bid::text);
 return bid;
end $$;

create function public.settle_demo_bet(p_bet uuid) returns text language plpgsql security invoker set search_path='' as $$
declare b public.bets; w public.wallets; row record; result text; has_pending boolean=false; has_loss boolean=false; active integer=0; product numeric=1; payout numeric=0;
begin
 -- All financial operations lock wallet before bet: consistent ordering avoids deadlocks.
 select w0.* into w from public.wallets w0 join public.bets b0 on b0.user_id=w0.user_id where b0.id=p_bet for update of w0;
 select * into b from public.bets where id=p_bet for update;
 if not found then raise exception 'Bet missing';end if;
 if b.status<>'PENDING' then return b.status;end if;
 for row in select s.*,f.status fixture_status,f.verified,f.home_score h,f.away_score a from public.bet_selections s join public.matches f on f.id=s.match_id where s.bet_id=p_bet loop
  result='PENDING';
  if row.fixture_status='VOID' then result='VOID';
  elsif row.fixture_status='FINAL' and row.verified and row.h is not null and row.a is not null then
   result=case when case row.market_type
    when 'WINNER' then (row.outcome_key='1' and row.h>row.a) or (row.outcome_key='X' and row.h=row.a) or (row.outcome_key='2' and row.h<row.a)
    when 'DOUBLE_CHANCE' then (row.outcome_key='1X' and row.h>=row.a) or (row.outcome_key='X2' and row.h<=row.a) or (row.outcome_key='12' and row.h<>row.a)
    when 'OVER_UNDER_15' then (row.outcome_key='OVER' and row.h+row.a>=2) or (row.outcome_key='UNDER' and row.h+row.a<=1)
    when 'OVER_UNDER_25' then (row.outcome_key='OVER' and row.h+row.a>=3) or (row.outcome_key='UNDER' and row.h+row.a<=2)
    when 'BOTH_SCORE' then (row.outcome_key='YES' and row.h>0 and row.a>0) or (row.outcome_key='NO' and (row.h=0 or row.a=0))
    else false end then 'WON' else 'LOST' end;
  end if;
  update public.bet_selections set status=result where id=row.id;
  if result='PENDING' then has_pending=true;end if;
  if result='LOST' then has_loss=true;end if;
  if result<>'VOID' then active=active+1;product=product*row.locked_odds;end if;
 end loop;
 if has_pending then return 'PENDING';end if;
 result=case when has_loss then 'LOST' when active=0 then 'VOID' else 'WON' end;
 payout=case when result='LOST' then 0 when result='VOID' then b.stake else trunc(b.stake*product,2) end;
 update public.bets set status=result,returned=payout,settled_at=now() where id=p_bet;
 update public.wallets set balance=balance+payout,updated_at=now() where id=w.id;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(w.id,case when result='VOID' then 'REFUND' else 'SETTLEMENT' end,payout,p_bet::text);
 return result;
end $$;

create function public.reset_demo_wallet(p_user uuid,p_request uuid) returns void language plpgsql security invoker set search_path='' as $$
declare w public.wallets;
begin
 select * into w from public.wallets where user_id=p_user for update;
 if exists(select 1 from public.wallet_transactions where wallet_id=w.id and transaction_type='RESET' and reference_id=p_request::text) then return;end if;
 if exists(select 1 from public.bets where user_id=p_user and status='PENDING') then raise exception 'Pending bets';end if;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(w.id,'RESET',10000-w.balance,p_request::text);
 update public.wallets set balance=10000,updated_at=now() where id=w.id;
end $$;

revoke execute on function public.ensure_demo_user(uuid),public.place_demo_bet(uuid,uuid,numeric,jsonb),public.settle_demo_bet(uuid),public.reset_demo_wallet(uuid,uuid) from public,anon,authenticated;
grant execute on function public.ensure_demo_user(uuid),public.place_demo_bet(uuid,uuid,numeric,jsonb),public.settle_demo_bet(uuid),public.reset_demo_wallet(uuid,uuid) to service_role;
