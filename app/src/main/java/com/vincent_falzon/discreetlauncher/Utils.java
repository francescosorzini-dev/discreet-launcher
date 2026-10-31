package com.vincent_falzon.discreetlauncher ;

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
import android.os.Build ;
import android.util.Log ;
import android.view.Gravity ;
import android.view.View ;
import android.view.ViewGroup ;
import android.widget.FrameLayout ;
import android.widget.Toast ;
import androidx.core.content.ContextCompat ;
import androidx.core.graphics.Insets ;
import androidx.core.view.ViewCompat ;
import androidx.core.view.WindowInsetsCompat ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.settings.ColorPickerDialog ;

/**
 * Provide utility methods used in various places of Discreet Launcher.
 */
public abstract class Utils
{
	// Constants
	private static final String LOG_PREFIX = "[LogDL] " ;


	/**
	 * Display an R.string message in a Toast for a short duration.
	 */
	public static void displayToast(Context context, int message)
	{
		if(context == null) return ;
		Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show() ;
	}


	/**
	 * Display a message in a Toast for a long duration.
	 */
	public static void displayLongToast(Context context, String message)
	{
		if(context == null) return ;
		Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show() ;
	}


	/**
	 * Retrieve the currently selected color for the given preference key.
	 */
	public static int getColor(SharedPreferences settings, String key, String fallback)
	{
		// Try to load the color at the given key, or use the provided fallback
		String hexadecimal = settings.getString(key, Constants.NONE) ;
		if(hexadecimal.equals(Constants.NONE)) hexadecimal = fallback ;

		// Convert the hexadecimal color to an "int" color
		return ColorPickerDialog.convertHexadecimalColorToInt(hexadecimal) ;
	}


	/**
	 * Retrieve the currently defined icon size in pixels.
	 */
	public static int getIconSize(Context context, SharedPreferences settings)
	{
		// Check if the legacy icon size setting is still used (v7.6.0 - 24/08/2024, to remove later)
		int legacy_icon_size = settings.getInt(Constants.OLD_ICON_SIZE, 0) ;
		if(legacy_icon_size != 0)
			{
				// Migrate the current value to the new setting
				SharedPreferences.Editor editor = settings.edit() ;
				editor.putString(Constants.ICON_SIZE_DP, Integer.toString(legacy_icon_size * 12)) ;
				editor.putInt(Constants.OLD_ICON_SIZE, 0) ;
				editor.apply() ;
			}

		// Retrieve the icon size in dp from the settings (Android default is 48dp)
		int icon_size_dp = Integer.parseInt(settings.getString(Constants.ICON_SIZE_DP, "48")) ;

		// Convert the icon size from dp to pixels
		return Math.round(icon_size_dp * context.getResources().getDisplayMetrics().density) ;
	}


	/**
	 * Try to start an app from the list using the ComponentInfo in the given setting key.
	 * @return <code>true</code> if something was done, <code>false</code> otherwise
	 */
	public static boolean searchAndStartApplication(View view, SharedPreferences settings, String setting_key)
	{
		// Retrieve the app to launch (if any) based on the given setting key
		String component_info = settings.getString(setting_key, Constants.NONE) ;
		if(component_info.equals(Constants.NONE)) return false ;

		// Search the application in the list
		for(Application application : ActivityMain.getApplicationsList().getApplications(true))
			if(application.getComponentInfo().equals(component_info))
			{
				// Start the application
				application.start(view) ;
				return true ;
			}

		// The application was not found, display an error message and reset the setting value
		Context context = view.getContext() ;
		Utils.displayLongToast(context, context.getString(R.string.error_app_not_found, component_info)) ;
		SharedPreferences.Editor editor = settings.edit() ;
		editor.putString(setting_key, Constants.NONE).apply() ;
		return true ;
	}


	/**
	 * Write an ERROR message to Android logcat (used in <code>catch</code> statements).
	 */
	public static void logError(String tag, String message)
	{
		if(BuildConfig.DEBUG)
			Log.e(LOG_PREFIX + tag, message) ;
	}


	/**
	 * Write an INFO message to Android logcat (used to report milestones or successes).
	 */
	public static void logInfo(String tag, String message)
	{
		if(BuildConfig.DEBUG)
			Log.i(LOG_PREFIX + tag, message) ;
	}


	/**
	 * Write a DEBUG message to Android logcat (used to follow the behavior while debugging).
	 */
	public static void logDebug(String tag, String message)
	{
		if(BuildConfig.DEBUG)
			Log.d(LOG_PREFIX + tag, message) ;
	}


	/**
	 * Starting with Android 15, windows of apps targeting API 35 are drawn behind the system bars
	 * and the bars are no longer colored by the system. For activities with an action bar, draw the
	 * status bar background and keep the content above the navigation bar.
	 */
	public static void fitSystemBars(Activity activity)
	{
		// Before Android 15, the system still handles the bars
		if(Build.VERSION.SDK_INT < 35) return ;

		// Add a view drawing the status bar background on top of the window
		ViewGroup decor = (ViewGroup)activity.getWindow().getDecorView() ;
		View statusBarScrim = new View(activity) ;
		statusBarScrim.setBackgroundColor(ContextCompat.getColor(activity, R.color.for_system_bars)) ;
		decor.addView(statusBarScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)) ;

		// Size the status bar background and pad the content above the navigation bar
		View content = activity.findViewById(android.R.id.content) ;
		ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
				WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(decor) ;
				if(rootInsets == null) rootInsets = insets ;
				Insets bars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()) ;
				ViewGroup.LayoutParams params = statusBarScrim.getLayoutParams() ;
				if(params.height != bars.top)
					{
						params.height = bars.top ;
						statusBarScrim.setLayoutParams(params) ;
					}
				view.setPadding(bars.left, view.getPaddingTop(), bars.right, bars.bottom) ;
				return insets ;
			}) ;

		// The action bar is moved below the status bar but the content is not: measure the overlap
		// after each layout and push the content down by exactly that amount
		View actionBar = decor.findViewById(androidx.appcompat.R.id.action_bar_container) ;
		if(actionBar == null) return ;
		View.OnLayoutChangeListener overlapFixer = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				int[] action_bar_location = new int[2] ;
				int[] content_location = new int[2] ;
				actionBar.getLocationInWindow(action_bar_location) ;
				content.getLocationInWindow(content_location) ;
				int overlap = Math.max(0, action_bar_location[1] + actionBar.getHeight() - content_location[1]) ;
				if(content.getPaddingTop() != overlap)
					content.post(() -> content.setPadding(content.getPaddingLeft(), overlap, content.getPaddingRight(), content.getPaddingBottom())) ;
			} ;
		actionBar.addOnLayoutChangeListener(overlapFixer) ;
		content.addOnLayoutChangeListener(overlapFixer) ;
	}
}
