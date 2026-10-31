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
import android.content.Context ;
import android.view.View ;
import android.widget.Filter ;
import android.widget.Filterable ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.core.Folder ;
import com.vincent_falzon.discreetlauncher.core.UsageTracker ;
import java.util.ArrayList ;
import java.util.IdentityHashMap ;
import java.util.List ;
import java.util.Map ;

/**
 * Fill a RecyclerView with a list of applications filtered with a search result.
 */
public class SearchAdapter extends RecyclerAdapter implements Filterable
{
	// Constants
	private static final int MOST_USED_COUNT = 8 ;

	// Attributes
	private final ArrayList<Application> withoutSearchList ;
	private final ArrayList<SearchMatcher.Entry<Application>> entries ;


	/**
	 * Constructor to fill a RecyclerView with the applications list.
	 */
	public SearchAdapter(Context context, ArrayList<Application> applicationsList)
	{
		// Let the parent actions be performed
		super(context, applicationsList, Constants.SEARCH) ;

		// Find the folders containing each application, so that folder names act as labels
		Map<Application, List<String>> folderNames = new IdentityHashMap<>() ;
		if(ActivityMain.getApplicationsList() != null)
			for(Folder folder : ActivityMain.getApplicationsList().getFolders())
				for(Application application : folder.getApplications())
				{
					List<String> names = folderNames.get(application) ;
					if(names == null)
						{
							names = new ArrayList<>() ;
							folderNames.put(application, names) ;
						}
					names.add(folder.getDisplayName()) ;
				}

		// Prepare the searchable entries with the current usage scores
		Map<String, Double> scores = UsageTracker.getScores(context) ;
		entries = new ArrayList<>() ;
		for(int i = 0 ; i < applicationsList.size() ; i++)
		{
			Application application = applicationsList.get(i) ;
			Double score = scores.get(application.getComponentInfo()) ;
			entries.add(new SearchMatcher.Entry<>(application, application.getDisplayName(), folderNames.get(application), (score == null) ? 0 : score, i)) ;
		}

		// Without search, display the most used applications first
		withoutSearchList = SearchMatcher.mostUsedFirst(entries, MOST_USED_COUNT) ;
		this.applicationsList = withoutSearchList ;
	}


	/**
	 * Create the filter which will be used to search in the list.
	 */
	@Override
	public Filter getFilter()
	{
		return new Filter()
		{
			/**
			 * Retrieve the results after the filter is applied (runs in a background thread).
			 */
			@Override
			protected FilterResults performFiltering(CharSequence filter)
			{
				// Without search pattern display all applications, otherwise rank the matching ones
				String search = filter.toString() ;
				ArrayList<Application> results ;
				if(search.isEmpty()) results = withoutSearchList ;
					else results = SearchMatcher.rank(entries, search) ;

				// Prepare the filter results
				FilterResults filterResults = new FilterResults() ;
				filterResults.values = results ;
				return filterResults ;
			}


			/**
			 * Display the search results.
			 */
			@SuppressLint("NotifyDataSetChanged")
			@Override
			protected void publishResults(CharSequence filter, FilterResults results)
			{
				//noinspection unchecked
				applicationsList = (ArrayList<Application>)results.values ;
				notifyDataSetChanged() ;
			}
		} ;
	}


	/**
	 * Launch the first app currently displayed in the adapter (if any).
	 */
	public void launchFirstApp(View view)
	{
		if(getItemCount() >= 1)
			{
				Application application = applicationsList.get(0) ;
				if(application.start(view)) UsageTracker.recordLaunch(view.getContext(), application) ;
			}
	}
}
