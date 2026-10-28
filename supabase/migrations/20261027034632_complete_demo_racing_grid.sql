create or replace function public.create_multisport_demo(p_user uuid,p_request uuid) returns void language plpgsql security invoker set search_path='' as $$
declare sport text; fid text; home jsonb; away jsonb; drivers jsonb; market text; mid uuid; values_list jsonb; value jsonb; i integer;
begin
 perform 1 from public.profiles where id=p_user for update;
 if not found or p_request is null then raise exception 'Missing owner';end if;
 drivers='[{"id":"demo-driver-0","name":"Mateo Solís","code":"P1"},{"id":"demo-driver-1","name":"Sebastián Vega","code":"P2"},{"id":"demo-driver-2","name":"Leo Campos","code":"P3"},{"id":"demo-driver-3","name":"Bruno Torres","code":"P4"},{"id":"demo-driver-4","name":"Nicolás Marín","code":"P5"},{"id":"demo-driver-5","name":"Adrián Ríos","code":"P6"},{"id":"demo-driver-6","name":"Diego Luna","code":"P7"},{"id":"demo-driver-7","name":"Hugo Peña","code":"P8"},{"id":"demo-driver-8","name":"Iván Mora","code":"P9"},{"id":"demo-driver-9","name":"Lucas Sierra","code":"P10"},{"id":"demo-driver-10","name":"Emilio Navarro","code":"P11"},{"id":"demo-driver-11","name":"Gael Romero","code":"P12"}]';
 foreach sport in array array['FOOTBALL','BASKETBALL','BASEBALL','FORMULA1'] loop
  fid='demo:'||p_user||':'||p_request||':'||lower(sport);
  if exists(select 1 from public.matches where id=fid) then continue;end if;
  home=jsonb_build_object('id','demo-'||sport||'-home','name',case sport when 'FOOTBALL' then 'Atlético Aurora' when 'BASKETBALL' then 'Halcones Campus' when 'BASEBALL' then 'Lobos del Parque' else 'Gran Premio Campus' end,'code','LOC');
  away=jsonb_build_object('id','demo-'||sport||'-away','name',case sport when 'FOOTBALL' then 'Deportivo Horizonte' when 'BASKETBALL' then 'Titanes del Valle' when 'BASEBALL' then 'Águilas del Sur' else 'Circuito Universitario' end,'code','VIS');
  insert into public.matches(id,home_team,away_team,league_id,league_name,status,start_time,source,sport,participants,demo_owner)
   values(fid,home,away,'demo-'||sport,'Campeonato Campus','SCHEDULED',now()+interval '90 minutes','DEMO',sport,case when sport='FORMULA1' then drivers else '[]'::jsonb end,p_user);
  foreach market in array case sport when 'FOOTBALL' then array['WINNER','DOUBLE_CHANCE','OVER_UNDER_15','OVER_UNDER_25','BOTH_SCORE'] when 'BASKETBALL' then array['BASKETBALL_WINNER','TOTAL_POINTS_2155'] when 'BASEBALL' then array['BASEBALL_WINNER','TOTAL_RUNS_85'] else array['RACE_WINNER','RACE_PODIUM','RACE_TOP10'] end loop
   mid=md5(fid||market)::uuid;
   insert into public.markets(id,match_id,market_type) values(mid,fid,market);
   if sport='FORMULA1' then
    for i in 0..jsonb_array_length(drivers)-1 loop
     insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values(md5(mid::text||i)::uuid,mid,drivers->i->>'id',drivers->i->>'name',case market when 'RACE_WINNER' then 2.2+i*.65 when 'RACE_PODIUM' then 1.35+i*.22 else 1.1+i*.07 end);
    end loop;
   else
    values_list=case market
     when 'WINNER' then '[["1","Local",1.85],["X","Empate",3.25],["2","Visitante",2.40]]'::jsonb
     when 'DOUBLE_CHANCE' then '[["1X","1 o empate",1.35],["X2","Empate o 2",1.45],["12","Sin empate",1.25]]'::jsonb
     when 'OVER_UNDER_15' then '[["OVER","Más de 1.5",1.40],["UNDER","Menos de 1.5",2.60]]'::jsonb
     when 'OVER_UNDER_25' then '[["OVER","Más de 2.5",1.90],["UNDER","Menos de 2.5",1.80]]'::jsonb
     when 'BOTH_SCORE' then '[["YES","Sí",1.75],["NO","No",2.00]]'::jsonb
     when 'TOTAL_POINTS_2155' then '[["OVER","Más de 215.5",1.90],["UNDER","Menos de 215.5",1.90]]'::jsonb
     when 'TOTAL_RUNS_85' then '[["OVER","Más de 8.5",1.90],["UNDER","Menos de 8.5",1.90]]'::jsonb
     else '[["1","Local",1.85],["2","Visitante",2.05]]'::jsonb end;
    for value in select * from jsonb_array_elements(values_list) loop
     insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values(md5(mid::text||(value->>0))::uuid,mid,value->>0,value->>1,(value->>2)::numeric);
    end loop;
   end if;
  end loop;
 end loop;
end $$;
revoke all on function public.create_multisport_demo(uuid,uuid) from public,anon,authenticated;
grant execute on function public.create_multisport_demo(uuid,uuid) to service_role;


-- Upgrade only unbet, unstarted educational grids; referenced history is immutable.
do $$ declare f record; m record; i integer; d jsonb; extra jsonb='[{"id":"demo-driver-6","name":"Diego Luna","code":"P7"},{"id":"demo-driver-7","name":"Hugo Peña","code":"P8"},{"id":"demo-driver-8","name":"Iván Mora","code":"P9"},{"id":"demo-driver-9","name":"Lucas Sierra","code":"P10"},{"id":"demo-driver-10","name":"Emilio Navarro","code":"P11"},{"id":"demo-driver-11","name":"Gael Romero","code":"P12"}]';
begin
 for f in select * from public.matches x where sport='FORMULA1' and source='DEMO' and status='SCHEDULED' and jsonb_array_length(participants)=6 and not exists(select 1 from public.bet_selections s where s.match_id=x.id) loop
  update public.matches set participants=participants||extra where id=f.id;
  for m in select * from public.markets where match_id=f.id loop
   for i in 6..11 loop
    d=extra->(i-6);
    insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values(md5(m.id::text||i)::uuid,m.id,d->>'id',d->>'name',case m.market_type when 'RACE_WINNER' then 2.2+i*.65 when 'RACE_PODIUM' then 1.35+i*.22 else 1.1+i*.07 end) on conflict do nothing;
   end loop;
  end loop;
 end loop;
end $$;
