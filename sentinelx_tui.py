import os
import sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
import time
import uuid
import random
import string
import re
from datetime import datetime, timedelta
from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn
from rich.columns import Columns
from rich.live import Live
from rich.align import Align
from prompt_toolkit import prompt

console = Console()

def now_iso(): return datetime.now().isoformat().replace("+00:00", "Z")
def generate_id(): return str(uuid.uuid4())[:8]

# --- GLOBALS & IN-MEMORY DB ---

USERS = [
  {"id":"1", "username":"superadmin",      "email":"admin@sentinel.com",  "password":"admin123",  "role":"ADMIN",    "status":"ACTIVE",    "emailVerified":True,  "riskScore":12, "createdAt":"2024-01-10"},
  {"id":"2", "username":"alice_analyst",   "email":"alice@sentinel.com",  "password":"alice123",  "role":"ANALYST",  "status":"ACTIVE",    "emailVerified":True,  "riskScore":38, "createdAt":"2024-02-14"},
  {"id":"3", "username":"bob_employee",    "email":"bob@sentinel.com",    "password":"bob123",    "role":"EMPLOYEE", "status":"ACTIVE",    "emailVerified":True,  "riskScore":78, "createdAt":"2024-03-01"},
  {"id":"4", "username":"carol_ops",       "email":"carol@sentinel.com",  "password":"carol123",  "role":"EMPLOYEE", "status":"SUSPENDED", "emailVerified":False, "riskScore":91, "createdAt":"2024-03-15"},
  {"id":"5", "username":"dave_analyst",    "email":"dave@sentinel.com",   "password":"dave123",   "role":"ANALYST",  "status":"ACTIVE",    "emailVerified":True,  "riskScore":55, "createdAt":"2024-04-01"},
  {"id":"6", "username":"eve_admin",       "email":"eve@sentinel.com",    "password":"eve123",    "role":"ADMIN",    "status":"ACTIVE",    "emailVerified":True,  "riskScore":20, "createdAt":"2024-04-10"},
  {"id":"7", "username":"frank_employee",  "email":"frank@sentinel.com",  "password":"frank123",  "role":"EMPLOYEE", "status":"LOCKED",    "emailVerified":True,  "riskScore":67, "createdAt":"2024-04-20"},
  {"id":"8", "username":"grace_employee",  "email":"grace@sentinel.com",  "password":"grace123",  "role":"EMPLOYEE", "status":"ACTIVE",    "emailVerified":True,  "riskScore":45, "createdAt":"2024-05-01"}
]

ALERTS = [
  {"id":"1",  "userId":"3", "severity":"HIGH",     "status":"OPEN",               "message":"Multiple failed logins detected",          "assignedTo":None,           "createdAt":"2025-04-28"},
  {"id":"2",  "userId":"4", "severity":"CRITICAL", "status":"UNDER_INVESTIGATION", "message":"Off-hours data export detected",           "assignedTo":"alice_analyst", "createdAt":"2025-04-27"},
  {"id":"3",  "userId":"3", "severity":"MEDIUM",   "status":"ACKNOWLEDGED",        "message":"Unusual access pattern from new IP",       "assignedTo":None,           "createdAt":"2025-04-26"},
  {"id":"4",  "userId":"2", "severity":"LOW",      "status":"RESOLVED",            "message":"Profile updated from unknown device",      "assignedTo":"alice_analyst", "createdAt":"2025-04-25"},
  {"id":"5",  "userId":"7", "severity":"HIGH",     "status":"OPEN",               "message":"Account locked after repeated failures",   "assignedTo":None,           "createdAt":"2025-04-29"},
  {"id":"6",  "userId":"4", "severity":"CRITICAL", "status":"OPEN",               "message":"Privilege escalation attempt detected",    "assignedTo":None,           "createdAt":"2025-04-29"},
  {"id":"7",  "userId":"8", "severity":"MEDIUM",   "status":"UNDER_INVESTIGATION", "message":"Bulk data access outside business hours",  "assignedTo":"dave_analyst", "createdAt":"2025-04-28"},
  {"id":"8",  "userId":"3", "severity":"HIGH",     "status":"OPEN",               "message":"API key accessed from foreign IP",         "assignedTo":None,           "createdAt":"2025-04-30"},
  {"id":"9",  "userId":"7", "severity":"LOW",      "status":"RESOLVED",            "message":"Settings changed without 2FA",            "assignedTo":"alice_analyst", "createdAt":"2025-04-24"},
  {"id":"10", "userId":"5", "severity":"MEDIUM",   "status":"ACKNOWLEDGED",        "message":"Repeated role permission checks",          "assignedTo":"dave_analyst", "createdAt":"2025-04-27"},
  {"id":"11", "userId":"8", "severity":"HIGH",     "status":"OPEN",               "message":"File download spike detected",             "assignedTo":None,           "createdAt":"2025-04-30"},
  {"id":"12", "userId":"6", "severity":"LOW",      "status":"RESOLVED",            "message":"Admin login from new browser",            "assignedTo":None,           "createdAt":"2025-04-23"}
]

RULES = [
  {"id":"1", "name":"High Frequency Rule",    "condition":"activity_count >= 10", "riskScore":40, "severity":"HIGH",     "active":True},
  {"id":"2", "name":"Off Hours Access",       "condition":"off_hours_count >= 3",  "riskScore":25, "severity":"MEDIUM",   "active":True},
  {"id":"3", "name":"Failed Login Threshold", "condition":"failed_logins >= 5",    "riskScore":35, "severity":"HIGH",     "active":True},
  {"id":"4", "name":"Data Export Rule",       "condition":"export_count >= 3",     "riskScore":30, "severity":"MEDIUM",   "active":True},
  {"id":"5", "name":"Privilege Escalation",   "condition":"access_denied >= 3",    "riskScore":50, "severity":"CRITICAL", "active":True}
]

AUDIT_LOGS = [
  {"id":"1", "event":"Rule 'High Frequency Rule' fired",          "userId":"3", "ip":"192.168.1.10", "createdAt":"2025-04-30 09:14:32"},
  {"id":"2", "event":"Alert #1 status OPEN→UNDER_INVESTIGATION",  "userId":"2", "ip":"10.0.0.55",    "createdAt":"2025-04-30 09:12:18"},
  {"id":"3", "event":"User carol_ops suspended by superadmin",    "userId":"1", "ip":"10.0.0.1",     "createdAt":"2025-04-29 14:33:05"},
  {"id":"4", "event":"Password reset initiated for frank",        "userId":"7", "ip":"192.168.1.77", "createdAt":"2025-04-29 11:22:41"},
  {"id":"5", "event":"Admin login from new IP detected",          "userId":"1", "ip":"10.0.0.1",     "createdAt":"2025-04-28 08:55:10"},
  {"id":"6", "event":"Risk recalculated for bob_employee → 78",   "userId":"3", "ip":"192.168.1.10", "createdAt":"2025-04-28 03:42:11"},
  {"id":"7", "event":"Alert #6 escalated to CRITICAL",            "userId":"1", "ip":"10.0.0.1",     "createdAt":"2025-04-29 15:01:44"},
  {"id":"8", "event":"New user grace_employee registered",        "userId":"1", "ip":"10.0.0.1",     "createdAt":"2025-05-01 10:00:00"}
]

SYSTEM_FEED = [
  "[09:14:32] bob_employee triggered LOGIN from 192.168.1.42",
  "[09:12:18] Alert #6 escalated to CRITICAL by superadmin",
  "[09:11:05] carol_ops account SUSPENDED",
  "[09:08:44] Risk recalculated for frank_employee → score: 67",
  "[09:06:21] alice_analyst resolved Alert #4",
  "[09:03:10] New user grace_employee registered"
]

SESSION = {"user": None, "token": None, "session_start": None, "actions": [], "recent_commands": []}

next_user_id = 9
next_alert_id = 13
next_activity_id = 1

# --- DATA GENERATION ---
ACTIVITIES = []
RISK_SCORES = []

def generate_data():
    global next_activity_id
    random.seed(42)
    now = datetime.now()
    
    ACTION_POOL = ["LOGIN","LOGOUT","VIEW_DASHBOARD","PASSWORD_CHANGED","PROFILE_UPDATED","VIEW_REPORT","EXPORT_DATA","FAILED_LOGIN","ACCESS_DENIED","VIEW_USER","DELETE_ATTEMPT","FILE_DOWNLOAD","SETTINGS_CHANGED","ROLE_VIEWED","API_KEY_ACCESSED"]
    ENTITY_TYPES = ["USER","ALERT","REPORT","DASHBOARD","SETTINGS","FILE"]
    METADATA_POOL = ['{"ip":"192.168.1.10","browser":"Chrome"}', '{"ip":"10.0.0.55","browser":"Firefox"}', '{"reason":"session_timeout"}', '{"file":"report_q1.pdf"}', '{"ip":"203.0.113.42","browser":"Safari"}', '{"reason":"wrong_password"}', '{"file":"export_users.csv"}', '{"ip":"192.168.1.99","browser":"Edge"}']
    
    for u in USERS:
        uid = u["id"]
        # 5 business hours (last 7 days, 09:00-17:00)
        for _ in range(5):
            d = now - timedelta(days=random.randint(0,6))
            d = d.replace(hour=random.randint(9,16), minute=random.randint(0,59), second=random.randint(0,59))
            ACTIVITIES.append({"id": str(next_activity_id), "userId": uid, "action": random.choice(ACTION_POOL), "entityType": random.choice(ENTITY_TYPES), "entityId": str(random.randint(1,100)), "metadata": random.choice(METADATA_POOL), "createdAt": d})
            next_activity_id += 1
        # 5 off-hours (last 7 days, 22:00-05:00)
        for _ in range(5):
            d = now - timedelta(days=random.randint(0,6))
            d = d.replace(hour=random.choice([22,23,0,1,2,3,4,5]), minute=random.randint(0,59), second=random.randint(0,59))
            ACTIVITIES.append({"id": str(next_activity_id), "userId": uid, "action": random.choice(ACTION_POOL), "entityType": random.choice(ENTITY_TYPES), "entityId": str(random.randint(1,100)), "metadata": random.choice(METADATA_POOL), "createdAt": d})
            next_activity_id += 1
        # 5 older mixed (8-14 days ago)
        for _ in range(5):
            d = now - timedelta(days=random.randint(8,14))
            d = d.replace(hour=random.randint(0,23), minute=random.randint(0,59), second=random.randint(0,59))
            ACTIVITIES.append({"id": str(next_activity_id), "userId": uid, "action": random.choice(ACTION_POOL), "entityType": random.choice(ENTITY_TYPES), "entityId": str(random.randint(1,100)), "metadata": random.choice(METADATA_POOL), "createdAt": d})
            next_activity_id += 1
            
        # 6 Risk Scores
        base = u["riskScore"]
        score_trend = random.choice([(0,0), (-10,10), (-5,15)]) if base > 50 else random.choice([(0,0), (-5,5)])
        for i in range(5):
            d = now - timedelta(days=random.randint(1,30))
            score = max(0, min(100, base + random.randint(score_trend[0], score_trend[1])))
            RISK_SCORES.append({"id": str(uuid.uuid4())[:8], "userId": uid, "score": score, "reason": random.choice(["High activity frequency","Off-hours access pattern","Multiple failed logins","Normal usage pattern","Elevated export activity","New IP login detected","Repeated access denied events","API key misuse suspected"]), "calculatedAt": d})
        RISK_SCORES.append({"id": str(uuid.uuid4())[:8], "userId": uid, "score": base, "reason": "Current calculated risk", "calculatedAt": now})

    ACTIVITIES.sort(key=lambda x: x["createdAt"], reverse=True)

# --- CORE UTILITIES ---

def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def transition(name):
    console.print(f"[dim]Loading {name}...[/dim]")
    time.sleep(0.3)
    clear_screen()

def draw_status_bar():
    user = SESSION.get("user") or {}
    username = user.get("username", "Not logged in")
    role = user.get("role", "")
    token = SESSION.get("token", "eyJhbGciOiJIUzI1NiJ9.demo.token")
    token_display = token[:16] + "..." if token else "N/A"
    now_str = datetime.now().strftime("%H:%M:%S")
    colors = {"ADMIN":"red", "ANALYST":"yellow", "EMPLOYEE":"cyan"}
    c = colors.get(role, "white")
    console.rule()
    role_str = f"[{c}][{role}][/{c}]" if role else ""
    console.print(f" [bold]● SENTINEL-X[/bold]  │  [bold]{username}[/bold]  {role_str}  │  Token: {token_display}  │  {now_str}")
    console.rule()

def draw_http_log(method, path, status_code, req_body="", resp_body=""):
    color = "green" if status_code < 300 else ("yellow" if status_code < 400 else "red")
    panel_text = (
        f"[bold]→ REQUEST[/bold]   {method} {path}\n"
        f"  Body: {req_body or 'N/A'}\n"
        f"[bold]← RESPONSE[/bold]  [{color}]HTTP {status_code}[/{color}]\n"
        f"  Body: {resp_body or 'N/A'}"
    )
    console.print(Panel(panel_text, title="HTTP EXCHANGE", border_style=color, padding=(0,2)))

def get_input(prompt_text):
    try:
        return prompt(f"  {prompt_text} › ").strip()
    except (KeyboardInterrupt, EOFError):
        return ""

def get_password(prompt_text):
    try:
        return prompt(f"  {prompt_text} › ", is_password=True).strip()
    except (KeyboardInterrupt, EOFError):
        return ""

def confirm(prompt_text):
    val = get_input(f"{prompt_text} (y/n)").lower()
    return val in ("y", "yes")

def track_action(action_name, entity_type="SYSTEM", entity_id="0", metadata="{}"):
    SESSION["actions"].append(action_name)
    user = SESSION.get("user")
    uname = user["username"] if user else "SYSTEM"
    SYSTEM_FEED.insert(0, f"[{datetime.now().strftime('%H:%M:%S')}] {uname} — {action_name}")
    
    if user:
        global next_activity_id
        action_fmt = action_name.upper().replace(" ", "_")[:25]
        ACTIVITIES.insert(0, {
            "id": str(next_activity_id),
            "userId": user["id"],
            "action": action_fmt,
            "entityType": entity_type,
            "entityId": entity_id,
            "metadata": metadata,
            "createdAt": datetime.now()
        })
        next_activity_id += 1

def find_user_by_id_or_email(val):
    for u in USERS:
        if str(u["id"]) == str(val) or u["email"] == val or u["username"] == val:
            return u
    return None

def find_alert_by_id(val):
    for a in ALERTS:
        if str(a["id"]) == str(val): return a
    return None

def get_user_activities(user_id): return [a for a in ACTIVITIES if a["userId"] == user_id]
def get_user_alerts(user_id): return [a for a in ALERTS if a["userId"] == user_id]
def get_user_risk_history(user_id): return sorted([r for r in RISK_SCORES if r["userId"] == user_id], key=lambda x: x["calculatedAt"])

def save_confirmation():
    console.print("\n[green]✓ Changes saved to in-memory database.[/green]")
    time.sleep(1)

VALID_TRANSITIONS = {
    "OPEN": ["UNDER_INVESTIGATION", "ACKNOWLEDGED"],
    "UNDER_INVESTIGATION": ["ACKNOWLEDGED"],
    "ACKNOWLEDGED": ["RESOLVED"],
    "RESOLVED": []
}

def sparkline(values):
    chars = "▁▂▃▄▅▆▇█"
    if not values: return ""
    mn, mx = min(values), max(values)
    if mx == mn: return chars[0] * len(values)
    return "".join(chars[int((v-mn)/(mx-mn)*7)] for v in values)

def risk_bar(score):
    filled = min(int(score/5), 20)
    bar = "█"*filled + "░"*(20-filled)
    color = "red" if score>=60 else ("yellow" if score>=40 else "green")
    return f"[{color}]{bar}[/{color}]"

def severity_color(s): return {"CRITICAL":"red","HIGH":"yellow","MEDIUM":"cyan","LOW":"blue"}.get(s,"white")
def status_color(s): return {"OPEN":"red","UNDER_INVESTIGATION":"yellow","ACKNOWLEDGED":"cyan","RESOLVED":"green","ACTIVE":"green","SUSPENDED":"yellow","LOCKED":"red"}.get(s,"white")

def fake_jwt():
    chars = string.ascii_letters + string.digits
    p = "".join(random.choices(chars, k=40))
    s = "".join(random.choices(chars, k=27))
    return f"eyJhbGciOiJIUzI1NiJ9.{p}.{s}"

def fake_bcrypt():
    return "$2a$10$" + "".join(random.choices(string.ascii_letters+string.digits, k=43))

def paginate(items, page_size=5, title="Results", render_fn=None):
    if not items:
        console.print("[yellow]No records found.[/yellow]")
        get_input("Press Enter to go back")
        return None
    page = 0
    search_filter = ""
    while True:
        clear_screen()
        draw_status_bar()
        filtered = [i for i in items if search_filter.lower() in str(i).lower()] if search_filter else items
        if not filtered:
            console.print(f"[yellow]No results matching '{search_filter}'[/yellow]")
        else:
            total_pages = max(1, (len(filtered) + page_size - 1) // page_size)
            page = min(page, total_pages - 1)
            start = page * page_size
            chunk = filtered[start:start+page_size]
            console.print(f"[dim]Showing {start+1}-{min(start+page_size, len(filtered))} of {len(filtered)}  [Page {page+1}/{total_pages}][/dim]\n")
            if render_fn:
                result = render_fn(chunk)
                if result is not None:
                    return result
        
        nav = "[N]ext  [P]rev  [S]earch  [C]lear filter  [B]ack"
        if search_filter: nav += f"  (filter: '{search_filter}')"
        console.print(f"\n[dim]{nav}[/dim]")
        choice = get_input("").lower()
        if choice == "n" and page < total_pages-1: page += 1
        elif choice == "p" and page > 0: page -= 1
        elif choice == "s": search_filter = get_input("Search term"); page = 0
        elif choice == "c": search_filter = ""; page = 0
        elif choice == "b" or choice == "": return None
        else: console.print(f"[yellow]⚠ Invalid option '{choice}'[/yellow]")

def get_notifications(user):
    notes = []
    role = user["role"]
    uid = user["id"]
    open_mine = [a for a in ALERTS if a["userId"]==uid and a["status"]=="OPEN"]
    assigned = [a for a in ALERTS if a.get("assignedTo")==user["username"] and a["status"] not in ("RESOLVED",)]
    if open_mine: notes.append(f"● {len(open_mine)} open alert(s) require your attention")
    if assigned and role in ("ANALYST","ADMIN"): notes.append(f"● {len(assigned)} alert(s) assigned to you")
    if role == "ADMIN":
        high_risk = [u for u in USERS if u["riskScore"]>=60]
        unassigned_crit = [a for a in ALERTS if a["severity"]=="CRITICAL" and not a.get("assignedTo") and a["status"]=="OPEN"]
        if high_risk: notes.append(f"● {len(high_risk)} users have risk scores above threshold (60)")
        if unassigned_crit: notes.append(f"● {len(unassigned_crit)} unassigned CRITICAL alerts need attention")
    return notes

def draw_notifications():
    if not SESSION.get("user"): return
    notes = get_notifications(SESSION["user"])
    if notes:
        console.print(Panel("\n".join(notes), title="NOTIFICATIONS", border_style="yellow"))

def animate_pipeline(title, nodes, success=True):
    clear_screen()
    draw_status_bar()
    with Live(Panel("", title="PIPELINE", border_style="cyan"), refresh_per_second=4, screen=False) as live:
        visible = []
        for i, node in enumerate(nodes):
            is_last = i == len(nodes) - 1
            if is_last and not success:
                visible.append(f"[red]✗ {node}[/red]")
                live.update(Panel("\n    │\n".join(visible), title=f"PIPELINE: {title}"))
                time.sleep(0.4)
                break
            
            if is_last and success:
                visible.append(f"[green]✓ {node}[/green]")
            else:
                visible.append(node)
                
            live.update(Panel("\n    │\n".join(visible), title=f"PIPELINE: {title}"))
            time.sleep(0.4)
    time.sleep(0.5)

def ask_menu(options):
    for k, v in options.items():
        if v.startswith("──"): console.print(f"\n  [bold cyan]{v}[/bold cyan]")
        else: console.print(f"  [[bold cyan]{k}[/bold cyan]] {v}")
    console.print()
    valid = [str(k).upper() for k,v in options.items() if not v.startswith("──")]
    valid.extend(["?", "!", "F"])
    while True:
        c = get_input("Select an option").upper()
        if c in valid:
            if c == "?": command_palette(); return "IGNORE"
            if c == "!":
                draw_notifications(); get_input("Press Enter to continue"); return "IGNORE"
            if c == "F" and "F" in valid:
                paginate(SYSTEM_FEED, title="SYSTEM FEED", render_fn=lambda c: console.print("\n".join(c)))
                return "IGNORE"
            
            action_desc = options.get(c, c)
            track_action(f"Selected {action_desc}", "DASHBOARD", c)
            
            return c
        console.print(f"  [yellow]⚠ Invalid option '{c}' — please choose from the menu above[/yellow]")

# --- SHARED COMPONENTS ---

def interactive_user_card(uid):
    while True:
        u = find_user_by_id_or_email(uid)
        if not u:
            console.print("[red]User not found.[/red]")
            time.sleep(1); return
            
        clear_screen(); draw_status_bar()
        ini = u['username'][0].upper() + u['role'][0].upper()
        acts = get_user_activities(u['id'])
        alts = get_user_alerts(u['id'])
        open_alts = [a for a in alts if a['status']=="OPEN"]
        
        card = (
            f"  ╔══════════════════════════════════════╗\n"
            f"  ║  [[cyan]{ini}[/cyan]]  {u['username']:<25} ║\n"
            f"  ║  {u['role']:<10} •  [{status_color(u['status'])}]{u['status']:<15}[/] ║\n"
            f"  ╚══════════════════════════════════════╝\n\n"
            f"  Email:      {u['email']}\n"
            f"  Member:     Since {u['createdAt']}\n"
            f"  Verified:   {'[green]✓ Email confirmed[/]' if u['emailVerified'] else '[red]✗ Unverified[/]'}\n"
            f"  Risk Score: {u['riskScore']}  {risk_bar(u['riskScore'])}\n\n"
            f"  ┌──── Quick Stats ───────────────────┐\n"
            f"  │ Activities: {len(acts):<3} Alerts: {len(alts):<3} Open: {len(open_alts):<2} │\n"
            f"  └────────────────────────────────────┘\n\n"
            f"  [1]Activities [2]Alerts [3]Risk [4]Edit\n"
            f"  [5]Suspend    [6]Lock   [7]Delete [B]Back\n"
        )
        console.print(Panel(card, title="USER PROFILE", border_style="cyan"))
        
        c = get_input("Action").upper()
        if c == 'B': return
        elif c == '1':
            def r_acts(chunk):
                t = Table()
                t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                console.print(t)
            paginate(acts, render_fn=r_acts)
        elif c == '2':
            alert_inbox(u['id'])
        elif c == '3':
            risk_monitor(u['id'])
        elif c == '4':
            if SESSION["user"]["role"] != "ADMIN": console.print("[red]Admin only[/red]"); time.sleep(1); continue
            un = get_input(f"New username ({u['username']})") or u['username']
            em = get_input(f"New email ({u['email']})") or u['email']
            u['username'], u['email'] = un, em
            save_confirmation(); draw_http_log("PATCH", f"/api/users/{u['id']}", 200)
        elif c in ['5','6']:
            if SESSION["user"]["role"] != "ADMIN": console.print("[red]Admin only[/red]"); time.sleep(1); continue
            st = "SUSPENDED" if c=='5' else "LOCKED"
            if confirm(f"Change status to {st}?"):
                u['status'] = st
                save_confirmation(); draw_http_log("PATCH", f"/api/users/{u['id']}/status", 200)
        elif c == '7':
            if SESSION["user"]["role"] != "ADMIN": console.print("[red]Admin only[/red]"); time.sleep(1); continue
            console.print("Checking if user is ADMIN...")
            time.sleep(0.5)
            admin_count = len([x for x in USERS if x["role"]=="ADMIN"])
            console.print(f"countByRole_Name(ADMIN) = {admin_count}")
            time.sleep(0.5)
            if u['role'] == "ADMIN" and admin_count <= 1:
                console.print(Panel("UserOperationNotAllowedException\nHTTP 403 - Cannot delete the last ADMIN.", border_style="red"))
                time.sleep(2)
            else:
                if confirm("Delete user?"):
                    USERS.remove(u)
                    save_confirmation(); draw_http_log("DELETE", f"/api/users/{u['id']}", 200)
                    return

def alert_detail_view(aid):
    while True:
        a = find_alert_by_id(aid)
        if not a: console.print("[red]Alert not found[/red]"); time.sleep(1); return
        u = find_user_by_id_or_email(a["userId"])
        
        clear_screen(); draw_status_bar()
        sev_c = severity_color(a["severity"])
        st_c = status_color(a["status"])
        
        acts = get_user_activities(a['userId'])[:4]
        act_str = ", ".join([x["action"] for x in acts])
        rs_hist = get_user_risk_history(a['userId'])
        rs = rs_hist[-1] if rs_hist else {"score":0, "reason":"N/A", "calculatedAt":""}
        
        panel = (
            f"  Severity:  [{sev_c}][{a['severity']}][/{sev_c}]          Status:  [{st_c}][{a['status']}][/{st_c}]\n"
            f"  Message:   {a['message']}\n"
            f"  User:      {u['username'] if u else 'Unknown'} (id:{a['userId']})    Created: {a['createdAt']}\n"
            f"  Assigned:  {a['assignedTo'] or 'Unassigned'}\n"
            f"├─ TRIGGERED BY ────────────────────────────────────────────────────────┤\n"
            f"  Risk Score: {rs['score']}   Reason: {rs['reason']}\n"
            f"  Calculated: {str(rs['calculatedAt'])[:19]}\n"
            f"├─ USER SNAPSHOT ───────────────────────────────────────────────────────┤\n"
            f"  Status: {u['status'] if u else 'N/A'}   Role: {u['role'] if u else 'N/A'}\n"
            f"  Recent activity: {act_str}\n"
            f"├─ ACTIONS ─────────────────────────────────────────────────────────────┤\n"
            f"  [1] Acknowledge  [2] Assign to analyst  [3] Update status  [B] Back\n"
        )
        console.print(Panel(panel, title=f"ALERT DETAIL #{a['id']}", border_style="cyan"))
        
        c = get_input("Action").upper()
        if c == 'B': return
        elif c == '1':
            if a["status"] not in ["OPEN", "UNDER_INVESTIGATION"]:
                console.print("[red]Must be OPEN or UNDER_INVESTIGATION to acknowledge.[/red]"); time.sleep(1)
            else:
                a["status"] = "ACKNOWLEDGED"; save_confirmation(); draw_http_log("PATCH", f"/api/alerts/{aid}/status", 200)
        elif c == '2':
            if SESSION["user"]["role"] not in ["ANALYST", "ADMIN"]: console.print("[red]Permission denied[/red]"); time.sleep(1); continue
            an = get_input("Analyst username")
            if find_user_by_id_or_email(an):
                a["assignedTo"] = an; save_confirmation(); draw_http_log("PATCH", f"/api/alerts/{aid}/assign", 200)
            else:
                console.print("[red]User not found[/red]"); time.sleep(1)
        elif c == '3':
            if SESSION["user"]["role"] not in ["ANALYST", "ADMIN"]: console.print("[red]Permission denied[/red]"); time.sleep(1); continue
            console.print(f"Valid transitions from {a['status']}: {VALID_TRANSITIONS.get(a['status'], [])}")
            ns = get_input("New status").upper()
            if ns in VALID_TRANSITIONS.get(a['status'], []):
                a["status"] = ns; save_confirmation(); draw_http_log("PATCH", f"/api/alerts/{aid}/status", 200)
            else:
                console.print(Panel(f"AlertInvalidStatusTransitionException: Cannot transition {a['status']} → {ns}\nValid: {VALID_TRANSITIONS.get(a['status'], [])}\nFSM: OPEN → UNDER_INVESTIGATION → ACKNOWLEDGED → RESOLVED", border_style="red"))
                time.sleep(2)

def alert_inbox(user_id=None):
    filt = "A"
    while True:
        alts = ALERTS if not user_id else get_user_alerts(user_id)
        if filt == "O": alts = [a for a in alts if a["status"]=="OPEN"]
        if filt == "C": alts = [a for a in alts if a["severity"]=="CRITICAL"]
        if filt == "R": alts = [a for a in alts if a["status"]=="RESOLVED"]
        
        def r_alts(chunk):
            t = Table(title=f"ALERT INBOX  |  Filter: [A]ll [O]pen [C]ritical [R]esolved")
            t.add_column("ID"); t.add_column("●"); t.add_column("Message"); t.add_column("Severity"); t.add_column("Status"); t.add_column("Age")
            for a in chunk:
                read = "●" if SESSION["user"]["username"] not in a.get("readBy", []) else " "
                age = "2d ago" # mock age
                t.add_row(str(a["id"]), read, a["message"], f"[{severity_color(a['severity'])}]{a['severity']}[/]", f"[{status_color(a['status'])}]{a['status']}[/]", age)
            console.print(t)
            console.print("Enter alert ID to view details, [F] filter, [B] back")
            v = get_input("").upper()
            if v == 'B': return "BACK"
            if v == 'F':
                f = get_input("Filter [A/O/C/R]").upper()
                if f in ['A','O','C','R']: return f"FILTER_{f}"
            elif v.isdigit():
                alert_detail_view(v)
                return "REFRESH"
            return None
        
        res = paginate(alts, render_fn=r_alts)
        if res in ("BACK", None): return
        if res and res.startswith("FILTER_"): filt = res.split("_")[1]

def risk_monitor(uid):
    while True:
        u = find_user_by_id_or_email(uid)
        if not u: return
        clear_screen(); draw_status_bar()
        
        hist = get_user_risk_history(uid)[-6:]
        scores = [h["score"] for h in hist]
        trend_diff = scores[-1] - scores[0] if len(scores)>1 else 0
        trend_str = f"↑ INCREASING (+{trend_diff})" if trend_diff>0 else (f"↓ DECREASING ({trend_diff})" if trend_diff<0 else "→ STABLE")
        
        hist_str = ""
        for h in hist:
            chars = "▁▂▃▄▅▆▇█"
            char = chars[int((h["score"])/100 * 7)]
            hist_str += f"  {str(h['calculatedAt'])[:10]}  {char}  {h['score']}\n"
        
        panel = (
            f"  Current Score:  {u['riskScore']} / 100\n"
            f"  {risk_bar(u['riskScore'])}\n\n"
            f"  Score History (last 6 calculations):\n"
            f"{hist_str}\n"
            f"  Trend: {trend_str}  ⚠ ALERT THRESHOLD: 60\n\n"
            f"  Breakdown:\n"
            f"  Base Score          +0\n"
            f"  Activity Frequency  +{(40 if u['riskScore']>40 else 0)}\n"
            f"  Off-hours Access    +{(u['riskScore'] - (40 if u['riskScore']>40 else 0))}\n"
            f"  Capped at           100  → Final: {u['riskScore']}\n\n"
            f"  [R] Recalculate  [A] View all activities  [L] View alerts  [B] Back\n"
        )
        console.print(Panel(panel, title=f"RISK MONITOR — {u['username']}", border_style="red" if u['riskScore']>=60 else "cyan"))
        
        c = get_input("Action").upper()
        if c == 'B': return
        elif c == 'A':
            def r_acts(chunk):
                t = Table()
                t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                console.print(t)
            paginate(get_user_activities(uid), render_fn=r_acts)
        elif c == 'L':
            alert_inbox(uid)
        elif c == 'R':
            if SESSION["user"]["role"] not in ["ANALYST", "ADMIN"] and SESSION["user"]["id"] != uid:
                console.print("[red]Permission denied[/red]"); time.sleep(1); continue
            
            clear_screen(); draw_status_bar()
            acts = get_user_activities(uid)[:50]
            console.print("Step 1: Fetching last 50 activities..."); time.sleep(0.5)
            console.print("Step 2: Base score = 0"); time.sleep(0.5)
            freq = 40 if len(acts)>=10 else 0
            console.print(f"Step 3: Frequency check — {len(acts)} acts → +{freq} pts"); time.sleep(0.5)
            off = sum(1 for a in acts if getattr(a['createdAt'], 'hour', 0) >= 22 or getattr(a['createdAt'], 'hour', 0) <= 5) * 4
            console.print(f"Step 4: Off-hours check — {off//4} acts × 4 = +{off} pts"); time.sleep(0.5)
            fin = min(100, freq + off)
            console.print(f"Step 5: Final score: {fin}"); time.sleep(0.5)
            u['riskScore'] = fin
            RISK_SCORES.append({"id":generate_id(), "userId":uid, "score":fin, "reason":"Live Recalc", "calculatedAt":datetime.now()})
            if fin >= 60:
                console.print("[red]Score >= 60! AlertService.generateAlert() triggered![/red]")
                global next_alert_id
                ALERTS.append({"id":str(next_alert_id), "userId":uid, "severity":"HIGH", "status":"OPEN", "message":f"High risk score: {fin}", "assignedTo":None, "createdAt":now_iso()})
                next_alert_id += 1
            save_confirmation(); draw_http_log("POST", f"/api/risk/{uid}/recalculate", 200)

def command_palette():
    clear_screen()
    draw_status_bar()
    recents = "\n".join([f"  • {x}" for x in SESSION["recent_commands"][-5:]])
    p = (
        f"  > _\n\n  Recent:\n{recents}\n\n"
        f"  Available commands:\n"
        f"  view user <id|username>     — open user profile\n"
        f"  view alert <id>             — open alert detail\n"
        f"  risk <username>             — show risk score\n"
        f"  recalc <username>           — trigger risk recalculation\n"
        f"  suspend <username>          — suspend user (admin only)\n"
        f"  assign <alertId> <analyst>  — assign alert\n"
        f"  status <alertId> <status>   — update alert status\n"
        f"  whoami                      — show current session info\n"
        f"  logout                      — end session\n"
        f"  [ESC] Close\n"
    )
    console.print(Panel(p, title="COMMAND PALETTE", border_style="cyan"))
    cmd = get_input(">").strip()
    if not cmd or cmd.upper() == "ESC": return
    SESSION["recent_commands"].append(cmd)
    pts = cmd.split()
    base = pts[0].lower()
    
    try:
        if base == "whoami":
            console.print(f"Logged in as {SESSION['user']['username']} [{SESSION['user']['role']}]"); time.sleep(2)
        elif base == "logout":
            session_logout_summary()
        elif base == "view" and pts[1].lower() == "user": interactive_user_card(pts[2])
        elif base == "view" and pts[1].lower() == "alert": alert_detail_view(pts[2])
        elif base == "risk": risk_monitor(pts[1])
        elif base == "recalc": risk_monitor(pts[1]) # just open monitor for them to press R
        elif base == "suspend":
            if SESSION["user"]["role"] != "ADMIN": console.print("[red]HTTP 403 Forbidden[/red]"); time.sleep(2); return
            u = find_user_by_id_or_email(pts[1])
            if u: u["status"] = "SUSPENDED"; save_confirmation()
        elif base == "assign":
            if SESSION["user"]["role"] not in ["ANALYST", "ADMIN"]: console.print("[red]HTTP 403 Forbidden[/red]"); time.sleep(2); return
            a = find_alert_by_id(pts[1]); a["assignedTo"] = pts[2]; save_confirmation()
        elif base == "status":
            if SESSION["user"]["role"] not in ["ANALYST", "ADMIN"]: console.print("[red]HTTP 403 Forbidden[/red]"); time.sleep(2); return
            a = find_alert_by_id(pts[1]); a["status"] = pts[2].upper(); save_confirmation()
        else:
            console.print("[red]Unknown command[/red]"); time.sleep(1)
    except Exception:
        console.print("[red]Error executing command[/red]"); time.sleep(1)

def screen_architecture():
    def page1_fn():
        content = (
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║              SENTINEL-X  SYSTEM ARCHITECTURE                    ║\n"
            "╚══════════════════════════════════════════════════════════════════╝\n\n"
            "                    ┌─────────────────────────┐\n"
            "                    │   Web / Mobile Client   │\n"
            "                    │   (Browser / App)       │\n"
            "                    └────────────┬────────────┘\n"
            "                                 │  HTTPS / JSON Requests\n"
            "                                 ▼\n"
            "                    ┌─────────────────────────┐\n"
            "                    │   Spring Security Layer  │\n"
            "                    │  JwtAuthenticationFilter │\n"
            "                    │  ┌─────────────────────┐│\n"
            "                    │  │ Extract Bearer Token ││\n"
            "                    │  │ Validate Signature   ││\n"
            "                    │  │ Load UserDetails     ││\n"
            "                    │  │ Set SecurityContext  ││\n"
            "                    │  └─────────────────────┘│\n"
            "                    └────────────┬────────────┘\n"
            "                                 │  Authenticated Request\n"
            "                                 ▼\n"
            "          ┌──────────────────────────────────────────────┐\n"
            "          │           SPRING BOOT APPLICATION            │\n"
            "          │                                              │\n"
            "          │  ┌─────────────┐                            │\n"
            "          │  │ Controllers │  ← HTTP Layer              │\n"
            "          │  │  /api/auth  │    Validates DTOs           │\n"
            "          │  │  /api/users │    Returns ResponseEntity   │\n"
            "          │  │  /api/risk  │                            │\n"
            "          │  │  /api/alerts│                            │\n"
            "          │  └──────┬──────┘                            │\n"
            "          │         │  DTOs / validated input            │\n"
            "          │  ┌──────▼──────┐                            │\n"
            "          │  │   Services  │  ← Business Logic Layer    │\n"
            "          │  │ AuthService │    @Transactional           │\n"
            "          │  │ UserService │    Strategy Pattern         │\n"
            "          │  │ RiskService │    Cross-module calls       │\n"
            "          │  └──────┬──────┘                            │\n"
            "          │         │  Entities                          │\n"
            "          │  ┌──────▼──────┐                            │\n"
            "          │  │Repositories │  ← Data Access Layer       │\n"
            "          │  │  JPA / JDBC │    Spring Data interfaces  │\n"
            "          │  │  Hibernate  │    Raw SQL for analytics   │\n"
            "          │  └──────┬──────┘                            │\n"
            "          └─────────┼────────────────────────────────────┘\n"
            "                    │  SQL Queries\n"
            "                    ▼\n"
            "          ┌─────────────────────────────────────────────┐\n"
            "          │              DATABASE LAYER                  │\n"
            "          │                                             │\n"
            "          │   users  │ roles │ alerts │ activities      │\n"
            "          │   risk_scores │ audit_logs │ rules          │\n"
            "          │   refresh_tokens │ password_reset_tokens    │\n"
            "          └─────────────────────────────────────────────┘\n\n"
            "TECHNOLOGY STACK:\n"
            "  ┌─────────────────┬──────────────────────────────────┐\n"
            "  │ Language        │ Java 17                          │\n"
            "  │ Framework       │ Spring Boot 3.5.x                │\n"
            "  │ Security        │ Spring Security + JWT (JJWT)     │\n"
            "  │ ORM             │ Hibernate / Spring Data JPA      │\n"
            "  │ Analytics DB    │ Raw JDBC (DashboardJdbcRepository)│\n"
            "  │ Migrations      │ Flyway V1 → V11                  │\n"
            "  │ Build Tool      │ Maven (pom.xml)                  │\n"
            "  │ Testing         │ JUnit 5 + MockMvc + H2 in-memory │\n"
            "  └─────────────────┴──────────────────────────────────┘"
        )
        console.print(Panel(content, title="PAGE 1 — High-Level Architecture", border_style="cyan"))

    def page2_fn():
        content = (
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║               BACKEND REQUEST LIFECYCLE                         ║\n"
            "║           Every HTTP call follows this exact path              ║\n"
            "╚══════════════════════════════════════════════════════════════════╝\n\n"
            "  ┌─────────────────────────────────┐\n"
            "  │        CLIENT REQUEST           │\n"
            "  │  GET /api/dashboard/me          │\n"
            "  │  Authorization: Bearer eyJ...   │\n"
            "  └────────────────┬────────────────┘\n"
            "                   │\n"
            "                   ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │      SECURITY FILTER CHAIN      │\n"
            "  │                                 │\n"
            "  │  JwtAuthenticationFilter runs   │\n"
            "  │  BEFORE every controller        │\n"
            "  └────────┬──────────┬─────────────┘\n"
            "           │          │\n"
            "     Token │          │ No Token /\n"
            "     Found │          │ Invalid Token\n"
            "           │          │\n"
            "           ▼          ▼\n"
            "  ┌──────────────┐  ┌─────────────────┐\n"
            "  │ Validate JWT │  │  401 Unauthorized│\n"
            "  │ signature +  │  │  Response sent  │\n"
            "  │ expiry date  │  │  immediately    │\n"
            "  └──────┬───────┘  └─────────────────┘\n"
            "         │\n"
            "         │ Valid\n"
            "         ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │       DISPATCHER SERVLET        │\n"
            "  │   Routes to correct Controller  │\n"
            "  └────────────────┬────────────────┘\n"
            "                   │\n"
            "                   ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │           CONTROLLER            │\n"
            "  │   @PreAuthorize role check      │\n"
            "  │   @Valid DTO validation         │\n"
            "  └────────┬──────────┬─────────────┘\n"
            "           │          │\n"
            "    Passed │          │ Failed\n"
            "           │          │\n"
            "           ▼          ▼\n"
            "  ┌──────────────┐  ┌─────────────────┐\n"
            "  │SERVICE LAYER │  │ 400 Bad Request │\n"
            "  │@Transactional│  │ GlobalException │\n"
            "  │Business logic│  │ Handler formats │\n"
            "  │Cross-module  │  │ JSON error resp │\n"
            "  │calls allowed │  └─────────────────┘\n"
            "  └──────┬───────┘\n"
            "         │\n"
            "         ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │        REPOSITORY LAYER         │\n"
            "  │                                 │\n"
            "  │  JPA: UserRepository,           │\n"
            "  │       AlertRepository           │\n"
            "  │       (Spring Data magic)       │\n"
            "  │                                 │\n"
            "  │  JDBC: DashboardJdbcRepository  │\n"
            "  │        (raw SQL for COUNT/AVG)  │\n"
            "  └────────────────┬────────────────┘\n"
            "                   │\n"
            "                   ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │           DATABASE              │\n"
            "  │   Query executes, returns rows  │\n"
            "  └────────────────┬────────────────┘\n"
            "                   │\n"
            "                   ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │      MAP ENTITY → DTO           │\n"
            "  │  User entity → UserResponse     │\n"
            "  │  (passwordHash NEVER included)  │\n"
            "  └────────────────┬────────────────┘\n"
            "                   │\n"
            "                   ▼\n"
            "  ┌─────────────────────────────────┐\n"
            "  │       HTTP RESPONSE             │\n"
            "  │   200 OK + JSON body returned   │\n"
            "  └─────────────────────────────────┘"
        )
        console.print(Panel(content, title="PAGE 2 — Request Lifecycle", border_style="cyan"))

    def page3_fn():
        content = (
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║                  JWT AUTHENTICATION FLOW                        ║\n"
            "║           From login request to token issuance                 ║\n"
            "╚══════════════════════════════════════════════════════════════════╝\n\n"
            "  ┌────────────────────────────────────────────┐\n"
            "  │  USER sends: POST /api/auth/login          │\n"
            "  │  Body: { email, password }                 │\n"
            "  └──────────────────┬─────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌────────────────────────────────────────────┐\n"
            "  │            AuthController                  │\n"
            "  │  Receives LoginRequest DTO                 │\n"
            "  │  @Valid ensures email/password not blank   │\n"
            "  └──────────────────┬─────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌────────────────────────────────────────────┐\n"
            "  │             AuthService                    │\n"
            "  │  login(LoginRequest request)               │\n"
            "  │  Delegates to AuthenticationManager        │\n"
            "  └──────────────────┬─────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌────────────────────────────────────────────┐\n"
            "  │        AuthenticationManager               │\n"
            "  │  authenticate(UsernamePasswordAuthToken)   │\n"
            "  └──────────────────┬─────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌────────────────────────────────────────────┐\n"
            "  │      CustomUserDetailsService              │\n"
            "  │  loadUserByUsername(email)                 │\n"
            "  │  → UserRepository.findByEmail(email)       │\n"
            "  │  → Returns UserDetails object              │\n"
            "  └──────────────────┬─────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌───────────────────────────────────────────────────┐\n"
            "  │           BCryptPasswordEncoder                   │\n"
            "  │           .matches(raw, encoded)                  │\n"
            "  └─────────────┬──────────────────┬──────────────────┘\n"
            "                │                  │\n"
            "          MATCH │                  │ NO MATCH\n"
            "                │                  │\n"
            "                ▼                  ▼\n"
            "  ┌─────────────────────┐  ┌───────────────────────┐\n"
            "  │  JwtTokenProvider   │  │  InvalidCredentials   │\n"
            "  │  .generateToken()   │  │  Exception thrown     │\n"
            "  │                     │  │                       │\n"
            "  │  Signs with HMAC    │  │  GlobalExceptionHandler│\n"
            "  │  Sets subject       │  │  catches it →         │\n"
            "  │  Adds role claims   │  │  HTTP 401 Unauthorized│\n"
            "  │  Sets 15min expiry  │  └───────────────────────┘\n"
            "  └──────┬──────────────┘\n"
            "         │\n"
            "         ▼\n"
            "  ┌─────────────────────────────────────────────────┐\n"
            "  │         RefreshTokenService                     │\n"
            "  │         .createRefreshToken(userId)             │\n"
            "  │         Generates UUID, saves to DB             │\n"
            "  │         Sets 7-day expiry                       │\n"
            "  └──────────────────┬──────────────────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  ┌─────────────────────────────────────────────────┐\n"
            "  │              HTTP 200 OK Response               │\n"
            "  │  {                                              │\n"
            "  │    \"token\":        \"eyJhbGciOiJIUzI1NiJ9...\",  │\n"
            "  │    \"refreshToken\": \"550e8400-e29b-41d4...\",     │\n"
            "  │    \"username\":     \"bob_employee\",              │\n"
            "  │    \"role\":         \"EMPLOYEE\",                  │\n"
            "  │    \"expiresIn\":    900                          │\n"
            "  │  }                                              │\n"
            "  └─────────────────────────────────────────────────┘\n\n"
            "  Token Refresh Flow:\n"
            "  POST /api/auth/refresh  →  Validate refresh token  →  Check not revoked\n"
            "  →  Check not expired  →  Generate NEW access token  →  200 OK\n\n"
            "  Logout Flow:\n"
            "  POST /api/auth/logout  →  Extract token from header\n"
            "  →  RefreshTokenService.revokeToken()  →  Delete from DB  →  200 OK"
        )
        console.print(Panel(content, title="PAGE 3 — Authentication Flow", border_style="cyan"))

    def page4_fn():
        content = (
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║             DATABASE SCHEMA — V1 through V11                    ║\n"
            "║                  Core Entity Relationships                      ║\n"
            "╚══════════════════════════════════════════════════════════════════╝\n\n"
            "  ┌───────────────┐         ┌──────────────────────┐\n"
            "  │     ROLES     │         │   REFRESH_TOKENS     │\n"
            "  ├───────────────┤         ├──────────────────────┤\n"
            "  │ id (PK)       │         │ id (PK)              │\n"
            "  │ name (UNIQUE) │         │ token (UNIQUE)       │\n"
            "  │               │         │ user_id (FK→users)   │\n"
            "  │ ADMIN         │         │ expiry_date          │\n"
            "  │ ANALYST       │         │ revoked BOOLEAN      │\n"
            "  │ EMPLOYEE      │         └──────────┬───────────┘\n"
            "  └──────┬────────┘                    │\n"
            "         │ 1                           │\n"
            "         │ role_id FK                  │ user_id FK\n"
            "         │ N                           │\n"
            "  ┌──────▼──────────────────────────────▼──────────┐\n"
            "  │                    USERS                        │\n"
            "  ├─────────────────────────────────────────────────┤\n"
            "  │ id (PK BIGSERIAL)    username (UNIQUE)          │\n"
            "  │ email (UNIQUE)       password_hash              │\n"
            "  │ role_id (FK)         is_active BOOLEAN          │\n"
            "  │ email_verified       status VARCHAR(20)         │\n"
            "  │ created_at           updated_at                 │\n"
            "  └────┬───────────────┬────────────────┬───────────┘\n"
            "       │               │                │\n"
            "       │ 1             │ 1              │ 1\n"
            "       │               │                │\n"
            "       │ N             │ N              │ N\n"
            "  ┌────▼──────────┐ ┌──▼────────────┐ ┌▼──────────────────┐\n"
            "  │  ACTIVITIES   │ │    ALERTS     │ │    RISK_SCORES     │\n"
            "  ├───────────────┤ ├───────────────┤ ├────────────────────┤\n"
            "  │ id (PK)       │ │ id (PK)       │ │ id (PK)            │\n"
            "  │ user_id (FK)  │ │ user_id (FK)  │ │ user_id (FK)       │\n"
            "  │ action        │ │ severity      │ │ score INTEGER      │\n"
            "  │ entity_type   │ │ status        │ │ reason VARCHAR     │\n"
            "  │ entity_id     │ │ message       │ │ calculated_at      │\n"
            "  │ metadata TEXT │ │ assigned_to   │ └────────────────────┘\n"
            "  │ created_at    │ │ risk_score_id │\n"
            "  └───────────────┘ │ created_at    │\n"
            "                    └───────────────┘\n\n"
            "  ┌─────────────────────┐  ┌──────────────────────┐\n"
            "  │       RULES         │  │     AUDIT_LOGS       │\n"
            "  ├─────────────────────┤  ├──────────────────────┤\n"
            "  │ id (PK)             │  │ id (PK)              │\n"
            "  │ name                │  │ event_type           │\n"
            "  │ condition_text      │  │ user_id (FK SET NULL)│\n"
            "  │ risk_score (0-100)  │  │ triggered_at         │\n"
            "  │ severity            │  │ metadata TEXT        │\n"
            "  │ is_active BOOLEAN   │  └──────────────────────┘\n"
            "  └─────────────────────┘\n\n"
            "  ┌─────────────────────────────────────────────────┐\n"
            "  │            FLYWAY MIGRATION TIMELINE            │\n"
            "  │                                                 │\n"
            "  │  V1 ──▶ V2 ──▶ V3 ──▶ V4 ──▶ V5 ──▶ V6        │\n"
            "  │  │      │      │      │      │      │           │\n"
            "  │ Core  Refresh  Pwd   Email  Activ  Risk         │\n"
            "  │ Users Tokens  Reset  Verif  Table Scores        │\n"
            "  │                                                 │\n"
            "  │  V6 ──▶ V7 ──▶ V8 ──▶ V9 ──▶ V10 ──▶ V11      │\n"
            "  │  │      │      │      │       │        │        │\n"
            "  │ Risk  Alerts Status Assign  Indexes  Rules+     │\n"
            "  │       Table         Col     Added    AuditLogs  │\n"
            "  └─────────────────────────────────────────────────┘"
        )
        console.print(Panel(content, title="PAGE 4 — Database Schema ERD", border_style="cyan"))

    def page5_fn():
        table = Table(show_header=True, header_style="bold magenta")
        table.add_column("Module")
        table.add_column("Package")
        table.add_column("Key Classes")
        table.add_column("Responsibility")
        
        table.add_row("auth", "com.sentinelx.auth", "AuthController, AuthService, JwtTokenProvider, JwtAuthFilter, PasswordResetService, EmailVerificationService", "Registration, Login, JWT generation/validation, Password resets, Email verification, Refresh token rotation")
        table.add_row("user", "com.sentinelx.user", "UserController, UserService, User.java, Role.java, UserRepository", "User CRUD, Role assignment, Status management (ACTIVE/SUSPENDED/LOCKED), Last-admin protection")
        table.add_row("risk", "com.sentinelx.risk", "RiskController, RiskScoreService, RiskScoringStrategy, BasicRiskScoringStrategy", "Risk calculation using Strategy Pattern, Score persistence, History tracking, Auto-alert triggering at score>=60")
        table.add_row("alert", "com.sentinelx.alert", "AlertController, AlertService", "Alert lifecycle management, Severity assignment, FSM state transitions (OPEN→INVESTIGATION→ACKNOWLEDGED→RESOLVED), Analyst assignment")
        table.add_row("activity", "com.sentinelx.activity", "ActivityController, ActivityService, Activity.java", "Universal audit trail, Event logging for all user actions, Paginated history retrieval")
        table.add_row("dashboard", "com.sentinelx.dashboard", "DashboardController, DashboardService, DashboardJdbcRepository", "Aggregated analytics, Raw JDBC queries for performance (bypasses Hibernate), Risk trends, Alert statistics")
        table.add_row("config", "com.sentinelx.config", "SecurityConfig, TransactionConfig, StartupEnvValidator, SslConfigValidator", "Spring Security filter chain, JWT setup, Transaction timeouts, Fail-fast env validation on startup")
        table.add_row("common", "com.sentinelx.common", "GlobalExceptionHandler, HealthController, RetryableReadService", "Global @ControllerAdvice error handling, Health endpoints, Automatic retry on DB transient errors")
        
        diagram = (
            "  ┌─────────────────────────────────────────────────────┐\n"
            "  │              MODULE DEPENDENCY FLOW                 │\n"
            "  │                                                     │\n"
            "  │   auth ──────────────────────────► user            │\n"
            "  │     │                                │             │\n"
            "  │     └──────────► risk ───────────► alert           │\n"
            "  │                    │                               │\n"
            "  │                    └──────────► activity           │\n"
            "  │                                     │              │\n"
            "  │   dashboard ◄───────────────────────┘              │\n"
            "  │      │                                             │\n"
            "  │   config ──────── wraps everything ────────────►  │\n"
            "  │   common ──────── wraps everything ────────────►  │\n"
            "  └─────────────────────────────────────────────────────┘"
        )
        
        from rich.console import Group
        group = Group(table, "\n", diagram)
        console.print(Panel(group, title="PAGE 5 — Module Map", border_style="cyan"))

    def page6_fn():
        table = Table(show_header=True, header_style="bold magenta")
        table.add_column("Endpoint")
        table.add_column("Method")
        table.add_column("EMPLOYEE")
        table.add_column("ANALYST")
        table.add_column("ADMIN")
        table.add_column("Guard")
        
        def mark(s):
            if "✓" in s and "own" in s: return f"[yellow]{s}[/yellow]"
            elif "✓" in s: return f"[green]{s}[/green]"
            elif "✗" in s: return f"[red]{s}[/red]"
            return s

        table.add_row("/api/auth/**", "POST", mark("✓ open"), mark("✓ open"), mark("✓ open"), "permitAll()")
        table.add_row("/api/users/ (list all)", "GET", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/users/{id} (own profile)", "GET", mark("✓ own"), mark("✓ own"), mark("✓ any"), "ensureAdminOrOwn()")
        table.add_row("/api/users/ (create)", "POST", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/users/{id} (update)", "PUT", mark("✓ own"), mark("✓ own"), mark("✓ any"), "ensureAdminOrOwn()")
        table.add_row("/api/users/{id}/status", "PATCH", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/users/{id} (delete)", "DELETE", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/alerts/me", "GET", mark("✓"), mark("✓"), mark("✓"), "authenticated()")
        table.add_row("/api/alerts/ (list all)", "GET", mark("✗"), mark("✓"), mark("✓"), "ANALYST or ADMIN")
        table.add_row("/api/alerts/{id}/acknowledge", "PATCH", mark("✓ own"), mark("✓"), mark("✓"), "assertModifyAccess()")
        table.add_row("/api/alerts/{id}/resolve", "PATCH", mark("✗"), mark("✓"), mark("✓"), "ANALYST or ADMIN")
        table.add_row("/api/alerts/{id}/assign", "POST", mark("✗"), mark("✓"), mark("✓"), "ANALYST or ADMIN")
        table.add_row("/api/alerts/{id} (delete)", "DELETE", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/risk/me", "GET", mark("✓"), mark("✓"), mark("✓"), "authenticated()")
        table.add_row("/api/risk/{userId}", "GET", mark("✗"), mark("✓"), mark("✓"), "ANALYST or ADMIN")
        table.add_row("/api/dashboard/me", "GET", mark("✓"), mark("✓"), mark("✓"), "authenticated()")
        table.add_row("/api/dashboard/admin", "GET", mark("✗"), mark("✗"), mark("✓"), "hasAuthority(ADMIN)")
        table.add_row("/api/activity/me", "GET", mark("✓"), mark("✓"), mark("✓"), "authenticated()")
        table.add_row("/api/activity/ (any user)", "GET", mark("✗"), mark("✓"), mark("✓"), "ANALYST or ADMIN")
        table.add_row("/health/**", "GET", mark("✓"), mark("✓"), mark("✓"), "permitAll()")
        
        top_panel = (
            "  ┌─────────────────────────────────────────────────────┐\n"
            "  │  Access control enforced via:                       │\n"
            "  │  1. SecurityConfig.securityFilterChain()            │\n"
            "  │  2. @PreAuthorize(\"hasAuthority('ADMIN')\")          │\n"
            "  │  3. ensureAdminOrOwnProfile() in controllers        │\n"
            "  │  4. assertModifyAccess() in AlertService            │\n"
            "  └─────────────────────────────────────────────────────┘\n"
        )
        
        from rich.console import Group
        group = Group(top_panel, table)
        console.print(Panel(group, title="PAGE 6 — Security RBAC Matrix", border_style="cyan"))

    def page7_fn():
        content = (
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║              DESIGN PATTERNS IN SENTINEL-X                      ║\n"
            "╚══════════════════════════════════════════════════════════════════╝\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 1. STRATEGY PATTERN\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: com.sentinelx.risk.strategy\n"
            " Purpose:  Allows swapping the risk algorithm without changing\n"
            "           RiskScoreService. Today: BasicRiskScoringStrategy.\n"
            "           Tomorrow: AiRiskScoringStrategy — zero service changes.\n\n"
            "  ┌─────────────────────┐\n"
            "  │   RiskScoreService  │───uses───► «interface»\n"
            "  └─────────────────────┘           RiskScoringStrategy\n"
            "                                          ▲\n"
            "                          ┌───────────────┴───────────────┐\n"
            "                          │                               │\n"
            "             ┌────────────────────────┐   ┌──────────────────────────┐\n"
            "             │ BasicRiskScoringStrategy│   │ AiRiskScoringStrategy    │\n"
            "             │ (currently active)      │   │ (future implementation)  │\n"
            "             └────────────────────────┘   └──────────────────────────┘\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 2. LAYERED ARCHITECTURE (N-Tier)\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: Every module follows this structure\n"
            " Purpose:  Separation of concerns. HTTP, logic, and data\n"
            "           access never mix in the same class.\n\n"
            "  ┌───────────────────────────────────────────┐\n"
            "  │    PRESENTATION LAYER — Controllers        │\n"
            "  │    Handles HTTP, validates DTOs, returns  │\n"
            "  │    ResponseEntity. No business logic.     │\n"
            "  └──────────────────┬────────────────────────┘\n"
            "                     │\n"
            "  ┌──────────────────▼────────────────────────┐\n"
            "  │    BUSINESS LOGIC LAYER — Services         │\n"
            "  │    @Transactional. Business rules live    │\n"
            "  │    here. Cross-module calls allowed.      │\n"
            "  └──────────────────┬────────────────────────┘\n"
            "                     │\n"
            "  ┌──────────────────▼────────────────────────┐\n"
            "  │    DATA ACCESS LAYER — Repositories        │\n"
            "  │    Spring Data JPA interfaces + raw JDBC  │\n"
            "  │    for analytics. No business logic.      │\n"
            "  └──────────────────┬────────────────────────┘\n"
            "                     │\n"
            "  ┌──────────────────▼────────────────────────┐\n"
            "  │              DATABASE                      │\n"
            "  └───────────────────────────────────────────┘\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 3. DTO PATTERN (Data Transfer Objects)\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: Every module's dto/ package\n"
            " Purpose:  Separates DB entities from API responses.\n"
            "           Prevents accidental data leaks to clients.\n\n"
            "  Database Entity          API Response DTO\n"
            "  ┌──────────────┐         ┌────────────────┐\n"
            "  │ User.java    │  maps   │UserResponse.java│\n"
            "  │ id           │ ──────► │ id             │\n"
            "  │ username     │         │ username       │\n"
            "  │ email        │         │ email          │\n"
            "  │ passwordHash │ ✗ NEVER │ role           │\n"
            "  │ role_id      │ exposed │ status         │\n"
            "  │ createdAt    │         │ createdAt      │\n"
            "  └──────────────┘         └────────────────┘\n"
            "           passwordHash is NEVER included in any DTO\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 4. FINITE STATE MACHINE — Alert Lifecycle\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: com.sentinelx.alert.service.AlertService\n"
            " Purpose:  Prevents invalid alert status transitions.\n"
            "           Enforces a strict one-way workflow.\n\n"
            "    ┌──────────┐    analyst       ┌─────────────────────┐\n"
            "    │  OPEN    │ ──────────────► │ UNDER_INVESTIGATION  │\n"
            "    └────┬─────┘                 └──────────┬──────────┘\n"
            "         │                                  │\n"
            "         │ direct ack                       │ acknowledged\n"
            "         │                                  │\n"
            "         └──────────────┬───────────────────┘\n"
            "                        │\n"
            "                        ▼\n"
            "               ┌─────────────────┐\n"
            "               │  ACKNOWLEDGED   │\n"
            "               └────────┬────────┘\n"
            "                        │ resolved\n"
            "                        ▼\n"
            "               ┌─────────────────┐\n"
            "               │    RESOLVED     │  ◄── Terminal state\n"
            "               └─────────────────┘     Cannot go back\n\n"
            "  AlertInvalidStatusTransitionException thrown on any backward move.\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 5. FAIL-FAST PATTERN\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: StartupEnvValidator.java, SslConfigValidator.java\n"
            " Purpose:  Crash immediately on bad config rather than fail\n"
            "           confusingly at runtime under load.\n\n"
            "  App Start → StartupEnvValidator.run()\n"
            "                    │\n"
            "            Check: DB_URL present?\n"
            "            Check: DB_USERNAME present?\n"
            "            Check: DB_PASSWORD present?\n"
            "            Check: JWT_SECRET present?\n"
            "                    │\n"
            "          ┌─────────┴─────────┐\n"
            "          │ Missing           │ All present\n"
            "          ▼                   ▼\n"
            "  IllegalStateException    Continue boot\n"
            "  \"Missing: DB_URL\"        normally ✓\n"
            "  App exits immediately\n\n"
            "─────────────────────────────────────────────────────────────────\n"
            " 6. GLOBAL EXCEPTION HANDLER\n"
            "─────────────────────────────────────────────────────────────────\n"
            " Location: com.sentinelx.exception.GlobalExceptionHandler\n"
            " Purpose:  Single @RestControllerAdvice converts ALL Java\n"
            "           exceptions into clean JSON HTTP responses.\n"
            "           Controllers have ZERO try/catch blocks.\n\n"
            "  Exception thrown anywhere in app\n"
            "              │\n"
            "              ▼\n"
            "  ┌─────────────────────────────────────┐\n"
            "  │     @RestControllerAdvice           │\n"
            "  │     GlobalExceptionHandler          │\n"
            "  ├─────────────────────────────────────┤\n"
            "  │ DuplicateEmailException    → 409   │\n"
            "  │ InvalidCredentialsException → 401  │\n"
            "  │ AccessDeniedException      → 403   │\n"
            "  │ ResourceNotFoundException  → 404   │\n"
            "  │ MethodArg NotValid         → 400   │\n"
            "  │ Exception (catch-all)      → 500   │\n"
            "  └──────────────────┬──────────────────┘\n"
            "                     │\n"
            "                     ▼\n"
            "  { \"timestamp\": \"...\", \"status\": 404,\n"
            "    \"error\": \"Resource not found.\" }"
        )
        console.print(Panel(content, title="PAGE 7 — Design Patterns", border_style="cyan"))

    pages = [page1_fn, page2_fn, page3_fn, page4_fn, page5_fn, page6_fn, page7_fn]
    page = 0
    while True:
        clear_screen()
        draw_status_bar()
        console.print(f"[dim]  Architecture Manual — Page {page+1} of 7  │  [N]ext  [P]rev  [B]ack[/dim]")
        pages[page]()
        choice = get_input("").lower()
        if choice == "n" and page < 6: page += 1
        elif choice == "p" and page > 0: page -= 1
        elif choice == "b": return
        else:
            console.print("[yellow]⚠ Use N/P/B to navigate[/yellow]")
            time.sleep(0.8)

def run_e2e_demo():
    transition("E2E Auth Flow Demo")
    steps = [
        ("POST /api/auth/register", "201 Created ✓"), ("POST /api/auth/login", "200 OK + JWT ✓"),
        ("GET /api/dashboard/me (valid JWT)", "200 OK ✓"), ("GET /api/dashboard/me (fake JWT)", "401 Unauthorized ✓"),
        ("POST /api/auth/refresh", "200 OK new token ✓"), ("POST /api/auth/logout", "200 OK ✓"),
        ("POST /api/auth/refresh (revoked)", "401 Unauthorized ✓"), ("POST /api/auth/forgot-password", "200 OK email sent ✓"),
        ("POST /api/auth/reset-password", "200 OK ✓"), ("POST /api/auth/login (old password)", "401 Unauthorized ✓"),
        ("POST /api/auth/login (new password)", "200 OK ✓")
    ]
    with Progress(SpinnerColumn(), TextColumn("[progress.description]{task.description}"), console=console) as prog:
        task = prog.add_task("[cyan]Running E2E tests...", total=len(steps))
        for i, (req, res) in enumerate(steps):
            console.print(f"[STEP {i+1:02d}/11] {req:<40} {res}")
            time.sleep(0.8)
            prog.advance(task)
    console.print("\n══════════════════════════════════════")
    console.print(" ALL 11 STEPS PASSED ✓  MockMvc E2E Test Complete ")
    console.print("══════════════════════════════════════")
    get_input("Press Enter to return")

def session_logout_summary():
    dur = int((datetime.now() - SESSION["session_start"]).total_seconds())
    mins, secs = divmod(dur, 60)
    acts = SESSION["actions"]
    
    act_counts = {}
    for a in acts: act_counts[a] = act_counts.get(a, 0) + 1
    bd = "\n".join([f"    {k} × {v}" for k,v in act_counts.items()])
    
    p = (
        f"  User: {SESSION['user']['username']} [{SESSION['user']['role']}]\n"
        f"  Session Duration: {mins} minutes {secs} seconds\n"
        f"  Actions Performed: {len(acts)}\n"
        f"  Breakdown:\n{bd}\n"
        f"  Token: {SESSION['token'][:16]}... → REVOKED\n\n"
        f"  Goodbye, {SESSION['user']['username']}."
    )
    clear_screen()
    console.print(Panel(p, title="SESSION SUMMARY", border_style="green"))
    get_input("Press Enter to return to main menu")
    SESSION["user"] = None; SESSION["token"] = None; SESSION["actions"] = []

# --- SCREENS 0-3 ---

def screen_splash():
    clear_screen()
    art = """
[bold cyan]███████╗███████╗███╗   ██╗████████╗██╗███╗   ██╗███████╗██╗      ██╗  ██╗[/]
[bold cyan]██╔════╝██╔════╝████╗  ██║╚══██╔══╝██║████╗  ██║██╔════╝██║      ╚██╗██╔╝[/]
[bold cyan]███████╗█████╗  ██╔██╗ ██║   ██║   ██║██╔██╗ ██║█████╗  ██║       ╚███╔╝ [/]
[bold cyan]╚════██║██╔══╝  ██║╚██╗██║   ██║   ██║██║╚██╗██║██╔══╝  ██║       ██╔██╗ [/]
[bold cyan]███████║███████╗██║ ╚████║   ██║   ██║██║ ╚████║███████╗███████╗ ██╔╝ ██╗[/]
[bold cyan]╚══════╝╚══════╝╚═╝  ╚═══╝   ╚═╝   ╚═╝╚═╝  ╚═══╝╚══════╝╚══════╝╚═╝  ╚═╝[/]
    """
    console.print(Align.center(art))
    console.print(Align.center("[bold yellow]Security Intelligence Platform  |  v2.1.0  |  Spring Boot 3.5 · Java 17[/bold yellow]\n"))
    
    steps = ["Loading application configuration...", "Establishing database connection...", "Running Flyway migrations (V1–V11)...", "Starting Spring Security filter chain...", "System ready. All services nominal."]
    with Progress(SpinnerColumn(), TextColumn("[progress.description]{task.description}"), console=console) as prog:
        task = prog.add_task("[cyan]Booting...", total=len(steps))
        for step in steps:
            time.sleep(0.6)
            prog.update(task, advance=1, description=f"[cyan]▸ {step}")
    time.sleep(1)

def screen_main_menu():
    clear_screen()
    left = Panel("[bold]Welcome to Sentinel-X[/bold]\n\nSecurity Intelligence Platform\nAll systems operational.", border_style="cyan")
    opts = {"L": "Login to existing account", "R": "Register new account", "A": "View System Architecture", "Q": "Quit"}
    
    for k,v in opts.items(): console.print(f"  [[bold cyan]{k}[/]] {v}")
    while True:
        c = get_input("Select an option").upper()
        if c == 'L': flow_login(); return
        elif c == 'R': flow_register(); return
        elif c == 'A': screen_architecture(); return
        elif c == 'Q': sys.exit(0)

def flow_register(auto_login=True):
    transition("Registration")
    name = get_input("Full name")
    email = get_input("Email")
    if not re.match(r"^[\w\.-]+@[\w\.-]+\.\w+$", email):
        console.print("[red]Invalid email format.[/red]"); time.sleep(1); return
    pwd = get_password("Password (min 6)")
    conf = get_password("Confirm password")
    if pwd != conf or len(pwd)<6:
        console.print("[red]Password invalid or mismatch.[/red]"); time.sleep(1); return
    
    rc = get_input("Role [1]EMPLOYEE [2]ANALYST [3]ADMIN")
    role = {"1":"EMPLOYEE", "2":"ANALYST", "3":"ADMIN"}.get(rc, "EMPLOYEE")
    
    nodes = ["POST /api/auth/register received", "DTO @Valid validation passed", "AuthService.register() invoked", "Checking duplicate email"]
    if find_user_by_id_or_email(email):
        animate_pipeline("Registration", nodes + ["Duplicate found"], success=False)
        draw_http_log("POST", "/api/auth/register", 409)
        get_input("Press Enter to return"); return
        
    global next_user_id
    nodes.extend(["RoleRepository.findByName(ROLE) → assigned", "BCryptPasswordEncoder.encode()", "UserRepository.save()", "EmailVerificationService.sendVerification()", "HTTP 201 Created"])
    animate_pipeline("Registration", nodes)
    
    USERS.append({"id":str(next_user_id), "username":name.split()[0].lower(), "email":email, "password":pwd, "role":role, "status":"ACTIVE", "emailVerified":False, "riskScore":0, "createdAt":now_iso()[:10]})
    next_user_id += 1
    draw_http_log("POST", "/api/auth/register", 201)
    console.print("[green]Registration successful.[/green]"); time.sleep(1)
    if auto_login: flow_login()

def flow_login():
    transition("Login")
    email = get_input("Email")
    pwd = get_password("Password")
    
    nodes = ["POST /api/auth/login received", "JwtAuthenticationFilter → permitted", "AuthenticationManager.authenticate()", f"CustomUserDetailsService.loadUserByUsername({email})", "UserRepository.findByEmail()"]
    
    u = find_user_by_id_or_email(email)
    if not u or u["password"] != pwd:
        animate_pipeline("Login", nodes + ["BCrypt.verify() → NO MATCH"], success=False)
        draw_http_log("POST", "/api/auth/login", 401)
        get_input("Press Enter to return"); return
        
    nodes.extend(["BCrypt.verify() → MATCH", "JwtTokenProvider.generateToken()", "RefreshTokenService.createRefreshToken()", "HTTP 200 OK"])
    animate_pipeline("Login", nodes)
    
    SESSION["user"] = u
    SESSION["token"] = fake_jwt()
    SESSION["session_start"] = datetime.now()
    SESSION["actions"] = []
    
    draw_http_log("POST", "/api/auth/login", 200)
    time.sleep(1)
    
    if u["role"] == "EMPLOYEE": employee_dashboard()
    elif u["role"] == "ANALYST": analyst_dashboard()
    elif u["role"] == "ADMIN": admin_dashboard()

# --- DASHBOARDS ---

def employee_dashboard():
    while True:
        if not SESSION.get("user"): return
        clear_screen(); draw_status_bar(); draw_notifications()
        u = SESSION["user"]
        
        left = Panel(f"Name: {u['username']}\nEmail: {u['email']}\nRole: {u['role']}\nStatus: {u['status']}", title="Profile")
        right = Panel(f"Score: {u['riskScore']}\n{risk_bar(u['riskScore'])}", title="Risk Score")
        console.print(Columns([left, right]))
        
        opts = {"1":"View full profile", "2":"Recalculate my risk score", "3":"View my alerts", "4":"View my activity history", "5":"Change my password", "6":"Request password reset", "7":"View system architecture", "8":"Logout", "9":"Simulate suspicious activity"}
        c = ask_menu(opts)
        if c == "IGNORE": continue
        elif c == "1": interactive_user_card(u["id"])
        elif c == "2": risk_monitor(u["id"])
        elif c == "3": alert_inbox(u["id"])
        elif c == "4":
            def r_acts(chunk):
                t = Table()
                t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                console.print(t)
            paginate(get_user_activities(u["id"]), render_fn=r_acts)
        elif c == "5":
            if get_password("Old password") == u["password"]:
                np = get_password("New password")
                if get_password("Confirm") == np: u["password"] = np; save_confirmation(); draw_http_log("PATCH", "/api/users/me/password", 200)
        elif c == "6":
            animate_pipeline("Reset Password", ["Locate user by email", "Generate UUID token", "Save to password_reset_tokens", "Send reset email", "HTTP 200 OK"])
            console.print(f"Fake reset URL: https://sentinel-x.app/reset?token={uuid.uuid4()}"); get_input("Enter")
        elif c == "7": screen_architecture()
        elif c == "8": session_logout_summary(); return
        elif c == "9":
            console.print("[yellow]Simulating 15 off-hours 'EXPORT_DATA' activities...[/yellow]")
            global next_activity_id
            for _ in range(15):
                ACTIVITIES.insert(0, {"id": str(next_activity_id), "userId": u["id"], "action": "EXPORT_DATA", "entityType": "REPORT", "entityId": "99", "metadata": '{"ip":"203.0.113.1"}', "createdAt": datetime.now().replace(hour=3)})
                next_activity_id += 1
            time.sleep(1)
            console.print("[green]Suspicious activities logged! Check option 4, then press 2 to recalculate your risk![/green]")
            get_input("Press Enter to continue")

def analyst_dashboard():
    while True:
        if not SESSION.get("user"): return
        clear_screen(); draw_status_bar(); draw_notifications()
        console.print(f"Total Users: {len(USERS)} | Open Alerts: {len([a for a in ALERTS if a['status']=='OPEN'])} | Critical Alerts: {len([a for a in ALERTS if a['severity']=='CRITICAL'])} | High Risk Users: {len([u for u in USERS if u['riskScore']>=60])}\n")
        
        opts = {"1":"View my profile", "2":"View all alerts", "3":"Update alert status", "4":"Assign alert to analyst", "5":"Search user activity log", "6":"View risk score for any user", "7":"Trigger risk recalculation for user", "8":"View my own risk score", "9":"View system architecture", "10":"Logout"}
        c = ask_menu(opts)
        if c == "IGNORE": continue
        elif c == "1": interactive_user_card(SESSION["user"]["id"])
        elif c == "2": alert_inbox()
        elif c == "3":
            aid = get_input("Alert ID")
            a = find_alert_by_id(aid)
            if a:
                console.print(f"Valid transitions: {VALID_TRANSITIONS.get(a['status'], [])}")
                ns = get_input("New status").upper()
                if ns in VALID_TRANSITIONS.get(a['status'], []): a["status"] = ns; save_confirmation(); draw_http_log("PATCH", f"/api/alerts/{aid}", 200)
        elif c == "4":
            aid = get_input("Alert ID")
            an = get_input("Analyst username")
            if find_alert_by_id(aid) and find_user_by_id_or_email(an): find_alert_by_id(aid)["assignedTo"] = an; save_confirmation(); draw_http_log("PATCH", "/assign", 200)
        elif c == "5":
            uid = get_input("User ID/Email")
            if u:=find_user_by_id_or_email(uid):
                def r_acts(chunk):
                    t = Table()
                    t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                    for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                    console.print(t)
                paginate(get_user_activities(u["id"]), render_fn=r_acts)
        elif c == "6": risk_monitor(get_input("User ID"))
        elif c == "7": risk_monitor(get_input("User ID"))
        elif c == "8": risk_monitor(SESSION["user"]["id"])
        elif c == "9": screen_architecture()
        elif c == "10": session_logout_summary(); return

def admin_dashboard():
    while True:
        if not SESSION.get("user"): return
        clear_screen(); draw_status_bar(); draw_notifications()
        console.print(f"Users: {len(USERS)} | Active: {len([u for u in USERS if u['status']=='ACTIVE'])} | Alerts: {len(ALERTS)} | Open Alerts: {len([a for a in ALERTS if a['status']=='OPEN'])} | Avg Risk: {int(sum(u['riskScore'] for u in USERS)/len(USERS))} | High Risk: {len([u for u in USERS if u['riskScore']>=60])}")
        console.print(Panel("\n".join(SYSTEM_FEED[:6]), title="LIVE SYSTEM FEED", border_style="dim"))
        
        opts = {
            "UM": "── USER MANAGEMENT ──", "1": "View all users", "2": "Create new user", "3": "View user details", "4": "Update user status", "5": "Update user role", "6": "Delete user",
            "AM": "── ALERT MANAGEMENT ──", "7": "View all alerts", "8": "Update alert status", "9": "Assign alert", "10": "Acknowledge alert", "11": "Resolve alert", "12": "Delete alert",
            "RA": "── RISK & ACTIVITY ──", "13": "View all risk scores", "14": "Trigger risk recalculation", "15": "View activity log — any user", "16": "View entity activity",
            "AN": "── ANALYTICS ──", "17": "System analytics (5 tabs)",
            "SY": "── SYSTEM ──", "18": "Run E2E Auth Flow Demo", "19": "View system architecture", "20": "Logout", "21": "View/Edit detection rules", "22": "View audit logs"
        }
        c = ask_menu(opts)
        if c == "IGNORE": continue
        elif c == "1":
            def r_u(chunk):
                t = Table()
                t.add_column("ID"); t.add_column("User"); t.add_column("Role"); t.add_column("Risk")
                for u in chunk: t.add_row(u["id"], u["username"], u["role"], risk_bar(u["riskScore"]))
                console.print(t)
            paginate(USERS, page_size=10, render_fn=r_u)
        elif c == "2": flow_register(auto_login=False)
        elif c == "3": interactive_user_card(get_input("User ID"))
        elif c == "4":
            u = find_user_by_id_or_email(get_input("User ID"))
            if u: u["status"] = get_input("New status (ACTIVE/SUSPENDED/LOCKED)").upper(); track_action("Update user status"); save_confirmation()
        elif c == "5":
            u = find_user_by_id_or_email(get_input("User ID"))
            if u: u["role"] = get_input("New role").upper(); track_action("Update user role"); save_confirmation()
        elif c == "6":
            u = find_user_by_id_or_email(get_input("User ID"))
            if u and confirm("Delete user?"): USERS.remove(u); save_confirmation()
        elif c == "7": alert_inbox()
        elif c == "8":
            aid = get_input("Alert ID")
            a = find_alert_by_id(aid)
            if a:
                console.print(f"Valid transitions: {VALID_TRANSITIONS.get(a['status'], [])}")
                ns = get_input("New status").upper()
                if ns in VALID_TRANSITIONS.get(a['status'], []): a["status"] = ns; save_confirmation(); draw_http_log("PATCH", f"/api/alerts/{aid}", 200)
        elif c == "9":
            aid = get_input("Alert ID")
            an = get_input("Analyst username")
            if find_alert_by_id(aid) and find_user_by_id_or_email(an): find_alert_by_id(aid)["assignedTo"] = an; save_confirmation(); draw_http_log("PATCH", "/assign", 200)
        elif c == "10":
            a = find_alert_by_id(get_input("Alert ID"))
            if a and a["status"] in ["OPEN", "UNDER_INVESTIGATION"]: a["status"] = "ACKNOWLEDGED"; save_confirmation()
        elif c == "11":
            a = find_alert_by_id(get_input("Alert ID"))
            if a and a["status"] == "ACKNOWLEDGED": a["status"] = "RESOLVED"; save_confirmation()
        elif c == "12":
            a = find_alert_by_id(get_input("Alert ID"))
            if a and confirm("Delete alert?"): ALERTS.remove(a); save_confirmation()
        elif c == "13":
            def r_scores(chunk):
                t = Table()
                t.add_column("Rank"); t.add_column("Username"); t.add_column("Score"); t.add_column("Bar")
                for i, u in enumerate(chunk): t.add_row(str(i+1), u["username"], str(u["riskScore"]), risk_bar(u["riskScore"]))
                console.print(t)
            paginate(sorted(USERS, key=lambda x: x["riskScore"], reverse=True), render_fn=r_scores)
        elif c == "14": risk_monitor(get_input("User ID"))
        elif c == "15": 
            u = find_user_by_id_or_email(get_input("User ID"))
            if u:
                def r_acts(chunk):
                    t = Table()
                    t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                    for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                    console.print(t)
                paginate(get_user_activities(u["id"]), render_fn=r_acts)
        elif c == "16":
            et = get_input("Entity Type").upper()
            def r_acts(chunk):
                t = Table()
                t.add_column("Timestamp"); t.add_column("Action"); t.add_column("Entity Type"); t.add_column("Entity ID"); t.add_column("Metadata")
                for a in chunk: t.add_row(str(a["createdAt"])[:19], a["action"], a["entityType"], a["entityId"], a["metadata"])
                console.print(t)
            paginate([a for a in ACTIVITIES if a["entityType"]==et], render_fn=r_acts)
        elif c == "17": interactive_analytics()
        elif c == "18": run_e2e_demo()
        elif c == "19": screen_architecture()
        elif c == "20": session_logout_summary(); return
        elif c == "21":
            def r_rules(chunk):
                t = Table()
                t.add_column("ID"); t.add_column("Name"); t.add_column("Active")
                for r in chunk: t.add_row(str(r["id"]), r["name"], "✓" if r["active"] else "✗")
                console.print(t)
                rid = get_input("Enter rule ID to toggle or [B]ack")
                if rid.isdigit():
                    r = next((x for x in RULES if str(x["id"])==rid), None)
                    if r: r["active"] = not r["active"]; save_confirmation()
                    return "REFRESH"
            paginate(RULES, render_fn=r_rules)
        elif c == "22":
            def r_audit(chunk):
                t = Table()
                t.add_column("Event"); t.add_column("Time")
                for a in chunk: t.add_row(a["event"], str(a["createdAt"]))
                console.print(t)
            paginate(AUDIT_LOGS, render_fn=r_audit)

def interactive_analytics():
    tab = "1"
    show_all = True
    while True:
        clear_screen(); draw_status_bar()
        console.print("[1]Top Users [2]Alert Trends [3]Risk Trends [4]Severity Map [5]System Health\n")
        
        if tab == "1":
            t = Table(title="Top Risky Users")
            t.add_column("Rank"); t.add_column("Username"); t.add_column("Score"); t.add_column("Bar"); t.add_column("Alerts")
            for i, u in enumerate(sorted(USERS, key=lambda x: x["riskScore"], reverse=True)[:5]):
                t.add_row(str(i+1), u["username"], str(u["riskScore"]), risk_bar(u["riskScore"]), str(len(get_user_alerts(u["id"]))))
            console.print(t)
        elif tab == "2":
            t = Table(title="Alert Trends (Last 7 Days)", show_header=False)
            counts = {}
            for a in ALERTS:
                if not show_all and a["status"] != "OPEN": continue
                d = str(a["createdAt"])[:10]
                counts[d] = counts.get(d, 0) + 1
            for d in sorted(counts.keys())[-7:]:
                t.add_row(d, f"[red]{'█'*counts[d]}[/]", str(counts[d]))
            console.print(t)
        elif tab == "3":
            console.print(Panel("Week 1  ▁  18\nWeek 2  ▂  24\nWeek 3  ▃  31\nWeek 4  ▄  42\nWeek 5  ▅  50\nWeek 6  ▆  57\nWeek 7  ▇  63\nWeek 8  █  56  ← current", title="Risk Trends"))
        elif tab == "4":
            sev = {"CRITICAL":0, "HIGH":0, "MEDIUM":0, "LOW":0}
            for a in ALERTS: sev[a["severity"]] += 1
            console.print(Panel("\n".join([f"{k:<10} {'█'*v} {v} alerts" for k,v in sev.items()]), title="Severity Map"))
        elif tab == "5":
            t = Table(title="System Health")
            t.add_column("Component"); t.add_column("Status"); t.add_column("Latency")
            for c in ["DB Connection", "JWT Service", "Email Service", "Flyway", "SecurityFilter", "RefreshToken Store"]:
                t.add_row(c, "[green]● UP[/]", f"{random.randint(1,50)}ms")
            console.print(t)
            
        c = get_input("Enter tab [1-5], [B]ack, or specific action").upper()
        if c == 'B': return
        elif c in ['1','2','3','4','5']: tab = c
        elif c == 'F' and tab == '2': show_all = not show_all
        elif c == 'R' and tab == '5':
            clear_screen(); console.print("Refreshing..."); time.sleep(1)

# --- MAIN ---

def main():
    generate_data()
    try:
        screen_splash()
        while True: screen_main_menu()
    except (KeyboardInterrupt, EOFError):
        console.print("\n[bold red]Exiting...[/bold red]")
        sys.exit(0)

if __name__ == "__main__":
    main()
