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
import android.content.Context ;
import android.content.SharedPreferences ;
import java.util.HashMap ;
import java.util.Map ;

/**
 * Count how often each application is launched, giving more weight to recent launches.
 * Each launch adds 1 to the score of the application, and scores are halved every HALF_LIFE.
 * Data stays on the device, in a private preferences file.
 */
public class UsageTracker
{
	// Constants
	private static final String FILE = "usage_stats" ;
	private static final double HALF_LIFE_MS = 14d * 24 * 60 * 60 * 1000 ;


	/**
	 * Record a launch of the application (folders and the search are ignored).
	 */
	public static void recordLaunch(Context context, Application application)
	{
		// Ignore elements which are not real applications
		if((context == null) || (application == null)) return ;
		if((application instanceof Folder) || (application instanceof Search)) return ;
		String key = application.getComponentInfo() ;
		if(key == null) return ;

		// Add the launch to the decayed score and save it with the current time
		SharedPreferences store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE) ;
		long now = System.currentTimeMillis() ;
		double score = decayedScore(store.getString(key, null), now) + 1 ;
		store.edit().putString(key, score + ";" + now).apply() ;
	}


	/**
	 * Return the current score of all launched applications, indexed by component info.
	 */
	public static Map<String, Double> getScores(Context context)
	{
		Map<String, Double> scores = new HashMap<>() ;
		if(context == null) return scores ;
		long now = System.currentTimeMillis() ;
		for(Map.Entry<String, ?> entry : context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().entrySet())
			if(entry.getValue() instanceof String)
				scores.put(entry.getKey(), decayedScore((String)entry.getValue(), now)) ;
		return scores ;
	}


	/**
	 * Decode a stored "score;time" value and return the score decayed until the provided time.
	 */
	static double decayedScore(String stored, long now)
	{
		if(stored == null) return 0 ;
		try
		{
			String[] parts = stored.split(";") ;
			double score = Double.parseDouble(parts[0]) ;
			long time = Long.parseLong(parts[1]) ;
			double elapsed = Math.max(0, now - time) ;
			return score * Math.pow(0.5, elapsed / HALF_LIFE_MS) ;
		}
		catch(Exception exception)
		{
			// Corrupted value, start again from zero
			return 0 ;
		}
	}
}
