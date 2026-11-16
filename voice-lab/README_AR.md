# مختبر الصوت العربي لمشاريع هيثم

**الأصوات المعتمدة للتجربة في ركضتك 0.5.2:** Charon (رجل) وDespina (امرأة) من Auditext. أُنشئت 20 عبارة جديدة لكل منهما، تتضمن «المرحلة الجاية ... بلّش»، من دون زامور أو عد تنازلي. ملفات المصدر المجزأة تُستورد بواسطة `import_speech_batch.py` ثم تُجهّز بواسطة `attach_pack.py` مع حدود كل مقطع بالمللي ثانية.

**الحقوق:** يجب التحقق من شروط Auditext عند التوزيع التجاري للتطبيق. الحزمة الحالية للتجربة ولا تحتوي على تسجيلات ElevenLabs.

أما الخيارات التالية فهي للمختبر العام وليست أصوات «ركضتك»:

- `edge`: صوت رجل أردني Taim أو امرأة أردنية Sana؛ التوليد يحتاج اتصال إنترنت فقط، وبعد ذلك يعمل ملف MP3 دون اتصال. لا يحتاج إلى GPU، وهو ليس نموذجًا محليًا. توفر هذه الواجهة غير الرسمية غير مضمون ويجب مراجعة شروط الاستخدام قبل النشر التجاري.
- `piper`: نموذج Kareem العربي الأردني متاح محليًا، ويعمل على CPU؛ النموذج المتاح حاليًا لصوت رجل واحد. الجودة الصوتية تختلف عن Edge. راجع MODEL_CARD وشروط البيانات قبل إعادة التوزيع.

## أمثلة

```bash
python voice-lab/voice_lab.py --backend edge --voice male --text "يلا نبدأ التمرين" --output output.mp3
python voice-lab/voice_lab.py --backend edge --voice female --text "الدرس اليوم عن القراءة" --output female.mp3
python voice-lab/voice_lab.py --backend piper --voice male --model ar_JO-kareem-medium.onnx --text "مرحبا" --output local.wav
python voice-lab/voice_lab.py --pack --output voice-pack
```

المتطلبات: Python و `edge-tts` و `ffmpeg`؛ ويحتاج Piper إلى `piper-tts` مع نموذج ONNX. لا تضف الأصوات التجريبية إلى الإعلانات التجارية دون التحقق من حقوق الاستخدام الخاصة بمصدر الصوت.
