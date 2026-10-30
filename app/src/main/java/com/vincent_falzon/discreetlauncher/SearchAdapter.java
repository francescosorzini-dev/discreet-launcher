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
import java.text.Normalizer ;
import java.util.ArrayList ;
import java.util.Locale ;
import java.util.regex.Pattern ;

/**
 * Fill a RecyclerView with a list of applications filtered with a search result.
 */
public class SearchAdapter extends RecyclerAdapter implements Filterable
{
	// Attributes
	private final ArrayList<Application> initialApplicationsList ;
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+") ;


	/**
	 * Constructor to fill a RecyclerView with the applications list.
	 */
	public SearchAdapter(Context context, ArrayList<Application> applicationsList)
	{
		// Let the parent actions be performed
		super(context, applicationsList, Constants.SEARCH) ;

		// Initializations
		initialApplicationsList = applicationsList ;
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
			 * Retrieve the results after the filter is applied.
			 */
			@Override
			protected FilterResults performFiltering(CharSequence filter)
			{
				// Check if there is a search pattern
				String search = filter.toString() ;
				if(search.isEmpty()) applicationsList = initialApplicationsList ;
					else
					{
						// Filter results based on the search pattern ignoring case and accents
						// Matches at the start of the name come first, then matches anywhere in the name
						ArrayList<Application> prefixMatches = new ArrayList<>() ;
						ArrayList<Application> containsMatches = new ArrayList<>() ;
						String normalized_search = normalize(search) ;
						for(Application application : initialApplicationsList)
						{
							String app_name = normalize(application.getDisplayName()) ;
							int index = app_name.indexOf(normalized_search) ;
							if(index == 0) prefixMatches.add(application) ;
								else if(index > 0) containsMatches.add(application) ;
						}
						applicationsList = prefixMatches ;
						applicationsList.addAll(containsMatches) ;
					}

				// Prepare the filter results
				FilterResults filterResults = new FilterResults() ;
				filterResults.values = applicationsList ;
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
	 * Lowercase a string and strip its accents to allow case/accent insensitive substring search.
	 */
	private static String normalize(String text)
	{
		if(text == null) return "" ;
		return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT) ;
	}


	/**
	 * Launch the first app currently displayed in the adapter (if any).
	 */
	public void launchFirstApp(View view)
	{
		if(getItemCount() >= 1)
			applicationsList.get(0).start(view) ;
	}
}
