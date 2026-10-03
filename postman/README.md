# HLS Postman collection

- `HLS.postman_collection.json` — one folder per spec. When a spec adds endpoints, add a folder
  (or requests) in the same commit.
- `HLS-Local|Dev|Prod.postman_environment.json` — `baseUrl`, `loginIdentifier`, `loginPassword`
  (secret), `loginPhone`. Dev and Prod `baseUrl` are placeholders; set the real hosts. Never commit
  credentials; fill them in Postman only.

Run **Password Login** first; it stores `accessToken` (collection variable, Bearer auth) for the
other requests.
