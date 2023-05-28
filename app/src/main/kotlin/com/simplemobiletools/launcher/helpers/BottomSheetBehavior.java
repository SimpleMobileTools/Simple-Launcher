package com.simplemobiletools.launcher.helpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.simplemobiletools.launcher.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * An interaction behavior plugin for a child view of {@link CoordinatorLayout} to make it work as a
 * bottom sheet.
 *
 * <p>To send useful accessibility events, set a title on bottom sheets that are windows or are
 * window-like. For BottomSheetDialog use {@link BottomSheetDialog#setTitle(int)}, and for
 * BottomSheetDialogFragment use {@link ViewCompat#setAccessibilityPaneTitle(View, CharSequence)}.
 */
public class BottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {

  /** Callback for monitoring events about bottom sheets. */
  public abstract static class BottomSheetCallback {

    /**
     * Called when the bottom sheet changes its state.
     *
     * @param bottomSheet The bottom sheet view.
     * @param newState The new state. This will be one of {@link #STATE_DRAGGING}, {@link
     *     #STATE_SETTLING}, {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, {@link
     *     #STATE_HIDDEN}, or {@link #STATE_HALF_EXPANDED}.
     */
    public abstract void onStateChanged(@NonNull View bottomSheet, @State int newState);

    /**
     * Called when the bottom sheet is being dragged.
     *
     * @param bottomSheet The bottom sheet view.
     * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
     *     as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
     *     expanded states and from -1 to 0 it is between hidden and collapsed states.
     */
    public abstract void onSlide(@NonNull View bottomSheet, float slideOffset);
  }

  /** The bottom sheet is dragging. */
  public static final int STATE_DRAGGING = 1;

  /** The bottom sheet is settling. */
  public static final int STATE_SETTLING = 2;

  /** The bottom sheet is expanded. */
  public static final int STATE_EXPANDED = 3;

  /** The bottom sheet is collapsed. */
  public static final int STATE_COLLAPSED = 4;

  /** The bottom sheet is hidden. */
  public static final int STATE_HIDDEN = 5;

  /** The bottom sheet is half-expanded (used when mFitToContents is false). */
  public static final int STATE_HALF_EXPANDED = 6;

  /** @hide */
  @RestrictTo(LIBRARY_GROUP)
  @IntDef({
    STATE_EXPANDED,
    STATE_COLLAPSED,
    STATE_DRAGGING,
    STATE_SETTLING,
    STATE_HIDDEN,
    STATE_HALF_EXPANDED
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {}

  /**
   * Peek at the 16:9 ratio keyline of its parent.
   *
   * <p>This can be used as a parameter for {@link #setPeekHeight(int)}. {@link #getPeekHeight()}
   * will return this when the value is set.
   */
  public static final int PEEK_HEIGHT_AUTO = -1;

  /** This flag will preserve the peekHeight int value on configuration change. */
  public static final int SAVE_PEEK_HEIGHT = 0x1;

  /** This flag will preserve the fitToContents boolean value on configuration change. */
  public static final int SAVE_FIT_TO_CONTENTS = 1 << 1;

  /** This flag will preserve the hideable boolean value on configuration change. */
  public static final int SAVE_HIDEABLE = 1 << 2;

  /** This flag will preserve the skipCollapsed boolean value on configuration change. */
  public static final int SAVE_SKIP_COLLAPSED = 1 << 3;

  /** This flag will preserve all aforementioned values on configuration change. */
  public static final int SAVE_ALL = -1;

  /**
   * This flag will not preserve the aforementioned values set at runtime if the view is destroyed
   * and recreated. The only value preserved will be the positional state, e.g. collapsed, hidden,
   * expanded, etc. This is the default behavior.
   */
  public static final int SAVE_NONE = 0;

  /** @hide */
  @RestrictTo(LIBRARY_GROUP)
  @IntDef(
      flag = true,
      value = {
        SAVE_PEEK_HEIGHT,
        SAVE_FIT_TO_CONTENTS,
        SAVE_HIDEABLE,
        SAVE_SKIP_COLLAPSED,
        SAVE_ALL,
        SAVE_NONE,
      })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SaveFlags {}

  private static final String TAG = "BottomDrawerBehavior";

  @SaveFlags private int saveFlags = SAVE_NONE;

  private static final float HIDE_THRESHOLD = 0.5f;

  private static final float HIDE_FRICTION = 0.1f;

  private boolean fitToContents = true;

  private float maximumVelocity;

  /** Peek height set by the user. */
  private int peekHeight;

  /** Whether or not to use automatic peek height. */
  private boolean peekHeightAuto;

  /** Minimum peek height permitted. */
  private int peekHeightMin;

  private SettleRunnable settleRunnable = null;

  int expandedOffset;

  int fitToContentsOffset;

  int halfExpandedOffset;

  float halfExpandedRatio = 0.5f;

  int collapsedOffset;

  boolean hideable;

  private boolean skipCollapsed;

  @State int state = STATE_COLLAPSED;

  @Nullable
  ViewDragHelper viewDragHelper;

  private boolean ignoreEvents;

  private int lastNestedScrollDy;

  private boolean nestedScrolled;

  int parentWidth;
  int parentHeight;

  @Nullable WeakReference<V> viewRef;

  @Nullable WeakReference<View> nestedScrollingChildRef;

  @NonNull private final ArrayList<BottomSheetCallback> callbacks = new ArrayList<>();

  @Nullable private VelocityTracker velocityTracker;

  int activePointerId;

  private int initialY;

  boolean touchingScrollingChild;

  @Nullable private Map<View, Integer> importantForAccessibilityMap;

  public BottomSheetBehavior() {}

  public BottomSheetBehavior(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomSheetBehavior_Layout);
    TypedValue value = a.peekValue(R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight);
    if (value != null && value.data == PEEK_HEIGHT_AUTO) {
      setPeekHeight(value.data);
    } else {
      setPeekHeight(a.getDimensionPixelSize(R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO));
    }
    setHideable(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false));
    setFitToContents(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_fitToContents, true));
    setSkipCollapsed(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed, false));
    setSaveFlags(a.getInt(R.styleable.BottomSheetBehavior_Layout_behavior_saveFlags, SAVE_NONE));
    setHalfExpandedRatio(a.getFloat(R.styleable.BottomSheetBehavior_Layout_behavior_halfExpandedRatio, 0.5f));
    setExpandedOffset(a.getInt(R.styleable.BottomSheetBehavior_Layout_behavior_expandedOffset, 0));
    a.recycle();
    ViewConfiguration configuration = ViewConfiguration.get(context);
    maximumVelocity = configuration.getScaledMaximumFlingVelocity();
  }

  @NonNull
  @Override
  public Parcelable onSaveInstanceState(@NonNull CoordinatorLayout parent, @NonNull V child) {
    return new SavedState(super.onSaveInstanceState(parent, child), this);
  }

  @Override
  public void onRestoreInstanceState(
      @NonNull CoordinatorLayout parent, @NonNull V child, @NonNull Parcelable state) {
    SavedState ss = (SavedState) state;
    super.onRestoreInstanceState(parent, child, ss.getSuperState());
    // Restore Optional State values designated by saveFlags
    restoreOptionalState(ss);
    // Intermediate states are restored as collapsed state
    if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
      this.state = STATE_COLLAPSED;
    } else {
      this.state = ss.state;
    }
  }

  @Override
  public void onAttachedToLayoutParams(@NonNull LayoutParams layoutParams) {
    super.onAttachedToLayoutParams(layoutParams);
    // These may already be null, but just be safe, explicitly assign them. This lets us know the
    // first time we layout with this behavior by checking (viewRef == null).
    viewRef = null;
    viewDragHelper = null;
  }

  @Override
  public void onDetachedFromLayoutParams() {
    super.onDetachedFromLayoutParams();
    // Release references so we don't run unnecessary codepaths while not attached to a view.
    viewRef = null;
    viewDragHelper = null;
  }

  @Override
  public boolean onLayoutChild(
      @NonNull CoordinatorLayout parent, @NonNull V child, int layoutDirection) {
    if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
      child.setFitsSystemWindows(true);
    }

    if (viewRef == null) {
      // First layout with this behavior.
      peekHeightMin = parent.getResources().getDimensionPixelSize(R.dimen.design_bottom_sheet_peek_height_min);
      viewRef = new WeakReference<>(child);
      updateAccessibilityActions();
      if (ViewCompat.getImportantForAccessibility(child)
          == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
        ViewCompat.setImportantForAccessibility(child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
      }
    }
    if (viewDragHelper == null) {
      viewDragHelper = ViewDragHelper.create(parent, dragCallback);
    }

    int savedTop = child.getTop();
    // First let the parent lay it out
    parent.onLayoutChild(child, layoutDirection);
    // Offset the bottom sheet
    parentWidth = parent.getWidth();
    parentHeight = parent.getHeight();
    fitToContentsOffset = Math.max(0, parentHeight - child.getHeight());
    calculateHalfExpandedOffset();
    calculateCollapsedOffset();

    if (state == STATE_EXPANDED) {
      ViewCompat.offsetTopAndBottom(child, getExpandedOffset());
    } else if (state == STATE_HALF_EXPANDED) {
      ViewCompat.offsetTopAndBottom(child, halfExpandedOffset);
    } else if (state == STATE_HIDDEN) {
      ViewCompat.offsetTopAndBottom(child, parentHeight);
    } else if (state == STATE_COLLAPSED) {
      ViewCompat.offsetTopAndBottom(child, collapsedOffset);
    } else if (state == STATE_DRAGGING || state == STATE_SETTLING) {
      ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
    }

    nestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));
    return true;
  }

  @Override
  public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
    if (!child.isShown()) {
      ignoreEvents = true;
      return false;
    }
    int action = event.getActionMasked();
    // Record the velocity
    if (action == MotionEvent.ACTION_DOWN) {
      reset();
    }
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    }
    velocityTracker.addMovement(event);
    switch (action) {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        touchingScrollingChild = false;
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        // Reset the ignore flag
        if (ignoreEvents) {
          ignoreEvents = false;
          return false;
        }
        break;
      case MotionEvent.ACTION_DOWN:
        int initialX = (int) event.getX();
        initialY = (int) event.getY();
        // Only intercept nested scrolling events here if the view not being moved by the
        // ViewDragHelper.
        if (state != STATE_SETTLING) {
          View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
          if (scroll != null && parent.isPointInChildBounds(scroll, initialX, initialY)) {
            activePointerId = event.getPointerId(event.getActionIndex());
            touchingScrollingChild = true;
          }
        }
        ignoreEvents =
            activePointerId == MotionEvent.INVALID_POINTER_ID
                && !parent.isPointInChildBounds(child, initialX, initialY);
        break;
      default: // fall out
    }
    if (!ignoreEvents
        && viewDragHelper != null
        && viewDragHelper.shouldInterceptTouchEvent(event)) {
      return true;
    }
    // We have to handle cases that the ViewDragHelper does not capture the bottom sheet because
    // it is not the top most view of its parent. This is not necessary when the touch event is
    // happening over the scrolling content as nested scrolling logic handles that case.
    View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
    return action == MotionEvent.ACTION_MOVE
        && scroll != null
        && !ignoreEvents
        && state != STATE_DRAGGING
        && !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY())
        && viewDragHelper != null
        && Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop();
  }

  @Override
  public boolean onTouchEvent(
      @NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
    if (!child.isShown()) {
      return false;
    }
    int action = event.getActionMasked();
    if (state == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
      return true;
    }
    if (viewDragHelper != null) {
      viewDragHelper.processTouchEvent(event);
    }
    // Record the velocity
    if (action == MotionEvent.ACTION_DOWN) {
      reset();
    }
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    }
    velocityTracker.addMovement(event);
    // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
    // to capture the bottom sheet in case it is not captured and the touch slop is passed.
    if (action == MotionEvent.ACTION_MOVE && !ignoreEvents) {
      if (Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop()) {
        viewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
      }
    }
    return !ignoreEvents;
  }

  @Override
  public boolean onStartNestedScroll(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View directTargetChild,
      @NonNull View target,
      int axes,
      int type) {
    lastNestedScrollDy = 0;
    nestedScrolled = false;
    return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
  }

  @Override
  public void onNestedPreScroll(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View target,
      int dx,
      int dy,
      @NonNull int[] consumed,
      int type) {
    if (type == ViewCompat.TYPE_NON_TOUCH) {
      // Ignore fling here. The ViewDragHelper handles it.
      return;
    }
    View scrollingChild = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
    if (target != scrollingChild) {
      return;
    }
    int currentTop = child.getTop();
    int newTop = currentTop - dy;
    if (dy > 0) { // Upward
      if (newTop < getExpandedOffset()) {
        consumed[1] = currentTop - getExpandedOffset();
        ViewCompat.offsetTopAndBottom(child, -consumed[1]);
        setStateInternal(STATE_EXPANDED);
      } else {
        consumed[1] = dy;
        ViewCompat.offsetTopAndBottom(child, -dy);
        setStateInternal(STATE_DRAGGING);
      }
    } else if (dy < 0) { // Downward
      if (!target.canScrollVertically(-1)) {
        if (newTop <= collapsedOffset || hideable) {
          consumed[1] = dy;
          ViewCompat.offsetTopAndBottom(child, -dy);
          setStateInternal(STATE_DRAGGING);
        } else {
          consumed[1] = currentTop - collapsedOffset;
          ViewCompat.offsetTopAndBottom(child, -consumed[1]);
          setStateInternal(STATE_COLLAPSED);
        }
      }
    }
    dispatchOnSlide(child.getTop());
    lastNestedScrollDy = dy;
    nestedScrolled = true;
  }

  @Override
  public void onStopNestedScroll(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View target,
      int type) {
    if (child.getTop() == getExpandedOffset()) {
      setStateInternal(STATE_EXPANDED);
      return;
    }
    if (nestedScrollingChildRef == null
        || target != nestedScrollingChildRef.get()
        || !nestedScrolled) {
      return;
    }
    int top;
    int targetState;
    if (lastNestedScrollDy > 0) {
      top = getExpandedOffset();
      targetState = STATE_EXPANDED;
    } else if (hideable && shouldHide(child, getYVelocity())) {
      top = parentHeight;
      targetState = STATE_HIDDEN;
    } else if (lastNestedScrollDy == 0) {
      int currentTop = child.getTop();
      if (fitToContents) {
        if (Math.abs(currentTop - fitToContentsOffset) < Math.abs(currentTop - collapsedOffset)) {
          top = fitToContentsOffset;
          targetState = STATE_EXPANDED;
        } else {
          top = collapsedOffset;
          targetState = STATE_COLLAPSED;
        }
      } else {
        if (currentTop < halfExpandedOffset) {
          if (currentTop < Math.abs(currentTop - collapsedOffset)) {
            top = expandedOffset;
            targetState = STATE_EXPANDED;
          } else {
            top = halfExpandedOffset;
            targetState = STATE_HALF_EXPANDED;
          }
        } else {
          if (Math.abs(currentTop - halfExpandedOffset) < Math.abs(currentTop - collapsedOffset)) {
            top = halfExpandedOffset;
            targetState = STATE_HALF_EXPANDED;
          } else {
            top = collapsedOffset;
            targetState = STATE_COLLAPSED;
          }
        }
      }
    } else {
      if (fitToContents) {
        top = collapsedOffset;
        targetState = STATE_COLLAPSED;
      } else {
        // Settle to nearest height.
        int currentTop = child.getTop();
        if (Math.abs(currentTop - halfExpandedOffset) < Math.abs(currentTop - collapsedOffset)) {
          top = halfExpandedOffset;
          targetState = STATE_HALF_EXPANDED;
        } else {
          top = collapsedOffset;
          targetState = STATE_COLLAPSED;
        }
      }
    }
    startSettlingAnimation(child, targetState, top, false);
    nestedScrolled = false;
  }

  @Override
  public void onNestedScroll(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View target,
      int dxConsumed,
      int dyConsumed,
      int dxUnconsumed,
      int dyUnconsumed,
      int type,
      @NonNull int[] consumed) {
    // Overridden to prevent the default consumption of the entire scroll distance.
  }

  @Override
  public boolean onNestedPreFling(
      @NonNull CoordinatorLayout coordinatorLayout,
      @NonNull V child,
      @NonNull View target,
      float velocityX,
      float velocityY) {
    if (nestedScrollingChildRef != null) {
      return target == nestedScrollingChildRef.get() && (state != STATE_EXPANDED || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
    } else {
      return false;
    }
  }

  /**
   * @return whether the height of the expanded sheet is determined by the height of its contents,
   *     or if it is expanded in two stages (half the height of the parent container, full height of
   *     parent container).
   */
  public boolean isFitToContents() {
    return fitToContents;
  }

  /**
   * Sets whether the height of the expanded sheet is determined by the height of its contents, or
   * if it is expanded in two stages (half the height of the parent container, full height of parent
   * container). Default value is true.
   *
   * @param fitToContents whether or not to fit the expanded sheet to its contents.
   */
  public void setFitToContents(boolean fitToContents) {
    if (this.fitToContents == fitToContents) {
      return;
    }
    this.fitToContents = fitToContents;

    // If sheet is already laid out, recalculate the collapsed offset based on new setting.
    // Otherwise, let onLayoutChild handle this later.
    if (viewRef != null) {
      calculateCollapsedOffset();
    }
    // Fix incorrect expanded settings depending on whether or not we are fitting sheet to contents.
    setStateInternal((this.fitToContents && state == STATE_HALF_EXPANDED) ? STATE_EXPANDED : state);

    updateAccessibilityActions();
  }

  /**
   * Sets the height of the bottom sheet when it is collapsed.
   *
   * @param peekHeight The height of the collapsed bottom sheet in pixels, or {@link
   *     #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically at 16:9 ratio keyline.
   * @attr ref
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_peekHeight
   */
  public void setPeekHeight(int peekHeight) {
    setPeekHeight(peekHeight, false);
  }

  /**
   * Sets the height of the bottom sheet when it is collapsed while optionally animating between the
   * old height and the new height.
   *
   * @param peekHeight The height of the collapsed bottom sheet in pixels, or {@link
   *     #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically at 16:9 ratio keyline.
   * @param animate Whether to animate between the old height and the new height.
   * @attr ref
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_peekHeight
   */
  public final void setPeekHeight(int peekHeight, boolean animate) {
    boolean layout = false;
    if (peekHeight == PEEK_HEIGHT_AUTO) {
      if (!peekHeightAuto) {
        peekHeightAuto = true;
        layout = true;
      }
    } else if (peekHeightAuto || this.peekHeight != peekHeight) {
      peekHeightAuto = false;
      this.peekHeight = Math.max(0, peekHeight);
      layout = true;
    }
    // If sheet is already laid out, recalculate the collapsed offset based on new setting.
    // Otherwise, let onLayoutChild handle this later.
    if (layout && viewRef != null) {
      calculateCollapsedOffset();
      if (state == STATE_COLLAPSED) {
        V view = viewRef.get();
        if (view != null) {
          if (animate) {
            settleToStatePendingLayout(state);
          } else {
            view.requestLayout();
          }
        }
      }
    }
  }

  /**
   * Gets the height of the bottom sheet when it is collapsed.
   *
   * @return The height of the collapsed bottom sheet in pixels, or {@link #PEEK_HEIGHT_AUTO} if the
   *     sheet is configured to peek automatically at 16:9 ratio keyline
   * @attr ref
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_peekHeight
   */
  public int getPeekHeight() {
    return peekHeightAuto ? PEEK_HEIGHT_AUTO : peekHeight;
  }

  /**
   * Determines the height of the BottomSheet in the {@link #STATE_HALF_EXPANDED} state. The
   * material guidelines recommended a value of 0.5, which results in the sheet filling half of the
   * parent. The height of the BottomSheet will be smaller as this ratio is decreased and taller as
   * it is increased. The default value is 0.5.
   *
   * @param ratio a float between 0 and 1, representing the {@link #STATE_HALF_EXPANDED} ratio.
   * @attr
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_halfExpandedRatio
   */
  public void setHalfExpandedRatio(@FloatRange(from = 0.0f, to = 1.0f) float ratio) {

    if ((ratio <= 0) || (ratio >= 1)) {
      throw new IllegalArgumentException("ratio must be a float value between 0 and 1");
    }
    this.halfExpandedRatio = ratio;
  }

  /**
   * Determines the top offset of the BottomSheet in the {@link #STATE_EXPANDED} state when
   * fitsToContent is false. The default value is 0, which results in the sheet matching the
   * parent's top.
   *
   * @param offset an integer value greater than equal to 0, representing the {@link
   *     #STATE_EXPANDED} offset. Value must not exceed the offset in the half expanded state.
   * @attr
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_expandedOffset
   */
  public void setExpandedOffset(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be greater than or equal to 0");
    }
    this.expandedOffset = offset;
  }

  /**
   * Gets the ratio for the height of the BottomSheet in the {@link #STATE_HALF_EXPANDED} state.
   *
   * @attr
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_halfExpandedRatio
   */
  @FloatRange(from = 0.0f, to = 1.0f)
  public float getHalfExpandedRatio() {
    return halfExpandedRatio;
  }

  /**
   * Sets whether this bottom sheet can hide when it is swiped down.
   *
   * @param hideable {@code true} to make this bottom sheet hideable.
   * @attr ref com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_hideable
   */
  public void setHideable(boolean hideable) {
    if (this.hideable != hideable) {
      this.hideable = hideable;
      if (!hideable && state == STATE_HIDDEN) {
        // Lift up to collapsed state
        setState(STATE_COLLAPSED);
      }
      updateAccessibilityActions();
    }
  }

  /**
   * Gets whether this bottom sheet can hide when it is swiped down.
   *
   * @return {@code true} if this bottom sheet can hide.
   * @attr ref com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_hideable
   */
  public boolean isHideable() {
    return hideable;
  }

  /**
   * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
   * is expanded once. Setting this to true has no effect unless the sheet is hideable.
   *
   * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
   * @attr ref
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_skipCollapsed
   */
  public void setSkipCollapsed(boolean skipCollapsed) {
    this.skipCollapsed = skipCollapsed;
  }

  /**
   * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
   * is expanded once.
   *
   * @return Whether the bottom sheet should skip the collapsed state.
   * @attr ref
   *     com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_skipCollapsed
   */
  public boolean getSkipCollapsed() {
    return skipCollapsed;
  }

  /**
   * Sets save flags to be preserved in bottomsheet on configuration change.
   *
   * @param flags bitwise int of {@link #SAVE_PEEK_HEIGHT}, {@link #SAVE_FIT_TO_CONTENTS}, {@link
   *     #SAVE_HIDEABLE}, {@link #SAVE_SKIP_COLLAPSED}, {@link #SAVE_ALL} and {@link #SAVE_NONE}.
   * @see #getSaveFlags()
   * @attr ref com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_saveFlags
   */
  public void setSaveFlags(@SaveFlags int flags) {
    this.saveFlags = flags;
  }
  /**
   * Returns the save flags.
   *
   * @see #setSaveFlags(int)
   * @attr ref com.google.android.material.R.styleable#BottomDrawerBehavior_Layout_saveFlags
   */
  @SaveFlags
  public int getSaveFlags() {
    return this.saveFlags;
  }

  /**
   * Adds a callback to be notified of bottom sheet events.
   *
   * @param callback The callback to notify when bottom sheet events occur.
   */
  public void addBottomSheetCallback(@NonNull BottomSheetCallback callback) {
    if (!callbacks.contains(callback)) {
      callbacks.add(callback);
    }
  }

  /**
   * Removes a previously added callback.
   *
   * @param callback The callback to remove.
   */
  public void removeBottomSheetCallback(@NonNull BottomSheetCallback callback) {
    callbacks.remove(callback);
  }

  /**
   * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
   * animation.
   *
   * @param state One of {@link #STATE_COLLAPSED}, {@link #STATE_EXPANDED}, {@link #STATE_HIDDEN},
   *     or {@link #STATE_HALF_EXPANDED}.
   */
  public void setState(@State int state) {
    if (state == this.state) {
      return;
    }
    if (viewRef == null) {
      // The view is not laid out yet; modify mState and let onLayoutChild handle it later
      if (state == STATE_COLLAPSED
          || state == STATE_EXPANDED
          || state == STATE_HALF_EXPANDED
          || state == STATE_HIDDEN) {
        this.state = state;
      }
      return;
    }
    settleToStatePendingLayout(state);
  }

  private void settleToStatePendingLayout(@State int state) {
    final V child = viewRef.get();
    if (child == null) {
      return;
    }
    // Start the animation; wait until a pending layout if there is one.
    ViewParent parent = child.getParent();
    if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
      final int finalState = state;
      child.post(new Runnable() {
        @Override
        public void run() {
          settleToState(child, finalState);
        }
      });
    } else {
      settleToState(child, state);
    }
  }

  /**
   * Gets the current state of the bottom sheet.
   *
   * @return One of {@link #STATE_EXPANDED}, {@link #STATE_HALF_EXPANDED}, {@link #STATE_COLLAPSED},
   *     {@link #STATE_DRAGGING}, {@link #STATE_SETTLING}, or {@link #STATE_HALF_EXPANDED}.
   */
  @State
  public int getState() {
    return state;
  }

  void setStateInternal(@State int state) {
    if (this.state == state) {
      return;
    }
    this.state = state;

    if (viewRef == null) {
      return;
    }

    View bottomSheet = viewRef.get();
    if (bottomSheet == null) {
      return;
    }

    if (state == STATE_HALF_EXPANDED || state == STATE_EXPANDED) {
      updateImportantForAccessibility(true);
    } else if (state == STATE_HIDDEN || state == STATE_COLLAPSED) {
      updateImportantForAccessibility(false);
    }

    for (int i = 0; i < callbacks.size(); i++) {
      callbacks.get(i).onStateChanged(bottomSheet, state);
    }
    updateAccessibilityActions();
  }

  private void calculateCollapsedOffset() {
    int peek;
    if (peekHeightAuto) {
      peek = Math.max(peekHeightMin, parentHeight - parentWidth * 9 / 16);
    } else {
      peek = peekHeight;
    }

    if (fitToContents) {
      collapsedOffset = Math.max(parentHeight - peek, fitToContentsOffset);
    } else {
      collapsedOffset = parentHeight - peek;
    }
  }

  private void calculateHalfExpandedOffset() {
    this.halfExpandedOffset = (int) (parentHeight * (1 - halfExpandedRatio));
  }

  private void reset() {
    activePointerId = ViewDragHelper.INVALID_POINTER;
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }

  private void restoreOptionalState(@NonNull SavedState ss) {
    if (this.saveFlags == SAVE_NONE) {
      return;
    }
    if (this.saveFlags == SAVE_ALL || (this.saveFlags & SAVE_PEEK_HEIGHT) == SAVE_PEEK_HEIGHT) {
      this.peekHeight = ss.peekHeight;
    }
    if (this.saveFlags == SAVE_ALL
        || (this.saveFlags & SAVE_FIT_TO_CONTENTS) == SAVE_FIT_TO_CONTENTS) {
      this.fitToContents = ss.fitToContents;
    }
    if (this.saveFlags == SAVE_ALL || (this.saveFlags & SAVE_HIDEABLE) == SAVE_HIDEABLE) {
      this.hideable = ss.hideable;
    }
    if (this.saveFlags == SAVE_ALL
        || (this.saveFlags & SAVE_SKIP_COLLAPSED) == SAVE_SKIP_COLLAPSED) {
      this.skipCollapsed = ss.skipCollapsed;
    }
  }

  boolean shouldHide(@NonNull View child, float yvel) {
    if (skipCollapsed) {
      return true;
    }
    if (child.getTop() < collapsedOffset) {
      // It should not hide, but collapse.
      return false;
    }
    final float newTop = child.getTop() + yvel * HIDE_FRICTION;
    return Math.abs(newTop - collapsedOffset) / (float) peekHeight > HIDE_THRESHOLD;
  }

  @Nullable
  @VisibleForTesting
  View findScrollingChild(View view) {
    if (ViewCompat.isNestedScrollingEnabled(view)) {
      return view;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0, count = group.getChildCount(); i < count; i++) {
        View scrollingChild = findScrollingChild(group.getChildAt(i));
        if (scrollingChild != null) {
          return scrollingChild;
        }
      }
    }
    return null;
  }

  private float getYVelocity() {
    if (velocityTracker == null) {
      return 0;
    }
    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
    return velocityTracker.getYVelocity(activePointerId);
  }

  private int getExpandedOffset() {
    return fitToContents ? fitToContentsOffset : expandedOffset;
  }

  void settleToState(@NonNull View child, int state) {
    int top;
    if (state == STATE_COLLAPSED) {
      top = collapsedOffset;
    } else if (state == STATE_HALF_EXPANDED) {
      top = halfExpandedOffset;
      if (fitToContents && top <= fitToContentsOffset) {
        // Skip to the expanded state if we would scroll past the height of the contents.
        state = STATE_EXPANDED;
        top = fitToContentsOffset;
      }
    } else if (state == STATE_EXPANDED) {
      top = getExpandedOffset();
    } else if (state == STATE_HIDDEN) {
      top = parentHeight;
    } else {
      throw new IllegalArgumentException("Illegal state argument: " + state);
    }
    startSettlingAnimation(child, state, top, false);
  }

  void startSettlingAnimation(View child, int state, int top, boolean settleFromViewDragHelper) {
    boolean startedSettling =
        settleFromViewDragHelper
            ? viewDragHelper.settleCapturedViewAt(child.getLeft(), top)
            : viewDragHelper.smoothSlideViewTo(child, child.getLeft(), top);
    if (startedSettling) {
      setStateInternal(STATE_SETTLING);
      // STATE_SETTLING won't animate the material shape, so do that here with the target state.
      if (settleRunnable == null) {
        // If the singleton SettleRunnable instance has not been instantiated, create it.
        settleRunnable = new SettleRunnable(child, state);
      }
      // If the SettleRunnable has not been posted, post it with the correct state.
      if (!settleRunnable.isPosted) {
        settleRunnable.targetState = state;
        ViewCompat.postOnAnimation(child, settleRunnable);
        settleRunnable.isPosted = true;
      } else {
        // Otherwise, if it has been posted, just update the target state.
        settleRunnable.targetState = state;
      }
    } else {
      setStateInternal(state);
    }
  }

  private final ViewDragHelper.Callback dragCallback =
      new ViewDragHelper.Callback() {

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
          if (state == STATE_DRAGGING) {
            return false;
          }
          if (touchingScrollingChild) {
            return false;
          }
          if (state == STATE_EXPANDED && activePointerId == pointerId) {
            View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
            if (scroll != null && scroll.canScrollVertically(-1)) {
              // Let the content scroll up
              return false;
            }
          }
          return viewRef != null && viewRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
          dispatchOnSlide(top);
        }

        @Override
        public void onViewDragStateChanged(int state) {
          if (state == ViewDragHelper.STATE_DRAGGING) {
            setStateInternal(STATE_DRAGGING);
          }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
          int top;
          @State int targetState;
          if (yvel < 0) { // Moving up
            if (fitToContents) {
              top = fitToContentsOffset;
              targetState = STATE_EXPANDED;
            } else {
              int currentTop = releasedChild.getTop();
              if (currentTop > halfExpandedOffset) {
                top = halfExpandedOffset;
                targetState = STATE_HALF_EXPANDED;
              } else {
                top = expandedOffset;
                targetState = STATE_EXPANDED;
              }
            }
          } else if (hideable
              && shouldHide(releasedChild, yvel)
              && (releasedChild.getTop() > collapsedOffset || Math.abs(xvel) < Math.abs(yvel))) {
            // Hide if we shouldn't collapse and the view was either released low or it was a
            // vertical swipe.
            top = parentHeight;
            targetState = STATE_HIDDEN;
          } else if (yvel == 0.f || Math.abs(xvel) > Math.abs(yvel)) {
            // If the Y velocity is 0 or the swipe was mostly horizontal indicated by the X velocity
            // being greater than the Y velocity, settle to the nearest correct height.
            int currentTop = releasedChild.getTop();
            if (fitToContents) {
              if (Math.abs(currentTop - fitToContentsOffset)
                  < Math.abs(currentTop - collapsedOffset)) {
                top = fitToContentsOffset;
                targetState = STATE_EXPANDED;
              } else {
                top = collapsedOffset;
                targetState = STATE_COLLAPSED;
              }
            } else {
              if (currentTop < halfExpandedOffset) {
                if (currentTop < Math.abs(currentTop - collapsedOffset)) {
                  top = expandedOffset;
                  targetState = STATE_EXPANDED;
                } else {
                  top = halfExpandedOffset;
                  targetState = STATE_HALF_EXPANDED;
                }
              } else {
                if (Math.abs(currentTop - halfExpandedOffset)
                    < Math.abs(currentTop - collapsedOffset)) {
                  top = halfExpandedOffset;
                  targetState = STATE_HALF_EXPANDED;
                } else {
                  top = collapsedOffset;
                  targetState = STATE_COLLAPSED;
                }
              }
            }
          } else { // Moving Down
            if (fitToContents) {
              top = collapsedOffset;
              targetState = STATE_COLLAPSED;
            } else {
              // Settle to the nearest correct height.
              int currentTop = releasedChild.getTop();
              if (Math.abs(currentTop - halfExpandedOffset)
                  < Math.abs(currentTop - collapsedOffset)) {
                top = halfExpandedOffset;
                targetState = STATE_HALF_EXPANDED;
              } else {
                top = collapsedOffset;
                targetState = STATE_COLLAPSED;
              }
            }
          }
          startSettlingAnimation(releasedChild, targetState, top, true);
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
          return MathUtils.clamp(top, getExpandedOffset(), hideable ? parentHeight : collapsedOffset);
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
          return child.getLeft();
        }

        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
          if (hideable) {
            return parentHeight;
          } else {
            return collapsedOffset;
          }
        }
      };

  void dispatchOnSlide(int top) {
    View bottomSheet = viewRef.get();
    if (bottomSheet != null && !callbacks.isEmpty()) {
      float slideOffset =
          (top > collapsedOffset)
              ? (float) (collapsedOffset - top) / (parentHeight - collapsedOffset)
              : (float) (collapsedOffset - top) / (collapsedOffset - getExpandedOffset());
      for (int i = 0; i < callbacks.size(); i++) {
        callbacks.get(i).onSlide(bottomSheet, slideOffset);
      }
    }
  }

  private class SettleRunnable implements Runnable {

    private final View view;

    private boolean isPosted;

    @State int targetState;

    SettleRunnable(View view, @State int targetState) {
      this.view = view;
      this.targetState = targetState;
    }

    @Override
    public void run() {
      if (viewDragHelper != null && viewDragHelper.continueSettling(true)) {
        ViewCompat.postOnAnimation(view, this);
      } else {
        setStateInternal(targetState);
      }
      this.isPosted = false;
    }
  }

  /** State persisted across instances */
  protected static class SavedState extends AbsSavedState {
    @State final int state;
    int peekHeight;
    boolean fitToContents;
    boolean hideable;
    boolean skipCollapsed;

    public SavedState(@NonNull Parcel source) {
      this(source, null);
    }

    public SavedState(@NonNull Parcel source, ClassLoader loader) {
      super(source, loader);
      //noinspection ResourceType
      state = source.readInt();
      peekHeight = source.readInt();
      fitToContents = source.readInt() == 1;
      hideable = source.readInt() == 1;
      skipCollapsed = source.readInt() == 1;
    }

    public SavedState(Parcelable superState, @NonNull BottomSheetBehavior<?> behavior) {
      super(superState);
      this.state = behavior.state;
      this.peekHeight = behavior.peekHeight;
      this.fitToContents = behavior.fitToContents;
      this.hideable = behavior.hideable;
      this.skipCollapsed = behavior.skipCollapsed;
    }

    /**
     * This constructor does not respect flags: {@link BottomSheetBehavior#SAVE_PEEK_HEIGHT}, {@link
     * BottomSheetBehavior#SAVE_FIT_TO_CONTENTS}, {@link BottomSheetBehavior#SAVE_HIDEABLE}, {@link
     * BottomSheetBehavior#SAVE_SKIP_COLLAPSED}. It is as if {@link BottomSheetBehavior#SAVE_NONE}
     * were set.
     *
     * @deprecated Use {@link SavedState(Parcelable, BottomSheetBehavior )} instead.
     */
    @Deprecated
    public SavedState(Parcelable superstate, int state) {
      super(superstate);
      this.state = state;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeInt(state);
      out.writeInt(peekHeight);
      out.writeInt(fitToContents ? 1 : 0);
      out.writeInt(hideable ? 1 : 0);
      out.writeInt(skipCollapsed ? 1 : 0);
    }

    public static final Creator<SavedState> CREATOR =
        new ClassLoaderCreator<SavedState>() {
          @NonNull
          @Override
          public SavedState createFromParcel(@NonNull Parcel in, ClassLoader loader) {
            return new SavedState(in, loader);
          }

          @Nullable
          @Override
          public SavedState createFromParcel(@NonNull Parcel in) {
            return new SavedState(in, null);
          }

          @NonNull
          @Override
          public SavedState[] newArray(int size) {
            return new SavedState[size];
          }
        };
  }

  /**
   * A utility function to get the {@link BottomSheetBehavior} associated with the {@code view}.
   *
   * @param view The {@link View} with {@link BottomSheetBehavior}.
   * @return The {@link BottomSheetBehavior} associated with the {@code view}.
   */
  @NonNull
  @SuppressWarnings("unchecked")
  public static <V extends View> BottomSheetBehavior<V> from(@NonNull V view) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (!(params instanceof LayoutParams)) {
      throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
    }
    CoordinatorLayout.Behavior<?> behavior =
        ((LayoutParams) params).getBehavior();
    if (!(behavior instanceof BottomSheetBehavior)) {
      throw new IllegalArgumentException("The view is not associated with BottomDrawerBehavior");
    }
    return (BottomSheetBehavior<V>) behavior;
  }

  private void updateImportantForAccessibility(boolean expanded) {
    if (viewRef == null) {
      return;
    }

    ViewParent viewParent = viewRef.get().getParent();
    if (!(viewParent instanceof CoordinatorLayout)) {
      return;
    }

    CoordinatorLayout parent = (CoordinatorLayout) viewParent;
    final int childCount = parent.getChildCount();
    if (expanded) {
      if (importantForAccessibilityMap == null) {
        importantForAccessibilityMap = new HashMap<>(childCount);
      } else {
        // The important for accessibility values of the child views have been saved already.
        return;
      }
    }

    for (int i = 0; i < childCount; i++) {
      final View child = parent.getChildAt(i);
      if (child == viewRef.get()) {
        continue;
      }

      if (!expanded) {
        if (importantForAccessibilityMap != null
            && importantForAccessibilityMap.containsKey(child)) {
          // Restores the original important for accessibility value of the child view.
          ViewCompat.setImportantForAccessibility(child, importantForAccessibilityMap.get(child));
        }
      } else {
        // Saves the important for accessibility value of the child view.
        importantForAccessibilityMap.put(child, child.getImportantForAccessibility());

        ViewCompat.setImportantForAccessibility(
            child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      }
    }

    if (!expanded) {
      importantForAccessibilityMap = null;
    }
  }

  private void updateAccessibilityActions() {
    if (viewRef == null) {
      return;
    }
    V child = viewRef.get();
    if (child == null) {
      return;
    }
    ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
    ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_EXPAND);
    ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_DISMISS);

    if (hideable && state != STATE_HIDDEN) {
      addAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_DISMISS, STATE_HIDDEN);
    }

    switch (state) {
      case STATE_EXPANDED:
        {
          int nextState = fitToContents ? STATE_COLLAPSED : STATE_HALF_EXPANDED;
          addAccessibilityActionForState(
              child, AccessibilityActionCompat.ACTION_COLLAPSE, nextState);
          break;
        }
      case STATE_HALF_EXPANDED:
        {
          addAccessibilityActionForState(
              child, AccessibilityActionCompat.ACTION_COLLAPSE, STATE_COLLAPSED);
          addAccessibilityActionForState(
              child, AccessibilityActionCompat.ACTION_EXPAND, STATE_EXPANDED);
          break;
        }
      case STATE_COLLAPSED:
        {
          int nextState = fitToContents ? STATE_EXPANDED : STATE_HALF_EXPANDED;
          addAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_EXPAND, nextState);
          break;
        }
      default: // fall out
    }
  }

  private void addAccessibilityActionForState(V child, AccessibilityActionCompat action, final int state) {
    ViewCompat.replaceAccessibilityAction(
        child,
        action,
        null,
        new AccessibilityViewCommand() {
          @Override
          public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
            setState(state);
            return true;
          }
        });
  }
}
