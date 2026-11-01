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
import android.content.ClipData ;
import android.content.ClipDescription ;
import android.content.Context ;
import android.os.Build ;
import android.util.Log ;
import android.view.DragEvent ;
import android.view.HapticFeedbackConstants ;
import android.view.View ;
import android.widget.EditText ;
import androidx.appcompat.app.AlertDialog ;
import androidx.recyclerview.widget.RecyclerView ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.core.ApplicationsList ;
import com.vincent_falzon.discreetlauncher.core.Folder ;
import com.vincent_falzon.discreetlauncher.core.Search ;
import com.vincent_falzon.discreetlauncher.storage.InternalFile ;
import com.vincent_falzon.discreetlauncher.storage.InternalFileTXT ;
import java.util.ArrayList ;

/**
 * Organize the launcher by dragging (long press then move):
 * - favorites panel: reorder the favorites
 * - app drawer: reorder apps and folders, drop an app on a folder to put it inside, hold an app
 *   over another app to create a folder with both
 * - folder popup: drag an app out of the popup (it closes) to drop it in the drawer or on a folder
 */
public final class DragAndDrop
{
	// Constants
	private static final long MERGE_DELAY = 500 ;
	private static final long REORDER_DELAY = 250 ;
	private static final String CLIP_LABEL = "discreetlauncher.drag" ;

	// Drag currently started by the launcher
	private static DragInfo current = null ;
	private static final float EDGE_ZONE_DP = 56 ;
	private static final float SCROLL_STEP_DP = 8 ;

	// Prevent instantiation
	private DragAndDrop() { }


	/**
	 * Information about the element being dragged.
	 */
	public static final class DragInfo
	{
		final Application application ;
		final int source ;
		final Folder sourceFolder ;
		final RecyclerAdapter sourceAdapter ;

		DragInfo(Application application, int source, Folder sourceFolder, RecyclerAdapter sourceAdapter)
		{
			this.application = application ;
			this.source = source ;
			this.sourceFolder = sourceFolder ;
			this.sourceAdapter = sourceAdapter ;
		}
	}


	/**
	 * Rebuild the lists after a change of folders or order.
	 */
	public interface Host
	{
		void refreshLists() ;
	}


	/**
	 * Start the system drag and drop of an element.
	 */
	@SuppressWarnings("deprecation")
	static boolean start(View view, DragInfo info)
	{
		ClipData data = ClipData.newPlainText(CLIP_LABEL, info.application.getComponentInfo()) ;
		current = info ;
		View.DragShadowBuilder shadow = new View.DragShadowBuilder(view) ;
		boolean started ;
		// Without the global flag, the system limits the drag to the window where it started: the
		// app drawer would never receive an app dragged out of a folder popup (another window)
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) started = view.startDragAndDrop(data, shadow, info, View.DRAG_FLAG_GLOBAL) ;
			else started = view.startDrag(data, shadow, info, 0) ;
		Log.i("DLDrag", "start " + info.application.getDisplayName() + " from " + info.source + ": " + started) ;
		return started ;
	}


	/**
	 * Return the drag information carried by an event (null if it does not come from the launcher).
	 */
	private static DragInfo getInfo(DragEvent event)
	{
		// The local state is only given to the window where the drag started (not to the app drawer
		// when an app is dragged out of a folder popup): fall back on the drag started by the launcher
		Object state = event.getLocalState() ;
		if(state instanceof DragInfo) return (DragInfo)state ;
		if(current == null) return null ;
		if(event.getAction() == DragEvent.ACTION_DRAG_ENDED) return current ;
		ClipDescription description = event.getClipDescription() ;
		if((description != null) && CLIP_LABEL.contentEquals(description.getLabel())) return current ;
		return null ;
	}


	// ---------------------------------------------------------------------------------------------
	// Favorites panel

	/**
	 * Allow to reorder the favorites by dragging them (scrollContainer: scrolled near its edges).
	 */
	public static void attachToFavorites(RecyclerView recycler, RecyclerAdapter adapter, View scrollContainer)
	{
		final EdgeScroller scroller = new EdgeScroller(scrollContainer) ;
		recycler.setOnDragListener((view, event) -> {
				DragInfo info = getInfo(event) ;
				if((info == null) || (info.source != Constants.FAVORITES_PANEL)) return false ;
				switch(event.getAction())
				{
					case DragEvent.ACTION_DRAG_LOCATION :
						moveUnder(recycler, adapter, info.application, event.getX(), event.getY()) ;
						scroller.update(recycler, event.getY()) ;
						break ;
					case DragEvent.ACTION_DRAG_EXITED :
						scroller.stop() ;
						break ;
					case DragEvent.ACTION_DROP :
					case DragEvent.ACTION_DRAG_ENDED :
						// The favorites have been reordered live: save their order
						scroller.stop() ;
						if(event.getAction() == DragEvent.ACTION_DRAG_ENDED)
							{
								saveFavorites(adapter.getApplicationsList()) ;
								info.sourceAdapter.setDragged(null) ;
							}
						break ;
				}
				return true ;
			}) ;
	}


	/**
	 * Save the favorites in the order of the given list.
	 */
	private static void saveFavorites(ArrayList<Application> favorites)
	{
		InternalFileTXT file = new InternalFileTXT(Constants.FILE_FAVORITES) ;
		file.remove() ;
		for(Application application : favorites) file.writeLine(application.getComponentInfo()) ;
	}


	/**
	 * Move the dragged element at the position under the finger (the others make room).
	 */
	private static void moveUnder(RecyclerView recycler, RecyclerAdapter adapter, Application dragged, float x, float y)
	{
		View child = recycler.findChildViewUnder(x, y) ;
		if(child == null) return ;
		int to = recycler.getChildAdapterPosition(child) ;
		ArrayList<Application> list = adapter.getApplicationsList() ;
		int from = list.indexOf(dragged) ;
		if((from < 0) || (to == RecyclerView.NO_POSITION) || (from == to)) return ;
		list.remove(from) ;
		list.add(to, dragged) ;
		adapter.notifyItemMoved(from, to) ;
	}


	// ---------------------------------------------------------------------------------------------
	// App drawer

	/**
	 * Allow to organize the app drawer by dragging apps and folders.
	 */
	public static void attachToDrawer(RecyclerView recycler, RecyclerAdapter adapter, Host host)
	{
		final EdgeScroller scroller = new EdgeScroller(recycler) ;
		final View[] merge_view = { null } ;
		final int[] merge_candidate = { RecyclerView.NO_POSITION } ;
		final int[] merge_target = { RecyclerView.NO_POSITION } ;
		final int[] insertion = { RecyclerView.NO_POSITION } ;
		final Runnable[] arm = { null } ;
		final int[] reorder_candidate = { RecyclerView.NO_POSITION } ;
		final float[] last_location = { 0, 0 } ;
		final Runnable[] reorder = { null } ;

		// Cancel a pending reorder (the finger did not rest long enough between two elements)
		final Runnable cancelReorder = () -> {
				if(reorder[0] != null) recycler.removeCallbacks(reorder[0]) ;
				reorder[0] = null ;
				reorder_candidate[0] = RecyclerView.NO_POSITION ;
			} ;

		// Cancel a merge candidate or target (the highlighted element goes back to normal)
		final Runnable cancelMerge = () -> {
				if(arm[0] != null) recycler.removeCallbacks(arm[0]) ;
				arm[0] = null ;
				if(merge_view[0] != null) merge_view[0].animate().scaleX(1).scaleY(1).setDuration(120).start() ;
				merge_view[0] = null ;
				merge_candidate[0] = RecyclerView.NO_POSITION ;
				merge_target[0] = RecyclerView.NO_POSITION ;
			} ;

		recycler.setOnDragListener((view, event) -> {
				DragInfo info = getInfo(event) ;
				if((info == null) || ((info.source != Constants.APP_DRAWER) && (info.source != Constants.FOLDER))) return false ;
				ArrayList<Application> list = adapter.getApplicationsList() ;
				if(event.getAction() != DragEvent.ACTION_DRAG_LOCATION) Log.i("DLDrag", "drawer event " + event.getAction()) ;
				switch(event.getAction())
				{
					case DragEvent.ACTION_DRAG_STARTED :
						insertion[0] = RecyclerView.NO_POSITION ;
						// An app dragged out of a folder: close the popup to show the drawer under the finger
						if((info.source == Constants.FOLDER) && (info.sourceFolder != null))
							recycler.post(info.sourceFolder::closePopupNow) ;
						return true ;

					case DragEvent.ACTION_DRAG_LOCATION :
						{
							float x = event.getX() ;
							float y = event.getY() ;
							last_location[0] = x ;
							last_location[1] = y ;
							scroller.update(recycler, y) ;
							View child = recycler.findChildViewUnder(x, y) ;
							if(child == null)
								{
									cancelMerge.run() ;
									cancelReorder.run() ;
									break ;
								}
							int position = recycler.getChildAdapterPosition(child) ;
							if(position == RecyclerView.NO_POSITION) break ;
							Application target = list.get(position) ;
							if(target == info.application)
								{
									cancelMerge.run() ;
									cancelReorder.run() ;
									break ;
								}

							// Over the center of an element which can receive the dragged app: wait a moment, then
							// highlight it (dropping will put the app in the folder or create a new folder)
							boolean in_center = (x > child.getLeft() + child.getWidth() * 0.2f) && (x < child.getRight() - child.getWidth() * 0.2f)
									&& (y > child.getTop() + child.getHeight() * 0.15f) && (y < child.getBottom() - child.getHeight() * 0.15f) ;
							if(in_center && canMerge(info, target))
								{
									cancelReorder.run() ;
									if(merge_candidate[0] != position)
										{
											cancelMerge.run() ;
											merge_candidate[0] = position ;
											merge_view[0] = child ;
											arm[0] = () -> {
													merge_target[0] = merge_candidate[0] ;
													child.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start() ;
													child.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) ;
												} ;
											recycler.postDelayed(arm[0], MERGE_DELAY) ;
										}
									break ;
								}

							// Elsewhere: reorder (live for drawer elements, at the drop for apps coming from a folder).
							// The finger must rest a moment, so that crossing the border of an element to reach its
							// center does not push it away
							cancelMerge.run() ;
							if(info.source == Constants.APP_DRAWER)
								{
									if(reorder_candidate[0] != position)
										{
											cancelReorder.run() ;
											reorder_candidate[0] = position ;
											reorder[0] = () -> {
													reorder[0] = null ;
													reorder_candidate[0] = RecyclerView.NO_POSITION ;
													moveUnder(recycler, adapter, info.application, last_location[0], last_location[1]) ;
												} ;
											recycler.postDelayed(reorder[0], REORDER_DELAY) ;
										}
								}
								else insertion[0] = position ;
							break ;
						}

					case DragEvent.ACTION_DRAG_EXITED :
						scroller.stop() ;
						cancelMerge.run() ;
						cancelReorder.run() ;
						break ;

					case DragEvent.ACTION_DROP :
						{
							scroller.stop() ;
							Context context = recycler.getContext() ;
							int target_position = merge_target[0] ;
							cancelMerge.run() ;
							// Dropped between elements before the reorder delay: reorder now
							Runnable pending = reorder[0] ;
							cancelReorder.run() ;
							if((pending != null) && (target_position == RecyclerView.NO_POSITION)) pending.run() ;
							if(target_position != RecyclerView.NO_POSITION)
								{
									// Dropped on a highlighted element: folder
									Application target = list.get(target_position) ;
									if(target instanceof Folder)
										{
											moveToFolder(info.application, target.getDisplayName()) ;
											host.refreshLists() ;
										}
										else askNewFolder(context, info.application, target, list, host) ;
								}
								else if(info.source == Constants.APP_DRAWER)
								{
									// Reordered live: save the order
									saveOrder(list, null, null, null) ;
								}
								else
								{
									// Coming from a folder: take it out and place it where it was dropped
									removeFromAllFolders(info.application.getComponentInfo()) ;
									ArrayList<Application> order = new ArrayList<>(list) ;
									int position = (insertion[0] == RecyclerView.NO_POSITION) ? order.size() : Math.min(insertion[0], order.size()) ;
									order.add(position, info.application) ;
									saveOrder(order, null, null, null) ;
									host.refreshLists() ;
								}
							return true ;
						}

					case DragEvent.ACTION_DRAG_ENDED :
						scroller.stop() ;
						cancelMerge.run() ;
						cancelReorder.run() ;
						info.sourceAdapter.setDragged(null) ;
						break ;
				}
				return true ;
			}) ;
	}


	/**
	 * Check if the dragged element can be put in a folder with (or in) the target.
	 */
	private static boolean canMerge(DragInfo info, Application target)
	{
		// Folders cannot be nested, and the search is not an app
		if((info.application instanceof Folder) || (info.application instanceof Search)) return false ;
		if(target instanceof Search) return false ;
		if(target instanceof Folder) return target != info.sourceFolder ;
		return true ;
	}


	/**
	 * Save the order of the drawer: optionally replacing an element by a new folder and removing another.
	 */
	private static void saveOrder(ArrayList<Application> list, Application replaced, String replacement, Application removed)
	{
		ArrayList<String> order = new ArrayList<>() ;
		for(Application application : list)
		{
			if(application == removed) continue ;
			if(application == replaced) order.add(replacement) ;
				else order.add(application.getComponentInfo()) ;
		}
		ApplicationsList.saveDrawerOrder(order) ;
	}


	/**
	 * Remove an application from any folder (an application is in one folder at most).
	 */
	private static void removeFromAllFolders(String component_info)
	{
		String folder_path = ActivityMain.getInternalFolder() ;
		if(folder_path == null) return ;
		java.io.File[] files = new java.io.File(folder_path).listFiles((dir, name) -> name.startsWith(Constants.FILE_FOLDER_PREFIX)) ;
		if(files == null) return ;
		for(java.io.File file : files) new InternalFileTXT(file.getName()).removeLine(component_info) ;
	}


	/**
	 * Move an application in the given folder (removing it from its previous folder).
	 */
	private static void moveToFolder(Application application, String folder_name)
	{
		String component_info = application.getComponentInfo() ;
		removeFromAllFolders(component_info) ;
		new InternalFileTXT(Constants.FILE_FOLDER_PREFIX + folder_name + ".txt").writeLine(component_info) ;
	}


	/**
	 * Ask the name of a new folder containing both applications, then create it at the target place.
	 */
	private static void askNewFolder(Context context, Application dragged, Application target, ArrayList<Application> list, Host host)
	{
		final EditText name = new EditText(context) ;
		name.setSingleLine(true) ;
		name.setText(context.getString(R.string.new_folder_default_name)) ;
		name.selectAll() ;
		int padding = Math.round(20 * context.getResources().getDisplayMetrics().density) ;
		android.widget.FrameLayout container = new android.widget.FrameLayout(context) ;
		container.setPadding(padding, padding / 2, padding, 0) ;
		container.addView(name) ;
		new AlertDialog.Builder(context)
			.setTitle(R.string.new_folder_title)
			.setView(container)
			.setNegativeButton(R.string.button_cancel, null)
			.setPositiveButton(R.string.button_create, (dialog, which) -> {
					// Check the name (same rules as the folders management screen)
					String folder_name = name.getText().toString().trim() ;
					if(folder_name.isEmpty())
						{
							Utils.displayLongToast(context, context.getString(R.string.error_folder_empty_name)) ;
							return ;
						}
					InternalFileTXT file = new InternalFileTXT(Constants.FILE_FOLDER_PREFIX + folder_name + ".txt") ;
					if(file.exists())
						{
							Utils.displayLongToast(context, context.getString(R.string.error_folder_already_exists)) ;
							return ;
						}

					// Create the folder with both applications (moved from their previous folders)
					removeFromAllFolders(target.getComponentInfo()) ;
					removeFromAllFolders(dragged.getComponentInfo()) ;
					file.writeLine(target.getComponentInfo()) ;
					file.writeLine(dragged.getComponentInfo()) ;

					// With a custom order, the folder takes the place of the target
					if(new InternalFileTXT(Constants.FILE_DRAWER_ORDER).exists())
						saveOrder(list, target, ApplicationsList.getFolderComponentInfo(folder_name), dragged) ;
					host.refreshLists() ;
				})
			.show() ;
	}


	// ---------------------------------------------------------------------------------------------
	// Folder popup

	/**
	 * Close the folder popup when an app of this folder is dragged out of it.
	 */
	public static void attachToFolderPopup(RecyclerView recycler, Folder folder)
	{
		recycler.setOnDragListener((view, event) -> {
				DragInfo info = getInfo(event) ;
				if((info == null) || (info.sourceFolder != folder)) return false ;
				Log.i("DLDrag", "popup event " + event.getAction()) ;
				switch(event.getAction())
				{
					case DragEvent.ACTION_DRAG_EXITED :
						folder.closePopupNow() ;
						break ;
					case DragEvent.ACTION_DRAG_ENDED :
						info.sourceAdapter.setDragged(null) ;
						break ;
				}
				return true ;
			}) ;
	}


	/**
	 * Debug: log the drag events received by a window root.
	 */
	public static void debugRoot(View root, String name)
	{
		if(root == null) return ;
		root.setOnDragListener((view, event) -> {
				if(event.getAction() != DragEvent.ACTION_DRAG_LOCATION) Log.i("DLDrag", name + " root event " + event.getAction() + " at " + (int)event.getX() + "," + (int)event.getY()) ;
					else Log.i("DLDrag", name + " root location " + (int)event.getX() + "," + (int)event.getY()) ;
				return event.getAction() != DragEvent.ACTION_DROP ;
			}) ;
	}


	// ---------------------------------------------------------------------------------------------

	/**
	 * Scroll a view while the dragged element stays near its top or bottom edge.
	 */
	private static final class EdgeScroller implements Runnable
	{
		private final View scrolled ;
		private int direction ;

		EdgeScroller(View scrolled)
		{
			this.scrolled = scrolled ;
			direction = 0 ;
		}

		void update(View reference, float y)
		{
			if(scrolled == null) return ;
			float density = scrolled.getResources().getDisplayMetrics().density ;
			float edge = EDGE_ZONE_DP * density ;

			// Position of the finger in the visible area of the scrolled view
			int[] reference_location = new int[2] ;
			int[] scrolled_location = new int[2] ;
			reference.getLocationOnScreen(reference_location) ;
			scrolled.getLocationOnScreen(scrolled_location) ;
			float visible_y = reference_location[1] + y - scrolled_location[1] ;
			int new_direction = (visible_y < edge) ? -1 : ((visible_y > scrolled.getHeight() - edge) ? 1 : 0) ;
			if(new_direction == direction) return ;
			direction = new_direction ;
			scrolled.removeCallbacks(this) ;
			if(direction != 0) scrolled.post(this) ;
		}

		void stop()
		{
			direction = 0 ;
			if(scrolled != null) scrolled.removeCallbacks(this) ;
		}

		@Override
		public void run()
		{
			if(direction == 0) return ;
			scrolled.scrollBy(0, Math.round(direction * SCROLL_STEP_DP * scrolled.getResources().getDisplayMetrics().density)) ;
			scrolled.postDelayed(this, 16) ;
		}
	}
}
