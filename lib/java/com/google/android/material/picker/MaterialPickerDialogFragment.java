/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.material.picker;

import com.google.android.material.R;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.internal.CheckableImageButton;
import com.google.android.material.resources.MaterialAttributes;
import com.google.android.material.shape.MaterialShapeDrawable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.content.res.AppCompatResources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.google.android.material.dialog.InsetDialogOnTouchListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashSet;

/**
 * A {@link Dialog} with a header, {@link MaterialCalendar}, and set of actions.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public abstract class MaterialPickerDialogFragment<S> extends DialogFragment {

  /**
   * The earliest selectable {@link Month} if {@link CalendarBounds} are not specified: January
   * 1900.
   */
  public static final Month DEFAULT_START = Month.create(1900, Calendar.JANUARY);
  /**
   * The earliest selectable {@link Month} if {@link CalendarBounds} are not specified: December
   * 2100.
   */
  public static final Month DEFAULT_END = Month.create(2100, Calendar.DECEMBER);

  /**
   * The default {@link CalendarBounds}: starting at {@code DEFAULT_START}, ending at {@code
   * DEFAULT_END}, and opening on {@link Month#today()}
   */
  public static final CalendarBounds DEFAULT_BOUNDS =
      CalendarBounds.create(DEFAULT_START, DEFAULT_END);

  private static final String THEME_RES_ID_KEY = "THEME_RES_ID";
  private static final String GRID_SELECTOR_KEY = "GRID_SELECTOR_KEY";
  private static final String CALENDAR_BOUNDS_KEY = "CALENDAR_BOUNDS_KEY";
  private static final String TITLE_TEXT_RES_ID_KEY = "TITLE_TEXT_RES_ID_KEY";

  @VisibleForTesting
  @RestrictTo(Scope.LIBRARY_GROUP)
  public static final Object CONFIRM_BUTTON_TAG = "CONFIRM_BUTTON_TAG";

  @VisibleForTesting
  @RestrictTo(Scope.LIBRARY_GROUP)
  public static final Object CANCEL_BUTTON_TAG = "CANCEL_BUTTON_TAG";

  @VisibleForTesting
  @RestrictTo(Scope.LIBRARY_GROUP)
  public static final Object TOGGLE_BUTTON_TAG = "TOGGLE_BUTTON_TAG";

  /**
   * Returns the text to display at the top of the {@link DialogFragment}
   *
   * <p>The text is updated when the Dialog launches and on user clicks.
   *
   * @param selection The current user selection
   */
  public abstract String getHeaderText(@Nullable S selection);

  /** Returns an {@link @AttrRes} to apply as a theme overlay to the DialogFragment */
  protected abstract int getDefaultThemeAttr();

  /**
   * Creates the {@link GridSelector} used for the {@link MaterialCalendar} in this {@link
   * DialogFragment}.
   */
  protected abstract GridSelector<S> createGridSelector();

  private SimpleDateFormat userDefinedSimpleDateFormat;
  private final LinkedHashSet<MaterialPickerOnPositiveButtonClickListener<? super S>>
      onPositiveButtonClickListeners = new LinkedHashSet<>();
  private final LinkedHashSet<View.OnClickListener> onNegativeButtonClickListeners =
      new LinkedHashSet<>();
  private final LinkedHashSet<DialogInterface.OnCancelListener> onCancelListeners =
      new LinkedHashSet<>();
  private final LinkedHashSet<DialogInterface.OnDismissListener> onDismissListeners =
      new LinkedHashSet<>();

  @StyleRes private int themeResId;
  private GridSelector<S> gridSelector;
  private PickerFragment<S> pickerFragment;
  private CalendarBounds calendarBounds;
  @StringRes private int titleTextResId;
  private boolean fullscreen;

  private TextView headerSelectionText;
  private CheckableImageButton headerToggleButton;
  private MaterialShapeDrawable background;

  /**
   * Adds the super class required arguments to the Bundle.
   *
   * <p>Call this method in subclasses before the initial call to {@link
   * DialogFragment#setArguments(Bundle)}
   *
   * @param args The Bundle from the subclassing DialogFragment
   * @param themeResId 0 or a {@link StyleRes} representing a ThemeOverlay
   */
  protected static void addArgsToBundle(
      Bundle args,
      int themeResId,
      CalendarBounds calendarBounds,
      @StringRes int overlineTextResId) {
    args.putInt(THEME_RES_ID_KEY, themeResId);
    args.putParcelable(CALENDAR_BOUNDS_KEY, calendarBounds);
    args.putInt(TITLE_TEXT_RES_ID_KEY, overlineTextResId);
  }

  @StyleRes
  private static int getThemeResource(Context context, int defaultThemeAttr, int themeResId) {
    if (themeResId != 0) {
      return themeResId;
    }
    return MaterialAttributes.resolveOrThrow(
        context, defaultThemeAttr, MaterialPickerDialogFragment.class.getCanonicalName());
  }

  @Override
  public final void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putInt(THEME_RES_ID_KEY, themeResId);
    bundle.putParcelable(GRID_SELECTOR_KEY, gridSelector);
    bundle.putParcelable(CALENDAR_BOUNDS_KEY, calendarBounds);
    bundle.putInt(TITLE_TEXT_RES_ID_KEY, titleTextResId);
  }

  @Override
  public final void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    Bundle activeBundle = bundle == null ? getArguments() : bundle;
    themeResId =
        getThemeResource(
            getContext(), getDefaultThemeAttr(), activeBundle.getInt(THEME_RES_ID_KEY));
    gridSelector = activeBundle.getParcelable(GRID_SELECTOR_KEY);
    calendarBounds = activeBundle.getParcelable(CALENDAR_BOUNDS_KEY);
    titleTextResId = activeBundle.getInt(TITLE_TEXT_RES_ID_KEY);

    if (gridSelector == null) {
      gridSelector = createGridSelector();
    }
  }

  @Override
  public final Dialog onCreateDialog(@Nullable Bundle bundle) {
    Dialog dialog = new Dialog(requireContext(), themeResId);
    Context context = dialog.getContext();
    fullscreen = isFullscreen(context);
    int surfaceColor =
        MaterialAttributes.resolveOrThrow(
            getContext(),
            R.attr.colorSurface,
            MaterialPickerDialogFragment.class.getCanonicalName());
    background =
        new MaterialShapeDrawable(
            context,
            null,
            R.attr.materialCalendarStyle,
            R.style.Widget_MaterialComponents_MaterialCalendar);
    background.initializeElevationOverlay(context);
    background.setFillColor(ColorStateList.valueOf(surfaceColor));
    return dialog;
  }

  @NonNull
  @Override
  public final View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {

    int layout = fullscreen ? R.layout.mtrl_picker_fullscreen : R.layout.mtrl_picker_dialog;
    View root = layoutInflater.inflate(layout, viewGroup);
    Context context = root.getContext();

    View frame = root.findViewById(R.id.mtrl_calendar_frame);
    frame.setLayoutParams(
        new LayoutParams(getPaddedPickerWidth(context), LayoutParams.WRAP_CONTENT));

    headerSelectionText = root.findViewById(R.id.mtrl_picker_header_selection_text);
    headerToggleButton = root.findViewById(R.id.mtrl_picker_header_toggle);
    ((TextView) root.findViewById(R.id.mtrl_picker_title_text)).setText(titleTextResId);
    initHeaderToggle(context);

    MaterialButton confirmButton = root.findViewById(R.id.confirm_button);
    confirmButton.setTag(CONFIRM_BUTTON_TAG);
    confirmButton.setOnClickListener(
        v -> {
          for (MaterialPickerOnPositiveButtonClickListener<? super S> listener :
              onPositiveButtonClickListeners) {
            listener.onPositiveButtonClick(getSelection());
          }
          dismiss();
        });

    MaterialButton cancelButton = root.findViewById(R.id.cancel_button);
    cancelButton.setTag(CANCEL_BUTTON_TAG);
    cancelButton.setOnClickListener(
        v -> {
          for (View.OnClickListener listener : onNegativeButtonClickListeners) {
            listener.onClick(v);
          }
          dismiss();
        });
    return root;
  }

  @Override
  public void onStart() {
    super.onStart();
    Window window = requireDialog().getWindow();
    // Dialogs use a background with an InsetDrawable by default, so we have to replace it.
    if (fullscreen) {
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      window.setBackgroundDrawable(background);
    } else {
      window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      int inset =
          getResources().getDimensionPixelOffset(R.dimen.mtrl_calendar_dialog_background_inset);
      window.setBackgroundDrawable(new InsetDrawable(background, inset));
      Rect insets = new Rect(inset, inset, inset, inset);
      window
          .getDecorView()
          .setOnTouchListener(new InsetDialogOnTouchListener(requireDialog(), insets));
    }
    startPickerFragment();
  }

  @Override
  public void onStop() {
    pickerFragment.getGridSelector().clearOnSelectionChangedListeners();
    super.onStop();
  }

  @Override
  public final void onCancel(@NonNull DialogInterface dialogInterface) {
    for (DialogInterface.OnCancelListener listener : onCancelListeners) {
      listener.onCancel(dialogInterface);
    }
    super.onCancel(dialogInterface);
  }

  @Override
  public final void onDismiss(@NonNull DialogInterface dialogInterface) {
    for (DialogInterface.OnDismissListener listener : onDismissListeners) {
      listener.onDismiss(dialogInterface);
    }
    ViewGroup viewGroup = ((ViewGroup) getView());
    if (viewGroup != null) {
      viewGroup.removeAllViews();
    }
    super.onDismiss(dialogInterface);
  }

  /**
   * Returns a {@link S} instance representing the selection or null if the user has not confirmed a
   * selection.
   */
  @Nullable
  public final S getSelection() {
    return gridSelector.getSelection();
  }

  /**
   * Sets a user-defined date formatter.
   *
   * <p>Useful when the default localized date format is inadequate
   */
  public final void setSimpleDateFormat(@Nullable SimpleDateFormat simpleDateFormat) {
    userDefinedSimpleDateFormat = simpleDateFormat;
  }

  /** Returns the user-defined date formatter. */
  @Nullable
  public final SimpleDateFormat getSimpleDateFormat() {
    return userDefinedSimpleDateFormat;
  }

  private void updateHeader(S selection) {
    headerSelectionText.setText(getHeaderText(selection));
  }

  private void startPickerFragment() {
    pickerFragment =
        headerToggleButton.isChecked()
            ? MaterialTextInputPicker.newInstance(gridSelector, calendarBounds)
            : MaterialCalendar.newInstance(gridSelector, themeResId, calendarBounds);
    updateHeader(gridSelector.getSelection());

    FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
    fragmentTransaction.replace(R.id.mtrl_calendar_frame, pickerFragment);
    fragmentTransaction.commitNow();

    pickerFragment.getGridSelector().addOnSelectionChangedListener(this::updateHeader);
  }

  private void initHeaderToggle(Context context) {
    headerToggleButton.setTag(TOGGLE_BUTTON_TAG);
    headerToggleButton.setImageDrawable(createHeaderToggleDrawable(context));
    headerToggleButton.setOnClickListener(
        v -> {
          headerToggleButton.toggle();
          startPickerFragment();
        });
  }

  // Create StateListDrawable programmatically for pre-lollipop support
  private static Drawable createHeaderToggleDrawable(Context context) {
    StateListDrawable toggleDrawable = new StateListDrawable();
    toggleDrawable.addState(
        new int[] {android.R.attr.state_checked},
        AppCompatResources.getDrawable(context, R.drawable.ic_calendar_black_24dp));
    toggleDrawable.addState(
        new int[] {}, AppCompatResources.getDrawable(context, R.drawable.ic_edit_black_24dp));
    return toggleDrawable;
  }

  static boolean isFullscreen(Context context) {
    int calendarStyle =
        MaterialAttributes.resolveOrThrow(
            context, R.attr.materialCalendarStyle, MaterialCalendar.class.getCanonicalName());
    int[] attrs = {android.R.attr.windowFullscreen};
    TypedArray a = context.obtainStyledAttributes(calendarStyle, attrs);
    boolean fullscreen = a.getBoolean(0, false);
    a.recycle();
    return fullscreen;
  }

  private static int getPaddedPickerWidth(Context context) {
    Resources resources = context.getResources();
    int padding = resources.getDimensionPixelOffset(R.dimen.mtrl_calendar_content_padding);
    int daysInWeek = Month.today().daysInWeek;
    int dayWidth = resources.getDimensionPixelSize(R.dimen.mtrl_calendar_day_width);
    int horizontalSpace =
        resources.getDimensionPixelOffset(R.dimen.mtrl_calendar_month_horizontal_padding);
    return 2 * padding + daysInWeek * dayWidth + (daysInWeek - 1) * horizontalSpace;
  }

  /** The supplied listener is called when the user confirms a valid selection. */
  public boolean addOnPositiveButtonClickListener(
      MaterialPickerOnPositiveButtonClickListener<? super S> onPositiveButtonClickListener) {
    return onPositiveButtonClickListeners.add(onPositiveButtonClickListener);
  }

  /**
   * Removes a listener previously added via {@link
   * MaterialPickerDialogFragment#addOnPositiveButtonClickListener}.
   */
  public boolean removeOnPositiveButtonClickListener(
      MaterialPickerOnPositiveButtonClickListener<? super S> onPositiveButtonClickListener) {
    return onPositiveButtonClickListeners.remove(onPositiveButtonClickListener);
  }

  /**
   * Removes all listeners added via {@link
   * MaterialPickerDialogFragment#addOnPositiveButtonClickListener}.
   */
  public void clearOnPositiveButtonClickListeners() {
    onPositiveButtonClickListeners.clear();
  }

  /** The supplied listener is called when the user clicks the cancel button. */
  public boolean addOnNegativeButtonClickListener(
      View.OnClickListener onNegativeButtonClickListener) {
    return onNegativeButtonClickListeners.add(onNegativeButtonClickListener);
  }

  /**
   * Removes a listener previously added via {@link
   * MaterialPickerDialogFragment#addOnNegativeButtonClickListener}.
   */
  public boolean removeOnNegativeButtonClickListener(
      View.OnClickListener onNegativeButtonClickListener) {
    return onNegativeButtonClickListeners.remove(onNegativeButtonClickListener);
  }

  /**
   * Removes all listeners added via {@link
   * MaterialPickerDialogFragment#addOnNegativeButtonClickListener}.
   */
  public void clearOnNegativeButtonClickListeners() {
    onNegativeButtonClickListeners.clear();
  }

  /**
   * The supplied listener is called when the user cancels the picker via back button or a touch
   * outside the view. It is not called when the user clicks the cancel button. To add a listener
   * for use when the user clicks the cancel button, use {@link
   * MaterialPickerDialogFragment#addOnNegativeButtonClickListener}.
   */
  public boolean addOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
    return onCancelListeners.add(onCancelListener);
  }

  /**
   * Removes a listener previously added via {@link
   * MaterialPickerDialogFragment#addOnCancelListener}.
   */
  public boolean removeOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
    return onCancelListeners.remove(onCancelListener);
  }

  /** Removes all listeners added via {@link MaterialPickerDialogFragment#addOnCancelListener}. */
  public void clearOnCancelListeners() {
    onCancelListeners.clear();
  }

  /**
   * The supplied listener is called whenever the DialogFragment is dismissed, no matter how it is
   * dismissed.
   */
  public boolean addOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
    return onDismissListeners.add(onDismissListener);
  }

  /**
   * Removes a listener previously added via {@link
   * MaterialPickerDialogFragment#addOnDismissListener}.
   */
  public boolean removeOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
    return onDismissListeners.remove(onDismissListener);
  }

  /** Removes all listeners added via {@link MaterialPickerDialogFragment#addOnDismissListener}. */
  public void clearOnDismissListeners() {
    onDismissListeners.clear();
  }
}
