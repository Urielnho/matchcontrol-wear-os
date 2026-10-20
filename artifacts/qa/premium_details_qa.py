from premium_ui_qa import *
import json
prefix=sys.argv[2] if len(sys.argv)>2 else "premium-round-227"
launch();top()
assert find("DEMO"),"This scenario requires DEMO"
# Open the featured match via its accessible full team name.
seek("Aurora FC contra",True);tap("Aurora FC contra",True);top()
assert find("Volver")
shot(prefix+"-details")
seek("Aurora FC");tap("Aurora FC")
seek("Línea de tiempo");swipe();shot(prefix+"-timeline")
seek("Posesión");swipe();shot(prefix+"-statistics")
top();back();top();seek("Favoritos");tap("Favoritos");top()
assert find("Volver")
seek("Aurora FC");shot(prefix+"-favorites-followed")
# Following survives a process restart and a date change.
launch();seek("Favoritos");tap("Favoritos");seek("Aurora FC")
assert find("Aurora FC"),"Favorite lost after restart"
seek("Día siguiente");tap("Día siguiente");top();seek("Aurora FC")
assert find("Aurora FC"),"Favorite disappeared on another date"
shot(prefix+"-favorites-next-day")
tap("Dejar de seguir a Aurora FC");top()
assert find("Tu propia tribuna"),"Favorite removal failed"
shot(prefix+"-favorites-empty")
print(json.dumps({"profile":prefix,"favorite_add_restart_next_date_remove":"passed"}),flush=True)
