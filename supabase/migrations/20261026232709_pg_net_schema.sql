-- Follow the managed pg_net relocation procedure only with an empty queue.
-- https://supabase.com/docs/guides/database/extensions/pg_net#troubleshooting
do $move$ begin
 if exists(select 1 from pg_extension e join pg_namespace n on n.oid=e.extnamespace where e.extname='pg_net' and n.nspname='public') then
  if exists(select 1 from net.http_request_queue) then raise exception 'Pending HTTP requests; retry after queue drains'; end if;
  create schema if not exists extensions;
  drop extension pg_net;
  create extension pg_net with schema extensions;
 end if;
end $move$;
