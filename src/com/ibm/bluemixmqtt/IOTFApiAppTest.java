package com.ibm.bluemixmqtt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.json.JSONObject;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.ibm.iotf.client.IoTFCReSTException;
import com.ibm.iotf.client.api.APIClient;
import com.ibm.iotf.sample.util.Utility;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

public class IOTFApiAppTest {

	private MqttHandler handler;
	private APIClient apiClient = null;
	
	private final String strKey = "AyanMukhAyanMukh";
	private final String uniqueParam = "myuniqueivparam1";

	private static final String DEVICE_TYPE = "MQTTDevice";
	private static final String DEVICE_ID = "TestDevice1";
	private static final String DEVICE_ID_TEST = "4213";

	private final static String locationToBeAdded = "{\"longitude\": 0, \"latitude\": 0, \"elevation\": "
			+ "0,\"measuredDateTime\": \"2015-23-07T11:23:23+00:00\"}";

	private final static String newlocationToBeAdded = "{\"longitude\": 10, \"latitude\": 20, \"elevation\": 0}";

	private final static String deviceInfoToBeAdded = "{\"serialNumber\": "
			+ "\"10087\",\"manufacturer\": \"IBM\",\"model\": \"7865\",\"deviceClass\": "
			+ "\"A\",\"description\": \"My RasPi100 Device\",\"fwVersion\": \"1.0.0\","
			+ "\"hwVersion\": \"1.0\",\"descriptiveLocation\": \"EGL C\"}";

	private final static String deviceToBeAdded = "{\"deviceId\": "
			+ "\"TestDevice1\",\"authToken\": \"password\"," + "\"location\": "
			+ locationToBeAdded + "," + "\"deviceInfo\": "
			+ deviceInfoToBeAdded + "," + "\"metadata\": {}}";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new IOTFApiAppTest().doApp();
	}

	/**
	 * Run the app
	 */
	public void doApp() {
		// Read properties from the conf file
		Properties props = MqttUtil.readProperties("MyData/application.prop");

		try {
			// Instantiate the class by passing the properties file
			this.apiClient = new APIClient(props);

			getAllHistoricalEventsByDeviceID();
			/*
			 * System.out.println("Adding a new device.."); addDevice();
			 * System.out.println("Get all devices.."); getAllDevices();
			 * System.out.println("Delete a device.."); deleteDevice();
			 * System.out.println("Success..Exiting..");
			 */
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

	}

	/**
	 * This sample showcases how to add a device using the Java Client Library.
	 * 
	 * @throws IoTFCReSTException
	 */
	private void addDevice() throws IoTFCReSTException {
		try {
			JsonParser parser = new JsonParser();
			JsonElement deviceInfo = parser.parse(deviceInfoToBeAdded);
			JsonElement location = parser.parse(locationToBeAdded);

			JsonObject response = this.apiClient.registerDevice(DEVICE_TYPE,
					DEVICE_ID, "Password", deviceInfo, location, null);

			System.out.println(response);
		} catch (IoTFCReSTException e) {
			System.out.println("HttpCode :" + e.getHttpCode()
					+ " ErrorMessage :: " + e.getMessage());
			// Print if there is a partial response
			System.out.println(e.getResponse());
		}
	}

	/**
	 * This sample showcases how to Delete a device using the Java Client
	 * Library.
	 * 
	 * @throws IoTFCReSTException
	 */
	private void deleteDevice() throws IoTFCReSTException {
		try {

			System.out.println("Removing device - " + DEVICE_ID);
			boolean status = this.apiClient
					.deleteDevice(DEVICE_TYPE, DEVICE_ID);
			System.out.println("Removal status - " + status);
		} catch (IoTFCReSTException e) {
			System.out.println("HttpCode :" + e.getHttpCode()
					+ " ErrorMessage :: " + e.getMessage());
			// Print if there is a partial response
			System.out.println(e.getResponse());
		}
	}

	/**
	 * This sample showcases how to retrieve all the devices in an organization
	 * using the Java Client Library.
	 * 
	 * @throws IoTFCReSTException
	 */
	private void getAllDevices() throws IoTFCReSTException {
		// Get all the devices of type SampleDT
		try {
			/**
			 * The Java ibmiotf client library provides an one argument
			 * constructor which can be used to control the output, for example,
			 * lets try to retrieve the devices in a sorted order based on
			 * device ID.
			 */

			ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
			parameters.add(new BasicNameValuePair("_sort", "deviceId"));

			JsonObject response = this.apiClient.retrieveDevices(DEVICE_TYPE,
					parameters);

			// The response will contain more parameters that will be used to
			// issue the next request. The result element will contain the current
			// list of devices
			JsonArray devices = response.get("results").getAsJsonArray();
			for (Iterator<JsonElement> iterator = devices.iterator(); iterator
					.hasNext();) {
				JsonElement deviceElement = iterator.next();
				JsonObject responseJson = deviceElement.getAsJsonObject();
				System.out.println(responseJson);
			}
		} catch (IoTFCReSTException e) {
			System.out.println("HttpCode :" + e.getHttpCode()
					+ " ErrorMessage :: " + e.getMessage());
			// Print if there is a partial response
			System.out.println(e.getResponse());
		}
	}
	/**
	 * Method to get the latest historical event and process it
	 */
	private void getAllHistoricalEventsByDeviceID() {
		// Get the historical events
		try {
			//Get the list of historical events by device type and device id
			JsonElement response = this.apiClient.getHistoricalEvents(
					DEVICE_TYPE, DEVICE_ID_TEST);
			JsonObject events = response.getAsJsonObject();
			JsonArray eventArray = events.getAsJsonArray("events");
			//Get the latest event
			JsonElement currentEvent = eventArray.get(0);
			JsonObject responseJson = currentEvent.getAsJsonObject();
			System.out.println("Most recent event - " + responseJson.toString());
			JsonObject evtObject = responseJson.getAsJsonObject("evt");
			System.out.println("Complete raw payload -" + evtObject.toString());
			String dString = evtObject.get("d").getAsString();
			System.out.println("Encrypted data part -" + dString);
			String processedData = IOTSecurityUtil.decryptDecodeString(dString.getBytes(), strKey, uniqueParam);
			System.out.println("Data part after decryption and decoding - " + processedData);
			String generatedChkSum = IOTSecurityUtil.getMD5(processedData);
			System.out.println("Generated checksum - " + generatedChkSum);
			String chkSum = evtObject.get("chksum").getAsString();
			System.out.println("MD5 Checksum stored in message -" + chkSum);
			if(generatedChkSum.equals(chkSum))
				System.out.println("Checksum validation successful");
			else
				System.out.println("Checksum validation failed");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This class implements as the application MqttHandler
	 * 
	 */
	private class AppMqttHandler extends MqttHandler {

		// Pattern to check whether the events comes from a device for an event
		Pattern pattern = Pattern.compile("iot-2/evt/");
		Pattern pattern1 = Pattern.compile("iot-2/type/"
				+ MqttUtil.DEFAULT_DEVICE_TYPE + "/id/(.+)/seq/"
				+ MqttUtil.DEFAULT_EVENT_ID + "/fmt/json");

		/**
		 * Once a subscribed message is received
		 */
		@Override
		public void messageArrived(String topic, MqttMessage mqttMessage)
				throws Exception {

			super.messageArrived(topic, mqttMessage);
			try {
				System.out.println("topic " + topic);

				Matcher matcher = pattern.matcher(topic);
				if (matcher.matches()) {
					String deviceid = "4213";
					String payload = new String(mqttMessage.getPayload());

					// Parse the payload in Json Format
					JSONObject jsonObject = new JSONObject(payload);

					if (jsonObject.containsKey("event")) {

						try {
							String strReq = jsonObject.getString("event");

						} catch (Exception ee) {
							ee.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * A thread to send message to the device
		 * 
		 */
		private class sendMessageToDevice extends Thread {
			private String deviceid = null;
			private String command = null;
			private JSONObject obj = null;

			public sendMessageToDevice(String deviceId1, String command1,
					JSONObject obj1) {
				this.command = command1;
				this.obj = obj1;
				this.deviceid = deviceId1;
			}

			public void run() {

				handler.publish("iot-2/cmd/", obj.toString(), false, 0);
			}
		}

	}
}
