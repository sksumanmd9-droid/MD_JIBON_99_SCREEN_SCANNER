# MD JIBON Screen Scanner — সম্পূর্ণ সেটআপ গাইড

## ধাপ 1: প্রয়োজনীয় সফটওয়্যার ইনস্টল করুন 💻

### Windows এ:
```bash
1. Java Development Kit (JDK 17) ডাউনলোড করুন
   https://www.oracle.com/java/technologies/downloads/

2. Android Studio ডাউনলোড করুন
   https://developer.android.com/studio

3. উভয় ইনস্টল করুন এবং পাথ সেট করুন
```

### Mac এ:
```bash
brew install openjdk@17
brew install android-studio
```

### Linux এ:
```bash
sudo apt-get install openjdk-17-jdk
# Android Studio ডাউনলোড করুন এবং চালান
```

## ধাপ 2: প্রজেক্ট ডাউনলোড করুন 📥

```bash
# Git ব্যবহার করে
git clone https://github.com/sksumanmd9-droid/MD_JIBON_99_SCREEN_SCANNER.git
cd MD_JIBON_99_SCREEN_SCANNER
```

## ধাপ 3: Android Studio সেটআপ করুন 🔧

1. **Android Studio খুলুন**
2. **Open Project** → আপনার প্রজেক্ট ফোল্ডার নির্বাচন করুন
3. **Gradle sync** স্বয়ংক্রিয়ভাবে হবে (প্রথমবার 5-10 মিনিট সময় লাগতে পারে)
4. যদি error আসে:
   - `File > Invalidate Caches` 
   - `Build > Clean Project`
   - `Build > Rebuild Project`

## ধাপ 4: ডিভাইস প্রস্তুত করুন 📱

### Physical Device (সুপারিশকৃত):

```bash
# 1. Developer Mode চালু করুন
# সেটিংস → About phone → Build number (7 বার চাপুন)

# 2. Developer Options চালু করুন
# সেটিংস → Developer Options → USB Debugging ON

# 3. ডিভাইস কম্পিউটারে সংযুক্ত করুন

# 4. চেক করুন
adb devices
# আপনার ডিভাইসের ID দেখা যাবে
```

### Emulator (বিকল্প):

```bash
Android Studio → AVD Manager → Create Virtual Device
# API 26+ সহ ডিভাইস তৈরি করুন
```

## ধাপ 5: APK বিল্ড করুন 🔨

### Option A: Android Studio GUI

```
Build → Build APK(s)
↓
Process শুরু হবে (2-5 মিনিট)
↓
APK তৈরি: app/build/outputs/apk/debug/app-debug.apk
```

### Option B: কমান্ড লাইন

```bash
# পুরোপুরি বিল্ড
./gradlew clean assembleDebug

# শুধু বিল্ড (দ্রুত)
./gradlew assembleDebug

# Output
# app/build/outputs/apk/debug/app-debug.apk
```

## ধাপ 6: ডিভাইসে ইনস্টল করুন 📱

### Android Studio থেকে:

```
Run → Run 'app' (Shift + F10)
↓
ডিভাইস নির্বাচন করুন
↓
অ্যাপ ইনস্টল এবং চালু হবে
```

### ADB থেকে:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk

# চেক করুন
adb shell pm list packages | grep mdjibon
```

## ধাপ 7: প্রথম ব্যবহার করুন 🚀

### অনুমতি প্রদান করুন:

1. **অ্যাপ খুলুন** (MD JIBON 99%)
2. **"1. Enable Floating Scanner"** চাপুন
   - Settings খুলবে
   - MD JIBON কে Allow করুন
3. **"2. Enable Screen Capture"** চাপুন
   - Permission request আসবে
   - Allow করুন
4. **Back যান** অ্যাপে

### স্ক্যান করুন:

```
1. Quotex অ্যাপ খুলুন
2. USD/BRL চার্ট খুলুন
3. সবুজ ⚡ বাটনে চাপুন
4. 1-2 সেকেন্ড অপেক্ষা করুন
5. ফলাফল দেখুন:
   - CALL (সবুজ) = ঊর্ধ্বমুখী
   - PUT (লাল) = নিম্নমুখী
   - WAIT (সাদা) = অনিশ্চিত
```

## সাধারণ সমস্যা এবং সমাধান 🔧

### সমস্যা 1: "Gradle sync failed"
```bash
✓ সমাধান:
1. Delete: .gradle folder
2. File → Invalidate Caches
3. Build → Clean Project
4. Gradle sync আবার করুন
```

### সমস্যা 2: "SDK not found"
```bash
✓ সমাধান:
1. local.properties ফাইল তৈরি করুন
2. নিচের লাইন যোগ করুন:
   sdk.dir=/path/to/android/sdk
3. Android Studio restart করুন
```

### সমস্যা 3: "Build failed - Out of memory"
```bash
✓ সমাধান:
1. gradle.properties খুলুন
2. -Xmx4g এ পরিবর্তন করুন (বর্তমান -Xmx2g)
3. আবার build করুন
```

### সমস্যা 4: "Screen capture permission denied"
```bash
✓ সমাধান:
1. সেটিংস → Apps → MD JIBON → Permissions
2. Screen recording → Allow
3. Overlay → Allow
```

### সমস্যা 5: "Screen frame not ready"
```bash
✓ সমাধান:
Quotex সম্পূর্ণভাবে লোড হওয়ার 2 সেকেন্ড অপেক্ষা করুন
```

### সমস্যা 6: WAIT সিগন্যাল সবসময় আসছে
```bash
✓ সমাধান:
1. চার্ট পূর্ণ-পর্দা করুন
2. কমপক্ষে 12টি candles দৃশ্যমান হতে হবে
3. উজ্জ্বল স্ক্রিনে পরীক্ষা করুন
```

## ক্লাউড বিল্ড (Codemagic) ☁️

### সুবিধা:
- কোনো লোকাল সেটআপের প্রয়োজন নেই
- স্বয়ংক্রিয় বিল্ড
- GitHub push এর সাথে সাথে বিল্ড শুরু হয়

### সেটআপ:

```
1. https://codemagic.io এ যান
2. GitHub অ্যাকাউন্ট দিয়ে লগইন করুন
3. "Add app" ক্লিক করুন
4. এই রিপোজিটরি নির্বাচন করুন
5. Build শুরু হবে স্বয়ংক্রিয়ভাবে
6. APK ডাউনলোড করুন এবং ইনস্টল করুন
```

## অ্যাপ কাস্টমাইজ করুন 🎨

### রং পরিবর্তন করুন:
```xml
# File: app/src/main/res/values/colors.xml
<color name="green">#18e38a</color> ← সবুজ রং
<color name="red">#ff4d5e</color>   ← লাল রং
<color name="bg">#101414</color>    ← ব্যাকগ্রাউন্ড
```

### টেক্সট পরিবর্তন করুন:
```xml
# File: app/src/main/res/values/strings.xml
<string name="app_name">আপনার নাম</string>
```

### নতুন Rule যোগ করুন:
```java
// File: app/src/main/java/com/mdjibon/scanner/Analyzer.java
// rules() মেথডে নতুন rule যোগ করুন:
add(r,"আপনার Rule", x-> your_logic);
```

## পরবর্তী ধাপ 📈

1. ✅ বিভিন্ন বাজার অবস্থার সাথে পরীক্ষা করুন
2. ✅ Confidence স্কোর বুঝুন
3. ✅ নিজের Rules তৈরি করুন
4. ✅ GitHub এ Pull Request পাঠান

## সাহায্য পান 📞

- **GitHub Issues**: প্রশ্ন এবং বাগ রিপোর্ট করুন
- **Discussions**: আলোচনা করুন
- **Documentation**: README_BANGLA.md পড়ুন

---

**Happy Scanning! 🚀**