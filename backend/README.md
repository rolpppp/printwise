# Printwise Gemini backend

This service keeps the Gemini API key off mobile devices. The Android app sends a JPEG payload to `POST /v1/class-suggestions`; this service calls Gemini and returns validated class labels only. It never generates print quantities.

## Local setup

1. Copy `.env.example` to `.env`.
2. Put the Gemini key in `.env` as `GEMINI_API_KEY`.
3. Run `npm install`, then `npm start`.
4. Check `GET http://localhost:8080/health`.

Never commit `.env` or paste a production key into source control.

## Free deployment: Cloudflare Workers

The recommended deployment is the Cloudflare Workers free plan. It does not require the Cloud Run one-time billing setup. Cloudflare account limits and Gemini API quotas still apply; Gemini itself may require billing depending on the account/model.

Install Wrangler and log in:

```powershell
npm install --save-dev wrangler
npx wrangler login
```

From this directory, store the key as an encrypted Worker secret and deploy:

```powershell
npx wrangler secret put GEMINI_API_KEY
npx wrangler deploy
```

Paste the key only when Wrangler prompts for it. Never put it in `wrangler.toml` or commit `.env`.

The deploy command prints an HTTPS URL such as `https://printwise-gemini.YOUR_ACCOUNT.workers.dev`. Use that base URL in the Android build:

```powershell
.\gradlew.bat assembleDebug -PprintwiseGeminiBackendUrl=https://printwise-gemini.YOUR_ACCOUNT.workers.dev
```

The app appends `/v1/class-suggestions`.

The Express service remains available for local development; `cloudflare-worker.js` is the production-free deployment entrypoint.
