package com.vintechplc.telebirr.setups;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.ravenioet.telebirr.R;
import com.vintechplc.telebirr.logs.SessionLogger;
import com.vintechplc.telebirr.model.PaymentResult;
import com.vintechplc.telebirr.model.SDKPayRequest;
import com.vintechplc.telebirr.model.WebResponse;
import com.vintechplc.telebirr.utils.EncryptUtils;
import com.vintechplc.telebirr.utils.PayUtil;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class PayOnWeb extends Activity {

    private WebView mWebView;

    private final String TAG = "web";
    public static String host = "";
    private String outTradeNo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        Intent intent = getIntent();
        if(intent.hasExtra("host")){
            host = intent.getStringExtra("host");
            initWebView(host);
            initData();
            PayUtil.getInstance().setWebViewActivity(this);
        }else {
            PaymentResult result;
            result = new PaymentResult();
            result.setCode(-10);
            result.setMsg("Unable to identify host address");
            PayUtil.getInstance().callBackPaymentResult(result);
            finish();
        }
    }

    @SuppressLint("CheckResult")
    private void initData() {
        Object obj = getIntent().getSerializableExtra(PayUtil.TRADESDKPAY);
        if (null == obj) {
            SessionLogger.log(TAG, "initData tradeSDKPayRequest is null");
            return;
        }
        SDKPayRequest request = (SDKPayRequest) obj;
        Object objNo = getIntent().getSerializableExtra(PayUtil.OUTTRADENO);
        if (null == objNo) {
            SessionLogger.log(TAG, "initData outTradeNO is null");
        }
        outTradeNo = (String) objNo;
        NetWorkManager.getInstance().getRequest().toTradeWebPay(request).subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<WebResponse>() {
            @Override
            public void onSubscribe(Disposable d) {
                SessionLogger.log(TAG, "Disposable");
            }

            @Override
            public void onNext(@NonNull WebResponse tradeWebPayResponse) {
                SessionLogger.log(TAG, "toTradeSDKPay success code " + tradeWebPayResponse.getCode() + ", message " + tradeWebPayResponse.getMsg());
                if ("200".equals(tradeWebPayResponse.getCode())) {
                    if (null == tradeWebPayResponse.getData()) {
                        SessionLogger.log(TAG, "toTradeSDKPay success data is null ");
                         return;
                    }
                    mWebView.loadUrl(tradeWebPayResponse.getData().getToPayUrl());
                    mWebView.evaluateJavascript("(function() { return document.getElementsByTagName('html')[0].innerHTML; })();",
                            html -> {
                                SessionLogger.log(TAG, html);
                            });
                } else {
                    PaymentResult result;
                    result = new PaymentResult();
                    result.setCode(-10);
                    result.setMsg(tradeWebPayResponse.getMsg());
                    PayUtil.getInstance().callBackPaymentResult(result);
                    finish();
                 }
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                SessionLogger.log(TAG, "toTradeSDKPay " + throwable.getMessage());
                PaymentResult result;
                    result = new PaymentResult();
                    result.setCode(-10);
                    result.setMsg("Network Error");
                PayUtil.getInstance().callBackPaymentResult(result);
                finish();
            }

            @Override
            public void onComplete() {
                SessionLogger.log(TAG, "Complete");
            }
        });
    }

    private void initWebView(String host) {
        SessionLogger.log(TAG, "initData tradeSDKPayRequest host-> "+host);
        NetWorkManager.getInstance().init(host);
        mWebView = findViewById(R.id.webView);
        //mWebview.setWebViewClient(new XWebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient());
        WebSettings webSettings = mWebView.getSettings();

        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setJavaScriptEnabled(true);

        webSettings.setAllowContentAccess(false);

        webSettings.setGeolocationEnabled(false);

        webSettings.setAllowFileAccess(false);

        webSettings.setDomStorageEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setPluginState(WebSettings.PluginState.ON);

        mWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        mWebView.addJavascriptInterface(new javascriptCallback(), "android");

    }
    private class XWebViewClient extends WebViewClient{
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mWebView.evaluateJavascript("(function() { return document.getElementsByTagName('html')[0].innerHTML; })();",
                    html -> {
                        SessionLogger.log(TAG, html);
                    });
        }
    }
    public class javascriptCallback {
        @JavascriptInterface
        public void paymentResult(String jsonstr) {
            SessionLogger.log(TAG, "paymentResult " + jsonstr);
            Object obj = EncryptUtils.getInstance().JSONToObj(jsonstr, PaymentResult.class);
            PaymentResult result;
            if (obj == null) {
                result = new PaymentResult();
                result.setCode(PaymentResult.SERVER_ERROR);
                result.setMsg("server error");
                PayUtil.getInstance().callBackPaymentResult(result);
            } else {
                result = (PaymentResult) obj;
                if (result.getData() != null) {
                    result.getData().setOutTradeNo(outTradeNo);
                }
                if (result.getCode() == 0) {
                    SessionLogger.log(TAG, "result code " + result.getCode());
                    PayUtil.getInstance().callBackPaymentResult(result);
                }
            }
            finish();
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        PaymentResult result = new PaymentResult();
        result.setCode(-3);
        result.setMsg("Payment Cancelled");
        PayUtil.getInstance().callBackPaymentResult(result);
        PayUtil.getInstance().stopPayment();
        finish();
    }

}