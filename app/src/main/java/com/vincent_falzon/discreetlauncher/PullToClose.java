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
import android.view.MotionEvent ;
import android.view.View ;
import android.view.animation.DecelerateInterpolator ;
import androidx.annotation.NonNull ;
import androidx.recyclerview.widget.RecyclerView ;

/**
 * "Pull and release" closure of a panel containing a list: once the list cannot scroll further
 * towards the closing direction, the panel follows the finger with resistance; releasing beyond a
 * threshold closes it, otherwise it goes back in place. Small finger slides while tapping are
 * therefore never taken as a closure.
 */
public class PullToClose implements RecyclerView.OnItemTouchListener
{
	// Constants
	private static final float RESISTANCE = 0.5f ;
	private static final float THRESHOLD_DP = 72 ;
	private static final long SPRING_BACK_DURATION = 200 ;

	// Attributes
	private final View panel ;
	private final View[] scrollables ;
	private final int direction ;
	private final Runnable closeAction ;
	private final float threshold ;
	private boolean enabled ;
	private boolean pulling ;
	private float anchor_y ;
	private float last_y ;


	/**
	 * Constructor.
	 * @param panel        view moved while pulling (and closed)
	 * @param direction    1 = closes by pulling downwards, -1 = upwards
	 * @param closeAction  action closing the panel (expected to continue from the current offset)
	 * @param scrollables  views which must be unable to scroll further before pulling
	 */
	public PullToClose(View panel, int direction, Runnable closeAction, View... scrollables)
	{
		this.panel = panel ;
		this.direction = direction ;
		this.closeAction = closeAction ;
		this.scrollables = scrollables ;
		threshold = THRESHOLD_DP * panel.getResources().getDisplayMetrics().density ;
		enabled = true ;
		pulling = false ;
	}


	/**
	 * Allow or forbid the pulling (ex: forbidden while the panel is closing).
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled ;
		if(!enabled) pulling = false ;
	}


	/**
	 * Check if one of the lists can still scroll in the direction that the finger is pushing.
	 */
	private boolean canScrollTowardsEdge()
	{
		// Pulling downwards shows the beginning of the list: check if it can scroll up, and conversely
		for(View view : scrollables)
			if((view != null) && view.canScrollVertically(-direction)) return true ;
		return false ;
	}


	/**
	 * Watch every event before the list and its items, start pulling when appropriate.
	 */
	@Override
	public boolean onInterceptTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent event)
	{
		if(!enabled) return false ;
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN :
				pulling = false ;
				last_y = event.getRawY() ;
				return false ;
			case MotionEvent.ACTION_MOVE :
				float y = event.getRawY() ;
				float dy = y - last_y ;
				last_y = y ;
				if((dy * direction > 0) && !canScrollTowardsEdge())
					{
						// The list is at its edge and the finger keeps pushing: start pulling the panel
						pulling = true ;
						anchor_y = y - dy ;
						panel.animate().cancel() ;
						update(y) ;
						return true ;
					}
				return false ;
			default :
				return false ;
		}
	}


	/**
	 * Follow the finger while pulling, then close or go back in place.
	 */
	@Override
	public void onTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent event)
	{
		if(!pulling) return ;
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_MOVE :
				update(event.getRawY()) ;
				break ;
			case MotionEvent.ACTION_UP :
				pulling = false ;
				if(Math.abs(panel.getTranslationY()) >= threshold) closeAction.run() ;
					else springBack() ;
				break ;
			case MotionEvent.ACTION_CANCEL :
				pulling = false ;
				springBack() ;
				break ;
		}
	}


	/**
	 * Move the panel according to the finger (with resistance) and fade it a little.
	 */
	private void update(float y)
	{
		float pull = Math.max(0, (y - anchor_y) * direction) * RESISTANCE ;
		panel.setTranslationY(direction * pull) ;
		panel.setAlpha(1 - 0.4f * Math.min(1, pull / threshold)) ;
	}


	/**
	 * Put the panel back in place.
	 */
	private void springBack()
	{
		panel.animate().cancel() ;
		panel.animate().translationY(0).alpha(1).setDuration(SPRING_BACK_DURATION).setInterpolator(new DecelerateInterpolator(2f)).start() ;
	}


	// Needed to implement OnItemTouchListener
	@Override
	public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }
}
