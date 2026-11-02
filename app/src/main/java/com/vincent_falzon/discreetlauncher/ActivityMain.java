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
import android.annotation.SuppressLint ;
import android.app.role.RoleManager ;
import android.content.Context ;
import android.content.Intent ;
import android.content.SharedPreferences ;
import android.content.pm.ActivityInfo ;
import android.content.res.ColorStateList ;
import android.graphics.Color ;
import android.graphics.Rect ;
import android.graphics.drawable.Drawable ;
import android.os.Build ;
import android.os.Bundle ;
import androidx.activity.result.ActivityResultLauncher ;
import androidx.activity.result.contract.ActivityResultContracts ;
import androidx.annotation.NonNull ;
import androidx.appcompat.app.AppCompatActivity ;
import androidx.appcompat.app.AppCompatDelegate ;
import androidx.appcompat.content.res.AppCompatResources ;
import androidx.core.content.ContextCompat ;
import androidx.core.graphics.Insets ;
import androidx.core.graphics.drawable.DrawableCompat ;
import androidx.core.view.GestureDetectorCompat ;
import androidx.core.view.ViewCompat ;
import androidx.core.view.WindowCompat ;
import androidx.core.view.WindowInsetsCompat ;
import androidx.core.widget.TextViewCompat ;
import androidx.preference.PreferenceManager ;
import androidx.recyclerview.widget.GridLayoutManager ;
import androidx.recyclerview.widget.RecyclerView ;
import android.view.GestureDetector ;
import android.view.MotionEvent ;
import android.view.View ;
import android.view.ViewGroup ;
import android.widget.ImageView ;
import android.widget.RelativeLayout ;
import android.widget.TextView ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.core.ApplicationsList ;
import com.vincent_falzon.discreetlauncher.core.Folder ;
import com.vincent_falzon.discreetlauncher.core.Search ;
import com.vincent_falzon.discreetlauncher.events.ShortcutLegacyListener ;
import com.vincent_falzon.discreetlauncher.events.PackagesListener ;
import com.vincent_falzon.discreetlauncher.menu.DialogMenu ;
import com.vincent_falzon.discreetlauncher.quickaccess.NotificationDisplayer ;

/**
 * Main activity managing the home screen and app drawer.
 */
public class ActivityMain extends AppCompatActivity implements View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener
{
	// Constants
	private static final String TAG = "ActivityMain" ;

	// Attributes
	private static ApplicationsList applicationsList ;
	private static boolean skip_list_update ;
	private static boolean adapters_update_needed ;
	private static String internal_folder ;
	private static int application_width ;
	private PackagesListener packagesListener ;
	private ShortcutLegacyListener shortcutLegacyListener ;
	private SharedPreferences settings ;
	private GestureDetectorCompat gestureDetector ;
	private NotificationDisplayer notification ;
	private DialogMenu dialogMenu ;
	private float density ;
	private int scroll_position ;
	private int scroll_last_position ;
	private int scroll_close_gesture ;
	private boolean reverse_interface ;

	// Attributes related to the home screen
	private RelativeLayout homeScreen ;
	private RecyclerView favorites ;
	private RecyclerAdapter favoritesAdapter ;
	private GridLayoutManager favoritesLayout ;
	private ImageView menuButton ;
	private TextView noFavoritesYet ;
	private TextView targetFavorites ;
	private TextView targetApplications ;
	private TextView defaultLauncherButton ;
	private View infoTouchTarget ;
	private TextView homeSearchField ;
	private Search homeSearch ;
	private GestureDetector homeSearchTapDetector ;
	private ViewClock homeClock ;
	private View favoritesScroll ;
	private boolean clock_disabled ;
	private final Rect clock_content = new Rect() ;
	private String clock_alignment = "center" ;

	// Attributes related to the system bars (drawn by the app since edge-to-edge is enforced on Android 15)
	private View statusBarScrim ;
	private View navigationBarScrim ;

	// Panels currently disappearing slowly
	private boolean closing_favorites ;
	private int favorites_close_direction ;
	private boolean closing_drawer ;
	private PullToClose drawerPull ;
	private PullToClose favoritesPull ;
	private PullToClose activePull ;
	private final Rect pull_hit = new Rect() ;

	// Attributes related to the drawer
	private RecyclerView drawer ;
	private RecyclerAdapter drawerAdapter ;
	private GridLayoutManager drawerLayout ;

	// Activity Results components
	private final ActivityResultLauncher<Intent> activityResultLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> { }) ;

	
	/**
	 * Constructor.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// Let the parent actions be performed
		super.onCreate(savedInstanceState) ;

		// Initializations
		internal_folder = getApplicationContext().getFilesDir().getAbsolutePath() ;
		density = getResources().getDisplayMetrics().density ;

		// Assign default values to settings not configured yet
		PreferenceManager.setDefaultValues(this, R.xml.settings_appearance, true) ;
		PreferenceManager.setDefaultValues(this, R.xml.settings_operation, true) ;

		// Retrieve the current settings and start to listen for changes
		settings = PreferenceManager.getDefaultSharedPreferences(this) ;
		settings.registerOnSharedPreferenceChangeListener(this) ;
		skip_list_update = false ;

		// Set the light or dark theme according to settings
		setApplicationTheme() ;

		// Check if the interface should be reversed and define the appropriate layout
		reverse_interface = settings.getBoolean(Constants.REVERSE_INTERFACE, false) ;
		if(reverse_interface) setContentView(R.layout.activity_main_reverse) ;
			else setContentView(R.layout.activity_main) ;

		// Initializations related to the interface
		dialogMenu = new DialogMenu(this) ;
		homeScreen = findViewById(R.id.home_screen) ;
		favorites = findViewById(R.id.favorites) ;
		noFavoritesYet = findViewById(R.id.info_no_favorites_yet) ;
		defaultLauncherButton = findViewById(R.id.default_launcher_button) ;
		infoTouchTarget = findViewById(R.id.info_touch_targets) ;
		drawer = findViewById(R.id.drawer) ;
		menuButton = findViewById(R.id.access_menu_button) ;
		targetFavorites = findViewById(R.id.target_favorites) ;
		targetApplications = findViewById(R.id.target_applications) ;
		menuButton.setOnClickListener(this) ;
		targetFavorites.setOnClickListener(this) ;
		targetApplications.setOnClickListener(this) ;
		defaultLauncherButton.setOnClickListener(this) ;
		gestureDetector = new GestureDetectorCompat(this, new GestureListener(homeScreen)) ;

		// Draw behind the system bars and color them with views sized from the window insets
		setupSystemBars() ;

		// Prepare the search field fixed on the home screen
		setupHomeSearchField() ;

		// Starting with Android 12, disable the elastic animation on favorites and drawer
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
			{
				favorites.setOverScrollMode(View.OVER_SCROLL_NEVER) ;
				drawer.setOverScrollMode(View.OVER_SCROLL_NEVER) ;
			}

		// If it does not exist yet, build the applications list
		if(applicationsList == null)
			{
				applicationsList = new ApplicationsList() ;
				applicationsList.update(this) ;
			}

		// Prepare the notification
		notification = new NotificationDisplayer(this) ;
		if(settings.getBoolean(Constants.NOTIFICATION, true)) notification.display(this) ;
			else notification.hide() ;

		// Define the width of an application item
		int app_size_pixels = Utils.getIconSize(this, settings) ;
		int padding_pixels ;
		if(settings.getBoolean(Constants.HIDE_APP_NAMES, false))
				padding_pixels = settings.getBoolean(Constants.REMOVE_PADDING, false) ? 0 : Math.round(25 * density) ;
			else padding_pixels = Math.round(Math.min(app_size_pixels / 1.5f, 32 * density)) ;
		application_width = app_size_pixels + padding_pixels ;

		// Update the display according to settings
		maybeForceOrientation() ;
		keepMenuAccessible() ;
		toggleTouchTargets() ;
		maybeHideSystemBars(false) ;

		// Initialize the content of the favorites panel
		favoritesAdapter = new RecyclerAdapter(this, applicationsList.getFavorites(), Constants.FAVORITES_PANEL) ;
		favoritesAdapter.setTextColor(Utils.getColor(settings, Constants.TEXT_COLOR_FAVORITES, Constants.COLOR_FOR_TEXT_ON_OVERLAY)) ;
		favorites.setAdapter(favoritesAdapter) ;
		favoritesLayout = new FlexibleGridLayout(this, application_width) ;
		favorites.setLayoutManager(favoritesLayout) ;
		favorites.addOnScrollListener(new ScrollListener(Constants.FAVORITES_PANEL)) ;

		// Initialize the content of the full applications list
		drawerAdapter = new RecyclerAdapter(this, applicationsList.getDrawer(), Constants.APP_DRAWER) ;
		drawerAdapter.setTextColor(Utils.getColor(settings, Constants.TEXT_COLOR_DRAWER, Constants.COLOR_FOR_TEXT_ON_OVERLAY)) ;
		drawer.setAdapter(drawerAdapter) ;
		drawerLayout = new FlexibleGridLayout(this, application_width) ;
		drawer.setLayoutManager(drawerLayout) ;
		drawer.addOnScrollListener(new ScrollListener(Constants.APP_DRAWER)) ;

		// Organize by dragging: reorder favorites, reorder the drawer and manage folders
		DragAndDrop.attachToFavorites(favorites, favoritesAdapter, findViewById(R.id.favorites_scroll)) ;
		DragAndDrop.attachToDrawer(drawer, drawerAdapter, this::refreshLists) ;

		// Close the drawer and the favorites panel by pulling them beyond the edge of their list
		// (favorites: pulled downwards beyond the beginning of the list, available as soon as it opens).
		// The gesture is watched in dispatchTouchEvent(), before any view can keep it for itself
		drawerPull = new PullToClose(drawer, reverse_interface ? -1 : 1, () -> displayDrawer(false), drawer) ;
		View favoritesPanel = findViewById(R.id.favorites_scroll) ;
		favoritesPull = new PullToClose(favoritesPanel, 1, () -> {
				favorites_close_direction = 1 ;
				displayFavorites(false) ;
				favorites_close_direction = 0 ;
			}, favoritesPanel, favorites) ;
		applyOneHandedDrawer() ;

		// Load the favorites panel if it should always be shown
		if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false)) displayFavorites(true) ;

		// Hide the favorites panel and the drawer by default
		displayFavorites(false, false) ;
		displayDrawer(false, false) ;

		// Start to listen for packages added or removed
		packagesListener = new PackagesListener() ;
		ContextCompat.registerReceiver(this, packagesListener, packagesListener.getFilter(), ContextCompat.RECEIVER_EXPORTED) ;

		// When Android version is before Oreo, start to listen for legacy shortcut requests
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			{
				shortcutLegacyListener = new ShortcutLegacyListener() ;
				ContextCompat.registerReceiver(this, shortcutLegacyListener, shortcutLegacyListener.getFilter(), ContextCompat.RECEIVER_EXPORTED) ;
			}
	}


	/**
	 * Set the application theme to light or dark according to system settings.
	 */
	private void setApplicationTheme()
	{
		// Retrieve the selected theme (if any) and react accordingly
		String theme = settings.getString(Constants.APPLICATION_THEME, Constants.NONE) ;
		switch(theme)
		{
			case "light" :
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) ;
				break ;
			case "dark" :
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) ;
				break ;
			case Constants.NONE :
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) ;
		}
	}


	/**
	 * Maybe apply a forced orientation according to the settings.
	 */
	@SuppressLint("SourceLockedOrientationActivity")
	private void maybeForceOrientation()
	{
		// Retrieve the selected orientation and apply it
		String forced_orientation = settings.getString(Constants.FORCED_ORIENTATION, Constants.NONE) ;
		switch(forced_orientation)
		{
			case "portrait" :
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) ;
				break ;
			case "landscape" :
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) ;
				break ;
			case "reverse_landscape" :
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) ;
				break ;
			default :
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) ;
				break ;
		}
	}


	/**
	 * Enable or disable the touch targets according to the settings.
	 */
	private void toggleTouchTargets()
	{
		// Check the user preference for touch targets
		if(settings.getBoolean(Constants.TOUCH_TARGETS, false))
			{
				// Display or not touch targets according to the other settings
				infoTouchTarget.setContentDescription(null) ;
				if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false))
						targetFavorites.setVisibility(View.GONE) ;
					else targetFavorites.setVisibility(View.VISIBLE) ;
				if(settings.getBoolean(Constants.DISABLE_APP_DRAWER, false))
						targetApplications.setVisibility(View.GONE) ;
					else targetApplications.setVisibility(View.VISIBLE) ;
			}
			else
			{
				// Hide touch targets
				targetFavorites.setVisibility(View.GONE) ;
				targetApplications.setVisibility(View.GONE) ;
				infoTouchTarget.setContentDescription(getString(R.string.help_touch_targets)) ;
			}
	}


	/**
	 * Make sure that the launcher menu always stays accessible.
	 */
	private void keepMenuAccessible()
	{
		// Do not continue if none of the risky settings is enabled
		if(!(settings.getBoolean(Constants.DISABLE_APP_DRAWER, false) ||
				settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false))) return ;

		// Check if the menu button is visible
		if(!settings.getBoolean(Constants.HIDE_MENU_BUTTON, false)) return ;

		// Browse all favorites
		String launcher = "{com.vincent_falzon.discreetlauncher/com.vincent_falzon.discreetlauncher.ActivityMain}" ;
		for(Application application : applicationsList.getFavorites())
		{
			// Search if the launcher icon or Search app is in favorites
			if(application instanceof Search) return ;
				else if(application.getComponentInfo().equals(launcher)) return ;
				else if(application instanceof Folder)
				{
					// Also search the launcher icon in folders
					for(Application folder_application : ((Folder)application).getApplications())
						if(folder_application.getComponentInfo().equals(launcher)) return ;
				}
		}

		// If the drawer cannot be safely disabled, display a message and disable the setting
		if(settings.getBoolean(Constants.DISABLE_APP_DRAWER, false))
			{
				Utils.displayLongToast(this, getString(R.string.error_disable_app_drawer_not_safe)) ;
				SharedPreferences.Editor editor = settings.edit() ;
				editor.putBoolean(Constants.DISABLE_APP_DRAWER, false).apply() ;
				return ;
			}

		// Retrieve the total height available in portrait mode (navigation bar automatically removed)
		int menu_button_height = Math.round(32 * density) ;
		int total_size = Math.max(getResources().getDisplayMetrics().heightPixels, getResources().getDisplayMetrics().widthPixels)
				- Math.round(25 * density)	// Remove 25dp for the status bar
				- Math.round(20 * density)  // Remove 20dp for button margins and spare
				- menu_button_height ;

		// Define the size of an app (icon + margins + text estimation) and the maximum number of favorites
		int app_size = Utils.getIconSize(this, settings) ;
		if(!settings.getBoolean(Constants.REMOVE_PADDING, false)) app_size += Math.round(20 * density) ;
		if(!settings.getBoolean(Constants.HIDE_APP_NAMES, false)) app_size += menu_button_height ;
		int max_favorites = Math.min(4, getResources().getDisplayMetrics().widthPixels / application_width) * (total_size / app_size) ;

		// Check if the number of favorites still allows to see the menu button
		if(applicationsList.getFavorites().size() <= max_favorites) return ;

		// If favorites cannot be always shown safely, display a message and disable the setting
		if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false))
			{
				Utils.displayLongToast(this, getString(R.string.error_always_show_favorites_not_safe)) ;
				SharedPreferences.Editor editor = settings.edit() ;
				editor.putBoolean(Constants.ALWAYS_SHOW_FAVORITES, false).apply() ;
			}
	}


	/**
	 * Watch every touch before the views: once a panel is pulled beyond the edge of its list, the
	 * views receive a cancellation and the gesture only moves the panel. No list, ScrollView or pressed
	 * element can keep the gesture for itself (which left the favorites stuck half pulled).
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent event)
	{
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN :
				activePull = choosePull(event) ;
				break ;
			case MotionEvent.ACTION_POINTER_DOWN :
				// Two fingers: not a pull
				if(activePull != null) activePull.abort() ;
				activePull = null ;
				break ;
		}

		if(activePull != null)
			{
				if(activePull.isPulling())
					{
						activePull.handleTouch(event) ;
						if(!activePull.isPulling()) activePull = null ;
						return true ;
					}
				// An element picked up by a long press is moved by the finger, not the panel
				if(!RecyclerAdapter.isPicking() && activePull.shouldIntercept(event))
					{
						MotionEvent cancel = MotionEvent.obtain(event) ;
						cancel.setAction(MotionEvent.ACTION_CANCEL) ;
						super.dispatchTouchEvent(cancel) ;
						cancel.recycle() ;
						return true ;
					}
			}
		return super.dispatchTouchEvent(event) ;
	}


	/**
	 * Return the pull gesture of the open panel under the finger (null if none).
	 */
	private PullToClose choosePull(MotionEvent event)
	{
		PullToClose pull = null ;
		if((drawerPull != null) && (drawer.getVisibility() == View.VISIBLE) && !closing_drawer && isUnder(drawer, event))
			pull = drawerPull ;
		else if((favoritesPull != null) && (favorites.getVisibility() == View.VISIBLE) && !closing_favorites
				&& !settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false) && isUnder(favoritesPull.getPanel(), event))
			pull = favoritesPull ;
		if(pull != null) pull.shouldIntercept(event) ;
		return pull ;
	}


	/**
	 * Check if the touch is on the given view.
	 */
	private boolean isUnder(View view, MotionEvent event)
	{
		if(!view.getGlobalVisibleRect(pull_hit)) return false ;
		return pull_hit.contains((int)event.getX(), (int)event.getY()) ;
	}


	/**
	 * Display or hide the favorites panel.
	 */
	private void displayFavorites(boolean display)
	{
		displayFavorites(display, true) ;
	}


	/**
	 * Display or hide the favorites panel, with or without the slow animation.
	 */
	private void displayFavorites(boolean display, boolean animate)
	{
		boolean was_visible = (favorites.getVisibility() == View.VISIBLE) && !closing_favorites ;
		if(display)
			{
				closing_favorites = false ;
				// Update the recyclers (favorites panel and applications drawer) if needed
				if(adapters_update_needed) updateAdapters() ;

				// Starting with Android 10, maybe display the default launcher button
				boolean show_default_launcher_button = false ;
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
					{
						RoleManager roleManager = (RoleManager)getSystemService(Context.ROLE_SERVICE) ;
						if(roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME))
							show_default_launcher_button = true ;
					}
				defaultLauncherButton.setVisibility(show_default_launcher_button ? View.VISIBLE : View.GONE) ;

				// Display a message if the user does not have any favorites applications yet
				if(applicationsList.getFavorites().size() == 0) noFavoritesYet.setVisibility(View.VISIBLE) ;
					else noFavoritesYet.setVisibility(View.GONE) ;

				// Retrieve the background color
				int background_color = Utils.getColor(settings, Constants.BACKGROUND_COLOR_FAVORITES, Constants.COLOR_FOR_OVERLAY) ;

				// Check if the interface is reversed and adjust the display accordingly
				Drawable tab_shape ;
				if(reverse_interface)
					{
						// Reversed interface
						tab_shape = AppCompatResources.getDrawable(this, R.drawable.shape_tab_reverse) ;
						setNavigationBarColor(background_color) ;
					}
					else
					{
						// Classic interface
						tab_shape = AppCompatResources.getDrawable(this, R.drawable.shape_tab) ;
						setStatusBarColor(background_color) ;
					}

				// Color the menu button and favorites panel
				Drawable menuButtonBackground ;
				if(tab_shape != null)
					{
						menuButtonBackground = DrawableCompat.wrap(tab_shape) ;
						menuButtonBackground.setTint(background_color) ;
						menuButton.setBackground(DrawableCompat.unwrap(menuButtonBackground)) ;
					}
				favorites.setBackgroundColor(background_color) ;
				noFavoritesYet.setBackgroundColor(background_color) ;

				// If the option is selected, hide the menu button
				if(settings.getBoolean(Constants.HIDE_MENU_BUTTON, false)) menuButton.setVisibility(View.GONE) ;
					else menuButton.setVisibility(View.VISIBLE) ;

				// Display the favorites panel
				scroll_position = 0 ;
				scroll_last_position = 0 ;
				scroll_close_gesture = 0 ;
				favorites.setVisibility(View.VISIBLE) ;
				targetFavorites.setText(R.string.target_close_favorites) ;

				// Slow appearance from its side of the screen (top, or bottom in reversed interface)
				if(!was_visible)
					{
						Utils.slowAppear(favoritesScroll, reverse_interface ? 1 : -1) ;
						Utils.slowAppear(reverse_interface ? navigationBarScrim : statusBarScrim, 0) ;
					}
			}
			else
			{
				// Do not continue if the option to always show the panel is selected
				if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false)) return ;

				// Disappear slowly towards its side of the screen, then hide (or hide at once)
				if(animate && was_visible)
					{
						closing_favorites = true ;
						Utils.slowDisappear(reverse_interface ? navigationBarScrim : statusBarScrim, 0, null) ;
						// Towards its side of the screen, or in the direction of the pull that closed it
						int direction = (favorites_close_direction != 0) ? favorites_close_direction : (reverse_interface ? 1 : -1) ;
						Utils.slowDisappear(favoritesScroll, direction, () -> {
								if(closing_favorites) hideFavorites() ;
							}) ;
					}
					else if(!closing_favorites || !animate) hideFavorites() ;
			}
	}


	/**
	 * Hide the favorites panel at once.
	 */
	private void hideFavorites()
	{
		closing_favorites = false ;
		favorites.setVisibility(View.GONE) ;
		menuButton.setVisibility(View.GONE) ;
		noFavoritesYet.setVisibility(View.GONE) ;
		defaultLauncherButton.setVisibility(View.GONE) ;
		targetFavorites.setText(R.string.target_open_favorites) ;

		// If the option is selected, make the status bar fully transparent
		if(settings.getBoolean(Constants.TRANSPARENT_STATUS_BAR, true))
				setStatusBarColor(ContextCompat.getColor(this, R.color.transparent)) ;
			else setStatusBarColor(Utils.getColor(settings, Constants.BACKGROUND_COLOR_FAVORITES, Constants.COLOR_FOR_OVERLAY)) ;

		// Make the navigation bar transparent
		setNavigationBarColor(ContextCompat.getColor(this, R.color.transparent)) ;

		// Restore the normal state of the animated views
		Utils.resetAppear(favoritesScroll) ;
		Utils.resetAppear(statusBarScrim) ;
		Utils.resetAppear(navigationBarScrim) ;
	}


	/**
	 * Display or hide the applications drawer.
	 */
	private void displayDrawer(boolean display)
	{
		displayDrawer(display, true) ;
	}


	/**
	 * Display or hide the applications drawer, with or without the slow animation.
	 */
	private void displayDrawer(boolean display, boolean animate)
	{
		if(display)
			{
				// Do not continue if the drawer has been disabled
				if(settings.getBoolean(Constants.DISABLE_APP_DRAWER, false)) return ;

				// Update the recyclers (favorites panel and applications drawer) if needed
				if(adapters_update_needed) updateAdapters() ;

				// Color the system bars and the drawer background
				int background_color = Utils.getColor(settings, Constants.BACKGROUND_COLOR_DRAWER, Constants.COLOR_FOR_OVERLAY) ;
				drawer.setBackgroundColor(background_color) ;
				setStatusBarColor(background_color) ;
				setNavigationBarColor(background_color) ;

				// Display the applications drawer
				if(reverse_interface) scroll_position = applicationsList.getDrawer().size() - 1 ;
					else scroll_position = 0 ;
				scroll_last_position = scroll_position ;
				scroll_close_gesture = 0 ;
				boolean drawer_was_visible = (drawer.getVisibility() == View.VISIBLE) && !closing_drawer ;
				closing_drawer = false ;
				Utils.resetAppear(homeScreen) ;
				homeScreen.setVisibility(View.GONE) ;
				drawer.setVisibility(View.VISIBLE) ;
				targetApplications.setText(R.string.target_close_apps) ;

				// Slow appearance from its side of the screen (bottom, or top in reversed interface)
				if(!drawer_was_visible)
					{
						Utils.slowAppear(drawer, reverse_interface ? -1 : 1) ;
						Utils.slowAppear(statusBarScrim, 0) ;
						Utils.slowAppear(navigationBarScrim, 0) ;
					}
			}
			else
			{
				// Disappear slowly towards its side of the screen, then hide (or hide at once)
				boolean drawer_visible = (drawer.getVisibility() == View.VISIBLE) && !closing_drawer ;
				if(animate && drawer_visible)
					{
						closing_drawer = true ;
						Utils.slowDisappear(statusBarScrim, 0, null) ;
						Utils.slowDisappear(navigationBarScrim, 0, null) ;
						Utils.slowDisappear(drawer, reverse_interface ? -1 : 1, () -> {
								if(closing_drawer) hideDrawer(true) ;
							}) ;
					}
					else if(!closing_drawer || !animate) hideDrawer(false) ;
			}
	}


	/**
	 * Hide the applications drawer at once (the home screen then appears slowly if requested).
	 */
	private void hideDrawer(boolean home_appears_slowly)
	{
		closing_drawer = false ;
		if(reverse_interface) drawer.scrollToPosition(applicationsList.getDrawer().size() - 1) ;
			else drawer.scrollToPosition(0) ;
		homeScreen.setVisibility(View.VISIBLE) ;
		if(home_appears_slowly) Utils.slowAppear(homeScreen, 0) ;
			else Utils.resetAppear(homeScreen) ;
		drawer.setVisibility(View.GONE) ;
		targetApplications.setText(R.string.target_open_apps) ;

		// Retrieve the background color for favorites
		int favorites_background_color = Utils.getColor(settings, Constants.BACKGROUND_COLOR_FAVORITES, Constants.COLOR_FOR_OVERLAY) ;

		// If the option is selected, make the status bar fully transparent
		if(settings.getBoolean(Constants.TRANSPARENT_STATUS_BAR, true))
				setStatusBarColor(ContextCompat.getColor(this, R.color.transparent)) ;
			else setStatusBarColor(favorites_background_color) ;

		// Make the navigation bar transparent, unless in reverse interface with favorites always shown
		if(reverse_interface && settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false))
				setNavigationBarColor(favorites_background_color) ;
			else setNavigationBarColor(ContextCompat.getColor(this, R.color.transparent)) ;

		// Restore the normal state of the animated views
		Utils.resetAppear(drawer) ;
		Utils.resetAppear(statusBarScrim) ;
		Utils.resetAppear(navigationBarScrim) ;
	}


	/**
	 * Draw the interface behind the system bars and size the bar backgrounds from the window insets.
	 * Starting with Android 15, the system no longer colors the bars for apps targeting API 35.
	 */
	@SuppressWarnings("deprecation")
	private void setupSystemBars()
	{
		// Let the app handle the system bars areas on all Android versions for a consistent display
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false) ;
		getWindow().setStatusBarColor(Color.TRANSPARENT) ;
		getWindow().setNavigationBarColor(Color.TRANSPARENT) ;

		// Size the scrims according to the system bars and display cutout
		statusBarScrim = findViewById(R.id.status_bar_scrim) ;
		navigationBarScrim = findViewById(R.id.navigation_bar_scrim) ;
		View root = (View)statusBarScrim.getParent() ;
		ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
				// Use stable insets so that the layout does not jump when immersive bars appear
				Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()) ;
				Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()) ;
				Insets total = Insets.max(bars, cutout) ;
				setHeight(statusBarScrim, total.top) ;
				setHeight(navigationBarScrim, total.bottom) ;
				view.setPadding(total.left, 0, total.right, 0) ;
				return WindowInsetsCompat.CONSUMED ;
			}) ;
	}


	/**
	 * Set the height of a view in pixels.
	 */
	private static void setHeight(View view, int height)
	{
		ViewGroup.LayoutParams params = view.getLayoutParams() ;
		if(params.height == height) return ;
		params.height = height ;
		view.setLayoutParams(params) ;
	}


	/**
	 * Color the status bar area.
	 */
	private void setStatusBarColor(int color)
	{
		statusBarScrim.setBackgroundColor(color) ;
	}


	/**
	 * Color the navigation bar area.
	 */
	private void setNavigationBarColor(int color)
	{
		navigationBarScrim.setBackgroundColor(color) ;
	}


	/**
	 * Prepare the search field fixed on the home screen.
	 * A tap opens the search (and the keyboard), swipes keep working as on the rest of the home screen.
	 */
	@SuppressLint("ClickableViewAccessibility")
	private void setupHomeSearchField()
	{
		homeSearchField = findViewById(R.id.home_search_field) ;
		homeClock = findViewById(R.id.home_clock) ;
		favoritesScroll = findViewById(R.id.favorites_scroll) ;
		homeSearch = new Search(getString(R.string.search_app_name), null) ;

		// Follow the clock: its content area defines where the field goes
		homeClock.setContentListener((disabled, content, alignment) -> {
				clock_disabled = disabled ;
				clock_content.set(content) ;
				clock_alignment = alignment ;
				positionHomeSearchField() ;
			}) ;

		// Update the reserved space and position when the field or the home screen size changes
		homeSearchField.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				if((bottom - top) != (oldBottom - oldTop) || (right - left) != (oldRight - oldLeft))
					{
						updateClockBottomReserve() ;
						positionHomeSearchField() ;
					}
			}) ;
		homeScreen.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				if((bottom - top) != (oldBottom - oldTop) || (right - left) != (oldRight - oldLeft))
					positionHomeSearchField() ;
			}) ;
		homeSearchTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener()
			{
				@Override
				public boolean onSingleTapUp(@NonNull MotionEvent event)
				{
					return true ;
				}
			}) ;
		homeSearchField.setOnClickListener(view -> homeSearch.start(homeSearchField)) ;
		homeSearchField.setOnTouchListener((view, event) -> {
				// Forward the event to the home screen gestures (swipes, double tap)
				gestureDetector.onTouchEvent(event) ;

				// Open the search only on a single tap
				if(homeSearchTapDetector.onTouchEvent(event)) view.performClick() ;
				return true ;
			}) ;
		updateHomeSearchField() ;
	}


	/**
	 * For one-handed use with the right hand, fill the rows of the app drawer from the right.
	 */
	private void applyOneHandedDrawer()
	{
		boolean right_hand = settings.getString(Constants.ONE_HANDED, "off").equals("right") ;
		drawer.setLayoutDirection(right_hand ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR) ;
	}


	/**
	 * Show or hide the search field according to the settings, and give it the clock colors.
	 */
	private void updateHomeSearchField()
	{
		if(homeSearchField == null) return ;
		boolean display = settings.getBoolean(Constants.HOME_SEARCH_FIELD, true) ;
		homeSearchField.setVisibility(display ? View.VISIBLE : View.GONE) ;

		// Use the clock colors (text, magnifying glass and shadow)
		// (the transparency is applied to the colors, not to the view, so that the text shadow is not clipped)
		int clock_color = Utils.getColor(settings, Constants.CLOCK_COLOR, Constants.COLOR_FOR_TEXT_ON_OVERLAY) ;
		clock_color = Color.argb(Math.round(Color.alpha(clock_color) * 0.6f), Color.red(clock_color), Color.green(clock_color), Color.blue(clock_color)) ;
		int shadow_color = Utils.getColor(settings, Constants.CLOCK_SHADOW_COLOR, Constants.COLOR_FOR_OVERLAY) ;
		homeSearchField.setTextColor(clock_color) ;
		homeSearchField.setShadowLayer(getResources().getDimension(R.dimen.spacing_normal), 0, 0, shadow_color) ;
		TextViewCompat.setCompoundDrawableTintList(homeSearchField, ColorStateList.valueOf(clock_color)) ;

		// Keep space under the clock when it is at the bottom, then place the field
		updateClockBottomReserve() ;
		homeClock.invalidate() ;
		positionHomeSearchField() ;
	}


	/**
	 * Ask the clock to keep space for the search field when it is displayed.
	 */
	private void updateClockBottomReserve()
	{
		int reserve = 0 ;
		if(homeSearchField.getVisibility() == View.VISIBLE) reserve = homeSearchField.getHeight() ;
		homeClock.setBottomReserve(reserve) ;
	}


	/**
	 * Place the search field under the clock, or at the bottom of the home screen without clock.
	 */
	private void positionHomeSearchField()
	{
		// Do not continue if the field is hidden or not measured yet
		if(homeSearchField.getVisibility() != View.VISIBLE) return ;
		int field_width = homeSearchField.getWidth() ;
		int field_height = homeSearchField.getHeight() ;
		int home_width = homeScreen.getWidth() ;
		int home_height = homeScreen.getHeight() ;
		if((field_width == 0) || (home_height == 0))
			{
				homeSearchField.post(this::positionHomeSearchField) ;
				return ;
			}

		float x ;
		float y ;
		if(clock_disabled || clock_content.isEmpty())
			{
				// Without clock, at the bottom of the home screen (above the favorites in reversed interface)
				int bottom_limit = home_height ;
				if(reverse_interface && (favoritesScroll.getHeight() > 0)) bottom_limit = Math.min(bottom_limit, favoritesScroll.getTop()) ;
				x = (home_width - field_width) / 2f ;
				y = bottom_limit - field_height ;
			}
			else
			{
				// Under the clock, aligned like the clock (the field padding is kept outside the alignment)
				if(clock_alignment.equals("left")) x = clock_content.left - homeSearchField.getPaddingStart() ;
					else if(clock_alignment.equals("right")) x = clock_content.right - field_width + homeSearchField.getPaddingEnd() ;
					else x = clock_content.centerX() - field_width / 2f ;
				y = clock_content.bottom ;
			}

		// Keep the field inside the home screen
		homeSearchField.setX(Math.max(0, Math.min(x, home_width - field_width))) ;
		homeSearchField.setY(Math.max(0, Math.min(y, home_height - field_height))) ;
	}


	/**
	 * Display or hide the system bars based on settings, also sets and unsets the dark icons.
	 */
	private void maybeHideSystemBars(boolean force_display)
	{
		// Check if the status bar icons must be displayed darker
		int dark_icons_flag = 0 ;
		if(settings.getBoolean(Constants.DARK_STATUS_BAR_ICONS, false))
			{
				// Use dark icons wherever it is possible
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) dark_icons_flag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR ;
					else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) dark_icons_flag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR ;
			}

		// Check if the system bars must be hidden (immersive mode)
		int immersive_mode_flag = View.SYSTEM_UI_FLAG_LAYOUT_STABLE ;
		int layout_flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION ;
		if(!force_display && settings.getBoolean(Constants.IMMERSIVE_MODE, false))
			immersive_mode_flag = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION ;

		// Update the system bars flags
		getWindow().getDecorView().setSystemUiVisibility(layout_flags | immersive_mode_flag | dark_icons_flag) ;
	}


	/**
	 * Return the list of applications.
	 */
	public static ApplicationsList getApplicationsList()
	{
		return applicationsList ;
	}


	/**
	 * Allow to skip updates of the list of applications when editing some settings.
	 */
	public static void setSkipListUpdate(boolean skip)
	{
		skip_list_update = skip ;
	}


	/**
	 * Return the internal files location (<code>null </code> if not initialized by ActivityMain).
	 */
	public static String getInternalFolder()
	{
		return internal_folder ;
	}


	/**
	 * Return the application width in pixels (0 if not initialized by ActivityMain).
	 */
	public static int getApplicationWidth()
	{
		return application_width ;
	}


	/**
	 * Update the list of favorite applications (provide <code>null</code> to hide the message).
	 */
	public static void updateFavorites(Context context)
	{
		applicationsList.updateFavorites() ;
		adapters_update_needed = true ;
		if(context != null)
			Utils.displayToast(context, R.string.info_favorites_refreshed) ;
	}


	/**
	 * Update the applications list and inform the user.
	 */
	public static void updateList(Context context)
	{
		if(skip_list_update) return ;
		applicationsList.update(context) ;
		adapters_update_needed = true ;
		Utils.displayToast(context, R.string.info_list_apps_refreshed) ;
	}


	/**
	 * Rebuild the applications list and refresh the display at once (after organizing by dragging).
	 */
	private void refreshLists()
	{
		applicationsList.update(this) ;
		updateAdapters() ;
	}


	/**
	 * Update the display in the favorites panel and applications drawer.
	 */
	@SuppressLint("NotifyDataSetChanged")
	private void updateAdapters()
	{
		favoritesAdapter.notifyDataSetChanged() ;
		drawerAdapter.notifyDataSetChanged() ;
		adapters_update_needed = false ;
	}


	/**
	 * Called when an element is clicked.
	 */
	public void onClick(View view)
	{
		// Check which view was selected and react accordingly
		int selection = view.getId() ;
		if(selection == R.id.access_menu_button) dialogMenu.show() ;
			else if(selection == R.id.target_favorites) displayFavorites(favorites.getVisibility() != View.VISIBLE) ;
			else if(selection == R.id.target_applications) displayDrawer(drawer.getVisibility() != View.VISIBLE) ;
			else if(selection == R.id.default_launcher_button)
			{
				// Starting with Android 10, display the list of available launchers
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
					{
						RoleManager roleManager = (RoleManager)getSystemService(Context.ROLE_SERVICE) ;
						activityResultLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) ;
					}
			}
	}


	/**
	 * Detect a user action on the screen and process it.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		gestureDetector.onTouchEvent(event) ;
		return super.onTouchEvent(event) ;
	}


	/**
	 * Called when displaying the home screen, including on app creation.
	 */
	@Override
	public void onResume()
	{
		// Let the parent actions be performed
		super.onResume() ;

		// Hide the favorites panel and the applications drawer
		keepMenuAccessible() ;
		displayFavorites(false, false) ;
		displayDrawer(false, false) ;

		// Update the display according to settings
		dialogMenu.hide() ;
		maybeForceOrientation() ;
		toggleTouchTargets() ;
		maybeHideSystemBars(false) ;
		updateHomeSearchField() ;
		if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false)) displayFavorites(true) ;

		// Update the favorites panel and applications drawer display if needed
		if(adapters_update_needed) updateAdapters() ;
	}


	/**
	 * Close the app drawer or favorites if opened, otherwise do nothing.
	 */
	@Override
	public void onBackPressed()
	{
		if(drawer.getVisibility() == View.VISIBLE) displayDrawer(false) ;
			else if(favorites.getVisibility() == View.VISIBLE) displayFavorites(false) ;
	}


	/**
	 * Called before leaving the home screen.
	 */
	@Override
	public void onPause()
	{
		// Let the parent actions be performed
		super.onPause() ;

		// Always show the system bars
		maybeHideSystemBars(true) ;

		// Hide popups if some are still opened
		if(homeSearch != null) homeSearch.closePopupNow() ;
		for(Application application : applicationsList.getDrawer())
		{
			if(application instanceof Folder) ((Folder)application).closePopupNow() ;
			if(application instanceof Search) ((Search)application).closePopupNow() ;
		}
	}


	/**
	 * Listen for changes in the settings and react accordingly.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if(key == null) return ;
		Utils.logDebug(TAG, "preference \"" + key + "\" has changed") ;
		switch(key)
		{
			// ========= Appearance settings ==========
			case Constants.APPLICATION_THEME :
				// Update the theme
				setApplicationTheme() ;
				break ;
			case Constants.ICON_SIZE_DP :
				// Update the applications list and column width
				updateList(this) ;
				recreate() ;
				break ;
			case Constants.HIDE_APP_NAMES :
			case Constants.HIDE_FOLDER_NAMES :
			case Constants.REMOVE_PADDING :
				// Update the column width
				recreate() ;
				break ;
			case Constants.TEXT_COLOR_FAVORITES :
				// Update the text color of the favorites panel
				favoritesAdapter.setTextColor(Utils.getColor(settings, Constants.TEXT_COLOR_FAVORITES, Constants.COLOR_FOR_TEXT_ON_OVERLAY)) ;
				break ;
			case Constants.TEXT_COLOR_DRAWER :
				// Update the text color of the drawer
				drawerAdapter.setTextColor(Utils.getColor(settings, Constants.TEXT_COLOR_DRAWER, Constants.COLOR_FOR_TEXT_ON_OVERLAY)) ;
				break ;
			case Constants.ICON_PACK :
			case Constants.ICON_PACK_SECONDARY :
			case Constants.ICON_COLOR_FILTER :
				// Update the applications list
				updateList(this) ;
				break ;
			// ========= Operation settings ==========
			case Constants.NOTIFICATION :
				// Toggle the notification
				if(settings.getBoolean(Constants.NOTIFICATION, true))
					{
						if(notification.isAllowed()) notification.display(this) ;
							else Utils.displayLongToast(this, getString(R.string.set_notification_warning_disabled)) ;
					}
					else notification.hide() ;
				break ;
			case Constants.HOME_SEARCH_FIELD :
			case Constants.CLOCK_COLOR :
			case Constants.CLOCK_SHADOW_COLOR :
			case Constants.CLOCK_FORMAT :
			case Constants.CLOCK_POSITION :
			case Constants.CLOCK_SIZE :
				// Show or hide the search field on the home screen
				updateHomeSearchField() ;
				break ;
			case Constants.ONE_HANDED :
				// Update the direction of the app drawer rows (the search reads it when opening)
				applyOneHandedDrawer() ;
				break ;
			case Constants.REVERSE_INTERFACE :
				// Change the interface direction
				reverse_interface = settings.getBoolean(Constants.REVERSE_INTERFACE, false) ;
				if(reverse_interface) setContentView(R.layout.activity_main_reverse) ;
					else setContentView(R.layout.activity_main) ;
				recreate() ;
				updateList(this) ;
				break ;
		}
	}


	/**
	 * Called when this activity is destroyed.
	 */
	@Override
	public void onDestroy()
	{
		// Unregister all remaining broadcast receivers
		if(packagesListener != null) unregisterReceiver(packagesListener) ;
		if(shortcutLegacyListener != null) unregisterReceiver(shortcutLegacyListener) ;
		settings.unregisterOnSharedPreferenceChangeListener(this) ;
		activityResultLauncher.unregister() ;

		// Let the parent actions be performed
		super.onDestroy() ;
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Detect and recognize a gesture on the home screen.
	 */
	class GestureListener extends GestureDetector.SimpleOnGestureListener
	{
		// Attributes
		private final View homeScreen ;


		/**
		 * Constructor.
		 */
		GestureListener(View homeScreen)
		{
			this.homeScreen = homeScreen ;
		}


		/**
		 * Implemented because all gestures start with an onDown() message.
		 */
		@Override
		public boolean onDown(@NonNull MotionEvent event)
		{
			return true ;
		}


		/**
		 * Detect a gesture over a distance.
		 */
		@Override
		public boolean onFling(MotionEvent event1, @NonNull MotionEvent event2, float velocityX, float velocityY)
		{
			// Ignore the gesture if the applications drawer is opened
			if(drawer.getVisibility() == View.VISIBLE) return false ;

			// Ignore incomplete gestures (may happen due to the interactive clock)
			if(event1 == null) return false ;

			// Calculate the traveled distances on both axes
			float x_distance = event1.getX() - event2.getX() ;
			float y_distance = event1.getY() - event2.getY() ;

			// Check if this is a vertical gesture over a distance and not a single tap
			int swipe_trigger_distance = Math.round(34 * density) ;
			if((Math.abs(y_distance) > Math.abs(x_distance)) && (Math.abs(y_distance) > swipe_trigger_distance))
				{
					// Adapt the gesture direction to the interface direction
					boolean swipe_drawer ;
					if(reverse_interface) swipe_drawer = y_distance < 0 ;
						else swipe_drawer = y_distance > 0 ;

					// Check if the favorites panel should always be shown
					if(settings.getBoolean(Constants.ALWAYS_SHOW_FAVORITES, false) && swipe_drawer)
						{
							displayDrawer(true) ;
							return true ;
						}

					// Check if the gesture is going up (if) or down (else), based on classic interface
					if(swipe_drawer)
						{
							// Display the applications drawer only if the favorites panel is closed
							if(favorites.getVisibility() == View.VISIBLE) displayFavorites(false) ;
								else displayDrawer(true) ;
						}
						else displayFavorites(true) ;

					// Indicate that the event has been consumed
					return true ;
				}

			// Check if this is an horizontal gesture over a distance and not a single tap
			if((Math.abs(x_distance) > Math.abs(y_distance)) && (Math.abs(x_distance) > swipe_trigger_distance))
				{
					// Check if the swipe is going towards left or right
					String swipe_direction_setting_key ;
					if(x_distance > 0) swipe_direction_setting_key = Constants.SWIPE_LEFTWARDS ;
						else swipe_direction_setting_key = Constants.SWIPE_RIGHTWARDS ;

					// Try to start the related application
					return Utils.searchAndStartApplication(homeScreen, settings, swipe_direction_setting_key) ;
				}

			// Ignore other gestures
			return false ;
		}


		/**
		 * Detect a double-tap.
		 */
		@Override
		public boolean onDoubleTap(@NonNull MotionEvent event)
		{
			return Utils.searchAndStartApplication(homeScreen, settings, Constants.DOUBLE_TAP) ;
		}


		/**
		 * Detect a long click on the home screen.
		 */
		@Override
		public void onLongPress(@NonNull MotionEvent event)
		{
			// Update the display according to settings
			maybeHideSystemBars(false) ;
		}
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Listen for scrolls on the app drawer or the favorites panel.
	 */
	class ScrollListener extends RecyclerView.OnScrollListener
	{
		// Attributes
		private final int target ;


		/**
		 * Constructor (target says if we listen on the app drawer or the favorites panel).
		 */
		ScrollListener(int target)
		{
			this.target = target ;
		}


		/**
		 * When the scrolling ends, check if it is stuck on top/bottom.
		 */
		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState)
		{
			// Let the parent actions be performed
			super.onScrollStateChanged(recyclerView, newState) ;

			// Keep track of the state to limit accidental closures
			switch(newState)
			{
				case RecyclerView.SCROLL_STATE_DRAGGING : // Active scrolling
					if(scroll_close_gesture == 0) scroll_close_gesture = 1 ;
						else scroll_close_gesture = 0 ;
					break ;
				case RecyclerView.SCROLL_STATE_SETTLING : // Scrolling inerty
					if(scroll_close_gesture == 1) scroll_close_gesture = 2 ;
						else scroll_close_gesture = 0 ;
					break ;
				case RecyclerView.SCROLL_STATE_IDLE : // Not scrolling
					if(scroll_close_gesture == 2) scroll_close_gesture = 3 ;
						else scroll_close_gesture = 0 ;
			}

			// Wait for the gesture to be finished
			if(newState == RecyclerView.SCROLL_STATE_IDLE)
				{
					// (Closing by scrolling is now done by pulling the panel beyond the edge, see PullToClose)

					// Update the last position to detect the stuck state
					scroll_last_position = scroll_position ;
				}
		}


		/**
		 * Update the position of the first/last visible item as the user is scrolling.
		 */
		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
		{
			// Let the parent actions be performed
			super.onScrolled(recyclerView, dx, dy) ;

			// Check if we are listening the scroll of the favorites panel or the app drawer
			if(target == Constants.FAVORITES_PANEL)
				{
					// Update the position of the first visible item
					scroll_position = favoritesLayout.findFirstCompletelyVisibleItemPosition() ;
				}
				else
				{
					// Update the position of the last/first visible item (based on layout)
					if(reverse_interface) scroll_position = drawerLayout.findLastCompletelyVisibleItemPosition() ;
						else scroll_position = drawerLayout.findFirstCompletelyVisibleItemPosition() ;
				}
		}
	}
}
