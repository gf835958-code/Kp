package com.kp.recapstudio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;
import java.io.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int VIDEO = 101, TXT = 102, AUDIO = 103, PDF = 104;
    private static final int SAVE_SRT = 105, SAVE_WAV = 106, MIC = 107;
    private final int BACKGROUND = Color.rgb(11, 16, 31), SURFACE = Color.rgb(24, 31, 54);
    private final int WHITE = Color.rgb(238, 239, 255), MUTED = Color.rgb(169, 180, 208);
    private final int ACCENT = Color.rgb(152, 74, 252);
    private final int PURPLE = Color.rgb(122, 85, 238), TEAL = Color.rgb(30, 191, 191);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout panel;
    private TextView info;
    private int currentTab = 0;
    private String transcript = "", recap = "", book = "", sampleName = "အသံနမူနာ မရွေးရသေး", pdfName = "";
    private Uri videoUri, sampleUri, pdfUri;
    private List<LocalSceneAnalyzer.Scene> videoScenes = new ArrayList<>();
    private long videoDurationMs = 0;
    private boolean analyzingScenes = false, extractingPdf = false;
    private VideoView player;
    private TextToSpeech tts;
    private boolean ttsReady = false, recording = false;
    private String selectedLanguage = "my";
    private MediaRecorder recorder;
    private MediaPlayer samplePlayer;
    private PdfRenderer pdf;
    private ParcelFileDescriptor pdfFd;
    private ImageView pdfImage;
    private TextView pdfPageLabel;
    private int pdfPage = 0;
    private String pendingSrt = "";

    private boolean reading = false, exporting = false, cancelled = false;
    private List<String> speechParts = new ArrayList<>();
    private List<File> audioParts = new ArrayList<>();
    private int speechPosition = 0;
    private File completedWav = null, wavWorkingFolder = null;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        book = loadDraft("book.txt");
        transcript = loadDraft("transcript.txt");
        recap = loadDraft("recap.txt");
        tts = new TextToSpeech(this, this);
        showInterface();
    }

    private void showInterface() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BACKGROUND);
        setContentView(outer);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(17), dp(13), dp(16), dp(13));
        GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(35, 17, 65), Color.rgb(13, 17, 33)});
        top.setBackground(headerBg);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.kp.recapstudio.R.drawable.kp_logo);
        top.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout headerText = new LinearLayout(this);
        headerText.setPadding(dp(12), 0, 0, 0);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("KP Recap Studio");
        brand.setTextColor(WHITE);
        brand.setTextSize(20);
        brand.setTypeface(null, 1);
        headerText.addView(brand);
        TextView subtitle = new TextView(this);
        subtitle.setText("MOVIE RECAP  •  AUDIOBOOK  •  VOICE");
        subtitle.setTextColor(Color.rgb(186, 155, 239));
        subtitle.setTextSize(10);
        headerText.addView(subtitle);
        top.addView(headerText);
        outer.addView(top);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(tabs);
        String[] names = {"⌂ Home", "🎬 Recap", "📚 Audiobook", "🎙 Voice", "📄 PDF"};
        for (int i = 0; i < names.length; i++) {
            final int n = i;
            Button b = button(names[i], i == currentTab ? PURPLE : SURFACE);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(48));
            bp.setMargins(dp(5), 0, dp(3), dp(6));
            tabs.addView(b, bp);
            b.setOnClickListener(v -> { saveDrafts(); currentTab = n; showInterface(); });
        }
        outer.addView(hs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(12), dp(15), dp(18));
        scroll.addView(panel);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        info = new TextView(this);
        info.setText("Internet permission မတောင်းပါ • Voice Clone AI Model မပါသေးပါ");
        info.setTextColor(MUTED);
        info.setTextSize(12);
        info.setPadding(dp(16), dp(12), dp(16), dp(18));
        info.setBackgroundColor(SURFACE);
        outer.addView(info);

        switch (currentTab) {
            case 0: renderHome(); break;
            case 1: renderRecap(); break;
            case 2: renderBook(); break;
            case 3: renderVoiceLab(); break;
            default: renderPdf(); break;
        }
    }

    private void renderHome() {
        title("✦ Your Creative Studio");
        note("Offline-first Android Creator • Purple Neon Edition");
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(21), dp(18), dp(18));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(106, 34, 180), Color.rgb(35, 23, 79), Color.rgb(13, 24, 52)});
        heroBg.setCornerRadius(dp(22));
        hero.setBackground(heroBg);
        TextView badge = new TextView(this);
        badge.setText("✦  KP CREATOR LAB");
        badge.setTextColor(Color.rgb(223, 185, 255));
        badge.setTextSize(12);
        badge.setTypeface(null,1);
        hero.addView(badge);
        TextView big = new TextView(this);
        big.setText("Create stories.\\nGive words a voice.");
        big.setTextColor(Color.WHITE);
        big.setTextSize(25);
        big.setTypeface(null,1);
        big.setPadding(0,dp(15),0,dp(10));
        hero.addView(big);
        TextView small = new TextView(this);
        small.setText("🎬 Video   •   📚 Audiobooks   •   🎙 Voice Lab");
        small.setTextColor(Color.rgb(222, 208, 244));
        small.setTextSize(13);
        hero.addView(small);
        panel.addView(hero, new LinearLayout.LayoutParams(-1, dp(190)));
        label("Quick tools");
        LinearLayout first = new LinearLayout(this);
        first.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(first, new LinearLayout.LayoutParams(-1, dp(148)));
        toolCard(first, "🎬", "Movie Recap", "Video • Script • SRT", 1, PURPLE);
        toolCard(first, "📚", "Audiobook", "Offline TTS • WAV", 2, Color.rgb(19, 138, 186));
        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, dp(148));
        row.topMargin = dp(8);
        panel.addView(second,row);
        toolCard(second, "🎙", "Voice Lab", "Record • Import", 3, Color.rgb(138, 48, 157));
        toolCard(second, "📄", "PDF Reader", "Offline PDF Pages", 4, Color.rgb(38, 108, 130));
        label("Studio status");
        note("✓ No website wrapper • Local files • No required API Key\\n" +
             "ⓘ Voice cloning AI, automatic video transcription and MP4 rendering are not installed yet.");
    }

    private void toolCard(LinearLayout row, String icon, String name, String detail, int tab, int tint) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13),dp(12),dp(8),dp(10));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tint, Color.rgb(29, 24, 58), SURFACE});
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(89, 65, 139));
        card.setBackground(background);
        TextView symbol = new TextView(this);
        symbol.setText(icon); symbol.setTextSize(27); card.addView(symbol);
        TextView nameView = new TextView(this);
        nameView.setText(name); nameView.setTextColor(WHITE); nameView.setTextSize(15);
        nameView.setTypeface(null,1); nameView.setPadding(0,dp(6),0,dp(4)); card.addView(nameView);
        TextView sub = new TextView(this);
        sub.setText(detail); sub.setTextColor(Color.rgb(205, 195, 224)); sub.setTextSize(11);
        card.addView(sub);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-1,1);
        p.setMargins(dp(3),0,dp(3),0);
        row.addView(card,p);
        card.setOnClickListener(v -> { saveDrafts(); currentTab = tab; showInterface(); });
    }

    private void renderRecap() {
        title("🎬 Movie Recap");
        note("ကိုယ်ပိုင် Video ကို ဖုန်းထဲကနေ ဖွင့်ကြည့်ပြီး Transcript ထည့်နိုင်ပါတယ်။ Video ကို AI နဲ့ အလိုအလျောက် နားလည်ခြင်း မပါသေးပါ။");
        addButton("📂 Video ဖိုင်ရွေးရန်", TEAL, v -> pick(VIDEO, "video/*"));
        player = new VideoView(this);
        player.setBackgroundColor(Color.BLACK);
        panel.addView(player, new LinearLayout.LayoutParams(-1, dp(200)));
        if (videoUri != null) player.setVideoURI(videoUri);
        addButton("▶ Video Play", SURFACE, v -> {
            if (videoUri == null) { status("Video အရင်ရွေးပါ"); return; }
            player.start();
        });
        addButton(analyzingScenes ? "🔍 Visual Scene Finder လုပ်ဆောင်နေသည်…" :
            "🔍 Offline Visual Scene Finder", PURPLE, v -> findVideoScenes());
        if (!videoScenes.isEmpty()) {
            label("🎞 Visual scene hints (" + videoScenes.size() + ")");
            note("ဖုန်းထဲမှာ Video Frame တွေကိုသာ နှိုင်းယှဉ်ထားတာပါ။ ဇာတ်လမ်းကို နားလည်တဲ့ AI သို့မဟုတ် အသံကနေ စာပြောင်းခြင်း မဟုတ်ပါ။");
            for (int i = 0; i < videoScenes.size(); i++) {
                final LocalSceneAnalyzer.Scene scene = videoScenes.get(i);
                final int index = i + 1;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(9), dp(5), dp(9), dp(5));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(dp(14)); rowBg.setColor(SURFACE);
                row.setBackground(rowBg);
                ImageView thumb = new ImageView(this);
                thumb.setImageBitmap(scene.thumbnail);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(thumb, new LinearLayout.LayoutParams(dp(110), dp(72)));
                TextView cue = new TextView(this);
                cue.setText("Scene " + String.format(Locale.US,"%02d", index) +
                    "\\n" + sceneStamp(scene.timeMs) + "  •  Tap to seek");
                cue.setTextColor(WHITE);
                cue.setTextSize(14);
                cue.setPadding(dp(12), 0, dp(3), 0);
                row.addView(cue, new LinearLayout.LayoutParams(0, dp(66), 1));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(82));
                lp.setMargins(0, dp(3), 0, dp(3));
                panel.addView(row, lp);
                row.setOnClickListener(v -> {
                    if (player != null && videoUri != null) {
                        player.seekTo((int)Math.min(scene.timeMs, Integer.MAX_VALUE));
                        status("Seek to " + sceneStamp(scene.timeMs));
                    }
                });
            }
            addButton("📝 Scene Timestamp Notes → Transcript", TEAL, v -> {
                StringBuilder guide = new StringBuilder();
                for (int j = 0; j < videoScenes.size(); j++) {
                    LocalSceneAnalyzer.Scene scene = videoScenes.get(j);
                    guide.append("Scene ").append(j + 1).append(" [")
                        .append(sceneStamp(scene.timeMs))
                        .append("] — [ဒီအခန်းကို ကြည့်ပြီး ဇာတ်ညွှန်းရေးရန်]").append("\\n");
                }
                transcript = guide.toString();
                saveDrafts();
                showInterface();
                status("Scene note template ready; you can edit the transcript.");
            });
        }
        label("Transcript / ဇာတ်ညွှန်းစာသား");
        EditText e = textBox(transcript, 9);
        e.addTextChangedListener(watch(value -> transcript = value));
        addButton("📝 အခြေခံ Recap ဖန်တီးရန်", PURPLE, v -> {
            String[] lines = transcript.trim().split("[။.!?\\n]+");
            StringBuilder b = new StringBuilder();
            int count = 0;
            for (String s : lines) {
                if (!s.trim().isEmpty()) {
                    b.append(s.trim()).append("။\n");
                    if (++count >= 12) break;
                }
            }
            recap = b.toString().trim();
            if (recap.isEmpty()) { status("Transcript ထည့်ပါ"); return; }
            saveDrafts();
            status("Basic extractive recap ထုတ်ပြီး — AI video analysis မဟုတ်ပါ");
            showInterface();
        });
        label("Editable Recap");
        EditText output = textBox(recap, 6);
        output.addTextChangedListener(watch(value -> recap = value));
        addButton("⬇ SRT Subtitle ထုတ်ရန်", TEAL, v -> {
            if (recap.trim().isEmpty()) { status("Recap စာသားလိုပါတယ်"); return; }
            pendingSrt = makeSrt(recap);
            status("အကြမ်းဖျဉ်း ၅ စက္ကန့် caption timings ဖြစ်ပြီး Video နဲ့ Auto Sync မဟုတ်ပါ");
            createDocument(SAVE_SRT, "application/x-subrip", "kp-recap.srt");
        });
    }

    private void renderBook() {
        title("📚 Offline Audiobook");
        note("စာသားကို အပိုင်းလိုက်ခွဲပြီး ဖုန်းမှာ Install လုပ်ထားတဲ့ OFFLINE TTS အသံနဲ့ ဖတ်/အသံဖိုင်ထုတ်နိုင်ပါတယ်။ Online Voice ကို မသုံးပါ။");
        addButton("📂 TXT စာအုပ် Import", TEAL, v -> pick(TXT, "text/*"));
        label("စာအုပ်စာသား / Book Text");
        EditText e = textBox(book, 12);
        e.addTextChangedListener(watch(value -> book = value));
        addButton("🇲🇲 မြန်မာ Offline Voice ရွေး", SURFACE, v -> chooseVoice("my"));
        addButton("🇬🇧 English Offline Voice ရွေး", SURFACE, v -> chooseVoice("en"));
        addButton("▶ စာအုပ်ဖတ်ရန်", PURPLE, v -> beginSpeak());
        addButton("⬇ Generate / Resume Long WAV", TEAL, v -> beginWav());
        addButton("■ ရပ်ရန် / Cancel", SURFACE, v -> stopSpeech());
        addButton("💾 စာသားသိမ်းရန်", SURFACE, v -> { saveDrafts(); status("စာသားကို ဖုန်းထဲမှာ သိမ်းပြီး"); });
        addButton("🧹 အသံထုတ်ရန် သိမ်းထားတဲ့ Chunks ရှင်းရန်", SURFACE, v ->
            new AlertDialog.Builder(this)
                .setTitle("Delete WAV progress files?")
                .setMessage("ဤလုပ်ဆောင်ချက်က အရင် Generate လုပ်ထားတဲ့ WAV အပိုင်းတွေကို ဖျက်ပါမယ်။ ပြန်စရင် အစကနေ Generate လုပ်ရမယ်။")
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .setPositiveButton("Delete", (dialog, which) -> clearWavProgress())
                .show());
        note("အသံရှည်ကို အပိုင်းလိုက် Generate လုပ်ပြီး WAV အဖြစ် ပေါင်းပါတယ်။ အသံဖိုင်အရှည်က Device Storage, TTS Engine နဲ့ WAV Format Limit ပေါ်မူတည်ပါတယ်။");
    }

    private void renderVoiceLab() {
        title("🎙 Offline Voice Lab");
        note("ကိုယ်ပိုင် သို့မဟုတ် ခွင့်ပြုချက်ရှိတဲ့ အသံကို Import/Record/Play လုပ်နိုင်ပါတယ်။");
        addButton("📂 အသံနမူနာ Import", TEAL, v -> pick(AUDIO, "audio/*"));
        label(sampleName);
        addButton("▶ Sample အသံနားထောင်ရန်", SURFACE, v -> playSample());
        addButton(recording ? "■ Recording ရပ်ရန်" : "● Microphone နဲ့ အသံဖမ်း", PURPLE,
            v -> { if (recording) stopRecord(); else requestRecord(); });
        label("Voice Clone AI — Not Installed");
        note("အသံနမူနာ Import လုပ်ရုံနဲ့ AI Voice Clone မဖြစ်ပါ။ ဒီ APK မှာ Voice-Cloning Model, Neural Inference Engine နဲ့ မြန်မာ Clone Voice မပါသေးပါ။");
        addButton("AI Engine အခြေအနေ", SURFACE, v -> status("Not installed: Android-compatible voice cloning AI model is needed."));
        note("စာသားကို ဖုန်းရဲ့ Offline TTS Voice နဲ့ ဖတ်/ထုတ်ချင်ရင် Audiobook Tab ကို သွားပါ။");
    }

    private void renderPdf() {
        title("📄 Offline PDF Reader");
        note("PDF စာအုပ်ကို ဖုန်းထဲမှာ Internet မလိုဘဲ Page အလိုက် ကြည့်နိုင်ပါတယ်။ PDF Text Extraction / OCR / Auto Voice Reading မပါသေးပါ။");
        addButton("📂 PDF ဖိုင်ရွေးရန်", TEAL, v -> pick(PDF, "application/pdf"));
        addButton(extractingPdf ? "⏳ PDF Text ထုတ်ယူနေသည်…" :
           "📚 PDF Text → Audiobook", PURPLE, v -> extractPdfTextToBook());
        note("Text-based PDF မှ စာသားကို အပြည့်ယူမယ်။ Scan လုပ်ထားတဲ့ ပုံ PDF အတွက် OCR မပါသေးပါ။");
        label(pdfName.isEmpty() ? "PDF မရွေးရသေး" : pdfName);
        pdfPageLabel = new TextView(this);
        pdfPageLabel.setTextColor(MUTED);
        pdfPageLabel.setTextSize(14);
        panel.addView(pdfPageLabel);
        pdfImage = new ImageView(this);
        pdfImage.setAdjustViewBounds(true);
        pdfImage.setBackgroundColor(Color.WHITE);
        panel.addView(pdfImage, new LinearLayout.LayoutParams(-1, -2));
        addButton("← Previous Page", SURFACE, v -> { pdfPage--; drawPdf(); });
        addButton("Next Page →", SURFACE, v -> { pdfPage++; drawPdf(); });
        drawPdf();
    }

    private void clearVideoScenes() {
        for (LocalSceneAnalyzer.Scene s : videoScenes) {
            if (!s.thumbnail.isRecycled()) s.thumbnail.recycle();
        }
        videoScenes = new ArrayList<>();
        videoDurationMs = 0;
    }

    private String sceneStamp(long ms) {
        return String.format(Locale.US, "%02d:%02d:%02d", ms / 3600000,
            (ms / 60000) % 60, (ms / 1000) % 60);
    }

    private void findVideoScenes() {
        if (videoUri == null) { status("Video အရင်ရွေးပါ"); return; }
        if (analyzingScenes) { status("Scene detection already running"); return; }
        analyzingScenes = true;
        final Uri target = videoUri;
        status("Video ကို Offline Visual Scene အဖြစ် စစ်ဆေးနေပါတယ်…");
        new Thread(() -> {
            try {
                final LocalSceneAnalyzer.Result found = LocalSceneAnalyzer.analyze(this, target);
                ui.post(() -> {
                    analyzingScenes = false;
                    if (isFinishing() || isDestroyed() || !target.equals(videoUri)) {
                        for (LocalSceneAnalyzer.Scene x : found.scenes) x.thumbnail.recycle();
                        return;
                    }
                    clearVideoScenes();
                    videoDurationMs = found.durationMs;
                    videoScenes = found.scenes;
                    if (currentTab == 1) showInterface();
                    status(found.scenes.size() + " visual cues found • " + found.warning);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    analyzingScenes = false;
                    status("Video scene analysis failed: " + e.getMessage());
                });
            }
        }, "KP-SceneAnalysis").start();
    }

    private void extractPdfTextToBook() {
        if (pdfUri == null) { status("PDF အရင် Import လုပ်ပါ"); return; }
        if (extractingPdf) { status("PDF extraction already running"); return; }
        extractingPdf = true;
        final Uri uri = pdfUri;
        status("PDF စာသားကို Offline ထုတ်ယူနေပါတယ်…");
        new Thread(() -> {
            try {
                PDFBoxResourceLoader.init(getApplicationContext());
                StringBuilder text = new StringBuilder();
                int pages;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("Cannot open PDF");
                    try (PDDocument doc = PDDocument.load(stream)) {
                        pages = doc.getNumberOfPages();
                        if (pages > 1500) throw new IOException("Over 1500 pages; split this PDF by book chapters");
                        PDFTextStripper stripper = new PDFTextStripper();
                        stripper.setSortByPosition(true);
                        for (int page = 1; page <= pages; page += 12) {
                            stripper.setStartPage(page);
                            stripper.setEndPage(Math.min(pages, page + 11));
                            text.append(stripper.getText(doc));
                            if (text.length() > 2500000)
                                throw new IOException("Extracted text exceeds 2.5 million characters");
                        }
                    }
                }
                final String extracted = text.toString().trim();
                if (extracted.isEmpty()) {
                    throw new IOException("No selectable PDF text. Scanned pages need OCR.");
                }
                ui.post(() -> {
                    extractingPdf = false;
                    if (isFinishing() || isDestroyed()) return;
                    book = extracted; saveDrafts(); currentTab = 2; showInterface();
                    status("PDF " + pages + " pages → Audiobook text ready (" +
                        extracted.length() + " chars)");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    extractingPdf = false;
                    status("PDF Text: " + e.getMessage());
                });
            } catch (OutOfMemoryError oom) {
                ui.post(() -> {
                    extractingPdf = false;
                    status("PDF too large for memory; divide into shorter PDF parts");
                });
            }
        }, "KP-PDF-Extract").start();
    }

    private void pick(int request, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        startActivityForResult(i, request);
    }
    private void createDocument(int request, String type, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == VIDEO) {
                videoUri = uri;
                clearVideoScenes();
                currentTab = 1; showInterface();
                status("Video Imported (Local Preview)");
            } else if (req == TXT) {
                book = readText(uri); saveDrafts(); currentTab = 2; showInterface();
                status("TXT Import အောင်မြင်");
            } else if (req == AUDIO) {
                sampleUri = uri; sampleName = "Sample: " + uri.getLastPathSegment();
                currentTab = 3; showInterface(); status("Sample audio ready — cloning is not installed");
            } else if (req == PDF) {
                closePdf();
                pdfUri = uri;
                pdfFd = getContentResolver().openFileDescriptor(uri, "r");
                if (pdfFd == null) throw new IOException("PDF not readable");
                pdf = new PdfRenderer(pdfFd);
                pdfPage = 0; pdfName = uri.getLastPathSegment();
                currentTab = 4; showInterface();
                status("Offline PDF open");
            } else if (req == SAVE_SRT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Output denied");
                    out.write(pendingSrt.getBytes(StandardCharsets.UTF_8));
                }
                status("SRT subtitle saved");
            } else if (req == SAVE_WAV) {
                if (completedWav == null) throw new IOException("No generated audio");
                final File file = completedWav;
                new Thread(() -> {
                    try (InputStream in = new FileInputStream(file); OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IOException("Output denied");
                        byte[] bytes = new byte[65536]; int n;
                        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
                        post("WAV ဖိုင် သိမ်းပြီး!");
                    } catch (Exception e) { post("Save error: " + e.getMessage()); }
                }).start();
            }
        } catch (Exception ex) { status("Error: " + ex.getMessage()); }
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder b = new StringBuilder();
        try (Reader in = new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8)) {
            char[] chars = new char[4096]; int n;
            while ((n = in.read(chars)) != -1) {
                b.append(chars, 0, n);
                if (b.length() > 3000000) throw new IOException("TXT exceeds 3,000,000 characters");
            }
        }
        return b.toString();
    }

    private void chooseVoice(String lang) {
        selectedLanguage = lang;
        if (tts == null || !ttsReady) { status("TTS Engine မဖြစ်သေး"); return; }
        try {
            Voice best = null;
            Set<Voice> voices = tts.getVoices();
            if (voices != null) for (Voice v : voices) {
                if (!v.isNetworkConnectionRequired() && v.getLocale() != null &&
                        lang.equals(v.getLocale().getLanguage())) {
                    if (best == null || v.getQuality() > best.getQuality()) best = v;
                }
            }
            if (best == null) {
                status("ဖုန်းထဲမှာ " + lang + " Offline TTS Voice မရှိပါ။ TTS Settings မှ Install လုပ်ပါ။");
                return;
            }
            int result = tts.setVoice(best);
            if (result != TextToSpeech.SUCCESS) throw new IOException("TTS voice rejected");
            status("Selected local voice: " + best.getName());
        } catch (Exception e) { status("Offline voice unavailable: " + e.getMessage()); }
    }

    private boolean assureOfflineVoice() {
        if (!ttsReady) { status("TTS initializing"); return false; }
        Set<Voice> voices = tts.getVoices();
        if (voices == null || tts.getVoice() == null ||
                tts.getVoice().isNetworkConnectionRequired() ||
                !selectedLanguage.equals(tts.getVoice().getLocale().getLanguage())) {
            chooseVoice(selectedLanguage);
        }
        Voice selected = tts.getVoice();
        if (selected == null || selected.isNetworkConnectionRequired()
                || !selectedLanguage.equals(selected.getLocale().getLanguage())) {
            status("Install or choose a local/offline " + selectedLanguage + " TTS voice first.");
            return false;
        }
        return true;
    }

    @Override public void onInit(int result) {
        ttsReady = result == TextToSpeech.SUCCESS;
        if (ttsReady) {
            tts.setSpeechRate(0.93f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) {
                    ui.post(() -> {
                        if (id.startsWith("READ_") && reading && !cancelled) nextSpeak();
                        else if (id.startsWith("WAV_") && exporting && !cancelled) nextWav();
                    });
                }
                @Override public void onError(String id) { ui.post(() -> stopWithError("TTS Engine failed: " + id)); }
                @Override public void onError(String id, int code) { ui.post(() -> stopWithError("TTS code " + code)); }
            });
        }
    }

    private void beginSpeak() {
        if (book.trim().isEmpty()) { status("Book text ထည့်ပါ"); return; }
        if (!assureOfflineVoice()) return;
        stopSpeech();
        cancelled = false; reading = true;
        speechParts = splitText(book, 280); speechPosition = 0;
        nextSpeak();
    }

    private void nextSpeak() {
        if (!reading || cancelled) return;
        if (speechPosition >= speechParts.size()) {
            reading = false; status("Finished reading"); return;
        }
        int index = speechPosition++;
        status("Reading " + speechPosition + "/" + speechParts.size());
        int result = tts.speak(speechParts.get(index), TextToSpeech.QUEUE_FLUSH, new Bundle(), "READ_" + index);
        if (result == TextToSpeech.ERROR) stopWithError("TTS speak failed");
    }

    /**
     * Resumable offline TTS: generated chunks remain in app-private storage
     * under a key derived from the exact book text and chosen offline voice.
     * Changing the text or voice cannot silently reuse another recording.
     */
    private void beginWav() {
        if (book.trim().isEmpty()) { status("စာအုပ်စာသား ထည့်ပါ"); return; }
        if (!assureOfflineVoice()) return;
        stopSpeech();
        cancelled = false; exporting = true;
        speechParts = splitText(book, 260);
        speechPosition = 0; audioParts.clear(); completedWav = null;
        try {
            String voice = tts.getVoice().getName();
            String hash = sha256Hex(voice + "\\u0000" + book);
            wavWorkingFolder = new File(new File(getFilesDir(),"kp-recapped-wav-jobs"), hash.substring(0, 28));
            if (!wavWorkingFolder.exists() && !wavWorkingFolder.mkdirs())
                throw new IOException("Cannot create persistent WAV work directory");
            File[] stored = wavWorkingFolder.listFiles();
            status("WAV: " + speechParts.size() + " segments; saved pieces: " +
                (stored == null ? 0 : stored.length));
            nextWav();
        } catch (Exception e) {
            exporting = false;
            status("WAV initialization failed: " + e.getMessage());
        }
    }

    private static String sha256Hex(String input) throws Exception {
        byte[] b = MessageDigest.getInstance("SHA-256").digest(
            input.getBytes(StandardCharsets.UTF_8));
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT,"%02x",x & 255));
        return s.toString();
    }

    private void nextWav() {
        if (!exporting || cancelled) return;
        // Skip previously generated valid WAV pieces without an unbounded recursive call.
        while (speechPosition < speechParts.size()) {
            int n = speechPosition++;
            File file = new File(wavWorkingFolder, String.format(Locale.US, "%06d.wav", n));
            if (file.isFile() && file.length() > 44) {
                try {
                    inspectWav(file);
                    audioParts.add(file);
                    continue;
                } catch (IOException ignored) { file.delete(); }
            }
            audioParts.add(file);
            status("Offline WAV: " + speechPosition + "/" + speechParts.size()
                + " (resume supported)");
            int result = tts.synthesizeToFile(speechParts.get(n), new Bundle(), file, "WAV_" + n);
            if (result == TextToSpeech.ERROR) stopWithError("TTS failed at segment " + n);
            return;
        }

        exporting = false;
        status("Merging " + audioParts.size() + " WAV chunks ...");
        final List<File> files = new ArrayList<>(audioParts);
        new Thread(() -> {
            try {
                File f = new File(getCacheDir(), "kp-recap-audiobook.wav");
                mergeWavs(files, f);
                completedWav = f;
                post("Long WAV ready; choose folder to save");
                ui.post(() -> {
                    if (!isFinishing() && !isDestroyed())
                        createDocument(SAVE_WAV, "audio/wav", "KP-Audiobook.wav");
                });
            } catch (Exception e) { post("Audio merge error: " + e.getMessage()); }
        }, "KP-WAV-Merge").start();
    }

    private void clearWavProgress() {
        stopSpeech();
        File root = new File(getFilesDir(), "kp-recapped-wav-jobs");
        long[] deleted = new long[2];
        removeFilesRecursively(root, deleted);
        wavWorkingFolder = null; audioParts.clear();
        status("WAV cache removed: " + deleted[0] + " files");
    }

    private void removeFilesRecursively(File root, long[] counts) {
        if (root == null || !root.exists()) return;
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) for (File f : children) removeFilesRecursively(f, counts);
        } else counts[0]++;
        root.delete();
    }

    private void stopSpeech() {
        cancelled = true;
        reading = false;
        exporting = false;
        if (tts != null) tts.stop();
        status("Stopped");
    }
    private void stopWithError(String s) { stopSpeech(); status(s); }

    private List<String> splitText(String source, int limit) {
        ArrayList<String> parts = new ArrayList<>();
        String text = source.replace("\r", "").trim();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + limit, text.length());
            if (end < text.length()) {
                int boundary = -1;
                for (int n = end - 1; n > i + Math.max(20, limit / 2); n--) {
                    char c = text.charAt(n);
                    if (Character.isWhitespace(c) || c == '။' || c == '.' || c == '၊' || c == ',') {
                        boundary = n + 1; break;
                    }
                }
                if (boundary > i) end = boundary;
            }
            String part = text.substring(i, end).trim();
            if (!part.isEmpty()) parts.add(part);
            i = end;
        }
        return parts;
    }

    private String makeSrt(String text) {
        List<String> items = splitText(text, 95);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            long start = i * 5000L;
            b.append(i + 1).append("\n")
                .append(stamp(start)).append(" --> ").append(stamp(start + 4500L)).append("\n")
                .append(items.get(i)).append("\n\n");
        }
        return b.toString();
    }
    private String stamp(long ms) {
        return String.format(Locale.US, "%02d:%02d:%02d,%03d",
            ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60, ms % 1000);
    }

    private void requestRecord() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC);
        } else startRecord();
    }
    @Override public void onRequestPermissionsResult(int request, String[] perms, int[] result) {
        super.onRequestPermissionsResult(request, perms, result);
        if (request == MIC) {
            if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) startRecord();
            else status("Microphone permission denied");
        }
    }
    private void startRecord() {
        try {
            if (recorder != null) { recorder.release(); recorder = null; }
            File file = new File(getFilesDir(), "voice-sample.m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare(); recorder.start();
            recording = true; status("Recording microphone sample…"); showInterface();
        } catch (Exception e) { recording = false; status("Recording failed: " + e.getMessage()); }
    }
    private void stopRecord() {
        if (recorder == null) return;
        try {
            recorder.stop();
            File file = new File(getFilesDir(), "voice-sample.m4a");
            sampleUri = Uri.fromFile(file);
            sampleName = "Recorded: " + file.getName();
            status("Sample recorded • Voice cloning still not installed");
        } catch (Exception e) { status("Recording too short or failed"); }
        finally { recorder.release(); recorder = null; recording = false; showInterface(); }
    }
    private void playSample() {
        if (sampleUri == null) { status("အသံအရင်ရွေးပါ"); return; }
        try {
            if (samplePlayer != null) samplePlayer.release();
            samplePlayer = new MediaPlayer();
            samplePlayer.setDataSource(this, sampleUri);
            samplePlayer.prepare();
            samplePlayer.start();
            status("Playing sample");
        } catch (Exception e) { status("Cannot play sample: " + e.getMessage()); }
    }

    private void drawPdf() {
        if (pdfImage == null || pdfPageLabel == null) return;
        if (pdf == null) { pdfPageLabel.setText("PDF import လုပ်ပါ"); return; }
        if (pdfPage < 0) pdfPage = 0;
        if (pdfPage >= pdf.getPageCount()) pdfPage = pdf.getPageCount() - 1;
        if (pdfPage < 0) return;
        try (PdfRenderer.Page page = pdf.openPage(pdfPage)) {
            int w = 900, h = Math.max(1, (int)(900.0 * page.getHeight() / page.getWidth()));
            if (h > 1800) { w = (int)(w * 1800.0 / h); h = 1800; }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfImage.setImageBitmap(bitmap);
            pdfPageLabel.setText("Page " + (pdfPage + 1) + " / " + pdf.getPageCount());
        } catch (Exception e) { status("PDF rendering error: " + e.getMessage()); }
    }
    private void closePdf() {
        pdfImage = null;
        try { if (pdf != null) pdf.close(); } catch (Exception ignore) {}
        try { if (pdfFd != null) pdfFd.close(); } catch (Exception ignore) {}
        pdf = null; pdfFd = null;
    }

    private static class WavPart {
        File file; long offset, length; byte[] fmt;
        WavPart(File f, long o, long l, byte[] format) { file=f; offset=o; length=l; fmt=format; }
    }
    private static String ascii(RandomAccessFile file, int n) throws IOException {
        byte[] b = new byte[n]; file.readFully(b); return new String(b, StandardCharsets.US_ASCII);
    }
    private static long le32(RandomAccessFile file) throws IOException {
        return ((long)file.readUnsignedByte()) | ((long)file.readUnsignedByte() << 8)
            | ((long)file.readUnsignedByte() << 16) | ((long)file.readUnsignedByte() << 24);
    }
    private static void le32(OutputStream out, long v) throws IOException {
        for (int i=0;i<4;i++) out.write((int)(v >> (8*i)) & 255);
    }
    private static WavPart inspectWav(File source) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(source, "r")) {
            if (!ascii(f, 4).equals("RIFF")) throw new IOException("Engine output is not RIFF WAV");
            le32(f);
            if (!ascii(f, 4).equals("WAVE")) throw new IOException("Invalid WAVE file");
            byte[] fmt = null; long dataOffset = -1, dataLen = 0;
            while (f.getFilePointer() + 8 <= f.length()) {
                String tag = ascii(f, 4);
                long length = le32(f);
                long offset = f.getFilePointer();
                if (length < 0 || length > f.length() - offset) throw new IOException("Bad WAV chunk");
                if (tag.equals("fmt ")) {
                    if (length > 4096 || length < 16) throw new IOException("Unsupported WAV fmt");
                    fmt = new byte[(int)length]; f.readFully(fmt);
                } else if (tag.equals("data")) { dataOffset = offset; dataLen = length; }
                f.seek(offset + length + (length % 2));
            }
            if (fmt == null || dataOffset < 0) throw new IOException("Missing WAV audio chunks");
            int pcm = (fmt[0] & 255) | ((fmt[1] & 255) << 8);
            if (pcm != 1) throw new IOException("Engine WAV is not PCM; native merge unavailable");
            return new WavPart(source, dataOffset, dataLen, fmt);
        }
    }
    private static void mergeWavs(List<File> inputs, File target) throws IOException {
        if (inputs.isEmpty()) throw new IOException("No WAV pieces");
        List<WavPart> parts = new ArrayList<>();
        long total = 0; byte[] fmt = null;
        for (File file : inputs) {
            WavPart p = inspectWav(file);
            if (fmt == null) fmt = p.fmt;
            if (!Arrays.equals(fmt, p.fmt)) throw new IOException("Audio chunks have differing formats");
            total += p.length; parts.add(p);
        }
        long size = 4 + 8 + fmt.length + (fmt.length % 2) + 8 + total + (total % 2);
        if (size >= 0xFFFFFFFFL) throw new IOException("Classic WAV has a 4GB size limit");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII)); le32(out, size);
            out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); le32(out, fmt.length);
            out.write(fmt); if (fmt.length % 2 != 0) out.write(0);
            out.write("data".getBytes(StandardCharsets.US_ASCII)); le32(out, total);
            byte[] buf = new byte[65536];
            for (WavPart p : parts) {
                try (RandomAccessFile in = new RandomAccessFile(p.file, "r")) {
                    in.seek(p.offset);
                    long rest = p.length;
                    while (rest > 0) {
                        int n = in.read(buf, 0, (int)Math.min(buf.length, rest));
                        if (n <= 0) throw new IOException("Unexpected end of audio segment");
                        out.write(buf, 0, n); rest -= n;
                    }
                }
            }
            if (total % 2 != 0) out.write(0);
        }
    }

    private void saveDrafts() { writeDraft("book.txt",book); writeDraft("transcript.txt",transcript); writeDraft("recap.txt",recap); }
    private void writeDraft(String name, String value) {
        try (FileOutputStream out = openFileOutput(name, MODE_PRIVATE)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
    private String loadDraft(String name) {
        try (FileInputStream in = openFileInput(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int n;
            while ((n=in.read(bytes))!=-1) { if (out.size()>3000000) break; out.write(bytes,0,n); }
            return out.toString("UTF-8");
        } catch(Exception ignore) { return ""; }
    }

    private interface Changed { void set(String value); }
    private TextWatcher watch(Changed onChange) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { onChange.set(s.toString()); }
            public void afterTextChanged(Editable s) {}
        };
    }
    private EditText textBox(String text, int lines) {
        EditText e = new EditText(this);
        e.setText(text); e.setTextColor(WHITE); e.setHintTextColor(MUTED);
        e.setGravity(Gravity.TOP); e.setTextSize(15);
        e.setMinLines(lines); e.setInputType(0x00020001 | 0x00040000);
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), PURPLE);
        e.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0, dp(6), 0, dp(12)); panel.addView(e,p); return e;
    }
    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(WHITE); t.setTextSize(24); t.setTypeface(null,1);
        t.setPadding(0,dp(9),0,dp(12)); panel.addView(t);
    }
    private void label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(WHITE); t.setTextSize(15);
        t.setPadding(dp(3),dp(14),0,dp(4)); panel.addView(t);
    }
    private void note(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(MUTED); t.setTextSize(13);
        t.setPadding(dp(4),dp(5),dp(4),dp(18)); panel.addView(t);
    }
    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(WHITE); b.setTextSize(13); b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{color, color == SURFACE ? Color.rgb(36, 29, 70) : Color.rgb(63, 32, 126)});
        bg.setCornerRadius(dp(13)); bg.setStroke(dp(1),Color.rgb(93, 61, 143));
        b.setBackground(bg);
        return b;
    }
    private void addButton(String s, int color, View.OnClickListener listener) {
        Button b=button(s,color);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));
        p.setMargins(0,dp(4),0,dp(8)); panel.addView(b,p); b.setOnClickListener(listener);
    }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density * v + 0.5f); }
    private void status(String s) {
        if (info != null) info.setText(s);
    }
    private void post(String s) { ui.post(() -> status(s)); }
    @Override protected void onPause() { saveDrafts(); super.onPause(); }
    @Override protected void onDestroy() {
        stopSpeech();
        clearVideoScenes();
        closePdf();
        if (recording) stopRecord();
        if (samplePlayer!=null) samplePlayer.release();
        if (tts!=null) tts.shutdown();
        super.onDestroy();
    }
}
