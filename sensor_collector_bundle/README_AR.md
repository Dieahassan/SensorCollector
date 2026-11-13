# نظام جمع حساسات Android وإرسالها إلى Linux

الحزمة تحتوي على:
- `android-app/`: مشروع Android Studio بلغة Kotlin، مع خدمة أمامية (Foreground Service) تجمع الحساسات وترسل آخر القراءات كل ثانية.
- `linux-server/`: سيرفر Flask مع سكربت تثبيت كخدمة systemd.

## 1) تثبيت السيرفر على Linux (Ubuntu/Debian)

انسخ مجلد `linux-server` إلى كمبيوتر Linux، ثم نفّذ:

```bash
cd linux-server
chmod +x install.sh
./install.sh
```

السكربت يثبت Flask داخل بيئة Python افتراضية، ينشئ خدمة systemd باسم `sensor-server` ويشغلها تلقائياً. قد يطلب كلمة مرور sudo.

تحقق من عمله:

```bash
curl http://127.0.0.1:5000/health
```

اعرف عنوان IP المحلي:

```bash
hostname -I
```

لو ظهر مثلاً `192.168.1.5`، عنوان الاستقبال في التطبيق سيكون:

`http://192.168.1.5:5000/sensors`

يجب أن يكون الهاتف والكمبيوتر على نفس شبكة Wi-Fi، وأن تسمح إعدادات الجدار الناري بالاتصال على TCP/5000.

عرض السجلات:

```bash
sudo journalctl -u sensor-server -f
```

البيانات تحفظ أيضاً في:
`/opt/sensor-server/data/sensor_data.jsonl`

## 2) إنشاء APK

لا أستطيع إرفاق APK مبني ومختبر من بيئة هذه المحادثة؛ الحزمة تتضمن مشروع Android كامل المصدر.

على جهاز عليه Android Studio وAndroid SDK:
1. افتح مجلد `android-app` في Android Studio.
2. انتظر انتهاء Gradle Sync.
3. اختر Build > Build Bundle(s) / APK(s) > Build APK(s).
4. ثبّت APK الناتج على الهاتف.

أو من الطرفية بعد تثبيت Android SDK وGradle:
```bash
cd android-app
gradle assembleDebug
```
الناتج:
`app/build/outputs/apk/debug/app-debug.apk`

## 3) استخدام التطبيق

1. افتح Sensor Collector.
2. اكتب عنوان السيرفر مثل `http://192.168.1.5:5000/sensors`.
3. اضغط Start.
4. وافق على إشعار الخدمة إذا طلب Android ذلك.
5. اضغط Stop لإيقاف الجمع.

التطبيق يكتشف حساسات `SensorManager` الموجودة بالجهاز فعلياً. بعض الحساسات لا ترسل قراءة إلا عند تغير القيمة؛ لذلك يرسل التطبيق آخر قراءة معروفة لكل حساس كل ثانية. GPS ليس ضمن `SensorManager` وله صلاحيات وواجهة مستقلة، ولم تتم إضافته في هذه النسخة.

## ملاحظات مهمة

- استخدم شبكة موثوقة فقط: هذه النسخة تستخدم HTTP غير مشفر ولا تحتوي على تسجيل دخول أو تشفير.
- لا تفتح المنفذ 5000 مباشرة على الإنترنت. استخدمه داخل الشبكة المحلية فقط أو أضف HTTPS ومصادقة قبل أي استخدام خارجي.
- جمع الحساسات المستمر يستهلك البطارية. Android والشركة المصنعة قد يوقفان الخدمة أو يقيّدان بعض الحساسات وفق إعدادات توفير الطاقة.
- `localhost` داخل الهاتف يشير إلى الهاتف نفسه، لذلك أدخل IP الكمبيوتر في التطبيق.


## 4) بناء APK تلقائياً باستخدام GitHub Actions (بدون Android Studio)

تم تضمين Workflow في:
`.github/workflows/build-apk.yml`

الخطوات:
1. أنشئ مستودعاً جديداً على GitHub.
2. ارفع محتويات مجلد `sensor_collector_bundle` إلى المستودع (يجب أن يكون مجلد `.github` موجوداً في جذر المستودع).
3. افتح تبويب **Actions** في المستودع.
4. اختر **Build Android APK** ثم **Run workflow**، أو ادفع Commit جديداً إلى `main`.
5. بعد نجاح البناء، افتح نتيجة التشغيل وانزل إلى **Artifacts**.
6. حمّل `sensor-collector-debug-apk`، ثم فك الضغط وثبّت `app-debug.apk` على هاتف Android.

ملاحظة: هذا Debug APK للتجربة. لا يحتاج توقيع إصدار Play Store. GitHub يحتاج حساباً ومستودعاً تستطيع رفع الملفات إليه.
