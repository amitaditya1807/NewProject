# Copy this file to set-env.local.ps1 and fill in your real local values.
# set-env.local.ps1 is ignored by Git.

$env:DB_PASSWORD="YOUR_POSTGRES_PASSWORD"
$env:JWT_SECRET="YOUR_LONG_JWT_SECRET"
$env:GOOGLE_CLIENT_ID="YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET="YOUR_GOOGLE_CLIENT_SECRET"
$env:GITHUB_CLIENT_ID="YOUR_GITHUB_CLIENT_ID"
$env:GITHUB_CLIENT_SECRET="YOUR_GITHUB_CLIENT_SECRET"
$env:GITHUB_REDIRECT_URI="http://127.0.0.1:5500/frontend/simple-pages/github-callback.html"
