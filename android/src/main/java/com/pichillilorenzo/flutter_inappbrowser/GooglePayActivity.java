package com.pichillilorenzo.flutter_inappbrowser;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.os.Bundle;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import com.pichillilorenzo.flutter_inappbrowser.InAppBrowserFlutterPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

public class GooglePayActivity extends Activity {

  protected static final String LOG_TAG = "GooglePayActivity";
  private PaymentsClient mPaymentsClient;
  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;
  private Map paymentDataRequest;
  private String environment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle b = getIntent().getExtras();
    assert b != null;
    paymentDataRequest = (Map) b.getSerializable("paymentDataRequest");
    environment = b.getString("environment");

    int env = WalletConstants.ENVIRONMENT_PRODUCTION;
    if (environment == "test") {
      env = WalletConstants.ENVIRONMENT_TEST;
    }
    mPaymentsClient = Wallet.getPaymentsClient(this, new Wallet.WalletOptions.Builder().setEnvironment(env).build());
    Log.d("GooglePayActivity", "onCreate from GooglePayActivity");
    Log.d("onCreate", env.toString());
  }

  @Override
  protected void onStart() {
    super.onStart();
    requestPayment();
  }

  private void requestPayment() {
    try {
      Log.d("GooglePayActivity", "requestPayment from GooglePayActivity");
      JSONObject paymentData = new JSONObject(paymentDataRequest);
      PaymentDataRequest request = PaymentDataRequest.fromJson(paymentData.toString());
      Log.d("request", String.valueOf(paymentData));
      this.makePayment(request);
    } catch (Exception e) {
      callToDartOnError(e.getMessage());
    }
  }

  private void makePayment(PaymentDataRequest request) {
    if (request != null) {
      Log.d("GooglePayActivity", "Making Payment");
      Task<PaymentData> task = mPaymentsClient.loadPaymentData(request);
      AutoResolveHelper.resolveTask(task, this, LOAD_PAYMENT_DATA_REQUEST_CODE);
    }
}

  @Override
  protected void onStop() {
    super.onStop();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
      switch (resultCode) {
        case Activity.RESULT_OK:
          PaymentData paymentData = PaymentData.getFromIntent(data);
          if (paymentData != null) {
            this.callToDartOnPaymentSuccess(paymentData);
          }
        case Activity.RESULT_CANCELED:
          this.callToDartOnCanceled();
        case AutoResolveHelper.RESULT_ERROR:
          Status status = AutoResolveHelper.getStatusFromIntent(data);
          this.callToDartOnError(status);
      }
    }
  }

  private void callToDartOnPaymentSuccess(PaymentData paymentData) {
    String paymentInfo = paymentData.toJson();
    Log.d("PaymentData:", String.valueOf(paymentInfo));

    Map<String, Object> data = new HashMap<>();
    data.put("status", paymentInfo != null ? "SUCCESS" : "UNKNOWN");
    if (paymentInfo != null) {
        data.put("result", paymentInfo);
    }
    InAppBrowserFlutterPlugin.channel.invokeMethod("parsePaymentData", data);
  }

  private void callToDartOnCanceled() {
    Map<String, Object> data = new HashMap<>();
    data.put("status", "RESULT_CANCELED");
    data.put("description", "Canceled by user");
    InAppBrowserFlutterPlugin.channel.invokeMethod("parsePaymentData", data);
  }

  private void callToDartOnError(String error) {
    Map<String, Object> data = new HashMap<>();
    data.put("error", error);
    InAppBrowserFlutterPlugin.channel.invokeMethod("parsePaymentData", data);
  }

  private void callToDartOnError(Status status) {
    Map<String, Object> data = new HashMap<>();
    if (status != null) {
      String statusMessage = status.getStatusMessage();
      if (TextUtils.isEmpty(statusMessage)) {
        statusMessage = "payment error";
      }
      int code = status.getStatusCode();
      String statusCode;
      switch (code) {
        case 8:
          statusCode = "RESULT_INTERNAL_ERROR";
          break;
        case 10:
          statusCode = "DEVELOPER_ERROR";
          break;
        case 15:
          statusCode = "RESULT_TIMEOUT";
          break;
        case 16:
          statusCode = "RESULT_CANCELED";
          break;
        case 18:
          statusCode = "RESULT_DEAD_CLIENT";
          break;
        default:
          statusCode = "UNKNOWN";
      }
      data.put("error", statusMessage);
      data.put("status", statusCode);
      data.put("description", status.toString());
    } else {
      data.put("error", "Wrong payment data");
      data.put("status", "UNKNOWN");
      data.put("description", "Payment finished without additional information");
    }
    InAppBrowserFlutterPlugin.channel.invokeMethod("parsePaymentData", data);
  }
}
