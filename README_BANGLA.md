# MD JIBON 99% Screen Scanner — v2.0

**Quotex USD/BRL OTC স্ক্রিনশট-ভিত্তিক স্ক্যানার**

## বৈশিষ্ট্য ✨

- 📱 **নেটিভ Android অ্যাপ্লিকেশন**
- 🎯 **ফ্লোটিং স্ক্যানার আইকন** - সবসময় দৃশ্যমান
- 📸 **MediaProjection স্ক্রিন ক্যাপচার** - রিয়েল-টাইম চার্ট বিশ্লেষণ
- 🤖 **100টি Technical Analysis Rules** - Deterministic সিগন্যাল ইঞ্জিন
- 📊 **CALL / PUT / WAIT সিগন্যাল** - Confidence স্কোর সহ
- 🎨 **ডার্ক থিম UI** - চোখের স্বাচ্ছন্দ্য
- ⚡ **দ্রুত বিশ্লেষণ** - তাৎক্ষণিক ফল

## সিস্টেম প্রয়োজনীয়তা 📱

- **Android 8.0+ (API 26+)**
- **2GB RAM ন্যূনতম**
- **Screen Capture Permission**
- **Overlay Permission**

## ইনস্টলেশন পদ্ধতি 🚀

### পদ্ধতি 1: Android Studio (স্থানীয় বিল্ড)

```bash
# 1. এই রিপোজিটরি ক্লোন করুন
git clone https://github.com/sksumanmd9-droid/MD_JIBON_99_SCREEN_SCANNER.git
cd MD_JIBON_99_SCREEN_SCANNER

# 2. Android Studio খুলুন
android-studio .

# 3. Gradle sync হতে দিন (স্বয়ংক্রিয়)

# 4. Build করুন
# Build > Build APK(s) মেনু থেকে

# 5. ফোনে ইনস্টল করুন
adb install app/build/outputs/apk/debug/app-debug.apk
```

### পদ্ধতি 2: Codemagic (ক্লাউড বিল্ড) ☁️

1. [Codemagic](https://codemagic.io) এ অ্যাকাউন্ট তৈরি করুন (ফ্রি)
2. এই গিটহাব রিপোজিটরি যুক্ত করুন
3. স্বয়ংক্রিয়ভাবে বিল্ড শুরু হবে
4. APK ডাউনলোড করুন এবং ইনস্টল করুন

## ব্যবহার নির্দেশিকা 📖

### প্রথম চালু

1. **অ্যাপ খুলুন** (MD JIBON 99%)
2. **"Enable Floating Scanner"** বাটন চাপুন
   - Overlay permission দিন
3. **"Enable Screen Capture"** বাটন চাপুন
   - Screen recording permission দিন
4. **Back যান** অ্যাপে

### স্ক্যান করুন

```
1. Quotex অ্যাপ খুলুন
2. USD/BRL চার্ট প্রদর্শন করুন
3. সবুজ ⚡ বাটনে চাপুন
4. 1-2 সেকেন্ড অপেক্ষা করুন
5. ফলাফল দেখুন:
   - CALL (সবুজ) = ঊর্ধ্বমুখী
   - PUT (লাল) = নিম্নমুখী
   - WAIT (সাদা) = অনিশ্চিত
```

### ফলাফল পডুন

```
CALL / PUT / WAIT
Confidence: 75%
100 Logic Checks: 95
Detected candles: 28
Frame quality: 82%
```

| ফলাফল | অর্থ |
|--------|------|
| **CALL** | উপরের দিকে ট্রেন্ড (সবুজ) |
| **PUT** | নিচের দিকে ট্রেন্ড (লাল) |
| **WAIT** | অনিশ্চিত / অপর্যাপ্ত ডেটা |

## 100টি Rules সম্পর্কে 📊

### ক্যাটাগরি:

1. **Moving Averages** (01-10)
   - EMA 9/21/50, SMA 5/10/20/40
   - ক্রসওভার এবং ঢাল

2. **Momentum Indicators** (11-30)
   - RSI, MACD, Stochastic
   - ROC, Momentum, CCI

3. **Volatility** (31-46)
   - Bollinger Bands
   - ATR, Range compression

4. **Candlestick Patterns** (47-76)
   - Engulfing, Hammer, Shooting Star
   - Pin Bars, Doji, Three White Soldiers
   - Wick Pressure, Support/Resistance

5. **Divergences** (81-84)
   - RSI/Price Divergence
   - MACD/Price Divergence

6. **Multi-Timeframe** (85-100)
   - Ribbon agreements
   - Trend strength
   - Composite final signal

## গুরুত্বপূর্ণ সীমাবদ্ধতা ⚠️

- ⚠️ **স্ক্রিনশট-ভিত্তিক**: সরাসরি ব্রোকার API নয়
- ⚠️ **অনুমান**: OHLC মূল্য সঠিক নাও হতে পারে
- ⚠️ **পরীক্ষা করুন**: প্রকৃত ডিভাইসে ৫০টি ট্রেড পরীক্ষা করুন
- ⚠️ **ঝুঁকি**: ট্রেডিং ঝুঁকিপূর্ণ - শুধুমাত্র সাহস্যী ব্যবহারকারীদের জন্য

## বিল্ড করুন 🔨

### ম্যানুয়ালি
```bash
./gradlew clean
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### কোডম্যাজিকের মাধ্যমে
```bash
# codemagic.yaml স্বয়ংক্রিয়ভাবে ব্যবহার হয়
gradle assembleDebug --stacktrace
```

## আর্কিটেকচার 🏗️

```
Android App

├── MainActivity (UI)
├── ScreenCaptureService (MediaProjection)
├── FloatingScannerService (Overlay)
└── Analyzer (100 Rules Engine)
    ├── extract() - OHLC extraction
    ├── analyze() - Rule evaluation
    └── Result (CALL/PUT/WAIT)
```

## বিকাশকারী তথ্য 👨‍💻

### প্যাকেজ গঠন
```
app/src/main/
├── java/com/mdjibon/scanner/
│   ├── MainActivity.java
│   ├── ScreenCaptureService.java
│   ├── FloatingScannerService.java
│   └── Analyzer.java
└── res/
    ├── values/colors.xml
    ├── values/strings.xml
    └── values/styles.xml
```

### কীভাবে modify করবেন

1. **নতুন Rule যোগ করুন**: `Analyzer.java` এ `rules()` মেথডে
2. **UI পরিবর্তন করুন**: `MainActivity.java` তে
3. **থিম বদলান**: `colors.xml` এবং `styles.xml` এ

## সমস্যা সমাধান 🔧

### সমস্যা: "Screen frame not ready"
**সমাধান**: Quotex সম্পূর্ণভাবে লোড হওয়ার 2 সেকেন্ড অপেক্ষা করুন

### সমস্যা: WAIT সিগন্যাল সবসময় আসছে
**সমাধান**: 
- চার্ট পূর্ণ-পর্দা করুন
- কমপক্ষে 12টি candles দৃশ্যমান হতে হবে
- উজ্জ্বল স্ক্রিনে পরীক্ষা করুন

### সমস্যা: APK ইনস্টল হচ্ছে না
**সমাধান**: 
```bash
adb uninstall com.mdjibon.scanner
adb install app/build/outputs/apk/debug/app-debug.apk
```

## লাইসেন্স 📄

MIT License - আপনি স্বাধীনভাবে ব্যবহার, পরিবর্তন এবং বিতরণ করতে পারেন

## যোগাযোগ 💬

সমস্যা বা পরামর্শের জন্য GitHub Issues ব্যবহার করুন।

---

**দায়িত্ব অস্বীকার**: এই অ্যাপ্লিকেশনটি শিক্ষামূলক উদ্দেশ্যে তৈরি। টেডিং ঝুঁকিপূর্ণ - নিজের দায়িত্বে ব্যবহার করুন। 🚀