# PostFusion — simple API setup (no database)

React talks to Spring Boot over HTTP. Spring Boot keeps users, accounts, and posts **in memory** (data resets when the server restarts).

## Run backend

Needs **JDK 17+** only. This project includes a **Maven Wrapper** (`mvnw`).

PostgreSQL must be running with database `postfusion_db` (see below).

```bash
cd backend
./mvnw spring-boot:run
```

### PostgreSQL

Create DB (if not already):

```sql
CREATE DATABASE postfusion_db;
```

In `backend/.env`:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/postfusion_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_postgres_password
```

Tables are created automatically on startup (`ddl-auto: update`).

Windows CMD/PowerShell: `mvnw.cmd spring-boot:run`

Health check: http://localhost:8080/api/health

LinkedIn secrets load from `backend/.env` (gitignored).

## Run frontend

```bash
cd frontend
npm start
```

Open the URL shown (usually http://localhost:3000). If it opens on **3001**, set in `backend/.env`:

```env
FRONTEND_URL=http://localhost:3001
```

Then restart the backend.

## LinkedIn (live posting)

### 1. LinkedIn Developer App settings

In [LinkedIn Developers](https://www.linkedin.com/developers/apps):

1. Open your app → **Auth**
2. Add Authorized redirect URL exactly:
   - `http://localhost:8080/api/oauth/linkedin/callback`
3. Products to enable (Products tab → request/add until status is **Added**):
   - **Sign In with LinkedIn using OpenID Connect** → scopes `openid profile email`
   - **Share on LinkedIn** → scope `w_member_social`

If you see **"The requested permission scope is invalid"**:
1. Confirm both products above show **Added** (not Pending)
2. Restart the backend after changing `.env`
3. Temporary test without posting (login only): set  
   `LINKEDIN_SCOPES=openid profile email`  
   then add `w_member_social` again after Share on LinkedIn is Added


### 2. Secrets in `backend/.env`

```env
LINKEDIN_CLIENT_ID=your_client_id
LINKEDIN_CLIENT_SECRET=your_client_secret
FRONTEND_URL=http://localhost:3000
LINKEDIN_REDIRECT_URI=http://localhost:8080/api/oauth/linkedin/callback
```

### 3. Use in the app

1. Sign up / log in to PostFusion
2. Go to **Accounts** → **Connect** on LinkedIn → approve on LinkedIn
3. Go to **Compose**, select LinkedIn, publish → post appears on your LinkedIn feed

### Facebook Page posting (live)

Uses a Page ID + Page Access Token in `backend/.env`:

```env
FACEBOOK_PAGE_ID=your_page_id
FACEBOOK_PAGE_ACCESS_TOKEN=your_page_token
```

1. Restart backend after saving `.env`
2. In the app: **Accounts → Connect Facebook** (verifies the token)
3. **Compose → select Facebook → Publish**

Posts go to that Facebook Page via Graph API `/feed`.

### Threads posting (live)

Uses a Threads user ID + long-lived access token in `backend/.env`:

```env
THREADS_ACCESS_TOKEN=your_long_lived_threads_token
# THREADS_USER_ID is optional — do not use a Facebook Page ID
```

1. In [Meta for Developers](https://developers.facebook.com/), create an app with the **Threads** use case
2. Request permissions: `threads_basic`, `threads_content_publish`
3. Generate a **Threads** user access token (not a Facebook Page token), then exchange for a long-lived token
4. Confirm the profile with:  
   `GET https://graph.threads.net/v1.0/me?fields=id,username&access_token=YOUR_TOKEN`
5. Put `THREADS_ACCESS_TOKEN` in `backend/.env` and restart
6. **Accounts → Connect Threads** → **Compose → select Threads → Publish**

If you see “Object with ID … does not exist / missing permissions”, the ID was wrong or the token lacks `threads_content_publish`. Leave `THREADS_USER_ID` blank so the app publishes via `/me`.

Text posts work with the token alone. Image posts need Meta to fetch a public URL — set:

```env
PUBLIC_BASE_URL=https://your-ngrok-or-public-host
```

(localhost will not work for Threads images.) Without `PUBLIC_BASE_URL`, caption-only still publishes; image-only fails with a clear error.


## Flow

1. Signup / Login → JWT-like token in browser
2. Connect LinkedIn → OAuth → token stored in memory on server
3. Connect Facebook / Threads → verify configured tokens
4. Publish → live APIs for LinkedIn, Facebook, Threads; Instagram simulated
5. History / Dashboard read from API

## API map

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/health` | no |
| POST | `/api/auth/signup` | no |
| POST | `/api/auth/login` | no |
| GET | `/api/oauth/linkedin/redirect?token=...` | token query |
| GET | `/api/oauth/linkedin/callback` | LinkedIn redirect |
| GET | `/api/social-accounts` | yes |
| DELETE | `/api/social-accounts/{id}` | yes |
| GET | `/api/posts` | yes |
| POST | `/api/posts` | yes |
| GET | `/api/media/{id}` | no (temp image for Threads) |

## Optional: install Maven globally (Windows)

1. Download: https://maven.apache.org/download.cgi
2. Unzip and add `bin` to PATH
3. Restart terminal → `mvn -v`
