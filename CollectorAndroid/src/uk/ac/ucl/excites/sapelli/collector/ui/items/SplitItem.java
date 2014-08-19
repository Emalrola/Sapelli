/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector.ui.items;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/**
 * A composite Item subclass which consists of any 2 child items being shown underneath one another
 * 
 * @author benelliott
 */
public class SplitItem extends Item
{
	
	static public final int HORIZONTAL = LinearLayout.HORIZONTAL;
	static public final int VERTICAL = LinearLayout.VERTICAL;
	
	private int orientation;
	private List<Item> items;
	private List<Float> weights;
	
	/**
	 * @param orientation
	 */
	public SplitItem(int orientation)
	{
		this(null, orientation);
	}
	
	/**
	 * @param id
	 * @param orientation
	 */
	public SplitItem(Integer id, int orientation)
	{
		super(id);
		if(orientation < HORIZONTAL || orientation > VERTICAL)
			throw new IllegalArgumentException("Invalid orientation");
		this.orientation = orientation;
		items = new ArrayList<Item>();
		weights = new ArrayList<Float>();
	}
	
	/**
	 * @param item
	 * @param weight
	 */
	public SplitItem addItem(Item item, float weight)
	{
		items.add(item);
		weights.add(weight);
		return this;
	}
	
	@Override
	protected View createView(Context context, boolean recycleChildren)
	{
		LinearLayout ll = new LinearLayout(context); //instantiate a LinearLayout to hold the items
		ll.setOrientation(orientation);
		
		// Add items & sum weights:
		float weightSum = 0;
		for(int i = 0, s = items.size(); i < s; i++)
		{
			Item item = items.get(i);
			float itemWeight = weights.get(i);
			View itemView = item.getView(context, recycleChildren);
			itemView.setLayoutParams(new LinearLayout.LayoutParams(
					orientation == HORIZONTAL ? 0 :	LinearLayout.LayoutParams.MATCH_PARENT,
					orientation == HORIZONTAL ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
					itemWeight));
			ll.addView(itemView);
			weightSum += itemWeight;
		}
		
		// Set weight sum:
		ll.setWeightSum(weightSum);
		
		return ll;
	}
	
}
