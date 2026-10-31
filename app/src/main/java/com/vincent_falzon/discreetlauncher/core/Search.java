package com.vincent_falzon.discreetlauncher.core ;

// License
/*

	This file is part of Discreet Launcher.

	Copyright (C) 2019-2025 Vincent Falzon

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

// Imports
import android.app.Activity ;
import android.content.Context ;
import android.content.SharedPreferences ;
import android.graphics.Color ;
import android.graphics.drawable.ColorDrawable ;
import android.graphics.drawable.Drawable ;
import android.os.Build ;
import android.text.Editable ;
import android.text.TextWatcher ;
import android.view.Gravity ;
import android.view.LayoutInflater ;
import android.view.MotionEvent ;
import android.view.View ;
import android.view.ViewGroup ;
import android.view.animation.DecelerateInterpolator ;
import android.view.inputmethod.EditorInfo ;
import android.view.inputmethod.InputMethod ;
import android.view.inputmethod.InputMethodManager ;
import android.widget.EditText ;
import android.widget.LinearLayout ;
import android.widget.PopupWindow ;
import androidx.core.view.ViewCompat ;
import androidx.core.view.WindowInsetsCompat ;
import androidx.preference.PreferenceManager ;
import androidx.recyclerview.widget.LinearLayoutManager ;
import androidx.recyclerview.widget.RecyclerView ;
import com.vincent_falzon.discreetlauncher.ActivityMain ;
import com.vincent_falzon.discreetlauncher.Constants ;
import com.vincent_falzon.discreetlauncher.FlexibleGridLayout ;
import com.vincent_falzon.discreetlauncher.R ;
import com.vincent_falzon.discreetlauncher.SearchAdapter ;
import com.vincent_falzon.discreetlauncher.Utils ;
import java.util.ArrayList ;

/**
 * Represent the Search application.
 */
public class Search extends Application
{
	// Attributes
	private AnimatedPopupWindow popup ;
	private SearchAdapter adapter ;
	private RecyclerView recycler ;


	/**
	 * Constructor.
	 */
	public Search(String display_name, Drawable icon)
	{
		super(display_name, Constants.APK_SEARCH, Constants.APK_SEARCH, icon, null) ;
		popup = null ;
	}


	/**
	 * Display the Search application as a popup.
	 */
	@SuppressWarnings({"RedundantCast", "RedundantSuppression"})
	public boolean start(View parent)
	{
		// Initializations
		Context context = parent.getContext() ;
		LayoutInflater inflater = LayoutInflater.from(context) ;

		// Prepare the popup view
		View popupView = inflater.inflate(R.layout.view_popup, (ViewGroup)null) ;
		popupView.findViewById(R.id.popup_header).setVisibility(View.INVISIBLE) ;
		popupView.findViewById(R.id.popup_line1).setVisibility(View.INVISIBLE) ;
		popupView.findViewById(R.id.popup_line2).setVisibility(View.VISIBLE) ;

		// Prepare the search bar
		EditText searchBar = popupView.findViewById(R.id.search_bar) ;
		searchBar.setVisibility(View.VISIBLE) ;
		searchBar.addTextChangedListener(new TextChangeListener()) ;
		searchBar.setOnEditorActionListener((view, actionId, event) -> {
				// Perform an action when the user presses "Enter"
				if(actionId == EditorInfo.IME_ACTION_SEARCH)
					{
						// Launch the first app displayed in the results
						adapter.launchFirstApp(view) ;
						return true ;
					}
				return false ;
			}) ;

		// Retrieve all the applications without folders and the Search
		ArrayList<Application> applications = ActivityMain.getApplicationsList().getApplications(false) ;
		Application search = null ;
		for(Application application : applications)
			if(application instanceof Search) search = application ;
		if(search != null) applications.remove(search) ;

		// Prepare the popup content
		recycler = popupView.findViewById(R.id.popup_recycler) ;
		adapter = new SearchAdapter(context, applications) ;

		// Close the search before launching an app, so that nothing moves on screen while it starts
		adapter.setBeforeLaunch(this::closePopup) ;
		recycler.setAdapter(adapter) ;
		// For one-handed use, start the results from the bottom (on the right for the right hand)
		String one_handed = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.ONE_HANDED, "off") ;
		recycler.setLayoutManager(new FlexibleGridLayout(context, ActivityMain.getApplicationWidth(), !one_handed.equals("off"))) ;
		recycler.setLayoutDirection(one_handed.equals("right") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR) ;

		recycler.setMinimumHeight(0) ;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
			recycler.setOverScrollMode(View.OVER_SCROLL_NEVER) ;

		// Retrieve the app drawer colors
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context) ;
		int text_color = Utils.getColor(settings, Constants.TEXT_COLOR_DRAWER, Constants.COLOR_FOR_TEXT_ON_OVERLAY) ;
		int background_color = Utils.getColor(settings, Constants.BACKGROUND_COLOR_DRAWER, Constants.COLOR_FOR_OVERLAY) ;

		// Lighten the search background color for better contrast
		float[] background_hsv = new float[3] ;
		Color.colorToHSV(background_color, background_hsv) ;
		background_hsv[2] += (background_hsv[2] <= 0.5) ? 0.2 : 0.1 ;
		int search_background_color = Color.HSVToColor(235, background_hsv) ;

		// Set the search colors
		recycler.setBackgroundColor(search_background_color) ;
		adapter.setTextColor(text_color) ;

		// Create the popup representing the Search application
		popup = new AnimatedPopupWindow(popupView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, true, 1) ;
		popupView.setOnTouchListener(new PopupTouchListener()) ;

		// Fix popup not closing on press back with API 21
		popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)) ;

		// Open the keyboard first, with an invisible popup reduced to the search bar. Only once the
		// keyboard is open and has stopped moving, add the results list (the popup is resized while
		// still invisible), make sure the first result is in place, then make it appear slowly
		popupView.setAlpha(0) ;
		recycler.setVisibility(View.GONE) ;
		final float density = context.getResources().getDisplayMetrics().density ;
		final LinearLayoutManager layoutManager = (LinearLayoutManager)recycler.getLayoutManager() ;
		final int[] last_bottom = { -1 } ;
		final int[] initial_bottom = { -1 } ;
		final boolean[] list_shown = { false } ;
		final boolean[] revealed = { false } ;
		final boolean[] user_scrolled = { false } ;
		final int[] skipped_frames = { 0 } ;

		// Add the results list (once)
		final Runnable showList = () -> {
				if(list_shown[0]) return ;
				list_shown[0] = true ;
				recycler.setVisibility(View.VISIBLE) ;
				recycler.scrollToPosition(0) ;
			} ;

		// The keyboard is detected by polling the position of the popup: when the keyboard opens the
		// small popup is only moved (not laid out again), which triggers no layout event. The list is
		// added once the popup is above the keyboard and has not moved for two checks in a row
		final View screen = parent.getRootView() ;
		final Runnable pollKeyboard = new Runnable()
			{
				private int last_seen = -1 ;
				private int stable_checks = 0 ;

				@Override
				public void run()
				{
					if(list_shown[0]) return ;
					int[] location = new int[2] ;
					popupView.getLocationOnScreen(location) ;
					int bottom = location[1] - Math.round(popupView.getTranslationY()) + popupView.getHeight() ;
					int[] screen_location = new int[2] ;
					screen.getLocationOnScreen(screen_location) ;
					int screen_bottom = screen_location[1] + screen.getHeight() ;
					WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(popupView) ;
					boolean keyboard = (bottom < screen_bottom - 120 * density)
							|| ((insets != null) && insets.isVisible(WindowInsetsCompat.Type.ime()) && (bottom < screen_bottom - 50 * density)) ;
					if(keyboard && (bottom == last_seen)) stable_checks++ ;
						else stable_checks = 0 ;
					last_seen = bottom ;
					if(stable_checks >= 2) showList.run() ;
						else popupView.postDelayed(this, 30) ;
				}
			} ;
		popupView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
				int[] location = new int[2] ;
				popupView.getLocationOnScreen(location) ;
				int bottom = location[1] - Math.round(popupView.getTranslationY()) + popupView.getHeight() ;
				if(initial_bottom[0] < 0) initial_bottom[0] = bottom ;
				if(bottom != last_bottom[0])
					{
						// Moved by the system after the appearance (ex: suggestions bar): follow slowly
						if(revealed[0] && (last_bottom[0] >= 0))
							{
								popupView.setTranslationY(popupView.getTranslationY() + (last_bottom[0] - bottom)) ;
								popupView.animate().cancel() ;
								popupView.animate().alpha(1).translationY(0).setDuration(Utils.SLOW_APPEAR_DURATION).setInterpolator(new DecelerateInterpolator(2f)).start() ;
							}
						last_bottom[0] = bottom ;

					}
			}) ;

		// Stop forcing the first result once the user scrolls the list by hand
		recycler.addOnScrollListener(new RecyclerView.OnScrollListener()
			{
				@Override
				public void onScrollStateChanged(RecyclerView view, int state)
				{
					if(state == RecyclerView.SCROLL_STATE_DRAGGING) user_scrolled[0] = true ;
				}
			}) ;

		// Just before drawing: keep the first result in place (skipping a wrong frame), then make
		// the popup appear slowly once the list is there
		popupView.getViewTreeObserver().addOnPreDrawListener(() -> {
				if(!list_shown[0]) return true ;
				boolean first_in_place = (adapter.getItemCount() == 0) || (layoutManager == null)
						|| (layoutManager.findFirstCompletelyVisibleItemPosition() == 0) ;
				if(!first_in_place && !user_scrolled[0] && (skipped_frames[0] < 3))
					{
						skipped_frames[0]++ ;
						recycler.scrollToPosition(0) ;
						return false ;
					}
				skipped_frames[0] = 0 ;
				if(!revealed[0])
					{
						revealed[0] = true ;
						Utils.slowAppear(popupView, 1) ;
					}
				return true ;
			}) ;

		// Start watching for the keyboard, and in any case show the list after 0.4 s at most
		popupView.post(pollKeyboard) ;
		popupView.postDelayed(showList, 400) ;

		// Display the popup and the keyboard
		popup.showAtLocation(parent, Gravity.BOTTOM, 0, 0) ;
		((InputMethodManager)context.getSystemService(Activity.INPUT_METHOD_SERVICE)).toggleSoftInputFromWindow(parent.getWindowToken(), InputMethod.SHOW_EXPLICIT, 0) ;
		searchBar.requestFocus() ;
		return true ;
	}


	/**
	 * Dismiss the popup if it is currently displayed.
	 */
	public void closePopup()
	{
		if(popup != null) popup.dismiss() ;
	}


	/**
	 * Dismiss the popup immediately, without animation (ex: when leaving the home screen).
	 */
	public void closePopupNow()
	{
		if(popup != null) popup.dismissNow() ;
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Dismiss the popup when the user touchs outside of it (needs <code>focusable = true</code>).
	 */
	private class PopupTouchListener implements View.OnTouchListener
	{
		/**
		 * Called when an element is touched.
		 */
		@Override
		public boolean onTouch(View view, MotionEvent event)
		{
			// Close the popup and mark the event as consumed
			view.performClick() ;
			closePopup() ;
			return true ;
		}
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Called when the text of an EditText is changed by the user.
	 */
	private class TextChangeListener implements TextWatcher
	{
		// Needed to implement TextWatcher
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after){ }


		/**
		 * Called when the text has just been changed.
		 */
		@Override
		public void onTextChanged(CharSequence text, int start, int before, int count)
		{
			// Update the display of the RecyclerView, then show the best result
			adapter.getFilter().filter(text, results_count -> { if(recycler != null) recycler.scrollToPosition(0) ; }) ;
		}

		// Needed to implement TextWatcher
		@Override
		public void afterTextChanged(Editable s){ }
	}
}
