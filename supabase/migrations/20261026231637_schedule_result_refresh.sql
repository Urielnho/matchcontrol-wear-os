-- Supabase platform-only integrations are skipped by the embedded PG test engine.
do $setup$
begin
 if exists(select 1 from pg_available_extensions where name='pg_cron') then
  create extension if not exists pg_cron;
  create extension if not exists pg_net;
  grant usage on schema vault to service_role;
  grant select on vault.secrets to service_role;
  grant execute on function vault.create_secret(text,text,text,uuid) to service_role;
  grant execute on function vault.update_secret(uuid,text,text,text,uuid) to service_role;
  revoke all on vault.decrypted_secrets from anon,authenticated;
  perform cron.schedule('matchcontrol-result-refresh','*/30 * * * *',$job$
   select net.http_post(
    url:=(select decrypted_secret from vault.decrypted_secrets where name='matchcontrol_project_url')||'/functions/v1/settle-sandbox',
    headers:=jsonb_build_object('Content-Type','application/json','apikey',(select decrypted_secret from vault.decrypted_secrets where name='matchcontrol_worker_key')),
    body:='{"refresh":true}'::jsonb,timeout_milliseconds:=60000
   ) where exists(select 1 from vault.decrypted_secrets where name='matchcontrol_worker_key');
  $job$);
 end if;
end $setup$;

create function public.store_refresh_key(p_key text,p_url text) returns void language plpgsql security invoker set search_path='' as $$
declare sid uuid;
begin
 if length(p_key)<20 or p_url not like 'https://%.supabase.co' then raise exception 'Invalid configuration';end if;
 select id into sid from vault.secrets where name='matchcontrol_worker_key';
 if sid is null then perform vault.create_secret(p_key,'matchcontrol_worker_key','MatchControl scheduled worker');else perform vault.update_secret(sid,p_key,'matchcontrol_worker_key','MatchControl scheduled worker');end if;
 select id into sid from vault.secrets where name='matchcontrol_project_url';
 if sid is null then perform vault.create_secret(p_url,'matchcontrol_project_url','MatchControl server URL');else perform vault.update_secret(sid,p_url,'matchcontrol_project_url','MatchControl server URL');end if;
end $$;
revoke execute on function public.store_refresh_key(text,text) from public,anon,authenticated;
grant execute on function public.store_refresh_key(text,text) to service_role;

create function public.pending_football_dates() returns text[] language sql security invoker set search_path='' as $$
 select coalesce(array_agg(scheduled_day),'{}') from (
  select distinct (f.start_time at time zone 'America/Phoenix')::date::text as scheduled_day
  from public.bets b join public.bet_selections s on s.bet_id=b.id join public.matches f on f.id=s.match_id
  where b.status='PENDING' and f.source='API_FOOTBALL' and f.status not in ('FINAL','VOID') and f.start_time<=now()
  order by scheduled_day desc limit 2
 ) dates;
$$;
revoke execute on function public.pending_football_dates() from public,anon,authenticated;
grant execute on function public.pending_football_dates() to service_role;
