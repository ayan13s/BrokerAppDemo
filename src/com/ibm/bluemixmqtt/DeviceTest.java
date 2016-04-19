package com.ibm.bluemixmqtt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import com.ibm.bluemixmqtt.IOTSecurityUtil;

import org.apache.commons.json.JSONException;
import org.apache.commons.json.JSONObject;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class DeviceTest {

	private int count = 0;
	private int totalcount = 0;
	private MqttHandler handler = null;
	private String strAuthStorageLocation = null;
	private boolean otpValidationDone = false;
	private String deviceIdentifier = null;
	private Map<String, ServerAuthVO> hmServerAuth = new HashMap<String, ServerAuthVO>();
	private int otpRetryCount = 0;
	boolean isSSL = false;
	boolean isOTPCapable = false;
	boolean isUIDValidationNeeded = false;
	boolean isUIDStoragePossible = false;
	private final String strKey = "AyanMukhAyanMukh";
	private final String uniqueParam = "myuniqueivparam1";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new DeviceTest().doDevice();
	}

	/**
	 * Run the device
	 */
	public void doDevice() {
		// Read properties from the conf file
		Properties props = MqttUtil.readProperties("MyData/device.conf");

		String org = props.getProperty("org");
		String id = props.getProperty("deviceid");
		deviceIdentifier = id;
		String authmethod = "use-token-auth";
		String authtoken = props.getProperty("token");
		// isSSL property
		String sslStr = props.getProperty("isSSL");
		String otpCapable = props.getProperty("isOTPCapableDevice");
		String uidValidationNeeded = props.getProperty("isUIDValidation");
		String strOTPRetryCount = props.getProperty("otpRetryCount");
		String uidStoragePossible = props.getProperty("isUIDStorageCapable");
		strAuthStorageLocation = props.getProperty("uidLocation");

		otpRetryCount = Integer.valueOf(strOTPRetryCount);

		if (sslStr.equals("T")) {
			isSSL = true;
		}
		if (otpCapable.equals("T")) {
			isOTPCapable = true;
		}
		if (uidValidationNeeded.equals("T")) {
			isUIDValidationNeeded = true;
		}
		if (uidStoragePossible.equals("T")) {
			isUIDStoragePossible = true;
		}

		if (isUIDStoragePossible && strAuthStorageLocation == null) {
			System.out.println("uidLocation is missing...");
			System.exit(1);
		}

		System.out.println("org: " + org);
		System.out.println("id: " + id);
		System.out.println("authmethod: " + authmethod);
		System.out.println("authtoken: " + authtoken);
		System.out.println("isSSL: " + isSSL);
		System.out.println("isOTPCapable: " + isOTPCapable);
		System.out.println("isUIDValidationNeeded: " + isUIDValidationNeeded);

		String serverHost = org + MqttUtil.SERVER_SUFFIX;

		// Format: d:<orgid>:<type-id>:<divice-id>
		String clientId = "d:" + org + ":" + MqttUtil.DEFAULT_DEVICE_TYPE + ":"
				+ id;
		handler = new DeviceMqttHandler();
		handler.connect(serverHost, clientId, authmethod, authtoken, isSSL);

		// Subscribe the Command events
		// iot-2/cmd/<cmd-type>/fmt/<format-id>
		handler.subscribe("iot-2/cmd/" + MqttUtil.DEFAULT_CMD_ID + "/fmt/json",
				0);

		// Format the Json String
		JSONObject testObj = new JSONObject();
		JSONObject testInnerObj = new JSONObject();
		try {
			testInnerObj.put("evt", "Test");
			testInnerObj.put("fld", "This is a test message");
			String checkSum = IOTSecurityUtil.getMD5(testInnerObj.toString());
			testObj.put("chksum", checkSum);
			testObj.put("ts", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
					.format(new Date()));
			testObj.put("d", IOTSecurityUtil.encryptEncodeString(
					testInnerObj.toString(), strKey, uniqueParam));
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		handler.publish("iot-2/evt/" + "eid" + "/fmt/json", testObj.toString(),
				true, 1);
		// IF device is OTP enabled send initiate OTP validation request to
		// server
		if (isOTPCapable) {
			System.out
					.println("Device initiating OTP validation.. Sending OTP validation request to server");
			initiateOTPAuth();

			while (!otpValidationDone) {
				try {
					Thread.sleep(3000);
				} catch (Exception te) {
					te.printStackTrace();
				}
			}
			;
			System.out.println("OTP validation successful...");
		}

		// Check if UID validation is needed
		// UID validation can only be done if OTP validation is done or device
		// is not OTP validation capable
		if (isUIDValidationNeeded && (otpValidationDone || !isOTPCapable)) {
			System.out
					.println("Device initiating server UID(MAC address) validation.. Sending UID validation request to server");
			initiateUIDAuth();
		} else {
			System.out
					.println("UID validation is not needed or OTP validation couldn't be done");
		}
		try {
			Thread.sleep(30000);
		} catch (Exception te) {
			te.printStackTrace();
		}
		while (totalcount < 20) {

			// Format the Json String
			JSONObject contObj = new JSONObject();
			JSONObject jsonObj = new JSONObject();
			try {
				contObj.put("count", count);
				contObj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
						.format(new Date()));
				jsonObj.put("d", contObj);
				jsonObj.put("event", "publish");
			} catch (JSONException e1) {
				e1.printStackTrace();
			}

			System.out.println("Send count as " + count);

			// Publish device events to the app
			// iot-2/evt/<event-id>/fmt/<format>
			handler.publishBytes("iot-2/evt/" + MqttUtil.DEFAULT_EVENT_ID
					+ "/fmt/json", IOTSecurityUtil.encryptString(
					jsonObj.toString(), strKey, uniqueParam), true, 0);

			count++;
			totalcount++;

			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println("Max Count reached, try to disconnect");
		handler.disconnect();
	}

	private void initiateUIDAuth() {
		// Create the request for server unique id
		JSONObject idObj = new JSONObject();
		try {
			idObj.put("event", "server_uid_request");
			idObj.put("deviceId", deviceIdentifier);
		} catch (JSONException e1) {
			System.out.println("Exception occured");
			e1.printStackTrace();
		}
		new sendMessageToServer("server_uid_request", idObj).start();
	}

	private void initiateOTPAuth() {
		// Create the request for OTP
		JSONObject idObj1 = new JSONObject();
		// deviceUid = strMacId;
		try {
			idObj1.put("event", "server_otp_request");
			idObj1.put("deviceId", deviceIdentifier);
		} catch (JSONException e1) {
			System.out.println("Exception occured");
			e1.printStackTrace();
		}
		new sendMessageToServer("server_otp_request", idObj1).start();
		System.out.println("otp request sent....");

	}

	/**
	 * This class implements as the device MqttHandler
	 * 
	 */
	private class DeviceMqttHandler extends MqttHandler {

		@Override
		public void messageArrived(String topic, MqttMessage mqttMessage)
				throws Exception {
			super.messageArrived(topic, mqttMessage);

			// Check whether the event is a command event from app
			if (topic.equals("iot-2/cmd/" + MqttUtil.DEFAULT_CMD_ID
					+ "/fmt/json")) {
				byte[] rawPayload = mqttMessage.getPayload();
				System.out.println("Raw payload before decryption - "
						+ rawPayload);
				String payload = IOTSecurityUtil.decryptString(
						mqttMessage.getPayload(), strKey, uniqueParam);
				System.out.println("Payload after decryption - " + payload);
				JSONObject jsonObject = new JSONObject(payload);
				String cmd = jsonObject.getString("cmd");

				if (cmd != null && cmd.equals("server_uid_response")) {
					try {
						String strServerUID = jsonObject.getString("uid");
						String strKey = jsonObject.getString("appid");
						System.out.println("Unique id received from server - "
								+ strServerUID);
						/*
						 * Save the unique id in memory as well as in file For
						 * very small devices if there is no storage capacity
						 * then uid request would be sent to server after every
						 * restart
						 */
						ServerAuthVO sAuth = new ServerAuthVO();
						sAuth.setAppKey(strKey);
						sAuth.setUid(strServerUID);
						sAuth.setOTPDone(otpValidationDone);
						sAuth.setOTPNeeded(isOTPCapable);
						sAuth.setFromFile(false);
						hmServerAuth.put(strKey, sAuth);
						if (isUIDStoragePossible) {
							sAuth.setFromFile(true);
							boolean success = saveAuthObjInFile(
									strAuthStorageLocation, sAuth);
							if (success) {
								System.out
										.println("Server Auth object has been stored into file");

							}
						}
					} catch (Exception ee) {
						System.out
								.println("Error in server UID response processing");
						ee.printStackTrace();
					}

				} else if (cmd != null && cmd.equals("server_otp_response")) {
					String inOTP = null;
					System.out
							.println("Please enter the OTP received in registered email within 5 mins...");
					Scanner in = new Scanner(System.in);
					// TBD- Following piece of code should be replaced with a
					// proper timer
					while (true) {
						try {
							System.out.println("Waiting for input...");
							Thread.currentThread();
							Thread.sleep(20);
							System.out.println("Enter OTP from server : ");
							inOTP = in.next();
							if (inOTP != null) { // if user type STOP in
													// terminal
								break;
							}
						} catch (InterruptedException ie) {
							// If this thread was intrrupted by nother thread
							ie.printStackTrace();
						}
					}
					JSONObject idObj = new JSONObject();
					try {
						idObj.put("event", "device_otp_response");
						idObj.put("deviceid", deviceIdentifier);
						idObj.put("otp", inOTP);
					} catch (JSONException e1) {
						System.out.println("Exception occured");
						e1.printStackTrace();
					}

					new sendMessageToServer("device_otp_response", idObj)
							.start();

				} else if (cmd != null && cmd.equals("server_otp_validate")) {
					boolean isOTPValid1 = jsonObject.getBoolean("isOTPValid");
					boolean isTimeOut1 = jsonObject.getBoolean("isTimeOut");
					if (isOTPValid1 && !isTimeOut1) {
						otpValidationDone = true;
						System.out.println("OTP Validation complete");

					} else if (otpRetryCount > 0) {
						System.out
								.println("OTP Validation failed... retrying..");
						otpRetryCount--;
						initiateOTPAuth();
					} else {
						System.out
								.println("OTP Validation failed.. Shutting down..");
						System.exit(1);
					}
				} else if (cmd != null && cmd.equals("reset")) {
					int resetCount = jsonObject.getInt("count");
					String uIDSent = jsonObject.getString("uid");
					String appIDSent = jsonObject.getString("appid");
					if (isUIDValidationNeeded) {
						ServerAuthVO svo = hmServerAuth.get(appIDSent);
						if (svo != null) {
							if (null != uIDSent && svo.getUid().equals(uIDSent)) {
								System.out.println(uIDSent);
								System.out.println(svo.getUid());
								System.out
										.println("UID matching with server is successful... executing command");
								count = resetCount;
								System.out
										.println("Received reset instructions from server.. resetting count to 0");
							}
						}
					} else {
						count = resetCount;
						System.out
								.println("Received reset instructions from server.. resetting count to 0");
					}
				}

			}
		}

		private boolean saveAuthObjInFile(String strFileName,
				ServerAuthVO serverObj) {
			FileOutputStream fout = null;
			ObjectOutputStream oos = null;
			boolean isSuccess = false;
			try {
				fout = new FileOutputStream(strFileName);
				oos = new ObjectOutputStream(fout);
				oos.writeObject(serverObj);
				isSuccess = true;
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					oos.close();
					fout.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return isSuccess;
		}

		private ServerAuthVO readAuthObjFromFile(String strFileName) {
			FileInputStream fin = null;
			ObjectInputStream ois = null;
			ServerAuthVO authVO = null;
			try {
				fin = new FileInputStream(strFileName);
				ois = new ObjectInputStream(fin);
				authVO = (ServerAuthVO) ois.readObject();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					ois.close();
					fin.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return authVO;
		}
	}

	private class sendMessageToServer extends Thread {
		private String eventss = null;
		private JSONObject objtd = null;

		public sendMessageToServer(String events, JSONObject obj1) {
			this.objtd = obj1;
			this.eventss = events;
		}

		public void run() {
			// Publish command to one specific device
			// iot-2/type/<type-id>/id/<device-id>/cmd/<cmd-id>/fmt/<format-id>
			handler.publishBytes("iot-2/evt/" + MqttUtil.DEFAULT_EVENT_ID
					+ "/fmt/json", IOTSecurityUtil.encryptString(
					objtd.toString(), strKey, uniqueParam), true, 2);
			try {
				sendMessageToServer.currentThread().join();
			} catch (Exception te) {
				te.printStackTrace();
			}
		}

	}

}
