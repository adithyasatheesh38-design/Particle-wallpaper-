package com.particle.wallpaper;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.Random;

/** Native Canvas port of the particle animation, used as the live wallpaper. */
public class ParticleWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new ParticleEngine();
    }

    private class ParticleEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private static final long FRAME_MS = 33; // ~30 fps, easy on battery

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable loop = this::frame;
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();

        private SharedPreferences sp;
        private float density = 1f;
        private int w, h;
        private float[] x = new float[0], y = new float[0], vx = new float[0],
                vy = new float[0], r = new float[0];
        private float touchX = -9999, touchY = -9999;
        private boolean visible;
        private long last;

        private int count = 140, speedV = 8, linksV = 120, theme = 0;
        private float hue = 200f;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            density = getResources().getDisplayMetrics().density;
            sp = Prefs.get(ParticleWallpaperService.this);
            sp.registerOnSharedPreferenceChangeListener(this);
            loadPrefs();
            setTouchEventsEnabled(true);
            linePaint.setStrokeWidth(0.7f * density);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(loop);
            sp.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            w = width;
            h = height;
            makeParticles();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            handler.removeCallbacks(loop);
            if (v) {
                last = System.nanoTime();
                handler.post(loop);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    touchX = e.getX();
                    touchY = e.getY();
                    break;
                default:
                    touchX = touchY = -9999;
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            int oldCount = count;
            loadPrefs();
            if (count != oldCount) makeParticles();
        }

        private void loadPrefs() {
            count = Math.max(1, sp.getInt(Prefs.COUNT, 140));
            speedV = sp.getInt(Prefs.SPEED, 8);
            linksV = sp.getInt(Prefs.LINKS, 120);
            theme = sp.getInt(Prefs.THEME, 0);
            hue = sp.getFloat(Prefs.HUE, 200f);
        }

        private void makeParticles() {
            if (w <= 0 || h <= 0) return;
            x = new float[count];
            y = new float[count];
            vx = new float[count];
            vy = new float[count];
            r = new float[count];
            for (int i = 0; i < count; i++) {
                x[i] = rnd.nextFloat() * w;
                y[i] = rnd.nextFloat() * h;
                vx[i] = rnd.nextFloat() - 0.5f;
                vy[i] = rnd.nextFloat() - 0.5f;
                r[i] = (rnd.nextFloat() * 1.8f + 0.6f) * density;
            }
        }

        private int bgColor() {
            switch (theme) {
                case 1: return 0xFF02040A;
                case 2: return 0xFF0B0714;
                default: return 0xFF05060F;
            }
        }

        private int fgColor() {
            switch (theme) {
                case 1: return Color.rgb(120, 220, 255);
                case 2: return hsl(300f, 0.85f, 0.70f);
                default: return hsl(hue, 0.90f, 0.65f);
            }
        }

        private int hsl(float hDeg, float s, float l) {
            float v = l + s * Math.min(l, 1 - l);
            float sv = v == 0 ? 0 : 2 * (1 - l / v);
            return Color.HSVToColor(new float[]{hDeg, sv, v});
        }

        private void frame() {
            if (!visible) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) drawFrame(c);
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            handler.removeCallbacks(loop);
            if (visible) handler.postDelayed(loop, FRAME_MS);
        }

        private void drawFrame(Canvas c) {
            long now = System.nanoTime();
            float dt = (now - last) / 16_666_667f; // 1.0 == one 60 fps frame
            last = now;
            dt = Math.max(0.5f, Math.min(dt, 3f));

            c.drawColor(bgColor());
            final int fg = fgColor();
            final float step = speedV / 1000f * 60f * dt * density;
            final float repelR = 140f * density;
            final int n = x.length;

            dotPaint.setColor(fg);
            dotPaint.setAlpha(230);

            for (int i = 0; i < n; i++) {
                x[i] += vx[i] * step;
                y[i] += vy[i] * step;
                if (x[i] < 0) { x[i] = 0; vx[i] = Math.abs(vx[i]); }
                else if (x[i] > w) { x[i] = w; vx[i] = -Math.abs(vx[i]); }
                if (y[i] < 0) { y[i] = 0; vy[i] = Math.abs(vy[i]); }
                else if (y[i] > h) { y[i] = h; vy[i] = -Math.abs(vy[i]); }

                float dx = touchX - x[i], dy = touchY - y[i];
                float d = (float) Math.hypot(dx, dy);
                if (d < repelR && d > 0.001f) {
                    x[i] -= dx / d * 1.4f * density * dt;
                    y[i] -= dy / d * 1.4f * density * dt;
                }
                c.drawCircle(x[i], y[i], r[i], dotPaint);
            }

            if (linksV > 0) {
                final float ld = linksV * density;
                final float ld2 = ld * ld;
                linePaint.setColor(fg);
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        float dx = x[i] - x[j], dy = y[i] - y[j];
                        float d2 = dx * dx + dy * dy;
                        if (d2 < ld2) {
                            float d = (float) Math.sqrt(d2);
                            linePaint.setAlpha((int) (255 * (1 - d / ld) * 0.35f));
                            c.drawLine(x[i], y[i], x[j], y[j], linePaint);
                        }
                    }
                }
            }
        }
    }
}
