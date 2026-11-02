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
import android.content.ActivityNotFoundException ;
import android.content.Context ;
import android.content.Intent ;
import androidx.annotation.NonNull ;
import androidx.appcompat.app.AlertDialog ;
import androidx.core.content.ContextCompat ;
import androidx.preference.PreferenceManager ;
import androidx.recyclerview.widget.RecyclerView ;
import android.content.SharedPreferences ;
import android.content.pm.PackageManager ;
import android.content.pm.ResolveInfo ;
import android.graphics.PorterDuff ;
import android.net.Uri ;
import android.view.LayoutInflater ;
import android.view.MotionEvent ;
import android.view.View ;
import android.view.ViewGroup ;
import android.view.inputmethod.EditorInfo ;
import android.widget.EditText ;
import android.widget.TextView ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.core.UsageTracker ;
import com.vincent_falzon.discreetlauncher.core.Folder ;
import com.vincent_falzon.discreetlauncher.core.Search ;
import com.vincent_falzon.discreetlauncher.core.Shortcut ;
import com.vincent_falzon.discreetlauncher.events.ShortcutListener ;
import com.vincent_falzon.discreetlauncher.menu.DialogHiddenApps ;
import com.vincent_falzon.discreetlauncher.storage.InternalFile ;
import com.vincent_falzon.discreetlauncher.storage.InternalFileTXT ;
import java.util.ArrayList ;

/**
 * Fill a RecyclerView with a list of applications.
 */
public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ApplicationView>
{
	// An element is currently picked up by a long press (the finger moves it, not the panel)
	private static boolean picking = false ;

	/**
	 * Return <code>true</code> while an element is picked up by a long press.
	 */
	static boolean isPicking()
	{
		return picking ;
	}

	// Constants
	private static final String TAG = "RecyclerAdapter" ;

	// Attributes
	ArrayList<Application> applicationsList ;
	private final boolean hide_app_names ;
	private final boolean hide_folder_names ;
	private final boolean remove_padding ;
	private final int padding ;
	private final int target ;
	private int text_color ;
	private Runnable beforeLaunch ;

	// Attributes related to dragging (favorites, drawer and folders)
	private Folder ownerFolder ;
	private Application dragged ;


	/**
	 * Define the folder displayed by this adapter (when the target is a folder popup).
	 */
	public void setOwnerFolder(Folder folder)
	{
		ownerFolder = folder ;
	}


	/**
	 * Return true if the elements of this list can be dragged.
	 */
	boolean isDragEnabled()
	{
		return (target == Constants.FAVORITES_PANEL) || (target == Constants.APP_DRAWER) || (target == Constants.FOLDER) ;
	}


	/**
	 * Return the list displayed by this adapter.
	 */
	public ArrayList<Application> getApplicationsList()
	{
		return applicationsList ;
	}


	/**
	 * Return the type of list displayed by this adapter.
	 */
	public int getTarget()
	{
		return target ;
	}


	/**
	 * Mark the element being dragged (displayed faded at its place), or null when the drag is over.
	 */
	@SuppressLint("NotifyDataSetChanged")
	public void setDragged(Application application)
	{
		dragged = application ;
		notifyDataSetChanged() ;
	}


	/**
	 * Action to perform just before an application is launched from this adapter (ex: close a popup).
	 */
	public void setBeforeLaunch(Runnable action)
	{
		beforeLaunch = action ;
	}


	/**
	 * Perform the action defined to be done before an application is launched (if any).
	 */
	void runBeforeLaunch(Application application)
	{
		if((beforeLaunch != null) && !(application instanceof com.vincent_falzon.discreetlauncher.core.Folder)) beforeLaunch.run() ;
	}


	/**
	 * Constructor to fill a RecyclerView with the applications list.
	 */
	public RecyclerAdapter(Context context, ArrayList<Application> applicationsList, int target)
	{
		// Initializations
		this.applicationsList = applicationsList ;
		this.target = target ;
		padding = Math.round(context.getResources().getDimension(R.dimen.spacing_normal)) ;
		text_color = ContextCompat.getColor(context, R.color.for_text_on_overlay) ;

		// Retrieve settings which do not need update (the activity is recreated if they change)
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context) ;
		hide_app_names = settings.getBoolean(Constants.HIDE_APP_NAMES, false) ;
		hide_folder_names = settings.getBoolean(Constants.HIDE_FOLDER_NAMES, false) ;
		remove_padding = settings.getBoolean(Constants.REMOVE_PADDING, false) ;
	}


	/**
	 * Set a new text color and refresh the recycler content.
	 */
	@SuppressLint("NotifyDataSetChanged")
	public void setTextColor(int new_text_color)
	{
		text_color = new_text_color ;
		notifyDataSetChanged() ;
	}


	/**
	 * Create an ApplicationView to add in the RecyclerView based on an XML layout.
	 */
	@NonNull
	@Override
	public ApplicationView onCreateViewHolder(ViewGroup parent, int viewType)
	{
		LayoutInflater inflater = LayoutInflater.from(parent.getContext()) ;
		View view = inflater.inflate(R.layout.view_application, parent, false) ;
		return new ApplicationView(view) ;
	}


	/**
	 * Write the metadata (name, icon) of each application in the RecyclerView
	 */
	@Override
	public void onBindViewHolder(@NonNull ApplicationView appView, int i)
	{
		// Display the application icon and text
		appView.name.setTextColor(text_color) ;
		appView.name.setText(applicationsList.get(i).getDisplayName()) ;
		appView.name.setCompoundDrawables(null, applicationsList.get(i).getIcon(), null, null) ;

		// Check the type of application
		if(applicationsList.get(i) instanceof Folder)
			{
				if(hide_folder_names) appView.name.setTextSize(0) ;
					else appView.name.setTextSize(14) ;
			}
			else
			{
				if(hide_app_names) appView.name.setTextSize(0) ;
					else appView.name.setTextSize(14) ;
			}

		// If the option is selected, remove padding around the applications
		if(hide_app_names && remove_padding) appView.name.setPadding(0, 0, 0, 0) ;
			else appView.name.setPadding(0, padding, 0, padding) ;

		// Reset the dragging appearance (the dragged element stays faded at its place)
		appView.itemView.animate().cancel() ;
		appView.itemView.setScaleX(1) ;
		appView.itemView.setScaleY(1) ;
		appView.itemView.setAlpha((dragged != null) && (applicationsList.get(i) == dragged) ? 0.3f : 1) ;
	}


	/**
	 * Return the number of items in the RecyclerView.
	 */
	@Override
	public int getItemCount()
	{
		if(applicationsList == null) return 0 ;
		return applicationsList.size() ;
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Represent a clickable application item in the RecyclerView.
	 */
	public class ApplicationView extends RecyclerView.ViewHolder implements View.OnTouchListener, View.OnClickListener, View.OnLongClickListener, View.OnFocusChangeListener
	{
		// Attributes
		private final TextView name ;
		private boolean touch_active ;
		private boolean picked ;
		private float down_x ;
		private float down_y ;


		/**
		 * Constructor.
		 */
		ApplicationView(View view)
		{
			// Let the parent actions be performed
			super(view) ;

			// Listen for clicks and long clicks on the application
			name = view.findViewById(R.id.application_item) ;
			view.setOnClickListener(this) ;
			view.setOnLongClickListener(this) ;

			// Listen for touchs and focus changes to display visual feedback
			view.setOnTouchListener(this) ;
			view.setOnFocusChangeListener(this) ;
		}


		/**
		 * Called when the application is touched.
		 */
		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View view, MotionEvent event)
		{
			final Context context = view.getContext() ;
			switch(event.getAction())
			{
				// Gesture started
				case MotionEvent.ACTION_DOWN :
					setVisualFeedback(context, true) ;
					touch_active = true ;
					picked = false ;
					picking = false ;
					down_x = event.getRawX() ;
					down_y = event.getRawY() ;
					break ;
				// The element has been picked up by a long press: moving it starts the dragging
				case MotionEvent.ACTION_MOVE :
					if(picked)
						{
							float distance = (float)Math.hypot(event.getRawX() - down_x, event.getRawY() - down_y) ;
							if(distance > android.view.ViewConfiguration.get(context).getScaledTouchSlop())
								{
									picked = false ;
									picking = false ;
									setVisualFeedback(context, false) ;
									startDragging(view) ;
								}
							return true ;
						}
					break ;
				// Gesture finished: an element picked up and released without moving opens the menu
				case MotionEvent.ACTION_UP :
					setVisualFeedback(context, false) ;
					touch_active = false ;
					if(picked)
						{
							picked = false ;
							picking = false ;
							dropAppearance(view) ;
							showMenu(view) ;
							return true ;
						}
					break ;
				// Gesture aborted
				case MotionEvent.ACTION_CANCEL :
					setVisualFeedback(context, false) ;
					touch_active = false ;
					if(picked)
						{
							picked = false ;
							picking = false ;
							dropAppearance(view) ;
						}
					break ;
			}

			// Do not consider the event as consumed
			return false ;
		}


		/**
		 * Pick up the element: it grows a little, the list stops scrolling under the finger.
		 */
		private void pickUp(View view)
		{
			picked = true ;
			picking = true ;
			if(view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true) ;
			view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start() ;
		}


		/**
		 * Put back the normal appearance of a picked element.
		 */
		private void dropAppearance(View view)
		{
			view.animate().scaleX(1).scaleY(1).setDuration(120).start() ;
		}


		/**
		 * Start dragging the element (system drag and drop, which can cross windows: ex: from a
		 * folder popup to the app drawer).
		 */
		private void startDragging(View view)
		{
			int position = getBindingAdapterPosition() ;
			if(position == RecyclerView.NO_POSITION) return ;
			Application application = applicationsList.get(position) ;
			DragAndDrop.DragInfo info = new DragAndDrop.DragInfo(application, target, ownerFolder, RecyclerAdapter.this) ;
			view.setScaleX(1) ;
			view.setScaleY(1) ;
			if(DragAndDrop.start(view, info)) setDragged(application) ;
		}


		/**
		 * Called whent the focus of the application has changed (selected, not selected).
		 */
		@Override
		public void onFocusChange(View view, boolean hasFocus)
		{
			setVisualFeedback(view.getContext(), hasFocus) ;
		}


		/**
		 * Show or hide visual feedback.
		 */
		private void setVisualFeedback(Context context, boolean display)
		{
			// Only change how the text is painted: changing the typeface would request a new layout of
			// the whole list during the touch, which moves the items in a list laid out from the bottom
			// (one-handed use) so the finger leaves the item and the click is lost
			name.getPaint().setFakeBoldText(display) ;
			name.invalidate() ;
			if(display)
				{
					name.setShadowLayer(15, 0, 0, ContextCompat.getColor(context, R.color.for_visual_feedback_shadow)) ;
					name.getCompoundDrawables()[1].setColorFilter(ContextCompat.getColor(context, R.color.for_visual_feedback_drawable), PorterDuff.Mode.SRC_ATOP) ;
				}
				else
				{
					name.setShadowLayer(0, 0, 0, 0) ;
					name.getCompoundDrawables()[1].clearColorFilter() ;
				}
		}


		/**
		 * Called when the application is clicked.
		 */
		@Override
		public void onClick(View view)
		{
			// Start the application
			Application application = applicationsList.get(getBindingAdapterPosition()) ;
			runBeforeLaunch(application) ;
			if(!application.start(view))
				{
					final Context context = view.getContext() ;
					Utils.displayLongToast(context, context.getString(R.string.error_app_not_found, application.getDisplayName())) ;
				}
				else UsageTracker.recordLaunch(view.getContext(), application) ;
		}


		/**
		 * Called when the application is long clicked.
		 */
		@SuppressLint("NotifyDataSetChanged")
		@Override
		public boolean onLongClick(final View view)
		{
			// In lists which can be reordered, a long press with the finger picks up the element: the
			// menu opens when releasing it without moving, moving it starts the dragging
			if((view != null) && isDragEnabled() && touch_active)
				{
					pickUp(view) ;
					return true ;
				}
			return showMenu(view) ;
		}


		/**
		 * Display the menu of the element (formerly displayed directly on long press).
		 */
		@SuppressLint("NotifyDataSetChanged")
		boolean showMenu(final View view)
		{
			// Get the clicked position and retrieve the selected application
			if(view == null) return false ;
			final Application application = applicationsList.get(getBindingAdapterPosition()) ;
			final Context context = view.getContext() ;

			// Check if the application is in the favorites panel
			String component_info = application.getComponentInfo() ;
			final boolean is_favorite = new InternalFileTXT(Constants.FILE_FAVORITES).isLineExisting(component_info) ;

			// Check if the application is in a folder
			String is_in_folder_buffer = null ;
			String[] folders_files = InternalFile.searchFilesStartingWith(context, Constants.FILE_FOLDER_PREFIX) ;
			for(String filename : folders_files)
				if(new InternalFileTXT(filename).isLineExisting(component_info))
					is_in_folder_buffer = filename.replace(Constants.FILE_FOLDER_PREFIX, "").replace(".txt", "") ;
			final String is_in_folder = is_in_folder_buffer ;

			// Prepare and display the selection dialog
			AlertDialog.Builder dialog = new AlertDialog.Builder(context) ;
			dialog.setTitle(context.getString(R.string.long_click_dialog_title)) ;
			if(application instanceof Shortcut)
				{
					CharSequence[] options = {
							context.getString(R.string.long_click_open, application.getDisplayName()),
							context.getString(R.string.long_click_remove_shortcut),
							context.getString(R.string.long_click_rename),
							context.getString(is_favorite ? R.string.long_click_remove_favorites : R.string.long_click_add_favorites),
							(is_in_folder != null) ? context.getString(R.string.long_click_remove_folder, is_in_folder) : context.getString(R.string.long_click_add_folder),
						} ;
					dialog.setItems(options, (dialog1, selection) -> {
							// Check which option has been selected
							switch(selection)
							{
								case 0 :
									// Open the shortcut
									application.start(view) ;
									break ;
								case 1 :
									// Remove the shortcut from the file and update the applications list
									ShortcutListener.removeShortcut(context, application.getName(), application.getApk(), application.getComponentInfo()) ;
									ActivityMain.updateList(context) ;
									notifyDataSetChanged() ;
									break ;
								case 2 :
									// Display the dialog to rename the shortcut
									showRenameDialog(context, application) ;
									break ;
								case 3 :
									// Toggle the presence of the shortcut in the favorites panel
									toggleFavorite(context, application, is_favorite) ;
									break ;
								case 4 :
									// Toggle the presence of the shortcut in a folder
									toggleFolder(context, application, is_in_folder) ;
									break ;
							}
						}) ;
				}
				else if(application instanceof Folder)
				{
					CharSequence[] options = {
							context.getString(R.string.long_click_open, application.getDisplayName()),
							context.getString(R.string.long_click_settings),
							context.getString(is_favorite ? R.string.long_click_remove_favorites : R.string.long_click_add_favorites),
						} ;
					dialog.setItems(options, (dialog12, selection) -> {
							// Check which option has been selected
							switch(selection)
							{
								case 0 :
									// Open the folder
									application.start(view) ;
									break ;
								case 1 :
									// Open the folder organizer
									application.showSettings(context) ;
									break ;
								case 2 :
									// Toggle the presence of the folder in the favorites panel
									toggleFavorite(context, application, is_favorite) ;
									break ;
							}
						}) ;
				}
				else if(application instanceof Search)
				{
					CharSequence[] options = {
							context.getString(R.string.long_click_open, application.getDisplayName()),
							context.getString(R.string.long_click_hide_search),
							context.getString(is_favorite ? R.string.long_click_remove_favorites : R.string.long_click_add_favorites),
						} ;
					dialog.setItems(options, (dialog13, selection) -> {
							// Check which option has been selected
							switch(selection)
							{
								case 0 :
									// Open the Search
									application.start(view) ;
									break ;
								case 1 :
									// Open the Hidden apps dialog
									DialogHiddenApps.showHiddenAppsDialog(context) ;
									break ;
								case 2 :
									// Toggle the presence of the search in the favorites panel
									toggleFavorite(context, application, is_favorite) ;
									break ;
							}
						}) ;
				}
				else
				{
					CharSequence[] options = {
							context.getString(R.string.long_click_open, application.getDisplayName()),
							context.getString(R.string.long_click_settings),
							context.getString(R.string.long_click_view_store),
							context.getString(R.string.long_click_rename),
							context.getString(is_favorite ? R.string.long_click_remove_favorites : R.string.long_click_add_favorites),
							(is_in_folder != null) ? context.getString(R.string.long_click_remove_folder, is_in_folder) : context.getString(R.string.long_click_add_folder),
						} ;
					dialog.setItems(options, (dialog14, selection) -> {
							// Check which option has been selected
							switch(selection)
							{
								case 0 :
									// Start the application and display an error message if it was not found
									if(!application.start(view))
										Utils.displayLongToast(context, context.getString(R.string.error_app_not_found, application.getDisplayName())) ;
									break ;
								case 1 :
									// Open the application system settings
									application.showSettings(context) ;
									break ;
								case 2 :
									// Open the application page in the store
									Intent storeIntent = new Intent(Intent.ACTION_VIEW) ;
									storeIntent.setData(Uri.parse("market://details?id=" + application.getApk())) ;
									storeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
									try
									{
										context.startActivity(storeIntent) ;
									}
									catch(ActivityNotFoundException exception)
									{
										Utils.logError(TAG, exception.getMessage()) ;
										Utils.displayLongToast(context, context.getString(R.string.error_app_not_found, "{market}")) ;
									}
									break ;
								case 3 :
									// Display the dialog to rename the application
									showRenameDialog(context, application) ;
									break ;
								case 4 :
									// Toggle the presence of the application in the favorites panel
									toggleFavorite(context, application, is_favorite) ;
									break ;
								case 5 :
									// Toggle the presence of the shortcut in a folder
									toggleFolder(context, application, is_in_folder) ;
									break ;
							}
						}) ;
				}
			dialog.show() ;

			// Consider the event as consumed
			return true ;
		}


		/**
		 * Display a dialog allowing to rename the application.
		 */
		@SuppressLint("NotifyDataSetChanged")
		private void showRenameDialog(final Context context, final Application application)
		{
			// Create the menu dialog
			AlertDialog.Builder dialog = new AlertDialog.Builder(context) ;
			dialog.setTitle(R.string.long_click_rename) ;
			dialog.setNegativeButton(R.string.button_cancel, null) ;

			// Prepare a text field that will be used to rename the application
			final EditText renameField = new EditText(context) ;
			renameField.setText(application.getDisplayName()) ;
			renameField.setSingleLine() ;
			renameField.setImeOptions(EditorInfo.IME_ACTION_DONE) ;

			// Retrieve the original name of the application
			Intent intent = new Intent(Intent.ACTION_MAIN) ;
			intent.addCategory(Intent.CATEGORY_LAUNCHER) ;
			PackageManager apkManager = context.getPackageManager() ;
			for(ResolveInfo entry : apkManager.queryIntentActivities(intent, 0))
				if(application.getComponentInfo().equals("{" + entry.activityInfo.packageName + "/" + entry.activityInfo.name + "}"))
					{
						renameField.setHint(entry.loadLabel(apkManager)) ;
						break ;
					}

			// Add the button to save a new name
			dialog.setPositiveButton(R.string.button_apply, (dialog1, which) -> {
					// Remove any existing name mapping
					InternalFileTXT rename_apps_file = new InternalFileTXT(Constants.FILE_RENAME_APPS) ;
					rename_apps_file.removeLine(application.getComponentInfo()) ;

					// Save the new name (an empty name will restore the original name)
					if(!renameField.getText().toString().isEmpty())
						rename_apps_file.writeLine(application.getComponentInfo() + Constants.SEPARATOR + renameField.getText()) ;

					// Update the applications list
					ActivityMain.updateList(context) ;
					notifyDataSetChanged() ;
				}) ;

			// Display the dialog
			dialog.setView(renameField) ;
			dialog.show() ;
		}


		/**
		 * Toggle the presence of an application in the favorites panel.
		 */
		@SuppressLint("NotifyDataSetChanged")
		private void toggleFavorite(Context context, Application application, boolean is_favorite)
		{
			// Retrieve the file and the application ComponentInfo
			InternalFileTXT favorites = new InternalFileTXT(Constants.FILE_FAVORITES) ;
			String component_info = application.getComponentInfo() ;

			// Toggle the presence of the application in the favorites panel
			if(is_favorite) favorites.removeLine(component_info) ;
				else favorites.writeLine(component_info) ;

			// Update the favorites list
			ActivityMain.updateFavorites(context) ;
			notifyDataSetChanged() ;
		}


		/**
		 * Toggle the presence of an application in a folder.
		 */
		@SuppressLint("NotifyDataSetChanged")
		private void toggleFolder(final Context context, final Application application, String is_in_folder)
		{
			// Check is the application needs to be added to a folder, or removed from a folder
			if(is_in_folder != null)
				{
					// Remove the application from its current folder
					InternalFileTXT folder = new InternalFileTXT(Constants.FILE_FOLDER_PREFIX + is_in_folder + ".txt") ;
					folder.removeLine(application.getComponentInfo()) ;

					// If we are currently in the folder, update its content manually
					if(target == Constants.FOLDER) applicationsList.remove(application) ;

					// Display a warning if some interface elements cannot be immediately updated
					if((target == Constants.FOLDER) || (target == Constants.SEARCH))
						Utils.displayLongToast(context, context.getString(R.string.info_display_partially_updated)) ;

					// Update the list of applications
					ActivityMain.updateList(context) ;
					notifyDataSetChanged() ;
				}
				else
				{
					// Prepare the list of folder names
					final ArrayList<Folder> folders = ActivityMain.getApplicationsList().getFolders() ;
					String[] folder_names = new String[folders.size()] ;
					for(int i = 0 ; i < folders.size() ; i++)
						folder_names[i] = folders.get(i).getDisplayNameWithCount() ;

					// Display the list of existing folders
					AlertDialog.Builder dialog = new AlertDialog.Builder(context) ;
					dialog.setTitle(context.getString(R.string.long_click_add_folder)) ;
					dialog.setItems(folder_names, (dialog1, selection) -> {
							// Add the application to the selected folder
							InternalFileTXT folder = new InternalFileTXT(folders.get(selection).getFileName()) ;
							folder.writeLine(application.getComponentInfo()) ;

							// Display a warning if some interface elements cannot be immediately updated
							if(target == Constants.SEARCH)
								Utils.displayLongToast(context, context.getString(R.string.info_display_partially_updated)) ;

							// Update the list of applications
							ActivityMain.updateList(context) ;
							notifyDataSetChanged() ;
						}) ;
					dialog.show() ;
				}
		}
	}
}
