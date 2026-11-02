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
import android.content.Context ;
import android.util.AttributeSet ;
import android.widget.ScrollView ;

/**
 * ScrollView of the favorites panel (the "pull and release" closure is now handled by the activity,
 * see PullToClose and ActivityMain.dispatchTouchEvent()).
 */
public class PullScrollView extends ScrollView
{
	/**
	 * Constructors.
	 */
	public PullScrollView(Context context) { super(context) ; }
	public PullScrollView(Context context, AttributeSet attrs) { super(context, attrs) ; }
	public PullScrollView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr) ; }
}
