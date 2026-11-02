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
import android.view.VelocityTracker ;
import android.view.View ;
import android.view.ViewConfiguration ;
import android.view.animation.DecelerateInterpolator ;

/**
 * "Pull and release" closure of a panel containing a list: once the list cannot scroll further
 * towards the closing direction, the panel follows the finger with resistance; releasing beyond a
 * threshold, or with a quick flick, closes it, otherwise it goes back in place.
 * The touches are watched by the activity itself (dispatchTouchEvent), before any view: the lists,
 * the ScrollView or a pressed element can therefore never keep the gesture for themselves.
 */
public class PullToClose
{
	// Constants
	private static final float RESISTANCE = 0.5f ;
	private static final float THRESHOLD_DP = 72 ;
	private static final float FLING_DP_PER_SECOND = 700 ;
	private static final long SPRING_BACK_DURATION = 200 ;

	// Attributes
	private final View panel ;
	private final View[] scrollables ;
	private final int direction ;
	private final Runnable closeAction ;
	private final float threshold ;
	private final float fling_velocity ;
	private final int touch_slop ;
	private VelocityTracker tracker ;
	private boolean pulling ;
	private float down_y ;
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
		float density = panel.getResources().getDisplayMetrics().density ;
		threshold = THRESHOLD_DP * density ;
		fling_velocity = FLING_DP_PER_SECOND * density ;
		touch_slop = ViewConfiguration.get(panel.getContext()).getScaledTouchSlop() ;
		pulling = false ;
	}


	/**
	 * Return the panel which this gesture can close.
	 */
	public View getPanel()
	{
		return panel ;
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
	 * Watch an event of the gesture: return <code>true</code> when the pulling starts (the views must
	 * then receive a cancellation, and the following events must be given to handleTouch()). The
	 * pulling only starts once the finger has moved more than the touch slop, mainly vertically.
	 */
	public boolean shouldIntercept(MotionEvent event)
	{
		if(pulling) return true ;
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN :
				down_y = event.getRawY() ;
				last_y = down_y ;
				if(tracker != null) tracker.recycle() ;
				tracker = VelocityTracker.obtain() ;
				tracker.addMovement(event) ;
				return false ;
			case MotionEvent.ACTION_MOVE :
				if(tracker != null) tracker.addMovement(event) ;
				float y = event.getRawY() ;
				float dy = y - last_y ;
				last_y = y ;
				boolean beyond_slop = (y - down_y) * direction > touch_slop ;
				if(beyond_slop && (dy * direction > 0) && !canScrollTowardsEdge())
					{
						// The list is at its edge and the finger keeps pushing: start pulling the panel
						pulling = true ;
						anchor_y = y ;
						panel.animate().cancel() ;
						return true ;
					}
				return false ;
			default :
				return false ;
		}
	}


	/**
	 * Return <code>true</code> while the panel is being pulled.
	 */
	public boolean isPulling()
	{
		return pulling ;
	}


	/**
	 * Follow the finger while pulling, then close or go back in place.
	 */
	public void handleTouch(MotionEvent event)
	{
		if(!pulling) return ;
		if(tracker != null) tracker.addMovement(event) ;
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_MOVE :
				update(event.getRawY()) ;
				break ;
			case MotionEvent.ACTION_UP :
				pulling = false ;
				float velocity = 0 ;
				if(tracker != null)
					{
						tracker.computeCurrentVelocity(1000) ;
						velocity = tracker.getYVelocity() * direction ;
					}
				// Far enough, or released with a quick flick in the closing direction (after a short pull,
				// so that a small slide of the finger while tapping never closes the panel)
				float pulled = Math.abs(panel.getTranslationY()) ;
				if((pulled >= threshold) || ((velocity >= fling_velocity) && (pulled >= threshold / 3))) closeAction.run() ;
					else springBack() ;
				release() ;
				break ;
			case MotionEvent.ACTION_CANCEL :
				pulling = false ;
				springBack() ;
				release() ;
				break ;
		}
	}


	/**
	 * Abandon the gesture (ex: a second finger is put on the screen).
	 */
	public void abort()
	{
		if(pulling) springBack() ;
		pulling = false ;
		release() ;
	}


	/**
	 * Free the velocity tracker.
	 */
	private void release()
	{
		if(tracker != null) tracker.recycle() ;
		tracker = null ;
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
	public void springBack()
	{
		panel.animate().cancel() ;
		panel.animate().translationY(0).alpha(1).setDuration(SPRING_BACK_DURATION).setInterpolator(new DecelerateInterpolator(2f)).start() ;
	}
}
