alter table public.matches add column provider_status text;
alter table public.matches add column regulation_home_score integer check(regulation_home_score between 0 and 100);
alter table public.matches add column regulation_away_score integer check(regulation_away_score between 0 and 100);
alter table public.matches add column provider_updated_at timestamptz not null default '-infinity';
alter table public.matches drop constraint matches_status_check;
alter table public.matches add constraint matches_status_check check(status in ('SCHEDULED','LIVE','FINAL','VOID','SUSPENDED'));
drop function public.ingest_football(jsonb);
create function public.ingest_football(p_rows jsonb,p_asof timestamptz default now()) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; count integer=0; code text; state text; fixture_id text; events_data jsonb; stats_data jsonb;
begin
 if jsonb_typeof(p_rows)<>'array' then raise exception 'Invalid provider response';end if;
 for r in select value from jsonb_array_elements(p_rows) loop
  if r#>>'{fixture,id}' is null or r#>>'{fixture,date}' is null then continue;end if;
  fixture_id='api:'||(r#>>'{fixture,id}');code=r#>>'{fixture,status,short}';
  state=case when code in ('FT','AET','PEN','AWD','WO') then 'FINAL' when code in ('1H','HT','2H','ET','BT','P','LIVE') then 'LIVE' when code in ('CANC','ABD') then 'VOID' when code in ('PST','SUSP','INT') then 'SUSPENDED' else 'SCHEDULED' end;
  if state='FINAL' and (r#>>'{goals,home}' is null or r#>>'{goals,away}' is null) then continue;end if;
  select coalesce(jsonb_agg(concat_ws(' · ',e#>>'{time,elapsed}',e->>'type',e#>>'{player,name}')),'[]') into events_data from jsonb_array_elements(coalesce(r->'events','[]')) e;
  select coalesce(jsonb_object_agg(label,coalesce(h,'—')||' · '||coalesce(a,'—')),'{}') into stats_data from (
   select case v->>'type' when 'Ball Possession' then 'Posesión' when 'Total Shots' then 'Tiros' when 'Shots on Goal' then 'Tiros a puerta' when 'Corner Kicks' then 'Córners' else v->>'type' end label,
    max(v->>'value') filter(where t#>>'{team,id}'=r#>>'{teams,home,id}') h,
    max(v->>'value') filter(where t#>>'{team,id}'=r#>>'{teams,away,id}') a
   from jsonb_array_elements(coalesce(r->'statistics','[]')) t cross join lateral jsonb_array_elements(t->'statistics') v where v->>'type' is not null group by v->>'type'
  ) stats;
  insert into public.matches(id,external_id,home_team,away_team,league_id,league_name,status,home_score,away_score,start_time,source,verified,events,statistics,provider_status,provider_updated_at)
  values(fixture_id,r#>>'{fixture,id}',jsonb_build_object('id','api:'||(r#>>'{teams,home,id}'),'name',r#>>'{teams,home,name}','code',upper(left(r#>>'{teams,home,name}',3)),'logo',r#>>'{teams,home,logo}'),jsonb_build_object('id','api:'||(r#>>'{teams,away,id}'),'name',r#>>'{teams,away,name}','code',upper(left(r#>>'{teams,away,name}',3)),'logo',r#>>'{teams,away,logo}'),
   'api:'||(r#>>'{league,id}'),r#>>'{league,name}',state,(r#>>'{goals,home}')::integer,(r#>>'{goals,away}')::integer,(r#>>'{fixture,date}')::timestamptz,'API_FOOTBALL',state in ('FINAL','VOID'),events_data,stats_data,code,p_asof)
  on conflict(id) do update set status=excluded.status,home_score=excluded.home_score,away_score=excluded.away_score,start_time=excluded.start_time,verified=excluded.verified,home_team=excluded.home_team,away_team=excluded.away_team,provider_status=excluded.provider_status,provider_updated_at=excluded.provider_updated_at,
   events=case when r?'events' then excluded.events else public.matches.events end,statistics=case when r?'statistics' then excluded.statistics else public.matches.statistics end
   where excluded.provider_updated_at>=public.matches.provider_updated_at;
  update public.markets set status='CLOSED' where match_id=fixture_id and exists(select 1 from public.matches f where f.id=fixture_id and (f.status<>'SCHEDULED' or f.start_time<=now()));
  update public.matches set regulation_home_score=coalesce((r#>>'{score,fulltime,home}')::integer,case when code in ('FT','AWD','WO') then (r#>>'{goals,home}')::integer end),regulation_away_score=coalesce((r#>>'{score,fulltime,away}')::integer,case when code in ('FT','AWD','WO') then (r#>>'{goals,away}')::integer end)
   where id=fixture_id and provider_updated_at=p_asof;
  count=count+1;
 end loop;
 return count;
end $$;
revoke execute on function public.ingest_football(jsonb,timestamptz) from public,anon,authenticated;
grant execute on function public.ingest_football(jsonb,timestamptz) to service_role;

create or replace function public.settle_demo_bet(p_bet uuid) returns text language plpgsql security invoker set search_path='' as $$
declare b public.bets; w public.wallets; row record; result text; has_pending boolean=false; has_loss boolean=false; active integer=0; product numeric=1; payout numeric=0;
begin
 -- All financial operations lock wallet before bet: consistent ordering avoids deadlocks.
 select w0.* into w from public.wallets w0 join public.bets b0 on b0.user_id=w0.user_id where b0.id=p_bet for update of w0;
 select * into b from public.bets where id=p_bet for update;
 if not found then raise exception 'Bet missing';end if;
 if b.status<>'PENDING' then return b.status;end if;
 for row in select s.*,f.status fixture_status,f.verified,coalesce(f.regulation_home_score,case when f.source='DEMO' then f.home_score end) h,coalesce(f.regulation_away_score,case when f.source='DEMO' then f.away_score end) a from public.bet_selections s join public.matches f on f.id=s.match_id where s.bet_id=p_bet loop
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
