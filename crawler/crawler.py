import os, time, json, sys
from typing import Dict, Any, Iterable
import requests, pymysql
from dotenv import load_dotenv
load_dotenv()
TARGET_URL=os.getenv("TARGET_URL")
AUTH_HEADER=os.getenv("AUTH_HEADER",""); AUTH_VALUE=os.getenv("AUTH_VALUE","")
PAGE_PARAM=os.getenv("PAGE_PARAM","page"); SIZE_PARAM=os.getenv("SIZE_PARAM","size")
PAGE_SIZE=int(os.getenv("PAGE_SIZE","50")); REQ_INTERVAL=float(os.getenv("REQUEST_INTERVAL_SEC","0.5"))
TIMEOUT=int(os.getenv("TIMEOUT_SEC","20")); MOCK_JSON=(os.getenv("MOCK_JSON","") or "").strip()
SOURCE_NAME=os.getenv("SOURCE_NAME","target")
MYSQL=dict(host=os.getenv("MYSQL_HOST","127.0.0.1"),port=int(os.getenv("MYSQL_PORT","3306")),
           user=os.getenv("MYSQL_USER","app"),password=os.getenv("MYSQL_PASSWORD","app_pw"),
           database=os.getenv("MYSQL_DB","appdb"),charset="utf8mb4",autocommit=False)
def headers()->Dict[str,str]:
  h={"User-Agent":"Mozilla/5.0","Accept":"application/json"}
  if AUTH_HEADER and AUTH_VALUE: h[AUTH_HEADER]=AUTH_VALUE
  return h
def fetch(page:int)->Dict[str,Any]:
  if MOCK_JSON:
    with open(MOCK_JSON,"r",encoding="utf-8") as f: return json.load(f)
  assert TARGET_URL, "TARGET_URL not set"
  r=requests.get(TARGET_URL, params={PAGE_PARAM:page,SIZE_PARAM:PAGE_SIZE}, headers=headers(), timeout=TIMEOUT)
  r.raise_for_status(); return r.json()
def items()->Iterable[Dict[str,Any]]:
  page=1
  while True:
    data=fetch(page); arr=data.get("items") or data.get("data") or []
    if not arr: break
    for it in arr: yield it
    page+=1; time.sleep(REQ_INTERVAL)
def upsert(conn, it:Dict[str,Any]):
  item_id=str(it.get("id") or it.get("uuid") or it.get("pk") or "")
  if not item_id: return
  source=it.get("source") or SOURCE_NAME
  payload=json.dumps(it, ensure_ascii=False)
  with conn.cursor() as cur:
    cur.execute("""INSERT INTO raw_listing (id, source, payload, status, scraped_at)
                   VALUES (%s,%s,%s,OK,NOW())
                   ON DUPLICATE KEY UPDATE payload=VALUES(payload), scraped_at=NOW()""",
                (item_id, source, payload))
def main():
  conn=pymysql.connect(**MYSQL); n=0
  try:
    for it in items(): upsert(conn,it); n+=1
    conn.commit(); print(f"OK: upserted {n} rows", file=sys.stderr)
  except Exception as e:
    conn.rollback(); print(f"ERROR: {e}", file=sys.stderr); raise
  finally:
    conn.close()
if __name__=="__main__": main()
