package com.antonionicolaspina.textimageview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

public class TextImageView extends ImageView {
  public interface OnTextMovedListener {
    void textMoved(PointF position);
  }

  public enum ClampMode {UNLIMITED, ORIGIN_INSIDE, TEXT_INSIDE}

  private String text;
  private Paint paint;
  private RectF imageRect;
  private Rect textRect;
  private PointF textPosition;

  private PointF focalPoint;

  private boolean panEnabled;

  private ClampMode clampTextMode;

  private OnTextMovedListener onTextMovedListener;

  public TextImageView(Context context) {
    super(context);
    init(context, null);
  }

  public TextImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public TextImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public TextImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context, attrs);
  }

  protected void init(Context context, AttributeSet attributeSet) {
    paint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    imageRect    = new RectF();
    textRect     = new Rect();
    textPosition = new PointF(0f, 0f);
    focalPoint   = new PointF();

    if (null != attributeSet) {
      TypedArray attrs    = context.getTheme().obtainStyledAttributes(attributeSet, R.styleable.TextImageView, 0, 0);
      Resources resources = context.getResources();
      paint.setTextSize(attrs.getDimensionPixelSize(R.styleable.TextImageView_android_textSize, resources.getDimensionPixelSize(R.dimen.default_text_size)));
      paint.setColor(attrs.getColor(R.styleable.TextImageView_android_textColor, Color.BLACK));
      panEnabled = attrs.getBoolean(R.styleable.TextImageView_tiv_panEnabled, false);
      clampTextMode = ClampMode.values()[attrs.getInt(R.styleable.TextImageView_tiv_clampTextMode, 0)];
      setText(attrs.getString(R.styleable.TextImageView_android_text));
      attrs.recycle();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if ( (null == text) && isInEditMode()) {
      text = "sample text";
    }

    if (null == text) {
      return;
    }

    // Get rectangle of the drawable
    imageRect.top    = 0;
    imageRect.left   = 0;

    Drawable drawable = getDrawable();
    if (null != drawable) {
      imageRect.right = drawable.getIntrinsicWidth();
      imageRect.bottom = drawable.getIntrinsicHeight();
    }
    // Translate and scale the rectangle
    getImageMatrix().mapRect(imageRect);

    // Draw text
    canvas.drawText(text, textPosition.x+imageRect.left, textPosition.y+imageRect.top+textRect.height(), paint);
  }

  protected void recalculateFocalPoint(MotionEvent event) {
    final int pointerCount = event.getPointerCount();
    if (pointerCount <= 0) {
      return;
    }

    focalPoint.x = 0f;
    focalPoint.y = 0f;
    for(int i=0; i<pointerCount; i++) {
      focalPoint.x += event.getX(i);
      focalPoint.y += event.getY(i);
    }
    focalPoint.x /= pointerCount;
    focalPoint.y /= pointerCount;
  }

  protected static float between(float value, float min, float max) {
    return Math.max(Math.min(value, max), min);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    super.onTouchEvent(event);

    final int action = event.getAction();
    switch(action & MotionEvent.ACTION_MASK) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        recalculateFocalPoint(event);
        return true;
      case MotionEvent.ACTION_MOVE: {
        final float x = focalPoint.x;
        final float y = focalPoint.y;

        recalculateFocalPoint(event);

        if (panEnabled) {
          textPosition.x += focalPoint.x - x;
          textPosition.y += focalPoint.y - y;

          reclampText();

          invalidate();
        }

        return true;
      }
    }
    return false;
  }

  protected void reclampText() {
    switch (clampTextMode) {
      case UNLIMITED:
        break;
      case ORIGIN_INSIDE:
        textPosition.x = between(textPosition.x, 0, imageRect.width());
        textPosition.y = between(textPosition.y, 0, imageRect.height());
        break;
      case TEXT_INSIDE:
        textPosition.x = between(textPosition.x, 0, imageRect.width()-textRect.width());
        textPosition.y = between(textPosition.y, 0, imageRect.height()-textRect.height());
        break;
    }

    if (null != onTextMovedListener) {
      PointF position = getTextPosition();
      if ( (!Float.isNaN(position.x)) && (!Float.isNaN(position.y)) ) {
        onTextMovedListener.textMoved(position);
      }
    }
  }

  /**************
   *** Public ***
   **************/

  /**
   * Set text to be drawn over the image.
   * @param text The text.
   */
  public void setText(String text) {
    this.text = text;
    if (null != text) {
      paint.getTextBounds(text, 0, text.length(), textRect);
    }
    reclampText();
    invalidate();
  }

  /**
   * Set the typeface to use for the text.
   * @param typeface The typeface to be used.
   */
  public void setTypeface(Typeface typeface) {
    paint.setTypeface(typeface);
    setText(text);
  }

  /**
   * Set the text color.
   * @param color Color in the format of <a href="http://developer.android.com/reference/android/graphics/Color.html">android.graphics.Color</a>.
   *
   * @see <a href="http://developer.android.com/reference/android/graphics/Color.html">android.graphics.Color</a>
   */
  public void setTextColor(int color) {
    paint.setColor(color);
    invalidate();
  }

  /**
   * Set the default text size to the given value, interpreted as "scaled pixel" units.
   * This size is adjusted based on the current density and user font size preference.
   * @param textSize The scaled pixel size.
   */
  public void setTextSize(float textSize) {
    paint.setTextSize(textSize);
    setText(text);
  }

  /**
   * Return offset position between the text and the image. Considers both top left corners to the the calculation.
   * @return Pointf containing x and y offsets, as a per-one value. Eg. (0,0)=top-left, (1,1)=bottom-right.
   */
  public PointF getTextPosition() {
    return new PointF(textPosition.x / imageRect.width(), textPosition.y / imageRect.height());
  }

  /**
   * Set the listener to be fired when the text changes its location.
   * @param listener the listener to be called, or null.
   */
  public void setOnTextMovedListener(OnTextMovedListener listener) {
    this.onTextMovedListener = listener;
  }

  /**
   * Get the relative size between the image and the text.
   * @return Relative size. Eg. 0.5=text half the height of the image.
   */
  public float getTextRelativeSize() {
    return paint.getTextSize() / imageRect.height();
  }
}
