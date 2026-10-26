-- Read snapshots and preferences with the caller's JWT and RLS, never admin.
grant execute on function public.sandbox_snapshot(uuid) to authenticated;
grant update(data_mode) on public.profiles to authenticated;
