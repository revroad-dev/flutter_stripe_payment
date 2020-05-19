package com.gettipsi.stripe;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.gettipsi.stripe.util.ArgCheck;
import com.gettipsi.stripe.util.Converters;
import com.gettipsi.stripe.util.Fun0;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.CardRequirements;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.ShippingAddressRequirements;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.stripe.android.BuildConfig;
import com.stripe.android.*;
import com.stripe.android.model.*;

import java.util.Arrays;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.gettipsi.stripe.Errors.toErrorCode;
import static com.gettipsi.stripe.util.Converters.convertTokenToWritableMap;
import static com.gettipsi.stripe.util.Converters.convertPaymentMethodToWritableMap;
import static com.gettipsi.stripe.util.Converters.getAllowedShippingCountryCodes;
import static com.gettipsi.stripe.util.Converters.getBillingAddress;
import static com.gettipsi.stripe.util.Converters.putExtraToTokenMap;
import static com.gettipsi.stripe.util.PayParams.CURRENCY_CODE;
import static com.gettipsi.stripe.util.PayParams.BILLING_ADDRESS_REQUIRED;
import static com.gettipsi.stripe.util.PayParams.SHIPPING_ADDRESS_REQUIRED;
import static com.gettipsi.stripe.util.PayParams.PHONE_NUMBER_REQUIRED;
import static com.gettipsi.stripe.util.PayParams.EMAIL_REQUIRED;
import static com.gettipsi.stripe.util.PayParams.TOTAL_PRICE;

/**
 * Created by ngoriachev on 13/03/2018.
 * see https://developers.google.com/pay/api/tutorial
 */
public final class GoogleApiPayFlowImpl extends PayFlow {

  private static final String TAG = GoogleApiPayFlowImpl.class.getSimpleName();
  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 65534;

  private PaymentsClient mPaymentsClient;
  private Promise payPromise;
  private Stripe stripe;

  public GoogleApiPayFlowImpl(@NonNull Fun0<Activity> activityProvider) {
    super(activityProvider);
  }

  private PaymentsClient createPaymentsClient(@NonNull Activity activity) {
    return Wallet.getPaymentsClient(
      activity,
      new Wallet.WalletOptions.Builder().setEnvironment(getEnvironment()).build());
  }

  private void isReadyToPay(@NonNull Activity activity, boolean isExistingPaymentMethodRequired, @NonNull final Promise promise) {
    ArgCheck.nonNull(activity);
    ArgCheck.nonNull(promise);

    IsReadyToPayRequest request =
      IsReadyToPayRequest.newBuilder()
        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
        .setExistingPaymentMethodRequired(isExistingPaymentMethodRequired)
        .build();
    mPaymentsClient = createPaymentsClient(activity);
    Task<Boolean> task = mPaymentsClient.isReadyToPay(request);
    task.addOnCompleteListener(
      new OnCompleteListener<Boolean>() {
        public void onComplete(Task<Boolean> task) {
          try {
            boolean result = task.getResult(ApiException.class);
            promise.resolve(result);
          } catch (ApiException exception) {
            promise.reject(toErrorCode(exception), exception.getMessage());
          }
        }
      });
  }

  private PaymentMethodTokenizationParameters createPaymentMethodTokenizationParameters() {
    return PaymentMethodTokenizationParameters.newBuilder()
      .setPaymentMethodTokenizationType(WalletConstants.PAYMENT_METHOD_TOKENIZATION_TYPE_PAYMENT_GATEWAY)
      .addParameter("gateway", "stripe")
      .addParameter("stripe:publishableKey", getPublishableKey())
      .addParameter("stripe:version", BuildConfig.VERSION_NAME)
      .build();
  }

  private PaymentDataRequest createPaymentDataRequest(ReadableMap payParams) throws JSONException {
    final String estimatedTotalPrice = payParams.getString(TOTAL_PRICE);
    final String currencyCode = payParams.getString(CURRENCY_CODE);
    final boolean billingAddressRequired = Converters.getValue(payParams, BILLING_ADDRESS_REQUIRED, false);
    final boolean shippingAddressRequired = Converters.getValue(payParams, SHIPPING_ADDRESS_REQUIRED, false);
    final boolean phoneNumberRequired = Converters.getValue(payParams, PHONE_NUMBER_REQUIRED, false);
    final boolean emailRequired = Converters.getValue(payParams, EMAIL_REQUIRED, false);
    final Collection<String> allowedCountryCodes = getAllowedShippingCountryCodes(payParams);

    return createPaymentDataRequest(
      estimatedTotalPrice,
      currencyCode,
      billingAddressRequired,
      shippingAddressRequired,
      phoneNumberRequired,
      emailRequired,
      allowedCountryCodes
    );
  }

    @NonNull
    private PaymentDataRequest createPaymentDataRequest(@NonNull final String totalPrice,
                                                @NonNull final String currencyCode,
                                                final boolean billingAddressRequired,
                                                final boolean shippingAddressRequired,
                                                final boolean phoneNumberRequired,
                                                final boolean emailRequired,
                                                @NonNull final Collection<String> countryCodes
    ) throws JSONException {
        final JSONObject tokenizationSpec =
                new GooglePayConfig(getPublishableKey()).getTokenizationSpecification();
        final JSONObject cardPaymentMethod = new JSONObject()
                .put("type", "CARD")
                .put(
                        "parameters",
                        new JSONObject()
                                .put("allowedAuthMethods", new JSONArray()
                                        .put("PAN_ONLY")
                                        .put("CRYPTOGRAM_3DS"))
                                .put("allowedCardNetworks",
                                        new JSONArray()
                                                .put("AMEX")
                                                .put("DISCOVER")
                                                .put("JCB")
                                                .put("MASTERCARD")
                                                .put("VISA"))

                                // require billing address
                                .put("billingAddressRequired", billingAddressRequired)
                                .put(
                                        "billingAddressParameters",
                                        new JSONObject()
                                                // require full billing address
                                                .put("format", "FULL")

                                                // require phone number
                                                .put("phoneNumberRequired", phoneNumberRequired)
                                )
                )
                .put("tokenizationSpecification", tokenizationSpec);

        // create PaymentDataRequest
        final JSONObject paymentDataRequest = new JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0)
                .put("allowedPaymentMethods",
                        new JSONArray().put(cardPaymentMethod))
                .put("transactionInfo", new JSONObject()
                        .put("totalPrice", totalPrice)
                        .put("totalPriceStatus", "FINAL")
                        .put("currencyCode", currencyCode)
                )
                .put("merchantInfo", new JSONObject()
                        .put("merchantName", "Example Merchant"))

                // require email address
                .put("emailRequired", emailRequired);

        return PaymentDataRequest.fromJson(paymentDataRequest.toString());
    }

  private void startPaymentRequest(@NonNull Activity activity, @NonNull PaymentDataRequest request) {
    ArgCheck.nonNull(activity);
    ArgCheck.nonNull(request);

    mPaymentsClient = createPaymentsClient(activity);

    AutoResolveHelper.resolveTask(
      mPaymentsClient.loadPaymentData(request),
      activity,
      LOAD_PAYMENT_DATA_REQUEST_CODE);
  }

  @Override
  public void paymentRequestWithAndroidPay(@NonNull ReadableMap payParams, @NonNull Promise promise, @NonNull Stripe stripe) {
    ArgCheck.nonNull(payParams);
    ArgCheck.nonNull(promise);

    Activity activity = activityProvider.call();
    if (activity == null) {
      promise.reject(
        getErrorCode("activityUnavailable"),
        getErrorDescription("activityUnavailable")
      );
      return;
    }

    try {
        this.payPromise = promise;
        this.stripe = stripe;
        startPaymentRequest(activity, createPaymentDataRequest(payParams));
    } catch (JSONException e) {

    }
  }

  @Override
  public void deviceSupportsAndroidPay(boolean isExistingPaymentMethodRequired, @NonNull Promise promise) {
    Activity activity = activityProvider.call();
    if (activity == null) {
      promise.reject(
        getErrorCode("activityUnavailable"),
        getErrorDescription("activityUnavailable")
      );
      return;
    }

    if (!isPlayServicesAvailable(activity)) {
      promise.reject(
        getErrorCode("playServicesUnavailable"),
        getErrorDescription("playServicesUnavailable")
      );
      return;
    }

    isReadyToPay(activity, isExistingPaymentMethodRequired, promise);
  }

  public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (payPromise == null) {
      return false;
    }

    switch (requestCode) {
      case LOAD_PAYMENT_DATA_REQUEST_CODE:
        switch (resultCode) {
          case Activity.RESULT_OK:
              if (data != null) {
                  try {
                      onGooglePayResult(data);
                  }
                  catch (JSONException e) {
                      payPromise.reject(
                              getErrorCode("parseResponse"),
                              getErrorDescription("parseResponse")
                      );
                      payPromise = null;
                  }
              }
              break;
          case Activity.RESULT_CANCELED:
            payPromise.reject(
              getErrorCode("purchaseCancelled"),
              getErrorDescription("purchaseCancelled")
            );
            payPromise = null;
            break;
          case AutoResolveHelper.RESULT_ERROR:
            Status status = AutoResolveHelper.getStatusFromIntent(data);
            // Log the status for debugging.
            // Generally, there is no need to show an error to
            // the user as the Google Pay API will do that.
            payPromise.reject(
              getErrorCode("stripe"),
              status.getStatusMessage()
            );
            payPromise = null;
            break;

          default:
            // Do nothing.
        }
        return true;
    }

    return false;
  }

    private void onGooglePayResult(@NonNull Intent data) throws JSONException {
        final PaymentData paymentData = PaymentData.getFromIntent(data);
        if (paymentData == null) {
            return;
        }
        final PaymentMethodCreateParams paymentMethodCreateParams =
                PaymentMethodCreateParams.createFromGooglePay(
                        new JSONObject(paymentData.toJson()));
        stripe.createPaymentMethod(
                paymentMethodCreateParams,
                new ApiResultCallback<PaymentMethod>() {
                    @Override
                    public void onSuccess(@NonNull PaymentMethod result) {
                        payPromise.resolve(convertPaymentMethodToWritableMap(result));
                        payPromise = null;
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        payPromise.reject(
                                getErrorCode("parseResponse"),
                                getErrorDescription("parseResponse")
                        );
                        payPromise = null;
                    }
                }
        );
    }
}
