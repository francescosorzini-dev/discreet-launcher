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
import java.text.Normalizer ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.List ;
import java.util.Locale ;
import java.util.regex.Pattern ;

/**
 * Pure matching logic of the search (no Android dependency, so it can be tested on its own).
 * Results are ranked by match quality first, then by usage score, then by their initial order.
 */
public final class SearchMatcher
{
	// Match quality, from best to worst
	public static final int NO_MATCH = Integer.MAX_VALUE ;
	public static final int NAME_PREFIX = 0 ;
	public static final int WORD_PREFIX = 1 ;
	public static final int INITIALS = 2 ;
	public static final int NAME_CONTAINS = 3 ;
	public static final int FOLDER = 4 ;

	// Patterns
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+") ;
	private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+|(?<=\\p{Ll})(?=\\p{Lu})") ;


	/**
	 * Searchable form of a name: normalized full text, words and initials.
	 */
	public static final class Key
	{
		final String full ;
		final List<String> words ;
		final String initials ;

		public Key(String name)
		{
			full = normalize(name) ;
			words = new ArrayList<>() ;
			StringBuilder initialsBuilder = new StringBuilder() ;
			if(name != null)
				for(String word : WORD_SEPARATOR.split(name))
				{
					String normalized = normalize(word) ;
					if(normalized.isEmpty()) continue ;
					words.add(normalized) ;
					initialsBuilder.append(normalized.charAt(0)) ;
				}
			initials = initialsBuilder.toString() ;
		}


		/**
		 * Return how well this name matches an already normalized search (NO_MATCH if it does not).
		 */
		int match(String search)
		{
			if(full.startsWith(search)) return NAME_PREFIX ;
			for(String word : words) if(word.startsWith(search)) return WORD_PREFIX ;
			if((search.length() >= 2) && (words.size() >= 2) && initials.startsWith(search)) return INITIALS ;
			if(full.contains(search)) return NAME_CONTAINS ;
			return NO_MATCH ;
		}
	}


	/**
	 * Element to rank: the name of the application and the names of the folders containing it.
	 */
	public static final class Entry<T>
	{
		final T item ;
		final Key name ;
		final List<Key> folders ;
		final double score ;
		final int order ;

		public Entry(T item, String name, List<String> folders, double score, int order)
		{
			this.item = item ;
			this.name = new Key(name) ;
			this.folders = new ArrayList<>() ;
			if(folders != null) for(String folder : folders) this.folders.add(new Key(folder)) ;
			this.score = score ;
			this.order = order ;
		}
	}


	/**
	 * Rank the entries matching the search (the search is normalized here).
	 */
	public static <T> ArrayList<T> rank(List<Entry<T>> entries, String search)
	{
		String normalized_search = normalize(search) ;
		ArrayList<Entry<T>> matches = new ArrayList<>() ;
		ArrayList<Integer> qualities = new ArrayList<>() ;
		for(Entry<T> entry : entries)
		{
			int quality = entry.name.match(normalized_search) ;
			// Folder names act as labels, matched only from the start of their words to avoid noise
			if(quality == NO_MATCH)
				for(Key folder : entry.folders)
					if(folder.match(normalized_search) < NAME_CONTAINS)
						{
							quality = FOLDER ;
							break ;
						}
			if(quality == NO_MATCH) continue ;
			matches.add(entry) ;
			qualities.add(quality) ;
		}

		// Sort by match quality, then most used first, then initial (alphabetical) order
		ArrayList<Integer> indexes = new ArrayList<>() ;
		for(int i = 0 ; i < matches.size() ; i++) indexes.add(i) ;
		Collections.sort(indexes, (a, b) -> {
				int compare = Integer.compare(qualities.get(a), qualities.get(b)) ;
				if(compare != 0) return compare ;
				compare = Double.compare(matches.get(b).score, matches.get(a).score) ;
				if(compare != 0) return compare ;
				return Integer.compare(matches.get(a).order, matches.get(b).order) ;
			}) ;
		ArrayList<T> result = new ArrayList<>() ;
		for(int index : indexes) result.add(matches.get(index).item) ;
		return result ;
	}


	/**
	 * Order used without search: the most used entries first (at most <code>count</code>,
	 * only those already used), then all the others in their initial order.
	 */
	public static <T> ArrayList<T> mostUsedFirst(List<Entry<T>> entries, int count)
	{
		// Select the most used entries
		ArrayList<Entry<T>> used = new ArrayList<>() ;
		for(Entry<T> entry : entries) if(entry.score > 0) used.add(entry) ;
		Collections.sort(used, (a, b) -> {
				int compare = Double.compare(b.score, a.score) ;
				return (compare != 0) ? compare : Integer.compare(a.order, b.order) ;
			}) ;
		if(used.size() > count) used.subList(count, used.size()).clear() ;

		// Put them first, followed by the other entries in their initial order
		ArrayList<T> result = new ArrayList<>() ;
		for(Entry<T> entry : used) result.add(entry.item) ;
		for(Entry<T> entry : entries) if(!used.contains(entry)) result.add(entry.item) ;
		return result ;
	}


	/**
	 * Lowercase a string and strip its accents to allow case/accent insensitive search.
	 */
	public static String normalize(String text)
	{
		if(text == null) return "" ;
		return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT).trim() ;
	}
}
