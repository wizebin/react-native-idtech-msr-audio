//
//  IDTECH_MSR_audio
//
//  Created by Peace Chen on 2/27/2018.
//  Copyright © 2018 OnceThere. All rights reserved.
//

package com.oncethere.idtechmsraudio;

import IDTech.MSR.XMLManager.StructConfigParameters;
import IDTech.MSR.uniMag.Common;
import IDTech.MSR.uniMag.StateList;
import IDTech.MSR.uniMag.uniMagReader;
import IDTech.MSR.uniMag.uniMagReaderMsg;
import IDTech.MSR.uniMag.UniMagTools.uniMagReaderToolsMsg;
import IDTech.MSR.uniMag.UniMagTools.uniMagSDKTools;
import IDTech.MSR.uniMag.uniMagReader.ReaderType;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class IDTechMSRAudioModule extends ReactContextBaseJavaModule implements uniMagReaderMsg {

  private uniMagReader _uniMagReader = null;
  private ReactApplicationContext _reactContext = null;
  private AutoConfigProfile autoConfigProfile = new AutoConfigProfile();
  public static final String CALLBACK_EVENT_NAME = "IdTechUniMagEvent";


  public IDTechMSRAudioModule(ReactApplicationContext reactContext) {
    super(reactContext);
    _reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "IDTECH_MSR_audio";
  }

  @ReactMethod
  public void activate(Integer readerType, Integer swipeTimeout, Boolean logging, Promise promise) {
    Integer statusCode = UmRet.UMRET_SUCCESS.getValue();
    String message = "";

    if (_uniMagReader!=null) {
			_uniMagReader.unregisterListen();
			_uniMagReader.release();
			_uniMagReader = null;
		}

    ReaderType _readerType;
    UmReader umReaderType = UmReader.valueOf(readerType);
    switch (umReaderType)	{
      case UMREADER_UNIMAG_ORIGINAL:
        _readerType = ReaderType.UM;
        break;
      case UMREADER_UNIMAG_PRO:
        _readerType = ReaderType.UM_PRO;
        break;
      case UMREADER_UNIMAG_II:
        _readerType = ReaderType.UM_II;
        break;
      case UMREADER_SHUTTLE:
      default:
        _readerType = ReaderType.SHUTTLE;
        break;
    }

		_uniMagReader = new uniMagReader(this, _reactContext, _readerType);

		if (_uniMagReader != null) {
      _uniMagReader.registerListen();
      _uniMagReader.setTimeoutOfSwipeCard(swipeTimeout == 0 ? Integer.MAX_VALUE : swipeTimeout);
      _uniMagReader.setVerboseLoggingEnable(logging);

      StructConfigParameters acProfile = autoConfigProfile.loadAutoConfigProfile(_reactContext);

      if (acProfile != null) {
        sendEvent(CALLBACK_EVENT_NAME, autoConfigProfile.toWritableMap(acProfile));
        _uniMagReader.connectWithProfile(acProfile);
        message = "Found existing auto config profile.";
      }
      else {
        message = "Starting auto config.";
        _uniMagReader.startAutoConfig(true);

        // ID Tech's device profile table is too limited for production use.
        // _uniMagReader.setXMLFileNameWithPath("/sdcard/IDT_uniMagCfg.xml");
        // if (_uniMagReader.loadingConfigurationXMLFile(true)) {
        //   message = "Found existing config file.";
        //   _uniMagReader.connect();
        // }
      }
    }
    else {
      statusCode = UmRet.UMRET_NO_READER.getValue();
      message = "Failed to initialize UniMag";
    }

    WritableMap result = Arguments.createMap();
    result.putInt("statusCode", statusCode);
    result.putString("message", message);
    promise.resolve(result);
  }

  @ReactMethod
  public void deactivate(Promise promise) {
    if (_uniMagReader != null) {
      _uniMagReader.stopSwipeCard();
      _uniMagReader.unregisterListen();
      _uniMagReader.release();
		}

    WritableMap result = Arguments.createMap();
    result.putInt("statusCode", UmRet.UMRET_SUCCESS.getValue());
    result.putString("message", "");
    promise.resolve(result);
  }

  @ReactMethod
  public void swipe(Promise promise) {
    Integer statusCode = UmRet.UMRET_SUCCESS.getValue();
    String message = "Starting swipe...";

    if (_uniMagReader != null) {
      if (_uniMagReader.startSwipeCard()) {

      }
    }
    else {
      statusCode = UmRet.UMRET_NO_READER.getValue();
      message = "Unable to start swipe.";
    }

    WritableMap result = Arguments.createMap();
    result.putInt("statusCode", statusCode);
    result.putString("message", message);
    promise.resolve(result);
  }


  // ---------------------------------------------------------------------------
  // Helper methods
  private void sendEvent(String eventName,
                         @Nullable WritableMap params) {
    _reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
      if (bytes == null) return "";

      char[] hexChars = new char[bytes.length * 2];
      for (int j = 0; j < bytes.length; j++) {
          int v = bytes[j] & 0xFF;
          hexChars[j * 2] = HEX_ARRAY[v >>> 4];
          hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
      }
      return new String(hexChars);
  }

  // ---------------------------------------------------------------------------
  // Required callbacks for uniMagReaderMsg
  public void onReceiveMsgToConnect() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umConnection_starting");
    result.putString("type", "initializing");
    result.putString("message", "Starting connection with reader.");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgConnected() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umConnection_connected");
    result.putString("type", "connected");
    result.putString("message", "Reader successfully connected.");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgDisconnected() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umConnection_disconnected");
    result.putString("type", "disconnected");
    result.putString("message", "Reader has been disconnected.");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgTimeout(String strTimeoutMsg) {
    WritableMap result = Arguments.createMap();
    if ("Swipe card".equals(strTimeoutMsg)) {
      result.putString("originalType", "umSwipe_timeout");
      result.putString("type", "swipe_timeout");
      result.putString("message", "Swipe timed out, please try again");
    } else if ("Start Auto config failed".equals(strTimeoutMsg)) {
      result.putString("originalType", "umAutoconfig_timeout");
      result.putString("type", "autoconfig_timeout");
      result.putString("message", "Autoconfiguration timeout");
    } else if ("Connect the reader with unsupported phone".equals(strTimeoutMsg)) {
      result.putString("originalType", "umDevice_unsupported");
      result.putString("type", "device_unsupported");
      result.putString("message", "Your device appears to be unsupported");
    } else { // if ("Connect the reader".equals(strTimeoutMsg)) {
      result.putString("originalType", "umConnection_timeout");
      result.putString("type", "connection_timeout");
      result.putString("message", "Connecting with reader timed out. Please try again.");
    }
    if (strTimeoutMsg != null) {
      result.putString("originalMessage", strTimeoutMsg);
    }
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgToSwipeCard() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umSwipe_starting");
    result.putString("type", "swiping");
    result.putString("message", "Waiting for card swipe...");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgCardData(byte flagOfCardData, byte[] cardData) {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umSwipe_receivedSwipe");
    result.putString("type", "swipe_received");
    result.putString("message", "Successful card swipe");
    result.putString("data", IDTechMSRAudioModule.bytesToHex(cardData));
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgProcessingCardData() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umSwipe_processing_card_data");
    result.putString("type", "swipe_processing");
    result.putString("message", "");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgToCalibrateReader() {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umSwipe_calibrate_card_reader");
    result.putString("type", "calibrate");
    result.putString("message", "");
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgCommandResult(int commandID, byte[] cmdReturn) {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umCommand_result");
    result.putString("type", "command_result");
    result.putString("message", Integer.toString(commandID));
    result.putString("result", new String(cmdReturn, java.nio.charset.StandardCharsets.ISO_8859_1));
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  @Deprecated
  public void onReceiveMsgSDCardDFailed(String strMSRData) {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umSD_card_failed");
    result.putString("type", "sd_card_failed");
    result.putString("message", strMSRData);
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgFailureInfo(int index , String strMessage) {
    WritableMap result = Arguments.createMap();
    if (index == 8) {
      result.putString("originalType", "umConnection_lowVolume");
      result.putString("type", "low_volume");
      result.putString("message", strMessage);
      result.putInt("index", index);
    } else {
      result.putString("originalType", "umFail");
      result.putString("type", "failed");
      result.putString("message", strMessage);
      result.putInt("index", index);
    }
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgAutoConfigProgress(int progressValue) {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umAutoconfig_progress");
    result.putString("type", "autoconfig_progress");
    result.putString("message", Integer.toString(progressValue));
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgAutoConfigProgress(int percent, double res, String profileName) {
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umAutoconfig_progress");
    result.putString("type", "autoconfig_progress");
    result.putString("message", Integer.toString(percent));
    result.putDouble("result", res);
    result.putString("profileName", profileName);
    sendEvent(CALLBACK_EVENT_NAME, result);
  }

  public void onReceiveMsgAutoConfigCompleted(StructConfigParameters profile) {
    if (!autoConfigProfile.saveAutoConfigProfile(profile, _reactContext)) {
      WritableMap saveResult = Arguments.createMap();
      saveResult.putString("originalType", "umAutoconfig_save_failed");
      saveResult.putString("type", "autoconfig_save_failed");
      saveResult.putString("message", "Failed to save auto config profile.");
      sendEvent(CALLBACK_EVENT_NAME, saveResult);
    }

    sendEvent(CALLBACK_EVENT_NAME, autoConfigProfile.toWritableMap(profile));
    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umAutoconfig_complete");
    result.putString("type", "autoconfig_complete");
    result.putString("message", "Completed autoconfig. Connecting to reader.");
    sendEvent(CALLBACK_EVENT_NAME, result);
    _uniMagReader.connectWithProfile(profile);
  }

  public boolean getUserGrant(int type, String strMessage) {
    boolean getUserGranted = false;
		switch(type) {
  		case uniMagReaderMsg.typeToPowerupUniMag:
  		case uniMagReaderMsg.typeToUpdateXML:
  		case uniMagReaderMsg.typeToOverwriteXML:
  		case uniMagReaderMsg.typeToReportToIdtech:
  			getUserGranted = true;
  			break;
		}

    WritableMap result = Arguments.createMap();
    result.putString("originalType", "umUser_grant");
    result.putString("type", "user_permissions");
    result.putString("message", strMessage);
    result.putInt("result", type);
    sendEvent(CALLBACK_EVENT_NAME, result);

		return getUserGranted;
  }

}
