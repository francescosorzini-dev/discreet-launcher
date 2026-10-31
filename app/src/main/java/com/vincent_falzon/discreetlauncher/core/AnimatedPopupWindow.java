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
import android.view.View ;
import android.widget.PopupWindow ;
import com.vincent_falzon.discreetlauncher.Utils ;

/**
 * PopupWindow which closes with the slow disappearance of the launcher, whatever closes it
 * (back key, touch outside, code). dismissNow() closes it immediately.
 */
public class AnimatedPopupWindow extends PopupWindow
{
	// Attributes
	private final int direction ;
	private boolean closing ;


	/**
	 * Constructor (<code>direction</code>: 1 = disappears downwards, -1 = upwards, 0 = in place).
	 */
	public AnimatedPopupWindow(View contentView, int width, int height, boolean focusable, int direction)
	{
		super(contentView, width, height, focusable) ;
		this.direction = direction ;
		closing = false ;
	}


	/**
	 * Close the popup with the slow disappearance (immediately if it is not visible yet).
	 */
	@Override
	public void dismiss()
	{
		if(closing) return ;
		View content = getContentView() ;
		if(!isShowing() || (content == null) || (content.getAlpha() == 0))
			{
				dismissNow() ;
				return ;
			}
		closing = true ;
		Utils.slowDisappear(content, direction, this::dismissNow) ;
	}


	/**
	 * Close the popup immediately (ex: when leaving the home screen).
	 */
	public void dismissNow()
	{
		View content = getContentView() ;
		if(content != null) content.animate().cancel() ;
		closing = false ;
		if(isShowing()) super.dismiss() ;
	}
}
