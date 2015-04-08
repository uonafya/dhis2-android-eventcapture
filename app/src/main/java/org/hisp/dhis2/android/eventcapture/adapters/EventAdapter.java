/*
 * Copyright (c) 2014, Araz Abishov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis2.android.eventcapture.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.hisp.dhis2.android.eventcapture.adapters.rows.EventRow;
import org.hisp.dhis2.android.eventcapture.adapters.rows.EventRowType;

import java.util.List;

public class EventAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private List<EventRow> mEventRows;

    public EventAdapter(LayoutInflater inflater) {
        this.mInflater = inflater;
    }

    @Override
    public int getCount() {
        if (mEventRows != null) {
            return mEventRows.size();
        } else {
            return 0;
        }
    }

    @Override
    public Object getItem(int position) {
        if (mEventRows != null) {
            return mEventRows.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        if (mEventRows != null) {
            return mEventRows.get(position).getId();
        } else {
            return -1;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mEventRows != null) {
            return mEventRows.get(position).getView(mInflater, convertView, parent);
        } else {
            return null;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        if (mEventRows != null) {
            return mEventRows.get(position).isEnabled();
        } else {
            return false;
        }
    }

    @Override
    public int getViewTypeCount() {
        return EventRowType.values().length;
    }

    @Override
    public int getItemViewType(int position) {
        if (mEventRows != null) {
            return mEventRows.get(position).getViewType();
        } else {
            return 0;
        }
    }

    public void swapData(List<EventRow> eventRows) {
        boolean notifyAdapter = mEventRows != eventRows;
        mEventRows = eventRows;

        if (notifyAdapter) {
            notifyDataSetChanged();
        }
    }
}
