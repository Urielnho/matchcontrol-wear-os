create or replace function public.ingest_football_odds(p_rows jsonb) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; bet jsonb; val jsonb; kind text; key text; mid uuid; count integer=0; price numeric; label text; provider_id integer;
begin
 for r in select value from jsonb_array_elements(p_rows) loop
  if not exists(select 1 from public.matches where id='api:'||(r#>>'{fixture,id}') and source='API_FOOTBALL' and status='SCHEDULED' and start_time>now()) then continue;end if;
  for bet in select value from jsonb_array_elements(coalesce(r#>'{bookmakers,0,bets}','[]')) loop
   provider_id=(bet->>'id')::integer;
   if provider_id not in (1,5,8,12) then continue;end if;
   for val in select value from jsonb_array_elements(bet->'values') loop
    label=val->>'value';
    kind=case provider_id when 1 then 'WINNER' when 12 then 'DOUBLE_CHANCE' when 8 then 'BOTH_SCORE' when 5 then case when label in ('Over 1.5','Under 1.5') then 'OVER_UNDER_15' when label in ('Over 2.5','Under 2.5') then 'OVER_UNDER_25' end end;
    if kind is null then continue;end if;
    key=case kind when 'WINNER' then case label when 'Home' then '1' when 'Draw' then 'X' when 'Away' then '2' end when 'BOTH_SCORE' then case label when 'Yes' then 'YES' when 'No' then 'NO' end when 'DOUBLE_CHANCE' then case label when 'Home/Draw' then '1X' when 'Draw/Away' then 'X2' when 'Home/Away' then '12' end else case when label like 'Over %' then 'OVER' when label like 'Under %' then 'UNDER' end end;
    if key is null or coalesce(val->>'odd','') !~ '^[0-9]+(\.[0-9]+)?$' then continue;end if;
    price=(val->>'odd')::numeric;if price<=1 or price>100 then continue;end if;
    insert into public.markets(match_id,market_type) values('api:'||(r#>>'{fixture,id}'),kind) on conflict(match_id,market_type) do update set status='OPEN' returning id into mid;
    insert into public.market_outcomes(market_id,outcome_key,label,decimal_odds) values(mid,key,case key when '1' then 'Local' when 'X' then 'Empate' when '2' then 'Visitante' when '1X' then 'Local o empate' when 'X2' then 'Empate o visitante' when '12' then 'Sin empate' when 'YES' then 'Sí' when 'NO' then 'No' else case when key='OVER' then 'Más de ' else 'Menos de ' end || case when kind='OVER_UNDER_15' then '1.5' else '2.5' end end,price)
    on conflict(market_id,outcome_key) do update set decimal_odds=excluded.decimal_odds,updated_at=now();
    count=count+1;
   end loop;
  end loop;
 end loop;
 return count;
end $$;
