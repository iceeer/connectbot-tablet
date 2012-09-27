/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package org.connectbot;

import org.connectbot.service.TerminalManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

public class HostListActivity extends FragmentActivity implements
		HostListFragment.HostListFragmentContainer {
	protected TerminalManager mManager = null;

	HostListFragment mFragmentHostList;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mManager = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			mFragmentHostList.updateList();
		}

		public void onServiceDisconnected(ComponentName className) {
			mManager = null;
			mFragmentHostList.updateList();
		}
	};

	@Override
	public void onStart() {
		super.onStart();

		// start the terminal manager service
		this.bindService(new Intent(this, TerminalManager.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop() {
		super.onStop();
		unbindService(mConnection);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		// If we are on tablet, skip this activity and move straight to console
		// activity
		// TODO: Need a new workaround when new OS are released
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			Intent i = new Intent(this, ConsoleActivity.class);
			startActivity(i);
			finish();
			return;
		}

		setContentView(R.layout.act_hostlist);

		this.setTitle(String.format("%s: %s", getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_hosts_list)));

		mFragmentHostList = HostListFragment.newInstance();

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.listFrame, mFragmentHostList);
		// ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		ft.commit();
	}

	public boolean startConsoleActivity(Uri uri) {
		Intent intent = new Intent(this, ConsoleActivity.class);
		intent.setData(uri);
		startActivity(intent);

		return true;
	}

	public TerminalManager getTerminalManager() {
		return mManager;
	}
}
