# Phase 20 Part 2 — Setup notes (Friend Activity Feed + Push Notifications)

## 1. Friend Activity Feed
No setup needed — it reuses the existing `recently_played` column in `user_data`
that's already synced. Just build and test.

## 2. Push Notifications (friend requests)

This is a real infrastructure feature, not just app code — three things need
to happen **once**, outside the Android project, with your own accounts:

### Step A — Create a Firebase project (you, one-time)
1. Go to https://console.firebase.google.com → Add project (name doesn't matter).
2. Add an Android app inside it with package name `com.soundwave.app`.
3. Download **`google-services.json`** and place it at `app/google-services.json`
   in the repo (same folder as `app/build.gradle.kts`).
4. **Until you do this, the app builds and runs completely normally** — the
   push notification code silently does nothing (this was deliberately
   built this way so it can't break your current CI). Once the file is
   added and committed, the next Actions build wires it in automatically.

### Step B — Add the `fcm_token` column (run once in Supabase SQL editor)
```sql
alter table public.profiles add column if not exists fcm_token text;
```
(Existing grants/RLS on `profiles` already cover this — no new policy needed
since it's just a new column on a table you already have policies for.)

### Step C — Deploy the notification-sending Edge Function (you, one-time)
This is the piece that actually sends the push when someone gets a friend
request. It needs a Firebase **service account key** (from Firebase Console →
Project Settings → Service Accounts → Generate new private key) stored as a
Supabase secret, and the Supabase CLI to deploy.

```bash
supabase secrets set FIREBASE_SERVICE_ACCOUNT='<paste the whole service account JSON here>'
supabase functions deploy send-friend-notification
```

Function code (`supabase/functions/send-friend-notification/index.ts`):
```ts
import { serve } from "https://deno.land/std/http/server.ts"

serve(async (req) => {
  const { receiver_fcm_token, title, body } = await req.json()
  if (!receiver_fcm_token) return new Response("no token", { status: 400 })

  const serviceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!)
  const accessToken = await getGoogleAccessToken(serviceAccount)

  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: receiver_fcm_token,
          notification: { title, body },
        },
      }),
    }
  )
  return new Response(await resp.text(), { status: resp.status })
})

// Standard Google OAuth2 JWT bearer flow for a service account.
async function getGoogleAccessToken(sa: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header = { alg: "RS256", typ: "JWT" }
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }
  const enc = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_")
  const unsigned = `${enc(header)}.${enc(claim)}`
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBuf(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  )
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned))
  const jwt = `${unsigned}.${btoa(String.fromCharCode(...new Uint8Array(sig))).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_")}`

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  })
  const { access_token } = await tokenResp.json()
  return access_token
}

function pemToBuf(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s/g, "")
  const bin = atob(b64)
  const buf = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i)
  return buf.buffer
}
```

### Step D — Trigger it when a friend request is sent/accepted
Simplest reliable option: a **Database Webhook** in the Supabase dashboard
(Database → Webhooks → Create), not a SQL trigger — this avoids needing the
`pg_net` extension enabled and is one click:
- Table: `friend_requests`
- Events: `Insert` (new request) and `Update` (for the accepted case, filter
  `status = accepted` in the webhook's payload check or a small `WHERE`
  condition if the dashboard supports it)
- URL: your deployed function URL, e.g.
  `https://<project-ref>.functions.supabase.co/send-friend-notification`
- The webhook payload includes the new row; the function will need a small
  lookup (via a second call, or a Postgres trigger function instead if you'd
  rather resolve `receiver_fcm_token` server-side before calling out) —
  this part is genuinely your call since it depends on how much logic you
  want in SQL vs. the Edge Function. Flagging this explicitly rather than
  guessing at your preference.

### Why this couldn't be fully finished end-to-end from here
Steps A–D each require credentials/consoles that only you have access to
(your Firebase account, your Supabase project's secrets and CLI login). The
Android app is fully built and ready — sign-in already pushes the device's
token, the notification shows and deep-links into the Friends screen when
tapped — but the "send" side is infrastructure you'll need to click through
yourself, once.
