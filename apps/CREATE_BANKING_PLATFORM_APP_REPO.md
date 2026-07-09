# Create the GitHub repository `banking-platform-app`

This agent could not create `Phoenix-Q5/banking-platform-app` because the
installation token lacks `createRepository` permission. The full iOS app is
ready to push.

## Option A — create empty repo on GitHub, then push

1. Open https://github.com/new
2. Owner: `Phoenix-Q5`
3. Repository name: `banking-platform-app`
4. Public, **no** README / gitignore / license
5. Create repository
6. From a machine with your GitHub credentials:

```bash
# If you have this workspace:
cd apps/ios-harbor-bank
git init -b main
git add -A
git commit -m "Initial Harbor Bank iOS mobile banking app"
git remote add origin https://github.com/Phoenix-Q5/banking-platform-app.git
git push -u origin main
```

Or from the standalone copy prepared by the agent (if available on the agent host):

```bash
cd /home/ubuntu/banking-platform-app   # already has git history
git remote add origin https://github.com/Phoenix-Q5/banking-platform-app.git
git push -u origin main
```

## Option B — use the included source tree

The complete SwiftUI project lives in this platform repo at:

```
apps/ios-harbor-bank/
```

Copy that folder into a new git repo and push as above.

## After the repo exists

Ask the agent (or run locally):

```bash
git remote add origin https://github.com/Phoenix-Q5/banking-platform-app.git
git push -u origin main
```
