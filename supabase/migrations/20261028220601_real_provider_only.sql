-- Only trusted provider events are exposed. Historical educational records remain as an audit trail.
update public.profiles set data_mode='API_FOOTBALL' where data_mode<>'API_FOOTBALL';
alter table public.profiles drop constraint if exists profiles_data_mode_check;
alter table public.profiles add constraint profiles_data_mode_check check(data_mode='API_FOOTBALL');
drop policy if exists read_matches on public.matches;
create policy read_matches on public.matches for select to authenticated using(source='API_FOOTBALL' and demo_owner is null);

create function public.real_favorite_exists(p_kind text,p_target text) returns boolean language sql stable security invoker set search_path='' as $$
select exists(select 1 from public.matches f where f.source='API_FOOTBALL' and f.demo_owner is null and case p_kind
 when 'match' then f.id=p_target when 'league' then f.league_id=p_target
 when 'team' then f.home_team->>'id'=p_target or f.away_team->>'id'=p_target or exists(select 1 from jsonb_array_elements(f.participants) d where d->>'id'=p_target) else false end);
$$;
revoke execute on function public.real_favorite_exists(text,text) from public,anon;
grant execute on function public.real_favorite_exists(text,text) to authenticated,service_role;

create or replace function public.place_demo_bet(p_user uuid,p_request uuid,p_stake numeric,p_selections jsonb) returns uuid language plpgsql security invoker set search_path='' as $$
declare wallet public.wallets; existing public.bets; oid text; row record; ids text[]='{}'; product numeric=1; bid uuid=gen_random_uuid(); payload jsonb; event_source text;
begin
 if p_user is null or p_request is null or p_stake is null or p_stake<1 or p_stake>100000 or p_stake<>trunc(p_stake,2) or p_selections is null or jsonb_typeof(p_selections)<>'object' then
  raise exception 'Invalid stake or selections';
 end if;
 payload=jsonb_build_object('stake',p_stake,'outcomes',p_selections);
 select * into wallet from public.wallets where user_id=p_user for update;
 if not found then raise exception 'Wallet missing'; end if;
 select * into existing from public.bets where user_id=p_user and request_id=p_request;
 if found then if exists(select 1 from public.bet_selections bs join public.matches f on f.id=bs.match_id where bs.bet_id=existing.id and f.source<>'API_FOOTBALL') then raise exception 'Only provider events are available';end if;if existing.request_payload<>payload then raise exception 'Idempotency conflict'; end if;return existing.id;end if;
 if wallet.balance<p_stake then raise exception 'Insufficient credits'; end if;
 if (select count(*) from jsonb_object_keys(p_selections)) not between 1 and 10 then raise exception 'Select 1 to 10 events';end if;
 for oid in select jsonb_object_keys(p_selections) order by 1 loop
  select o.*,m.match_id,m.market_type,m.status market_status,f.start_time,f.status fixture_status,f.source,f.sport,f.participants,f.demo_owner into row from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id=oid::uuid for update of o,m,f;
  if not found or row.market_status<>'OPEN' or row.fixture_status<>'SCHEDULED' or row.start_time<=now() or row.source<>'API_FOOTBALL' then raise exception 'Closed or unavailable event';end if;
  if row.demo_owner is not null then raise exception 'Only provider events are available';end if;
  if row.source<>(select data_mode from public.profiles where id=p_user) then raise exception 'Event is not in the selected data mode';end if;
  if row.source='API_FOOTBALL' and row.updated_at<now()-interval '30 minutes' then raise exception 'Stale odds';end if;
  if event_source is not null and event_source<>row.source then raise exception 'Cannot combine real and demo events';end if;
  event_source=row.source;
  if row.match_id=any(ids) then raise exception 'Only one selection per event';end if;
  if row.decimal_odds*10000<>(p_selections->>oid)::numeric then raise exception 'Odds changed';end if;
  if not public.valid_sport_market(row.sport,row.market_type,row.outcome_key,row.participants) then raise exception 'Invalid market outcome';end if;
  ids=array_append(ids,row.match_id);product=product*row.decimal_odds;
 end loop;
 insert into public.bets(id,user_id,request_id,request_payload,stake,total_odds,potential_return) values(bid,p_user,p_request,payload,p_stake,product,trunc(p_stake*product,2));
 insert into public.bet_selections(bet_id,match_id,outcome_id,market_type,outcome_key,label,fixture_label,locked_odds)
 select bid,m.match_id,o.id,m.market_type,o.outcome_key,o.label,case when f.sport='FORMULA1' then f.home_team->>'name' else (f.home_team->>'code')||' · '||(f.away_team->>'code') end,o.decimal_odds from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id::text in (select jsonb_object_keys(p_selections));
 update public.wallets set balance=balance-p_stake,updated_at=now() where id=wallet.id;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(wallet.id,'STAKE',-p_stake,bid::text);
 return bid;
end $$;
create or replace function public.sandbox_snapshot(p_user uuid) returns jsonb language sql security invoker set search_path='' as $$
select jsonb_build_object(
 'profile',jsonb_build_object('id',p.id,'displayName',p.display_name),
 'balance',(w.balance*100)::bigint,'source','API_FOOTBALL','notifications',coalesce((select enabled from public.notification_preferences where user_id=p_user),false),
 'fixtures',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'home',f.home_team,'away',f.away_team,'leagueId',f.league_id,'league',f.league_name,'startsAt',(extract(epoch from f.start_time)*1000)::bigint,'status',f.status,'homeScore',f.home_score,'awayScore',f.away_score,'verified',f.verified,'source',f.source,'events',f.events,'statistics',f.statistics,'sport',f.sport,'participants',f.participants,'raceResults',f.race_results,'classificationComplete',f.classification_complete,'regulationHomeScore',f.regulation_home_score,'regulationAwayScore',f.regulation_away_score) order by f.start_time) from public.matches f where f.source='API_FOOTBALL' and f.demo_owner is null),'[]'),
 'outcomes',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'matchId',m.match_id,'market',m.market_type,'key',o.outcome_key,'label',o.label,'odds',(o.decimal_odds*10000)::integer,'updatedAt',(extract(epoch from o.updated_at)*1000)::bigint,'open',m.status='OPEN' and f.status='SCHEDULED' and f.start_time>now() and o.updated_at>now()-interval '30 minutes')) from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where f.source='API_FOOTBALL' and f.demo_owner is null),'[]'),
 'bets',coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'requestId',b.request_id,'stake',(b.stake*100)::bigint,'totalOdds',b.total_odds::text,'potential',(b.potential_return*100)::bigint,'source',coalesce((select f.source from public.bet_selections bs join public.matches f on f.id=bs.match_id where bs.bet_id=b.id limit 1),'DEMO'),'status',b.status,'createdAt',(extract(epoch from b.created_at)*1000)::bigint,'settledAt',(extract(epoch from b.settled_at)*1000)::bigint,'returned',(b.returned*100)::bigint,
  'picks',coalesce((select jsonb_agg(jsonb_build_object('outcomeId',s.outcome_id,'matchId',s.match_id,'market',s.market_type,'key',s.outcome_key,'label',s.label,'fixtureLabel',s.fixture_label,'lockedOdds',(s.locked_odds*10000)::integer,'status',s.status,'sport',(select sport from public.matches where id=s.match_id))) from public.bet_selections s where s.bet_id=b.id),'[]')) order by b.created_at) from public.bets b where b.user_id=p_user and exists(select 1 from public.bet_selections rb join public.matches rf on rf.id=rb.match_id where rb.bet_id=b.id) and not exists(select 1 from public.bet_selections nb join public.matches nf on nf.id=nb.match_id where nb.bet_id=b.id and nf.source<>'API_FOOTBALL')),'[]'),
 'transactions',coalesce((select jsonb_agg(jsonb_build_object('id',t.id,'kind',t.transaction_type,'amount',(t.amount*100)::bigint,'reference',t.reference_id,'createdAt',(extract(epoch from t.created_at)*1000)::bigint) order by t.created_at) from public.wallet_transactions t where t.wallet_id=w.id and (t.transaction_type='INITIAL' or exists(select 1 from public.bets b where b.id::text=t.reference_id and exists(select 1 from public.bet_selections rb join public.matches rf on rf.id=rb.match_id where rb.bet_id=b.id) and not exists(select 1 from public.bet_selections nb join public.matches nf on nf.id=nb.match_id where nb.bet_id=b.id and nf.source<>'API_FOOTBALL')))),'[]'),
 'favoriteTeams',coalesce((select jsonb_agg(team_id) from public.favorite_teams ft where user_id=p_user and public.real_favorite_exists('team',ft.team_id)),'[]'),
 'favoriteLeagues',coalesce((select jsonb_agg(league_id) from public.favorite_leagues fl where user_id=p_user and public.real_favorite_exists('league',fl.league_id)),'[]'),
 'favoriteMatches',coalesce((select jsonb_agg(match_id) from public.favorite_matches fm where user_id=p_user and public.real_favorite_exists('match',fm.match_id)),'[]'),
 'dataUpdatedAt',coalesce((select (extract(epoch from max(nullif(f.provider_updated_at,'-infinity'::timestamptz)))*1000)::bigint from public.matches f where f.source='API_FOOTBALL' and f.demo_owner is null),0),'updatedAt',(extract(epoch from now())*1000)::bigint,'revision',(extract(epoch from now())*1000)::bigint
) from public.profiles p join public.wallets w on w.user_id=p.id where p.id=p_user;
$$;

-- Retire generation, manual result editing and balance-reset RPCs from every application role.
revoke execute on function public.create_multisport_demo(uuid,uuid),public.finish_user_demo(uuid,text,integer,integer,jsonb,boolean),public.finish_sandbox_fixture(text,integer,integer,boolean),public.reset_demo_wallet(uuid,uuid) from public,anon,authenticated,service_role;

-- Public feed is formatted by the backend service role; anonymous callers receive no table grants.
create function public.public_scores_snapshot(p_sport text,p_date date,p_timezone text default 'UTC',p_id text default null) returns jsonb language plpgsql security invoker set search_path='' as $$
declare result jsonb;
begin
 if p_sport not in ('FOOTBALL','BASKETBALL','BASEBALL','FORMULA1') or p_date not between current_date-7 and current_date+7 then raise exception 'Unsupported sports date';end if;
 with selected as (
  select f.* from public.matches f where f.source='API_FOOTBALL' and f.demo_owner is null and f.sport=p_sport
   and (p_id is null or f.id=p_id)
   and (p_id is not null or case when p_sport='FORMULA1' then extract(year from f.start_time)=extract(year from p_date)
    else (f.start_time at time zone p_timezone)::date=p_date end)
  order by f.start_time limit 500
 )
 select jsonb_build_object(
 'profile',jsonb_build_object('id','','displayName',''),'balance',0,'source','API_FOOTBALL','accountSignedIn',false,
 'fixtures',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'home',f.home_team,'away',f.away_team,'leagueId',f.league_id,'league',f.league_name,'startsAt',(extract(epoch from f.start_time)*1000)::bigint,'status',f.status,'homeScore',f.home_score,'awayScore',f.away_score,'verified',f.verified,'source',f.source,'events',f.events,'statistics',f.statistics,'sport',f.sport,'participants',f.participants,'raceResults',f.race_results,'classificationComplete',f.classification_complete,'regulationHomeScore',f.regulation_home_score,'regulationAwayScore',f.regulation_away_score) order by f.start_time) from selected f),'[]'),
 'outcomes',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'matchId',m.match_id,'market',m.market_type,'key',o.outcome_key,'label',o.label,'odds',(o.decimal_odds*10000)::integer,'updatedAt',(extract(epoch from o.updated_at)*1000)::bigint,'open',m.status='OPEN' and f.status='SCHEDULED' and f.start_time>now() and o.updated_at>now()-interval '30 minutes')) from public.market_outcomes o join public.markets m on m.id=o.market_id join selected f on f.id=m.match_id),'[]'),
 'bets','[]'::jsonb,'transactions','[]'::jsonb,'favoriteTeams','[]'::jsonb,'favoriteLeagues','[]'::jsonb,'favoriteMatches','[]'::jsonb,'notifications',false,
 'dataUpdatedAt',coalesce((select (extract(epoch from max(nullif(f.provider_updated_at,'-infinity'::timestamptz)))*1000)::bigint from selected f),0),
 'updatedAt',(extract(epoch from now())*1000)::bigint,'revision',(extract(epoch from now())*1000)::bigint
 ) into result;
 return result;
end $$;
revoke execute on function public.public_scores_snapshot(text,date,text,text) from public,anon,authenticated;
grant execute on function public.public_scores_snapshot(text,date,text,text) to service_role;

create function public.pending_real_bets() returns table(id uuid) language sql security invoker set search_path='' as $$
 select b.id from public.bets b where b.status='PENDING'
 and exists(select 1 from public.bet_selections s where s.bet_id=b.id)
 and not exists(select 1 from public.bet_selections s join public.matches f on f.id=s.match_id where s.bet_id=b.id and f.source<>'API_FOOTBALL')
 order by b.created_at limit 200;
$$;
revoke execute on function public.pending_real_bets() from public,anon,authenticated;
grant execute on function public.pending_real_bets() to service_role;
create or replace function public.reconcile_user_bets(p_user uuid) returns void language plpgsql security invoker set search_path='' as $$
declare b record;
begin
 for b in select b.id from public.bets b where b.user_id=p_user and b.status='PENDING' and exists(select 1 from public.bet_selections bs where bs.bet_id=b.id) and not exists(select 1 from public.bet_selections bs join public.matches f on f.id=bs.match_id where bs.bet_id=b.id and f.source<>'API_FOOTBALL') order by b.created_at loop
  perform public.settle_demo_bet(b.id);
 end loop;
end $$;

-- Cached quotes retain their provider cache timestamp; reading them cannot extend freshness.
create function public.provider_quote_time(p_match text,p_asof timestamptz) returns void language sql security invoker set search_path='' as $$
 update public.market_outcomes o set updated_at=least(o.updated_at,p_asof)
 from public.markets m join public.matches f on f.id=m.match_id
 where o.market_id=m.id and f.id=p_match and f.source='API_FOOTBALL' and p_asof is not null;
$$;
revoke execute on function public.provider_quote_time(text,timestamptz) from public,anon,authenticated;
grant execute on function public.provider_quote_time(text,timestamptz) to service_role;
