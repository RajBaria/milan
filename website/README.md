# Milan Web — Companion Website (`https://rajbaria.github.io/milan/`)

This folder contains the complete, ready-to-publish **static website** (`index.html`, `styles.css`, and `js/`) for **Milan**, connected to the exact same Firebase project (`gen-lang-client-0827004385`) and named Firestore database (`ai-studio-android-milan-cf65548f-3cec-4024-88d4-b06cf7eebde0`) as the Android app.

---

## Publishing on GitHub Pages (`https://github.com/RajBaria/milan.git`)

A GitHub Actions workflow (`.github/workflows/deploy-website.yml`) is already included to publish the `website/` folder automatically to **`https://rajbaria.github.io/milan/`**.

### Step 1: Push This Project to GitHub
Push the repository to `https://github.com/RajBaria/milan.git` (either using AI Studio's **Push to GitHub** button or `git push origin main`).

### Step 2: Enable GitHub Pages in Your Repository
1. Open **`https://github.com/RajBaria/milan/settings/pages`**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Go to the **Actions** tab and run **Deploy Milan Website to GitHub Pages** (it also runs automatically on every push to `website/`).
4. Once finished, your live website URL will be: **`https://rajbaria.github.io/milan/`**.

### Step 3: Authorize `rajbaria.github.io` in Firebase (Required for Google Sign-In)
1. Open the [Firebase Console](https://console.firebase.google.com/) and select project **`gen-lang-client-0827004385`**.
2. Go to **Authentication** → **Settings** → **Authorized domains**.
3. Click **Add domain** and enter:
   ```
   rajbaria.github.io
   ```
4. Confirm **Google** is enabled under **Authentication** → **Sign-in method**.

After those 3 steps, anyone visiting **`https://rajbaria.github.io/milan/`** can sign in with Google and chat in real time with Android app users!
