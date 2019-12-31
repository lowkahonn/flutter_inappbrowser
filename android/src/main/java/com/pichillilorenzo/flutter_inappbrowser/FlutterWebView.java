package com.pichillilorenzo.flutter_inappbrowser;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.pichillilorenzo.flutter_inappbrowser.InAppWebView.InAppWebView;
import com.pichillilorenzo.flutter_inappbrowser.InAppWebView.InAppWebViewOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import static io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import static io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformView;

public class FlutterWebView implements PlatformView, MethodCallHandler  {

  static final String LOG_TAG = "FlutterWebView";

  public final Activity activity;
  public InAppWebView webView;
  public MethodChannel channel;
  public final Registrar registrar;

  public FlutterWebView(Registrar registrar, int id, HashMap<String, Object> params) {

    this.registrar = registrar;
    this.activity = registrar.activity();

    String initialUrl = (String) params.get("initialUrl");
    String initialFile = (String) params.get("initialFile");
    Map<String, String> initialData = (Map<String, String>) params.get("initialData");
    Map<String, String> initialHeaders = (Map<String, String>) params.get("initialHeaders");
    HashMap<String, Object> initialOptions = (HashMap<String, Object>) params.get("initialOptions");

    InAppWebViewOptions options = new InAppWebViewOptions();
    options.parse(initialOptions);

    webView = new InAppWebView(registrar, this, id, options);
    webView.prepare();

    channel = new MethodChannel(registrar.messenger(), "com.pichillilorenzo/flutter_inappwebview_" + id);
    channel.setMethodCallHandler(this);

    if (initialFile != null) {
      try {
        initialUrl = Util.getUrlAsset(registrar, initialFile);
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(LOG_TAG, initialFile + " asset file cannot be found!", e);
        return;
      }
    }

    if (initialData != null) {
      String data = initialData.get("data");
      String mimeType = initialData.get("mimeType");
      String encoding = initialData.get("encoding");
      String baseUrl = initialData.get("baseUrl");
      webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, null);
    }
    else
      webView.loadUrl(initialUrl, initialHeaders);
  }

  @Override
  public View getView() {
    return webView;
  }

  @Override
  public void onMethodCall(MethodCall call, final Result result) {
    String source;
    String urlFile;
    switch (call.method) {
      case "getUrl":
        result.success((webView != null) ? webView.getUrl() : null);
        break;
      case "getTitle":
        result.success((webView != null) ? webView.getTitle() : null);
        break;
      case "getProgress":
        result.success((webView != null) ? webView.getProgress() : null);
        break;
      case "loadUrl":
        if (webView != null)
          webView.loadUrl(call.argument("url").toString(), (Map<String, String>) call.argument("headers"), result);
        else
          result.success(false);
        break;
      case "postUrl":
        if (webView != null)
          webView.postUrl(call.argument("url").toString(), (byte[]) call.argument("postData"), result);
        else
          result.success(false);
        break;
      case "loadData":
        {
          String data = call.argument("data").toString();
          String mimeType = call.argument("mimeType").toString();
          String encoding = call.argument("encoding").toString();
          String baseUrl = call.argument("baseUrl").toString();

          if (webView != null)
            webView.loadData(data, mimeType, encoding, baseUrl, result);
          else
            result.success(false);
        }
        break;
      case "loadFile":
        if (webView != null)
          webView.loadFile(call.argument("url").toString(), (Map<String, String>) call.argument("headers"), result);
        else
          result.success(false);
        break;
      case "injectScriptCode":
        if (webView != null) {
          source = call.argument("source").toString();
          webView.injectScriptCode(source, result);
        }
        else {
          result.success("");
        }
        break;
      case "injectScriptFile":
        if (webView != null) {
          urlFile = call.argument("urlFile").toString();
          webView.injectScriptFile(urlFile);
        }
        result.success(true);
        break;
      case "injectStyleCode":
        if (webView != null) {
          source = call.argument("source").toString();
          webView.injectStyleCode(source);
        }
        result.success(true);
        break;
      case "injectStyleFile":
        if (webView != null) {
          urlFile = call.argument("urlFile").toString();
          webView.injectStyleFile(urlFile);
        }
        result.success(true);
        break;
      case "reload":
        if (webView != null)
          webView.reload();
        result.success(true);
        break;
      case "goBack":
        if (webView != null)
          webView.goBack();
        result.success(true);
        break;
      case "canGoBack":
        result.success((webView != null) && webView.canGoBack());
        break;
      case "goForward":
        if (webView != null)
          webView.goForward();
        result.success(true);
        break;
      case "canGoForward":
        result.success((webView != null) && webView.canGoForward());
        break;
      case "goBackOrForward":
        if (webView != null)
          webView.goBackOrForward((Integer) call.argument("steps"));
        result.success(true);
        break;
      case "canGoBackOrForward":
        result.success((webView != null) && webView.canGoBackOrForward((Integer) call.argument("steps")));
        break;
      case "stopLoading":
        if (webView != null)
          webView.stopLoading();
        result.success(true);
        break;
      case "isLoading":
        result.success((webView != null) && webView.isLoading());
        break;
      case "takeScreenshot":
        result.success((webView != null) ? webView.takeScreenshot() : null);
        break;
      case "setOptions":
        if (webView != null) {
          InAppWebViewOptions inAppWebViewOptions = new InAppWebViewOptions();
          HashMap<String, Object> inAppWebViewOptionsMap = (HashMap<String, Object>) call.argument("options");
          inAppWebViewOptions.parse(inAppWebViewOptionsMap);
          webView.setOptions(inAppWebViewOptions, inAppWebViewOptionsMap);
        }
        result.success(true);
        break;
      case "getOptions":
        result.success((webView != null) ? webView.getOptions() : null);
        break;
      case "getCopyBackForwardList":
        result.success((webView != null) ? webView.getCopyBackForwardList() : null);
        break;
      case "loadPaymentData":
        if (call.arguments != null) {
          PaymentDataRequest request = PaymentDataRequest.fromJson((String) call.arguments);
          int env = WalletConstants.ENVIRONMENT_PRODUCTION;
          PaymentsClient client = Wallet.getPaymentsClient(activity,
              new Wallet.WalletOptions.Builder().setEnvironment(env).build());
          Task<PaymentData> task = client.loadPaymentData(request);
          task.addOnSuccessListener(new OnSuccessListener<PaymentData>() {
            @Override
            public void onSuccess(PaymentData paymentData) {
              if (paymentData.toJson() != null) {
                Map<String, Object> data = new HashMap<>();
                Log.d("PaymentData", String.valueOf(paymentData.toJson()));
                data.put("paymentData", paymentData.toJson());
              }
            }
          }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
              Log.d("GooglePayError", e.toString());
            }
          });
        }
        break;
      case "dispose":
        dispose();
        result.success(true);
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void dispose() {
    if (webView != null) {
      webView.setWebChromeClient(new WebChromeClient());
      webView.setWebViewClient(new WebViewClient() {
        public void onPageFinished(WebView view, String url) {
          webView.destroy();
          webView = null;
        }
      });
      webView.loadUrl("about:blank");
    }
  }
}