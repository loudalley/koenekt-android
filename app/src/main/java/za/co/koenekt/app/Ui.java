package za.co.koenekt.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Small, dependency-free design kit: brand colours, type, fields, buttons, cards, chips and rows. */
final class Ui {
    static final int BRAND = Color.rgb(18, 52, 41), LIME = Color.rgb(115, 197, 9), INK = Color.rgb(28, 32, 30);
    static final int MUTED = Color.rgb(104, 114, 108), LINE = Color.rgb(226, 231, 224), BG = Color.rgb(246, 248, 245);
    static final int SURFACE = Color.WHITE, TINT = Color.rgb(232, 244, 220), DANGER = Color.rgb(176, 48, 34);
    static final int WARN_BG = Color.rgb(255, 246, 222), WARN = Color.rgb(122, 80, 10), OUT = Color.rgb(196, 203, 198);
    private static final int[] AVATARS = {
        Color.rgb(221, 238, 205), Color.rgb(214, 232, 244), Color.rgb(246, 226, 210),
        Color.rgb(232, 222, 245), Color.rgb(245, 222, 228), Color.rgb(222, 240, 236),
    };

    private Ui() {}

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, CharSequence value, float sp, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setLineSpacing(0, 1.15f); return t;
    }
    static TextView title(Context c, CharSequence value, float sp) {
        TextView t = text(c, value, sp, INK); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(-0.01f); return t;
    }
    static TextView label(Context c, CharSequence value) {
        TextView t = text(c, value, 13, MUTED); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }

    static GradientDrawable shape(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(c, radiusDp)); return g;
    }
    static GradientDrawable outline(int fill, int stroke, float widthDp, float radiusDp, Context c) {
        GradientDrawable g = shape(fill, radiusDp, c); g.setStroke(Math.max(1, dp(c, widthDp)), stroke); return g;
    }
    /** Background with a touch ripple clipped to the same rounded shape. */
    static Drawable pressable(Drawable content, float radiusDp, Context c) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 18, 52, 41)), content, shape(Color.BLACK, radiusDp, c));
    }

    static LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    static void gap(LinearLayout l, int heightDp) { View v = new View(l.getContext()); l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), heightDp))); }
    static LinearLayout.LayoutParams fill(Context c, int heightDp) { return new LinearLayout.LayoutParams(-1, heightDp < 0 ? -2 : dp(c, heightDp)); }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView i = new ImageView(c); i.setImageResource(res); i.setColorFilter(color);
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))); return i;
    }
    /** 48dp circular icon button for app bars. */
    static ImageView iconButton(Context c, int res, String description, Runnable action) {
        ImageView i = icon(c, res, INK, 48); int pad = dp(c, 12); i.setPadding(pad, pad, pad, pad);
        i.setBackground(pressable(shape(Color.TRANSPARENT, 24, c), 24, c));
        i.setContentDescription(description); i.setOnClickListener(v -> action.run()); return i;
    }

    static TextView primary(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(Color.WHITE);
        b.setBackground(pressable(shape(BRAND, 16, c), 16, c)); return b;
    }
    static TextView secondary(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(BRAND);
        b.setBackground(pressable(outline(SURFACE, LINE, 1, 16, c), 16, c)); return b;
    }
    static TextView quiet(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(BRAND);
        b.setBackground(pressable(shape(Color.TRANSPARENT, 16, c), 16, c)); return b;
    }
    private static TextView button(Context c, String label, Runnable action) {
        TextView b = text(c, label, 16, BRAND); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(c, 52));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setFocusable(true); b.setClickable(true); b.setOnClickListener(v -> { if (v.isEnabled()) action.run(); }); return b;
    }
    static void enable(View v, boolean enabled) { v.setEnabled(enabled); v.setAlpha(enabled ? 1f : 0.4f); }

    /** Labelled, rounded input; the outline turns brand green while focused. */
    static EditText field(LinearLayout parent, String label, String hint, int inputType) {
        Context c = parent.getContext();
        if (label != null) { parent.addView(label(c, label)); gap(parent, 6); }
        EditText f = new EditText(c); f.setHint(hint); f.setTextSize(17); f.setTextColor(INK); f.setHintTextColor(Color.rgb(160, 168, 163));
        f.setSingleLine(true); f.setInputType(inputType); f.setTypeface(Typeface.DEFAULT); f.setMinHeight(dp(c, 54)); f.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, outline(SURFACE, BRAND, 2, 14, c));
        bg.addState(new int[]{}, outline(SURFACE, LINE, 1, 14, c));
        f.setBackground(bg); f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(f, fill(c, -1)); gap(parent, 16); return f;
    }
    static int textInput() { return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS; }

    /** White rounded card with a hairline border. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c); l.setBackground(outline(SURFACE, LINE, 1, 20, c));
        int pad = dp(c, 18); l.setPadding(pad, pad, pad, pad); return l;
    }

    /** Pill-shaped filter chip showing the current choice, e.g. "Demo Bakery ▾". */
    static TextView chip(Context c, String value, Runnable action) {
        TextView t = text(c, value + "  ▾", 14, INK); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); t.setMaxWidth(dp(c, 220));
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 12), dp(c, 8)); t.setGravity(Gravity.CENTER_VERTICAL); t.setMinHeight(dp(c, 40));
        t.setBackground(pressable(outline(SURFACE, LINE, 1, 20, c), 20, c)); t.setOnClickListener(v -> action.run()); return t;
    }

    /** Circle with a person's initials, tinted consistently from their name. */
    static TextView avatar(Context c, String name, int sizeDp) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) if (!part.isEmpty() && initials.length() < 2) initials.append(Character.toUpperCase(part.charAt(0)));
        TextView t = text(c, initials.length() == 0 ? "?" : initials, sizeDp * 0.36f, BRAND); t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
        g.setColor(AVATARS[Math.floorMod(name.hashCode(), AVATARS.length)]); t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))); return t;
    }

    static View dot(Context c, int color) {
        View v = new View(c); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color);
        v.setBackground(g); v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 10), dp(c, 10))); return v;
    }

    /** List row: leading view, title, optional subtitle, optional trailing text and chevron. */
    static LinearLayout listRow(Context c, View leading, String title, String subtitle, String trailing, Runnable action) {
        LinearLayout r = row(c); r.setMinimumHeight(dp(c, 64)); r.setPadding(dp(c, 16), dp(c, 10), dp(c, 12), dp(c, 10));
        if (leading != null) { r.addView(leading); View s = new View(c); r.addView(s, new LinearLayout.LayoutParams(dp(c, 14), 1)); }
        LinearLayout words = column(c);
        TextView t = title(c, title, 16); t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); words.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(c, subtitle, 13.5f, MUTED); s.setSingleLine(true); s.setEllipsize(TextUtils.TruncateAt.END); words.addView(s);
        }
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null && !trailing.isEmpty()) { TextView tr = text(c, trailing, 13.5f, MUTED); tr.setPadding(dp(c, 8), 0, 0, 0); r.addView(tr); }
        if (action != null) {
            r.addView(icon(c, R.drawable.ic_chevron, Color.rgb(176, 184, 179), 20));
            r.setBackground(pressable(shape(Color.TRANSPARENT, 0, c), 0, c)); r.setOnClickListener(v -> action.run());
            r.setFocusable(true); r.setClickable(true);
        }
        r.setContentDescription(title + (subtitle == null ? "" : ". " + subtitle) + (trailing == null ? "" : ". " + trailing));
        return r;
    }
    static View divider(Context c, int insetDp) {
        View v = new View(c); v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0.5f))); p.leftMargin = dp(c, insetDp);
        v.setLayoutParams(p); return v;
    }

    /** Coloured notice strip for warnings and errors. */
    static TextView notice(Context c, String message, boolean error) {
        TextView t = text(c, message, 14, error ? DANGER : WARN); t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        t.setBackground(shape(error ? Color.rgb(252, 234, 230) : WARN_BG, 14, c)); return t;
    }

    static void choose(Activity a, String title, String[] items, int checked, IntConsumer picked) {
        new AlertDialog.Builder(a).setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> { d.dismiss(); picked.accept(which); })
                .setNegativeButton("Cancel", null).show();
    }

    /** Centred spinner placeholder while a screen loads. */
    static View loading(Context c) {
        FrameLayout f = new FrameLayout(c); android.widget.ProgressBar p = new android.widget.ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(BRAND));
        f.addView(p, new FrameLayout.LayoutParams(dp(c, 36), dp(c, 36), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 160))); return f;
    }
}
