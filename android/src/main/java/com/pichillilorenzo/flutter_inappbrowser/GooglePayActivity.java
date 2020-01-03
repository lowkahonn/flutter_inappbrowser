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
import java.io.Serializable;

public class GooglePayActivity extends Activity {

  protected static final String LOG_TAG = "GooglePayActivity";
  private PaymentsClient mPaymentsClient;
  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;
  private Map paymentDataRequest;
  private String environment;
  private Boolean mIsReadyToPay;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle b = getIntent().getExtras();
    assert b != null;
    paymentDataRequest = (Map) b.getSerializable("paymentDataRequest");
    environment = (String) b.getString("environment");
    int env = WalletConstants.ENVIRONMENT_TEST;
    if (environment.equals("production")) {
      env = WalletConstants.ENVIRONMENT_PRODUCTION;
    }
    mPaymentsClient = Wallet.getPaymentsClient(this, new Wallet.WalletOptions.Builder().setEnvironment(env).build());
    Log.d("GooglePayActivity", "onCreate from GooglePayActivity");
    checkIsGooglePayAvailable();
  }

  @Override
  protected void onStart() {
    super.onStart();
    requestPayment();
  }

  private void checkIsGooglePayAvailable() {
    if (mIsReadyToPay == null) {
      IsReadyToPayRequest request = IsReadyToPayRequest.newBuilder()
        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
        .build();
      // The call to isReadyToPay is asynchronous and returns a Task. We need to provide an
      // OnCompleteListener to be triggered when the result of the call is known.
      Task<Boolean> task = mPaymentsClient.isReadyToPay(request);
      task.addOnCompleteListener(this,
        new OnCompleteListener<Boolean>() {
          @Override
          public void onComplete(Task<Boolean> task) {
            if (task.isSuccessful()) {
              mIsReadyToPay = true;
              Log.d("IsGooglePayAvailable", String.valueOf(mIsReadyToPay));
            } else {
              mIsReadyToPay = false;
              Log.w("isReadyToPay failed", task.getException());
            }
          }
        });
    }
  }

  private void requestPayment() {
    if (mIsReadyToPay) {
      try {
        Log.d("GooglePayActivity", "requestPayment from GooglePayActivity");
        JSONObject paymentData = new JSONObject((Map) paymentDataRequest);

        Log.d("PaymentData", String.valueOf(paymentData.toString()));
        PaymentDataRequest request = PaymentDataRequest.fromJson(paymentData.toString());
        Log.d("request", String.valueOf(request));
        this.makePayment(request);
      } catch (Exception e) {
        callToDartOnError(e.getMessage());
      }
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
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
    Intent returnIntent = new Intent();
    Bundle b = new Bundle();
    b.putSerializable("result", (Serializable) data);
    returnIntent.putExtras(b);
    setResult(Activity.RESULT_OK,returnIntent);
    finish();
  }

  private void callToDartOnCanceled() {
    Map<String, Object> data = new HashMap<>();
    data.put("status", "RESULT_CANCELED");
    data.put("description", "Canceled by user");
    Intent returnIntent = new Intent();
    Bundle b = new Bundle();
    b.putSerializable("result", (Serializable) data);
    returnIntent.putExtras(b);
    setResult(Activity.RESULT_CANCELED,returnIntent);
    finish();
  }

  private void callToDartOnError(String error) {
    Map<String, Object> data = new HashMap<>();
    data.put("error", error);
    Intent returnIntent = new Intent();
    Bundle b = new Bundle();
    b.putSerializable("result", (Serializable) data);
    returnIntent.putExtras(b);
    setResult(Activity.RESULT_CANCELED,returnIntent);
    finish();
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
    Intent returnIntent = new Intent();
    Bundle b = new Bundle();
    b.putSerializable("result", (Serializable) data);
    returnIntent.putExtras(b);
    setResult(Activity.RESULT_CANCELED,returnIntent);
    finish();
  }
}
