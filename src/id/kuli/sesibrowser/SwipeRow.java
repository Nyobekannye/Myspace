package id.kuli.sesibrowser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * LinearLayout yang menangkap geser horizontal (di atas tombol sekalipun) untuk
 * "menyusutkan" nav pill ke samping. Ketukan biasa tetap sampai ke tombol.
 */
public class SwipeRow extends LinearLayout {
    public interface Listener { void onSwipeEnd(float dx, float vx); void onSwipeMove(float dx); }

    private Listener listener;
    private float x0, y0, lastX; private long t0;
    private boolean dragging;
    private final int slop;
    private boolean enabled = true;

    public SwipeRow(Context c, AttributeSet a) { super(c, a); slop = ViewConfiguration.get(c).getScaledTouchSlop(); }
    public void setListener(Listener l) { listener = l; }
    public void setSwipeEnabled(boolean b) { enabled = b; if (!b) dragging = false; }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!enabled) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: x0 = lastX = e.getX(); y0 = e.getY(); t0 = e.getEventTime(); dragging = false; break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) { dragging = true; return true; }
                break;
            }
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                lastX = e.getX();
                if (listener != null) listener.onSwipeMove(e.getX() - x0);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dt = Math.max(1, e.getEventTime() - t0);
                if (listener != null) listener.onSwipeEnd(e.getX() - x0, (e.getX() - x0) / dt * 1000f);
                dragging = false;
                return true;
            }
        }
        return true;
    }
}
