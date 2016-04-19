package com.ibm.bluemixmqtt;

import java.util.TimerTask;

public class TimeOutTask extends TimerTask {

	boolean isTimedOut = false;

	@Override
	public void run() {
		isTimedOut = true;
	}
}
