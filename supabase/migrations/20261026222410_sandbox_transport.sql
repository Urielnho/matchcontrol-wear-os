-- Server-only wire adapter. The mobile/watch never receive a service-role key.
create function public.sandbox_snapshot(p_user uuid) returns jsonb language sql security invoker set search_path='' as $$
select jsonb_build_object(
 'profile',jsonb_build_object('id',p.id,'displayName',p.display_name),
 'balance',(w.balance*100)::bigint,'source',p.data_mode,'notifications',coalesce((select enabled from public.notification_preferences where user_id=p_user),false),
 'fixtures',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'home',f.home_team,'away',f.away_team,'leagueId',f.league_id,'league',f.league_name,'startsAt',(extract(epoch from f.start_time)*1000)::bigint,'status',f.status,'homeScore',f.home_score,'awayScore',f.away_score,'verified',f.verified,'source',f.source,'events',f.events,'statistics',f.statistics) order by f.start_time) from public.matches f where f.source=p.data_mode),'[]'),
 'outcomes',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'matchId',m.match_id,'market',m.market_type,'key',o.outcome_key,'label',o.label,'odds',(o.decimal_odds*10000)::integer,'updatedAt',(extract(epoch from o.updated_at)*1000)::bigint,'open',m.status='OPEN' and f.status='SCHEDULED' and f.start_time>now())) from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where f.source=p.data_mode),'[]'),
 'bets',coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'requestId',b.request_id,'stake',(b.stake*100)::bigint,'totalOdds',b.total_odds::text,'potential',(b.potential_return*100)::bigint,'status',b.status,'createdAt',(extract(epoch from b.created_at)*1000)::bigint,'settledAt',(extract(epoch from b.settled_at)*1000)::bigint,'returned',(b.returned*100)::bigint,
  'picks',coalesce((select jsonb_agg(jsonb_build_object('outcomeId',s.outcome_id,'matchId',s.match_id,'market',s.market_type,'key',s.outcome_key,'label',s.label,'fixtureLabel',s.fixture_label,'lockedOdds',(s.locked_odds*10000)::integer,'status',s.status)) from public.bet_selections s where s.bet_id=b.id),'[]')) order by b.created_at) from public.bets b where b.user_id=p_user),'[]'),
 'transactions',coalesce((select jsonb_agg(jsonb_build_object('id',t.id,'kind',t.transaction_type,'amount',(t.amount*100)::bigint,'reference',t.reference_id,'createdAt',(extract(epoch from t.created_at)*1000)::bigint) order by t.created_at) from public.wallet_transactions t where t.wallet_id=w.id),'[]'),
 'favoriteTeams',coalesce((select jsonb_agg(team_id) from public.favorite_teams where user_id=p_user),'[]'),
 'favoriteLeagues',coalesce((select jsonb_agg(league_id) from public.favorite_leagues where user_id=p_user),'[]'),
 'favoriteMatches',coalesce((select jsonb_agg(match_id) from public.favorite_matches where user_id=p_user),'[]'),
 'updatedAt',(extract(epoch from now())*1000)::bigint,'revision',(extract(epoch from now())*1000)::bigint
) from public.profiles p join public.wallets w on w.user_id=p.id where p.id=p_user;
$$;
revoke execute on function public.sandbox_snapshot(uuid) from public,anon,authenticated;
grant execute on function public.sandbox_snapshot(uuid) to service_role;

-- Called only by a trusted result worker, never by a user's JWT.
create function public.finish_sandbox_fixture(p_match text,p_home integer,p_away integer,p_void boolean default false) returns void language plpgsql security invoker set search_path='' as $$
declare b record; f public.matches;
begin
 select * into f from public.matches where id=p_match for update;
 if not found or f.source<>'DEMO' then raise exception 'Only sandbox fixtures';end if;
 if f.status in ('FINAL','VOID') then
  if (p_void and f.status='VOID') or (not p_void and f.status='FINAL' and f.home_score=p_home and f.away_score=p_away) then return;end if;
  raise exception 'Result already verified';
 end if;
 if not p_void and (p_home is null or p_away is null or p_home not between 0 and 20 or p_away not between 0 and 20) then raise exception 'Invalid score';end if;
 update public.matches set status=case when p_void then 'VOID' else 'FINAL' end,home_score=case when p_void then null else p_home end,away_score=case when p_void then null else p_away end,verified=true where id=p_match;
 update public.markets set status='CLOSED' where match_id=p_match;
 -- Settlement runs separately after this result transaction commits. This avoids
 -- holding fixture locks while acquiring wallet locks in the settlement worker.
end $$;
revoke execute on function public.finish_sandbox_fixture(text,integer,integer,boolean) from public,anon,authenticated;
grant execute on function public.finish_sandbox_fixture(text,integer,integer,boolean) to service_role;

create function public.reserve_provider_request(p_day date) returns boolean language plpgsql security invoker set search_path='' as $$
declare used integer;
begin
 insert into private.provider_budget(day,requests) values(p_day,0) on conflict do nothing;
 update private.provider_budget set requests=requests+1 where day=p_day and requests<95 returning requests into used;
 return used is not null;
end $$;
revoke execute on function public.reserve_provider_request(date) from public,anon,authenticated;
grant execute on function public.reserve_provider_request(date) to service_role;

create function public.sports_cache_read(p_key text) returns jsonb language sql security invoker set search_path='' as $$
 select jsonb_build_object('payload',payload,'updated_at',updated_at) from private.provider_cache where cache_key=p_key;
$$;
create function public.sports_cache_write(p_key text,p_payload jsonb) returns void language sql security invoker set search_path='' as $$
 insert into private.provider_cache(cache_key,payload) values(p_key,p_payload) on conflict(cache_key) do update set payload=excluded.payload,updated_at=now();
$$;
revoke execute on function public.sports_cache_read(text),public.sports_cache_write(text,jsonb) from public,anon,authenticated;
grant execute on function public.sports_cache_read(text),public.sports_cache_write(text,jsonb) to service_role;
