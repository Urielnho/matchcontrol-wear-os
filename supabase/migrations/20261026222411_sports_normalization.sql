create function public.ingest_football(p_rows jsonb) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; count integer=0; code text; state text; fixture_id text;
begin
 if jsonb_typeof(p_rows)<>'array' then raise exception 'Invalid provider response';end if;
 for r in select value from jsonb_array_elements(p_rows) loop
  if r#>>'{fixture,id}' is null or r#>>'{fixture,date}' is null then continue;end if;
  fixture_id='api:'||(r#>>'{fixture,id}');code=r#>>'{fixture,status,short}';
  state=case when code in ('FT','AET','PEN','AWD','WO') then 'FINAL' when code in ('1H','HT','2H','ET','BT','P','LIVE') then 'LIVE' when code in ('CANC','ABD') then 'VOID' else 'SCHEDULED' end;
  -- A partial result never becomes final/verified.
  if state='FINAL' and (r#>>'{goals,home}' is null or r#>>'{goals,away}' is null) then continue;end if;
  insert into public.matches(id,external_id,home_team,away_team,league_id,league_name,status,home_score,away_score,start_time,source,verified)
  values(fixture_id,r#>>'{fixture,id}',jsonb_build_object('id','api:'||(r#>>'{teams,home,id}'),'name',r#>>'{teams,home,name}','code',upper(left(r#>>'{teams,home,name}',3))),jsonb_build_object('id','api:'||(r#>>'{teams,away,id}'),'name',r#>>'{teams,away,name}','code',upper(left(r#>>'{teams,away,name}',3))),
   'api:'||(r#>>'{league,id}'),r#>>'{league,name}',state,(r#>>'{goals,home}')::integer,(r#>>'{goals,away}')::integer,(r#>>'{fixture,date}')::timestamptz,'API_FOOTBALL',state in ('FINAL','VOID'))
  on conflict(id) do update set status=excluded.status,home_score=excluded.home_score,away_score=excluded.away_score,start_time=excluded.start_time,verified=excluded.verified;
  if state<>'SCHEDULED' or code in ('PST','SUSP','INT','TBD') then update public.markets set status='CLOSED' where match_id=fixture_id;end if;
  count=count+1;
 end loop;
 return count;
end $$;
revoke execute on function public.ingest_football(jsonb) from public,anon,authenticated;
grant execute on function public.ingest_football(jsonb) to service_role;

-- Odds are admitted only when the provider actually publishes supported markets.
create function public.ingest_football_odds(p_rows jsonb) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; bet jsonb; val jsonb; kind text; key text; mid uuid; count integer=0; price numeric; label text;
begin
 for r in select value from jsonb_array_elements(p_rows) loop
  if not exists(select 1 from public.matches where id='api:'||(r#>>'{fixture,id}') and source='API_FOOTBALL' and status='SCHEDULED' and start_time>now()) then continue;end if;
  -- One bookmaker per event: never mix prices from different bookmakers.
  for bet in select value from jsonb_array_elements(coalesce(r#>'{bookmakers,0,bets}','[]')) loop
   kind=case (bet->>'id')::integer when 1 then 'WINNER' when 12 then 'DOUBLE_CHANCE' when 8 then 'BOTH_SCORE' else null end;
   if kind is null then continue;end if;
   for val in select value from jsonb_array_elements(bet->'values') loop
    label=val->>'value';key=case kind when 'WINNER' then case label when 'Home' then '1' when 'Draw' then 'X' when 'Away' then '2' end when 'BOTH_SCORE' then case label when 'Yes' then 'YES' when 'No' then 'NO' end when 'DOUBLE_CHANCE' then case label when 'Home/Draw' then '1X' when 'Draw/Away' then 'X2' when 'Home/Away' then '12' end end;
    if key is null then continue;end if;
    price=(val->>'odd')::numeric;if price<=1 or price>100 then continue;end if;
    insert into public.markets(match_id,market_type) values('api:'||(r#>>'{fixture,id}'),kind) on conflict(match_id,market_type) do update set status='OPEN' returning id into mid;
    insert into public.market_outcomes(market_id,outcome_key,label,decimal_odds) values(mid,key,label,price) on conflict(market_id,outcome_key) do update set decimal_odds=excluded.decimal_odds,updated_at=now();
    count=count+1;
   end loop;
  end loop;
 end loop;
 return count;
end $$;
revoke execute on function public.ingest_football_odds(jsonb) from public,anon,authenticated;
grant execute on function public.ingest_football_odds(jsonb) to service_role;

create function public.reconcile_user_bets(p_user uuid) returns void language plpgsql security invoker set search_path='' as $$
declare b record;
begin
 for b in select id from public.bets where user_id=p_user and status='PENDING' order by created_at loop
  perform public.settle_demo_bet(b.id);
 end loop;
end $$;
revoke execute on function public.reconcile_user_bets(uuid) from public,anon,authenticated;
grant execute on function public.reconcile_user_bets(uuid) to service_role;
