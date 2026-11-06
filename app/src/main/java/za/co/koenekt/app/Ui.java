package za.co.koenekt.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * Small, dependency-free design kit in the koenekt.co.za / People look: the site's light and dark palettes,
 * Big Shoulders Display titles, IBM Plex Sans text, IBM Plex Mono labels and buttons, 2dp corners.
 * Colours are set by {@link #apply}; screens read them when they build, so a theme change rebuilds the screen.
 */
final class Ui {
    /** Appearance choices, saved per phone. */
    static final String SYSTEM = "system", LIGHT = "light", DARK_MODE = "dark";

    static boolean dark;
    static int BG, SURFACE, SURFACE2, LINE, BORDER_STRONG, FIELD, INK, MUTED;
    /** LIME is the accent fill (buttons, dots, bars); BRAND is the accent as text (links, selected tabs). */
    static int LIME, BRAND, ON_ACCENT, TINT, TINT_LINE;
    static int DANGER, DANGER_BG, DANGER_LINE, WARN, WARN_BG, WARN_LINE, OUT;
    static Typeface DISPLAY = Typeface.DEFAULT_BOLD, SANS = Typeface.DEFAULT, SANS_BOLD = Typeface.DEFAULT_BOLD,
            MONO = Typeface.MONOSPACE, MONO_BOLD = Typeface.MONOSPACE;
    private static final float RADIUS = 2;

    static { palette(false); }

    private Ui() {}

    /** Light or dark from the saved choice; System follows the phone. */
    static boolean wantsDark(Context c, String appearance) {
        if (DARK_MODE.equals(appearance)) return true;
        if (LIGHT.equals(appearance)) return false;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Loads the fonts (once) and the palette, then colours the window and its system bars to match. */
    static void apply(Activity a, boolean darkMode) {
        if (SANS == Typeface.DEFAULT) {
            try {
                DISPLAY = a.getResources().getFont(R.font.big_shoulders_display_extrabold);
                SANS = a.getResources().getFont(R.font.ibm_plex_sans_regular);
                SANS_BOLD = a.getResources().getFont(R.font.ibm_plex_sans_semibold);
                MONO = a.getResources().getFont(R.font.ibm_plex_mono_medium);
                MONO_BOLD = a.getResources().getFont(R.font.ibm_plex_mono_semibold);
            } catch (RuntimeException ignored) { /* system fonts stay */ }
        }
        palette(darkMode);
        Window w = a.getWindow();
        w.setStatusBarColor(BG); w.setNavigationBarColor(BG);
        w.getDecorView().setBackgroundColor(BG);
        lightBars(w, !darkMode);
    }

    @SuppressWarnings("deprecation")
    private static void lightBars(Window w, boolean light) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int bits = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light ? bits : 0, bits);
            }
            return;
        }
        View d = w.getDecorView(); int flags = d.getSystemUiVisibility();
        int bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (android.os.Build.VERSION.SDK_INT >= 27 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        d.setSystemUiVisibility(light ? flags | bits : flags & ~bits);
    }

    private static void palette(boolean d) {
        dark = d;
        if (d) {
            BG = 0xFF1B1B1B; SURFACE = 0xFF242424; SURFACE2 = 0xFF2B2B2B; LINE = 0xFF3A3A3A; BORDER_STRONG = 0xFF4D4D4D;
            FIELD = 0xFF1F1F1F; INK = 0xFFF0F0EC; MUTED = 0xFFA8A8A4;
            LIME = 0xFF83CC17; BRAND = 0xFF9AD635; ON_ACCENT = 0xFF14181C; TINT = 0xFF26301A; TINT_LINE = 0xFF45602A;
            DANGER = 0xFFF3A497; DANGER_BG = 0xFF3A2320; DANGER_LINE = 0xFF6E3B33;
            WARN = 0xFFEBC86F; WARN_BG = 0xFF352C17; WARN_LINE = 0xFF6B5A2A;
        } else {
            BG = 0xFFF7F7F4; SURFACE = 0xFFFFFFFF; SURFACE2 = 0xFFEFEFEA; LINE = 0xFFDCDCD5; BORDER_STRONG = 0xFFC4C4BC;
            FIELD = 0xFFFFFFFF; INK = 0xFF262626; MUTED = 0xFF6B6B68;
            LIME = 0xFF5FA300; BRAND = 0xFF4B8300; ON_ACCENT = 0xFF14181C; TINT = 0xFFEEF5E2; TINT_LINE = 0xFFC6DDA2;
            DANGER = 0xFF8A2E22; DANGER_BG = 0xFFFBE9E6; DANGER_LINE = 0xFFE8B7AE;
            WARN = 0xFF7A5200; WARN_BG = 0xFFFFF4D6; WARN_LINE = 0xFFEED493;
        }
        OUT = MUTED;
    }

    /** Dialogs in the current mode (the activity's own theme is fixed when it starts). */
    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, dark ? R.style.KoenektDialog_Dark : R.style.KoenektDialog);
    }
    static int logo() { return dark ? R.drawable.ic_logo_pale : R.drawable.ic_logo; }
    static int brandMark() { return dark ? R.drawable.ic_brand_mark_pale : R.drawable.ic_brand_mark; }

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, CharSequence value, float sp, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(SANS);
        t.setLineSpacing(0, 1.15f); return t;
    }
    /** Screen titles and big numbers in Big Shoulders Display; smaller titles (list rows, cards) in Plex Sans SemiBold. */
    static TextView title(Context c, CharSequence value, float sp) {
        if (sp >= 22) {
            TextView t = text(c, value, sp * 1.18f, INK); t.setTypeface(DISPLAY); t.setLetterSpacing(0.01f);
            t.setLineSpacing(0, 0.95f); t.setIncludeFontPadding(false); return t;
        }
        TextView t = text(c, value, sp, INK); t.setTypeface(SANS_BOLD); return t;
    }
    /** Mono capitals, as the site's labels. */
    static TextView label(Context c, CharSequence value) {
        TextView t = text(c, value, 11.5f, MUTED); mono(t, true); return t;
    }
    static void mono(TextView t, boolean bold) {
        t.setTypeface(bold ? MONO_BOLD : MONO); t.setAllCaps(true); t.setLetterSpacing(0.08f);
    }

    static GradientDrawable shape(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(c, Math.min(radiusDp, RADIUS))); return g;
    }
    static GradientDrawable outline(int fill, int stroke, float widthDp, float radiusDp, Context c) {
        GradientDrawable g = shape(fill, radiusDp, c); g.setStroke(Math.max(1, dp(c, widthDp)), stroke); return g;
    }
    /** Background with a touch ripple clipped to the same shape. */
    static Drawable pressable(Drawable content, float radiusDp, Context c) {
        int ripple = Color.argb(dark ? 40 : 28, Color.red(INK), Color.green(INK), Color.blue(INK));
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, shape(Color.BLACK, radiusDp, c));
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
    /** 48dp square icon button for app bars. */
    static ImageView iconButton(Context c, int res, String description, Runnable action) {
        ImageView i = icon(c, res, INK, 48); int pad = dp(c, 12); i.setPadding(pad, pad, pad, pad);
        i.setBackground(pressable(shape(Color.TRANSPARENT, RADIUS, c), RADIUS, c));
        i.setContentDescription(description); i.setOnClickListener(v -> action.run()); return i;
    }

    /** Accent fill, dark mono capitals: the site's main button. */
    static TextView primary(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(ON_ACCENT);
        b.setBackground(pressable(shape(LIME, RADIUS, c), RADIUS, c)); return b;
    }
    static TextView secondary(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(INK);
        b.setBackground(pressable(outline(Color.TRANSPARENT, LINE, 1, RADIUS, c), RADIUS, c)); return b;
    }
    static TextView quiet(Context c, String label, Runnable action) {
        TextView b = button(c, label, action); b.setTextColor(BRAND);
        b.setBackground(pressable(shape(Color.TRANSPARENT, RADIUS, c), RADIUS, c)); return b;
    }
    private static TextView button(Context c, String label, Runnable action) {
        TextView b = text(c, label, 14, BRAND); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(c, 52));
        mono(b, true); b.setLetterSpacing(0.05f); b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setFocusable(true); b.setClickable(true); b.setOnClickListener(v -> { if (v.isEnabled()) action.run(); }); return b;
    }
    static void enable(View v, boolean enabled) { v.setEnabled(enabled); v.setAlpha(enabled ? 1f : 0.4f); }

    /** Labelled input; the outline turns accent green while focused. */
    static EditText field(LinearLayout parent, String label, String hint, int inputType) {
        Context c = parent.getContext();
        if (label != null) { parent.addView(label(c, label)); gap(parent, 8); }
        EditText f = new EditText(c); f.setHint(hint); f.setTextSize(17); f.setTextColor(INK); f.setHintTextColor(MUTED);
        f.setSingleLine(true); f.setInputType(inputType); f.setTypeface(SANS); f.setMinHeight(dp(c, 54)); f.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, outline(FIELD, LIME, 2, RADIUS, c));
        bg.addState(new int[]{}, outline(FIELD, BORDER_STRONG, 1, RADIUS, c));
        f.setBackground(bg); f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(f, fill(c, -1)); gap(parent, 16); return f;
    }
    static int textInput() { return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS; }

    /** Card: surface with a hairline border. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c); l.setBackground(outline(SURFACE, LINE, 1, RADIUS, c));
        int pad = dp(c, 16); l.setPadding(pad, pad, pad, pad); return l;
    }

    /** Filter chip showing the current choice, e.g. "DEMO BAKERY ▾". */
    static TextView chip(Context c, String value, Runnable action) {
        TextView t = text(c, value + "  ▾", 12.5f, INK); t.setTypeface(MONO); t.setLetterSpacing(0.02f);
        t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); t.setMaxWidth(dp(c, 220));
        t.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8)); t.setGravity(Gravity.CENTER_VERTICAL); t.setMinHeight(dp(c, 40));
        t.setBackground(pressable(outline(SURFACE, LINE, 1, RADIUS, c), RADIUS, c)); t.setOnClickListener(v -> action.run()); return t;
    }

    /** Square tile with a person's initials. */
    static TextView avatar(Context c, String name, int sizeDp) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) if (!part.isEmpty() && initials.length() < 2) initials.append(Character.toUpperCase(part.charAt(0)));
        TextView t = text(c, initials.length() == 0 ? "?" : initials, sizeDp * 0.42f, BRAND); t.setGravity(Gravity.CENTER);
        t.setTypeface(DISPLAY); t.setIncludeFontPadding(false);
        t.setBackground(outline(TINT, TINT_LINE, 1, RADIUS, c));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))); return t;
    }

    /** Status dot: filled in its colour; OUT (not in, offline) is a hollow ring. */
    static View dot(Context c, int color) {
        View v = new View(c); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
        if (color == OUT) { g.setColor(Color.TRANSPARENT); g.setStroke(dp(c, 2), OUT); } else g.setColor(color);
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
        if (trailing != null && !trailing.isEmpty()) {
            TextView tr = text(c, trailing, 13.5f, MUTED); tr.setTypeface(MONO); tr.setPadding(dp(c, 8), 0, 0, 0); r.addView(tr);
        }
        if (action != null) {
            r.addView(icon(c, R.drawable.ic_chevron, MUTED, 20));
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
        t.setBackground(outline(error ? DANGER_BG : WARN_BG, error ? DANGER_LINE : WARN_LINE, 1, RADIUS, c)); return t;
    }

    static void choose(Activity a, String title, String[] items, int checked, IntConsumer picked) {
        dialog(a).setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> { d.dismiss(); picked.accept(which); })
                .setNegativeButton("Cancel", null).show();
    }

    /** Centred spinner placeholder while a screen loads. */
    static View loading(Context c) {
        FrameLayout f = new FrameLayout(c); android.widget.ProgressBar p = new android.widget.ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(LIME));
        f.addView(p, new FrameLayout.LayoutParams(dp(c, 36), dp(c, 36), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 160))); return f;
    }
}
