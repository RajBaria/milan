import { ADMIN_EMAIL } from "./firebase-config.js";

/**
 * Escapes HTML entities to keep all user-generated text XSS-safe.
 */
export function escapeHtml(value) {
  if (value === null || value === undefined) return "";
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Deterministic chatId matching Android MilanRepository.buildChatId(uid1, uid2)
 */
export function buildChatId(uid1, uid2) {
  return uid1 < uid2 ? `${uid1}_${uid2}` : `${uid2}_${uid1}`;
}

/**
 * Haversine distance in km rounded to 1 decimal place (matches Android MilanRepository.calculateDistanceKm)
 */
export function calculateDistanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (deg) => (deg * Math.PI) / 180;
  const earthRadiusKm = 6371.0;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.floor(earthRadiusKm * c * 10) / 10;
}

export function todayDateString() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function yesterdayDateString() {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function timestampToMs(ts) {
  if (!ts) return 0;
  if (typeof ts === "number") return ts;
  if (typeof ts.toMillis === "function") return ts.toMillis();
  if (typeof ts.seconds === "number") return ts.seconds * 1000;
  return 0;
}

export function formatMessageDateTime(ts) {
  const ms = timestampToMs(ts);
  if (!ms) return "Just now";
  const d = new Date(ms);
  return d.toLocaleString("en-US", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function formatMillisDateTime(ms) {
  if (!ms || ms <= 0) return "";
  return new Date(ms).toLocaleString("en-US", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function isCurrentlyOnline(profile, nowMs = Date.now()) {
  if (!profile) return false;
  return Boolean(profile.isOnline && profile.lastActiveAtMs > 0 && nowMs - profile.lastActiveAtMs <= 120_000);
}

export function isVerifiedWithBlueTick(profile) {
  if (!profile) return false;
  return Boolean(profile.isProfileVerified || profile.verificationStatus === "Verified");
}

export function isPaidPremium(profile) {
  if (!profile) return false;
  return Boolean(profile.isPremium && (profile.premiumPlan === "Monthly" || profile.premiumPlan === "Yearly"));
}

export function fullNameOf(profile) {
  if (!profile) return "Milan Member";
  return `${profile.firstName || ""} ${profile.lastName || ""}`.trim() || "Milan Member";
}

export function isValidUpiUtr12Digits(rawUtr) {
  const cleaned = String(rawUtr || "").trim();
  if (!/^\d{12}$/.test(cleaned)) return false;
  if (new Set(cleaned.split("")).size <= 2) return false;
  if (cleaned === "123456789012" || cleaned === "987654321098" || cleaned === "012345678901") return false;
  return true;
}

function javaStringHashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i += 1) {
    hash = (hash * 31 + str.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

/**
 * Mirrors extractGoogleAccountIdentity() from DatingModels.kt
 */
export function extractGoogleAccountIdentity(displayName, email, uid) {
  const cleanEmail = String(email || "").trim();
  const isAdmin = cleanEmail.toLowerCase() === ADMIN_EMAIL.toLowerCase();

  const localEmailPart = cleanEmail.split("@")[0] || "";
  const rawName =
    (displayName && displayName.trim()) ||
    localEmailPart.replace(/[0-9_.-]+/g, " ").trim();

  const parts = rawName.split(/\s+/).filter(Boolean);
  const cap = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : "");

  const firstName = isAdmin
    ? "admin"
    : parts.length > 0
      ? cap(parts[0])
      : "Milan";

  const lastName = isAdmin
    ? "Milan"
    : parts.length > 1
      ? cap(parts.slice(1).join(" "))
      : "Member";

  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  const currentDay = now.getDate();

  const fullDateMatch = localEmailPart.match(/(\d{1,2})(\d{1,2})(19[6-9]\d|200[0-8])/);
  const yearMatch = localEmailPart.match(/(19[6-9]\d|200[0-8])/);

  let computedAge;
  let computedBirthDate;

  if (fullDateMatch) {
    const day = Math.min(28, Math.max(1, parseInt(fullDateMatch[1], 10) || 1));
    const month = Math.min(12, Math.max(1, parseInt(fullDateMatch[2], 10) || 1));
    const year = parseInt(fullDateMatch[3], 10) || 1998;
    let years = currentYear - year;
    if (currentMonth < month || (currentMonth === month && currentDay < day)) {
      years -= 1;
    }
    computedAge = Math.min(80, Math.max(18, years));
    computedBirthDate = `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  } else if (yearMatch) {
    const year = parseInt(yearMatch[1], 10) || 2000;
    computedAge = Math.min(80, Math.max(18, currentYear - year));
    computedBirthDate = `${String(year).padStart(4, "0")}-01-01`;
  } else {
    const seed = javaStringHashCode(uid || cleanEmail || "milan");
    computedAge = 21 + (seed % 10);
    const birthYear = currentYear - computedAge;
    computedBirthDate = `${String(birthYear).padStart(4, "0")}-01-01`;
  }

  const lowerFirst = firstName.toLowerCase();
  const lowerEmail = localEmailPart.toLowerCase();
  const femaleNames = new Set([
    "priya", "anjali", "neha", "pooja", "puja", "kavita", "riya", "sneha", "nisha", "divya",
    "kruti", "mansi", "bhakti", "riddhi", "siddhi", "janvi", "janhvi", "khushi", "payal",
    "komal", "meera", "aarti", "arti", "anita", "sunita", "gita", "sita", "radha", "shreya",
    "tanvi", "vidhi", "diya", "isha", "nandini", "swati", "kinjal", "hiral", "kajal", "hetal",
    "rupali", "sonal", "palak", "nikita", "shruti", "aditi", "kriti", "simran", "muskan",
    "ayesha", "fatima", "sara", "zoya", "mary", "emma", "olivia", "sophia", "ava", "mia",
    "dharti", "dhara", "bhumi", "bhoomi", "tejal", "jalpa", "ekta", "rekha", "asha", "usha"
  ]);
  const maleNames = new Set([
    "admin", "raj", "rajkumar", "rahul", "amit", "rohit", "vikram", "karan", "arjun", "harsh",
    "yash", "jay", "meet", "parth", "dhruv", "chirag", "bhavesh", "hardik", "pratik", "nikhil",
    "sanjay", "vijay", "ajay", "deepak", "manish", "suresh", "ramesh", "mahesh", "dinesh",
    "jignesh", "kalpesh", "mitesh", "hitesh", "rakesh", "mukesh", "ankit", "vishal", "sachin",
    "gaurav", "saurabh", "abhishek", "aditya", "aryan", "dev", "kunal", "mayur", "tushar",
    "vivek", "neeraj", "pankaj", "akash", "ashish", "ketan", "milan", "ronak", "keyur", "nirav"
  ]);

  let computedGender = "Male";
  if (isAdmin || lowerEmail.startsWith("rk")) {
    computedGender = "Male";
  } else if (femaleNames.has(lowerFirst) || [...femaleNames].some((n) => lowerEmail.startsWith(n))) {
    computedGender = "Female";
  } else if (maleNames.has(lowerFirst) || [...maleNames].some((n) => lowerEmail.startsWith(n))) {
    computedGender = "Male";
  } else if (
    lowerFirst.endsWith("ben") ||
    lowerFirst.endsWith("ba") ||
    lowerFirst.endsWith("i") ||
    lowerFirst.endsWith("a")
  ) {
    computedGender = "Female";
  }

  return {
    firstName,
    lastName,
    age: computedAge,
    birthDate: computedBirthDate,
    gender: computedGender
  };
}

/**
 * Converts a stored photoBase64 (raw Base64 from Android or Web) into a browser image src.
 */
export function getPhotoSrc(photoBase64) {
  if (!photoBase64 || typeof photoBase64 !== "string") return null;
  if (photoBase64.startsWith("avatar_") || photoBase64.length < 80) return null;
  if (photoBase64.startsWith("data:image/")) return photoBase64;
  const mime = photoBase64.startsWith("iVBOR") ? "image/png" : "image/jpeg";
  return `data:${mime};base64,${photoBase64}`;
}

export function avatarGradientStyle(photoBase64) {
  switch (photoBase64) {
    case "avatar_rose":
      return "linear-gradient(135deg, #F8BBD0, #E86F88)";
    case "avatar_lavender":
      return "linear-gradient(135deg, #D1C4E9, #8E7AB5)";
    case "avatar_mint":
      return "linear-gradient(135deg, #B2DFDB, #5FA896)";
    case "avatar_peach":
      return "linear-gradient(135deg, #FFCCBC, #E67E5A)";
    case "avatar_sky":
      return "linear-gradient(135deg, #BBDEFB, #5C9CE6)";
    default:
      return "linear-gradient(135deg, #F8BBD0, #8E7AB5)";
  }
}

export function initialsOf(displayName) {
  const clean = String(displayName || "").trim();
  if (!clean) return "M";
  return clean
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p.charAt(0).toUpperCase())
    .join("");
}

export function renderAvatarHtml(profileOrPhoto, displayName, sizePx = 58, options = {}) {
  const photoBase64 = typeof profileOrPhoto === "string" ? profileOrPhoto : profileOrPhoto?.photoBase64 || "avatar_rose";
  const isVerified = options.isVerified ?? (typeof profileOrPhoto === "object" && isVerifiedWithBlueTick(profileOrPhoto));
  const isPremium = options.isPremium ?? (typeof profileOrPhoto === "object" && Boolean(profileOrPhoto?.isPremium));
  const circle = options.circle ? "circle" : "";
  const ringClass = isVerified ? "verified-ring" : isPremium ? "premium-ring" : "";
  const imgSrc = getPhotoSrc(photoBase64);
  const nameText = displayName || (typeof profileOrPhoto === "object" ? fullNameOf(profileOrPhoto) : "Milan");
  const fontSize = Math.max(14, Math.round(sizePx * 0.34));

  return `
    <div class="avatar-wrap" style="width:${sizePx}px;height:${sizePx}px;">
      <div class="avatar-box ${circle} ${ringClass}" style="background:${avatarGradientStyle(photoBase64)};font-size:${fontSize}px;">
        ${
          imgSrc
            ? `<img src="${imgSrc}" alt="${escapeHtml(nameText)} photo" loading="lazy" />`
            : `<span>${escapeHtml(initialsOf(nameText))}</span>`
        }
      </div>
      ${isVerified ? `<span class="avatar-verified-corner" title="Verified User">✓</span>` : ""}
      ${isPremium ? `<span class="avatar-gold-corner" title="Milan Gold">★</span>` : ""}
    </div>
  `;
}

/**
 * Compresses an uploaded File image via HTML5 Canvas and returns RAW Base64 (no data: prefix)
 * so Android BitmapFactory.decodeByteArray and Web <img> can both read it identically.
 */
export function compressImageToRawBase64(file, maxDim = 480, quality = 0.72, maxChars = 650000) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read image file"));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Invalid image file"));
      img.onload = () => {
        let { width, height } = img;
        if (width > maxDim || height > maxDim) {
          if (width >= height) {
            height = Math.round((height * maxDim) / width);
            width = maxDim;
          } else {
            width = Math.round((width * maxDim) / height);
            height = maxDim;
          }
        }
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, width, height);

        let q = quality;
        let dataUrl = canvas.toDataURL("image/jpeg", q);
        let raw = dataUrl.split(",")[1] || "";
        while (raw.length > maxChars && q > 0.25) {
          q -= 0.12;
          dataUrl = canvas.toDataURL("image/jpeg", q);
          raw = dataUrl.split(",")[1] || "";
        }
        resolve(raw.slice(0, maxChars));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

export function generateShortId(prefix = "id") {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  let out = "";
  for (let i = 0; i < 20; i += 1) {
    out += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return `${prefix}_${out}`;
}
