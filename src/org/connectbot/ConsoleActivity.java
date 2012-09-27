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

import org.connectbot.bean.HostBean;
import org.connectbot.service.OnBridgeConnectionListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

public class ConsoleActivity extends FragmentActivity implements ConsoleFragment.ConsoleFragmentContainer, HostListFragment.HostListFragmentContainer, OnBridgeConnectionListener {
	public final static String TAG = "ConnectBot.ConsoleActivity";

	protected TerminalManager bound = null;

	// determines whether or not menuitem accelerators are bound
	// otherwise they collide with an external keyboard's CTRL-char
	private boolean hardKeyboard = false;

	protected Uri requested;

	ConsoleFragment fragmentConsole;
	HostListFragment fragmentHostList;

	private static final int MSG_INVALIDATE_MENU = 1;

	private static final int MSG_CLOSE_BRIDGE = 2;

	private Handler mUiHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_INVALIDATE_MENU:
				invalidateOptionsMenu();
				break;
			case MSG_CLOSE_BRIDGE:
				final TerminalBridge bridge = (TerminalBridge) msg.obj;
				if (bridge.isAwaitingClose()) {
					fragmentConsole.removeBridgeView(bridge);
				}
				break;
			}
		}
	};

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// let manager know about our event handling services
			bound.addOnBridgeConnectionListener(ConsoleActivity.this);

			Log.d(TAG, String.format("Connected to TerminalManager and found bridges.size=%d", bound.bridges.size()));

			bound.setResizeAllowed(true);

			fragmentConsole.setupConsoles();

			// update our listview binder to find the service
			if (fragmentHostList != null) fragmentHostList.updateList();
		}

		public void onServiceDisconnected(ComponentName className) {
			// tell each bridge to forget about our prompt handler
			synchronized (bound.bridges) {
				for (TerminalBridge bridge : bound.bridges)
					bridge.promptHelper.setHandler(null);
			}

			bound.removeOnBridgeConnectionListener(ConsoleActivity.this);
			fragmentConsole.destroyConsoles();

			if (fragmentHostList != null) {
				fragmentHostList.updateList();
			}

			bound = null;
		}
	};

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		hardKeyboard = getResources().getConfiguration().keyboard ==
				Configuration.KEYBOARD_QWERTY;

		this.setContentView(R.layout.act_console);

		fragmentConsole = ConsoleFragment.newInstance();

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.consoleFrame, fragmentConsole);
		if (findViewById(R.id.listFrame) != null) {
			fragmentHostList = HostListFragment.newInstance();
			ft.replace(R.id.listFrame, fragmentHostList);
		}
		ft.commit();
	}

	/**
	 *
	 */
	private void configureOrientation() {
		/*String rotateDefault;
		if (getResources().getConfiguration().keyboard == Configuration.KEYBOARD_NOKEYS)
			rotateDefault = PreferenceConstants.ROTATION_PORTRAIT;
		else
			rotateDefault = PreferenceConstants.ROTATION_LANDSCAPE;

		String rotate = prefs.getString(PreferenceConstants.ROTATION, rotateDefault);
		if (PreferenceConstants.ROTATION_DEFAULT.equals(rotate))
			rotate = rotateDefault;

		// request a forced orientation if requested by user
		if (PreferenceConstants.ROTATION_LANDSCAPE.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			forcedOrientation = true;
		} else if (PreferenceConstants.ROTATION_PORTRAIT.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			forcedOrientation = true;
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			forcedOrientation = false;
		}*/

		// Hide Host List fragment in portrait mode
		int orientation = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
		Log.d("ConnectBotTablet", "Orientation: "+orientation);
		final View listFrame = findViewById(R.id.listFrame);
		if (listFrame != null) {
			if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180 ) {
				findViewById(R.id.listFrame).setVisibility(View.VISIBLE);
			} else {
				findViewById(R.id.listFrame).setVisibility(View.GONE);
			}
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause called");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called");

		configureOrientation();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		Log.d(TAG, "onNewIntent called");

		requested = intent.getData();

		if (requested == null) {
			Log.e(TAG, "Got null intent data in onNewIntent()");
			return;
		}

		if (bound == null) {
			Log.e(TAG, "We're not bound in onNewIntent()");
			return;
		}

		fragmentConsole.startConsole(requested);
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(connection);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		Log.d(TAG, String.format("onConfigurationChanged; requestedOrientation=%d, newConfig.orientation=%d", getRequestedOrientation(), newConfig.orientation));
		/*if (bound != null) {
			if (forcedOrientation &&
					(newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE &&
					getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) ||
					(newConfig.orientation != Configuration.ORIENTATION_PORTRAIT &&
					getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
				bound.setResizeAllowed(false);
			else
				bound.setResizeAllowed(true);

			bound.hardKeyboardHidden = (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);

			mKeyboardButton.setVisibility(bound.hardKeyboardHidden ? View.VISIBLE : View.GONE);
		}*/

		configureOrientation();
	}

	public TerminalManager getTerminalManager() {
		return bound;
	}

	public void onTerminalViewChanged(HostBean host) {
		fragmentHostList.updateHandler.sendEmptyMessage(-1);
		fragmentHostList.setCurrentSelected(host);
		mUiHandler.sendEmptyMessage(MSG_INVALIDATE_MENU);
	}

	public boolean startConsoleActivity(Uri uri) {
		fragmentConsole.startConsole(uri);

		return true;
	}

	/* (non-Javadoc)
	 * @see org.connectbot.service.OnBridgeConnectionListener#onBridgeConnected(org.connectbot.service.TerminalBridge)
	 */
	public void onBridgeConnected(TerminalBridge bridge) {
		mUiHandler.sendEmptyMessage(MSG_INVALIDATE_MENU);
	}

	/* (non-Javadoc)
	 * @see org.connectbot.service.OnBridgeConnectionListener#onBridgeDisconnected(org.connectbot.service.TerminalBridge)
	 */
	public void onBridgeDisconnected(TerminalBridge bridge) {
		final Message msg = mUiHandler.obtainMessage(MSG_CLOSE_BRIDGE, bridge);
		mUiHandler.sendMessage(msg);

		mUiHandler.sendEmptyMessage(MSG_INVALIDATE_MENU);
	}
}
