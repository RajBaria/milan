/**
 * Firebase Web Configuration for Milan
 * Shares the exact same Firebase Project & Named Firestore Database as the Android app.
 *
 * PUBLISHING CHECKLIST:
 * 1. In Firebase Console -> Authentication -> Settings -> Authorized domains:
 *    Add your live website domain (e.g. yourdomain.com, www.yourdomain.com, or your-app.netlify.app).
 * 2. Optional: If you register a Web App in Firebase Console -> Project Settings -> Your Apps,
 *    you can replace `appId` below with your Web App ID (optional for Auth + Firestore).
 */
export const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAEyHQm4YsXoG3HLwX3XeatVoUgCHpzvls",
  authDomain: "gen-lang-client-0827004385.firebaseapp.com",
  projectId: "gen-lang-client-0827004385",
  storageBucket: "gen-lang-client-0827004385.firebasestorage.app",
  messagingSenderId: "775530719250",
  appId: "1:775530719250:web:milancompanionweb"
};

// Must match app/src/main/res/values/firebase_applet_config.xml -> firestore_database_id
export const FIRESTORE_DATABASE_ID = "ai-studio-android-milan-cf65548f-3cec-4024-88d4-b06cf7eebde0";

// Shared Milan App Constants (mirrors DatingModels.kt & MilanViewModel.kt)
export const ADMIN_EMAIL = "rk3171998@gmail.com";
export const ADMIN_SUPPORT_USER_ID = "milan_admin_support";
export const MILAN_UPI_ID = "rk3171998@oksbi";
export const UPI_RECEIVER_NAME = "Milan";

export const DAILY_STREAK_TARGET_SECONDS = 15 * 60; // 900 seconds
export const STREAK_REQUIRED_DAYS = 6;
export const MAX_FREE_DISTANCE_KM = 50;
export const MAX_PREMIUM_DISTANCE_KM = 5000;
export const UNLIMITED_DISTANCE_KM = 25000;

export const FREE_DAILY_MESSAGE_LIMIT = 5;
export const FREE_DAILY_RANDOM_FIND_LIMIT = 2;
export const FREE_MAX_LIKES_LIMIT = 15;
export const FREE_MAX_PROFILE_VIEWS = 25;

export const ALL_INTERESTS = [
  "Music", "Travel", "Coffee", "Fitness", "Art",
  "Foodie", "Books", "Movies", "Photography", "Sports"
];

export const AVATAR_PRESETS = [
  { id: "avatar_rose", label: "Rose" },
  { id: "avatar_lavender", label: "Lavender" },
  { id: "avatar_mint", label: "Mint" },
  { id: "avatar_peach", label: "Peach" },
  { id: "avatar_sky", label: "Sky" }
];

export const COLOR_THEMES = [
  { id: "pink", name: "Pink", swatch: "#E86F88" },
  { id: "black", name: "Black", swatch: "#1E1E22" },
  { id: "green", name: "Green", swatch: "#2E9E6B" },
  { id: "blue", name: "Blue", swatch: "#2563EB" },
  { id: "orange", name: "Orange", swatch: "#EA580C" },
  { id: "purple", name: "Purple", swatch: "#7C3AED" }
];
