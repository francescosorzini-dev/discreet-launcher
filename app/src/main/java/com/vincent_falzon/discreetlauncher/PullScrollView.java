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
import android.util.AttributeSet ;
import android.view.MotionEvent ;
import android.widget.ScrollView ;

/**
 * ScrollView supporting "pull and release" closure: as the parent of its content, it sees every
 * touch first, so the pulling is not stolen by the list inside or by its own scrolling.
 */
public class PullScrollView extends ScrollView
{
	// Attributes
	private PullToClose pullToClose ;


	/**
	 * Constructors.
	 */
	public PullScrollView(Context context) { super(context) ; }
	public PullScrollView(Context context, AttributeSet attrs) { super(context, attrs) ; }
	public PullScrollView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr) ; }


	/**
	 * Define the pulling behavior.
	 */
	public void setPullToClose(PullToClose pullToClose)
	{
		this.pullToClose = pullToClose ;
	}


	/**
	 * Take the touch when the pulling starts, before the content and the scrolling.
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event)
	{
		if((pullToClose != null) && pullToClose.shouldIntercept(event)) return true ;
		return super.onInterceptTouchEvent(event) ;
	}


	/**
	 * Follow the finger while pulling, otherwise scroll normally (and start pulling at the edge).
	 */
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if(pullToClose != null)
			{
				if(pullToClose.isPulling() || pullToClose.shouldIntercept(event))
					{
						pullToClose.handleTouch(event) ;
						return true ;
					}
			}
		return super.onTouchEvent(event) ;
	}
}
