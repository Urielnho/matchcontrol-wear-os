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
 if found then if existing.request_payload<>payload then raise exception 'Idempotency conflict'; end if;return existing.id;end if;
 if wallet.balance<p_stake then raise exception 'Insufficient credits'; end if;
 if (select count(*) from jsonb_object_keys(p_selections)) not between 1 and 10 then raise exception 'Select 1 to 10 events';end if;
 for oid in select jsonb_object_keys(p_selections) order by 1 loop
  select o.*,m.match_id,m.market_type,m.status market_status,f.start_time,f.status fixture_status,f.source into row from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id=oid::uuid for update of o,m,f;
  if not found or row.market_status<>'OPEN' or row.fixture_status<>'SCHEDULED' or row.start_time<=now() or row.source not in ('DEMO','API_FOOTBALL') then raise exception 'Closed or unavailable event';end if;
  if row.source<>(select data_mode from public.profiles where id=p_user) then raise exception 'Event is not in the selected data mode';end if;
  if row.source='API_FOOTBALL' and row.updated_at<now()-interval '30 minutes' then raise exception 'Stale odds';end if;
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
create or replace function public.sandbox_snapshot(p_user uuid) returns jsonb language sql security invoker set search_path='' as $$
select jsonb_build_object(
 'profile',jsonb_build_object('id',p.id,'displayName',p.display_name),
 'balance',(w.balance*100)::bigint,'source',p.data_mode,'notifications',coalesce((select enabled from public.notification_preferences where user_id=p_user),false),
 'fixtures',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'home',f.home_team,'away',f.away_team,'leagueId',f.league_id,'league',f.league_name,'startsAt',(extract(epoch from f.start_time)*1000)::bigint,'status',f.status,'homeScore',f.home_score,'awayScore',f.away_score,'verified',f.verified,'source',f.source,'events',f.events,'statistics',f.statistics) order by f.start_time) from public.matches f where f.source=p.data_mode),'[]'),
 'outcomes',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'matchId',m.match_id,'market',m.market_type,'key',o.outcome_key,'label',o.label,'odds',(o.decimal_odds*10000)::integer,'updatedAt',(extract(epoch from o.updated_at)*1000)::bigint,'open',m.status='OPEN' and f.status='SCHEDULED' and f.start_time>now())) from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where f.source=p.data_mode),'[]'),
 'bets',coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'requestId',b.request_id,'stake',(b.stake*100)::bigint,'totalOdds',b.total_odds::text,'potential',(b.potential_return*100)::bigint,'source',coalesce((select f.source from public.bet_selections bs join public.matches f on f.id=bs.match_id where bs.bet_id=b.id limit 1),'DEMO'),'status',b.status,'createdAt',(extract(epoch from b.created_at)*1000)::bigint,'settledAt',(extract(epoch from b.settled_at)*1000)::bigint,'returned',(b.returned*100)::bigint,
  'picks',coalesce((select jsonb_agg(jsonb_build_object('outcomeId',s.outcome_id,'matchId',s.match_id,'market',s.market_type,'key',s.outcome_key,'label',s.label,'fixtureLabel',s.fixture_label,'lockedOdds',(s.locked_odds*10000)::integer,'status',s.status)) from public.bet_selections s where s.bet_id=b.id),'[]')) order by b.created_at) from public.bets b where b.user_id=p_user),'[]'),
 'transactions',coalesce((select jsonb_agg(jsonb_build_object('id',t.id,'kind',t.transaction_type,'amount',(t.amount*100)::bigint,'reference',t.reference_id,'createdAt',(extract(epoch from t.created_at)*1000)::bigint) order by t.created_at) from public.wallet_transactions t where t.wallet_id=w.id),'[]'),
 'favoriteTeams',coalesce((select jsonb_agg(team_id) from public.favorite_teams where user_id=p_user),'[]'),
 'favoriteLeagues',coalesce((select jsonb_agg(league_id) from public.favorite_leagues where user_id=p_user),'[]'),
 'favoriteMatches',coalesce((select jsonb_agg(match_id) from public.favorite_matches where user_id=p_user),'[]'),
 'dataUpdatedAt',coalesce((select (extract(epoch from max(nullif(f.provider_updated_at,'-infinity'::timestamptz)))*1000)::bigint from public.matches f where f.source=p.data_mode),0),'updatedAt',(extract(epoch from now())*1000)::bigint,'revision',(extract(epoch from now())*1000)::bigint
) from public.profiles p join public.wallets w on w.user_id=p.id where p.id=p_user;
$$;
